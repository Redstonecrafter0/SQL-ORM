@file:Suppress("EXPERIMENTAL_IS_NOT_ENABLED")

package net.redstonecraft.sqlorm

import net.redstonecraft.sqlorm.statement.*
import net.redstonecraft.sqlorm.statement.StatementBuilder.Companion.unaryPlus
import java.io.Closeable
import java.sql.*
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaGetter
import kotlin.reflect.jvm.jvmErasure

/**
 * This is the entrypoint of the library.
 * @constructor Creates an instance of an existing database connection.
 * @param connection Connection of the existing database.
 * @param dbType Translator to create the SQL statements. Currently, either [net.redstonecraft.sqlorm.translator.MySQLTranslator] or [net.redstonecraft.sqlorm.translator.SQLiteTranslator].
 * */
class Database(val connection: Connection, val dbType: SQLTypeTranslator): Closeable {

    override fun close() = connection.close()

    companion object {
        lateinit var current: Database

        private fun parse(clazz: KClass<*>, resultSet: ResultSet): Any {
            return clazz.primaryConstructor!!.callBy(buildMap {
                for (i in clazz.primaryConstructor!!.parameters) {
                    this += i to resultSet.getValue(i.name!!, i.type.jvmErasure)
                }
            })
        }

        inline fun <reified T> createTable() = current.createTable<T>()
        inline operator fun <reified T> get(id: Any): T = current[id]
        fun set(value: Any) = current.set(value)
        fun add(value: Any) = current.add(value)
        fun update(value: Any) = current.update(value)
        inline operator fun <reified T> contains(value: T) = value in current
        inline fun <reified T> count(noinline block: Query.() -> List<Query.Clause> = { emptyList() }) = current.count<T>(block)
        inline fun <reified T> sum(prop: KProperty<*>, noinline block: Query.() -> List<Query.Clause> = { emptyList() }) = current.sum<T>(prop, block)
        inline fun <reified T> avg(prop: KProperty<*>, noinline block: Query.() -> List<Query.Clause> = { emptyList() }) = current.avg<T>(prop, block)
        fun remove(value: Any) = current.remove(value)
        inline fun <reified T> query(noinline block: Query.() -> List<Query.Clause> = { emptyList() }) = current.query<T>(block)
        operator fun plusAssign(value: Any) { current += value }
        operator fun minusAssign(value: Any) { current -= value }
    }

    /**
     * Make this database current on the companion object.
     * */
    fun makeCurrent(): Database {
        current = this
        return this
    }

    /**
     * Create a new table from the definition of the data class.s
     * */
    inline fun <reified T> createTable() = createTable(T::class)

    /**
     * Create a new table from the definition of the data class.
     * */
    fun createTable(clazz: KClass<*>) {
        require(clazz.declaredMemberProperties.count { it.hasAnnotation<PrimaryKey>() } == 1) { "${clazz.simpleName} has no property declared as PrimaryKey." }
        create(this) {
            clazz.simpleName!! {
                for (i in clazz.declaredMemberProperties) {
                    val attr = when (val type = i.declaringClass) {
                        String::class -> when {
                            i.hasAnnotation<SQLChar>() -> char(i.findAnnotation<SQLChar>()!!.n, i.name)
                            i.hasAnnotation<SQLVarchar>() -> varchar(i.findAnnotation<SQLVarchar>()!!.n, i.name)
                            else -> text(i.name)
                        }
                        Int::class -> int(i.name)
                        Long::class -> bigint(i.name)
                        ByteArray::class -> blob(i.name)
                        Boolean::class -> boolean(i.name)
                        Float::class -> float(i.name)
                        Double::class -> double(i.name)
                        else -> {
                            val key = i.declaringClass.declaredMemberProperties.first { it.hasAnnotation<PrimaryKey>() }
                            when (key.declaringClass) {
                                String::class -> when {
                                    i.hasAnnotation<SQLChar>() -> char(i.findAnnotation<SQLChar>()!!.n, i.name)
                                    i.hasAnnotation<SQLVarchar>() -> varchar(i.findAnnotation<SQLVarchar>()!!.n, i.name)
                                    else -> text(i.name)
                                }
                                Int::class -> int(i.name)
                                Long::class -> bigint(i.name)
                                ByteArray::class -> blob(i.name)
                                Boolean::class -> boolean(i.name)
                                Float::class -> float(i.name)
                                Double::class -> double(i.name)
                                else -> error("Unacceptable primary key")
                            }.foreignKey(type.simpleName!!, key.name)
                        }
                    }
                    if (i.hasAnnotation<PrimaryKey>()) attr.primaryKey()
                    if (!clazz.primaryConstructor!!.parameters.first { it.name == i.name }.type.isMarkedNullable) attr.notNull()
                    if (i.hasAnnotation<Unique>()) attr.unique()
                }
            }
        }.combine().build(this).executeUpdate()
    }

    /**
     * Get an entry by the data class and id.
     * */
    inline operator fun <reified T> get(id: Any): T = get(T::class, id) as T

    /**
     * Get an entry by the data class and id.
     * */
    @OptIn(UnescapedSQL::class)
    operator fun get(clazz: KClass<*>, id: Any): Any {
        val result = select(this) {
            clazz.simpleName!! {
                where {
                    clazz.declaredMemberProperties.first { it.hasAnnotation<PrimaryKey>() }.name eq id
                }
            }
        }.combine().build(this).executeQuery()
        return if (result.next()) {
            parse(clazz, result)
        } else {
            error("No result")
        }
    }

    /**
     * Adds the entry if it does not already exist or modifies it.
     * Try to use [add] and [update] directly to reduce overhead.
     * */
    fun set(value: Any) = if (value in this) {
        update(value)
    } else {
        add(value)
    }

    /**
     * Adds a new entry.
     * */
    fun add(value: Any): Boolean {
        val statement = insert(this) {
            value::class.simpleName!! {
                for (i in value::class.declaredMemberProperties.filter {
                    !(it.hasAnnotation<PrimaryKey>() && it.getter.call(value) == -1L)
                }) {
                    i.name(i.getter.call(value))
                }
            }
        }.combine().build(this)
        statement.executeUpdate()
        val result = statement.generatedKeys
        return if (result.next()) {
            (value::class.declaredMemberProperties.first { it.hasAnnotation<PrimaryKey>() } as KMutableProperty<*>).setter.call(value, result.getLong(1))
            true
        } else {
            false
        }
    }

    /**
     * Updates an existing entry.
     * */
    fun update(value: Any) = update(this) {
        value::class.simpleName!! {
            for (i in value::class.declaredMemberProperties) {
                i.name(i.getter.call(value))
            }
        }
    }.combine().build(this).executeUpdate() != 0

    inline operator fun <reified T> contains(value: T) = contains(T::class, value)

    fun contains(clazz: KClass<*>, value: Any?) = count(clazz) {
        val id = clazz.declaredMemberProperties.first { it.hasAnnotation<PrimaryKey>() }
        id eq id.getter.call(value)
    } > 0

    /**
     * Counts the entries matching the specified criteria.
     * */
    inline fun <reified T> count(noinline block: Query.() -> List<Query.Clause> = { emptyList() } ): Int = count(T::class, block)

    /**
     * Counts the entries matching the specified criteria.
     * */
    @OptIn(UnescapedSQL::class)
    fun count(clazz: KClass<*>, block: Query.() -> List<Query.Clause> = { emptyList() }): Int {
        val result = select(this) {
            clazz.simpleName!!("COUNT(*) AS `count`") {
                val (sql, data) = Query.where(clazz, block)
                where = sql
                whereData = data
            }
        }.combine().build(this).executeQuery()
        return if (result.next()) {
            result.getInt("count")
        } else {
            0
        }
    }

    /**
     * Sums up the fields matching the specified criteria.
     * */
    inline fun <reified T> sum(prop: KProperty<*>, noinline block: Query.() -> List<Query.Clause> = { emptyList() }): Double = sum(T::class, prop, block)

    /**
     * Sums up the fields matching the specified criteria.
     * */
    @OptIn(UnescapedSQL::class)
    fun sum(clazz: KClass<*>, prop: KProperty<*>, block: Query.() -> List<Query.Clause> = { emptyList() }): Double {
        val result = select(this) {
            clazz.simpleName!!("SUM(${+prop.name}) AS `sum`") {
                val (sql, data) = Query.where(clazz, block)
                where = sql
                whereData = data
            }
        }.combine().build(this).executeQuery()
        return if (result.next()) {
            result.getDouble("sum")
        } else {
            .0
        }
    }

    /**
     * Returns the average value of the fields matching the specified criteria.
     * */
    inline fun <reified T> avg(prop: KProperty<*>, noinline block: Query.() -> List<Query.Clause> = { emptyList() }): Double = avg(T::class, prop, block)

    /**
     * Returns the average value of the fields matching the specified criteria.
     * */
    @OptIn(UnescapedSQL::class)
    fun avg(clazz: KClass<*>, prop: KProperty<*>, block: Query.() -> List<Query.Clause> = { emptyList() }): Double {
        val result = select(this) {
            clazz.simpleName!!("AVG(${+prop.name}) AS `avg`") {
                val (sql, data) = Query.where(clazz, block)
                where = sql
                whereData = data
            }
        }.combine().build(this).executeQuery()
        return if (result.next()) {
            result.getDouble("avg")
        } else {
            .0
        }
    }

    /**
     * Delete an entry.
     * */
    fun remove(value: Any): Boolean {
        val clazz = value::class
        return delete(this) {
            clazz.simpleName!! {
                val (sql, data) = Query.where(clazz) {
                    println(clazz)
                    val pKey = clazz.declaredMemberProperties.first { it.hasAnnotation<PrimaryKey>() }
                    pKey eq pKey.getter.call(value)
                }
                where = sql.also { println(it) }
                whereData = data.also { println(it) }
            }
        }.combine().build(this).executeUpdate() != 0
    }

    /**
     * Query for entries matching the specified criteria.
     * */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> query(noinline block: Query.() -> List<Query.Clause> = { emptyList() }): List<T> = query(T::class, block) as List<T>

    /**
     * Query for entries matching the specified criteria.
     * */
    @OptIn(UnescapedSQL::class)
    fun query(clazz: KClass<*>, block: Query.() -> List<Query.Clause> = { emptyList() }): List<Any> = buildList {
        val result = select(this@Database) {
            clazz.simpleName!! {
                val (sql, data) = Query.where(clazz, block)
                where = " $sql"
                whereData = data
            }
        }.combine().build(this@Database).executeQuery()
        while (result.next()) {
            this += parse(clazz, result)
        }
    }

    operator fun plusAssign(value: Any) { add(value) }

    operator fun minusAssign(value: Any) { remove(value) }

    class Query(private val clazz: KClass<*>) {

        companion object {
            fun where(clazz: KClass<*>, block: Query.() -> List<Clause>): Pair<String, List<Any?>> {
                val data = Query(clazz)
                val clauses = data.block()
                return if (clauses.isEmpty()) {
                    "" to emptyList()
                } else {
                    " WHERE " + clauses.joinToString("") { it.queryString } to clauses.map { it.data }.flatten()
                }
            }
        }

        @SqlDSL3 infix fun <T> KProperty<T>.eq(value: T): List<Clause> {
            require(this.javaGetter!!.declaringClass.kotlin == clazz) { "Property not from declaring class." }
            return listOf(Clause("${+this.name} = ?", listOf(value)))
        }
        @SqlDSL3 infix fun <T> KProperty<T>.notEq(value: T): List<Clause> {
            require(this.javaGetter!!.declaringClass.kotlin == clazz) { "Property not from declaring class." }
            return listOf(Clause("${+this.name} != ?", listOf(value)))
        }
        @SqlDSL3 infix fun <T> KProperty<T>.greater(value: T): List<Clause> {
            require(this.javaGetter!!.declaringClass.kotlin == clazz) { "Property not from declaring class." }
            return listOf(Clause("${+this.name} > ?", listOf(value)))
        }
        @SqlDSL3 infix fun <T> KProperty<T>.less(value: T?): List<Clause> {
            require(this.javaGetter!!.declaringClass.kotlin == clazz) { "Property not from declaring class." }
            return listOf(Clause("${+this.name} < ?", listOf(value)))
        }
        @SqlDSL3 infix fun <T> KProperty<T>.greaterEq(value: T?): List<Clause> {
            require(this.javaGetter!!.declaringClass.kotlin == clazz) { "Property not from declaring class." }
            return listOf(Clause("${+this.name} >= ?", listOf(value)))
        }
        @SqlDSL3 infix fun <T> KProperty<T>.lessEq(value: T?): List<Clause> {
            require(this.javaGetter!!.declaringClass.kotlin == clazz) { "Property not from declaring class." }
            return listOf(Clause("${+this.name} <= ?", listOf(value)))
        }
        @SqlDSL3 infix fun <T> KProperty<T>.between(value: Pair<T, T>): List<Clause> {
            require(this.javaGetter!!.declaringClass.kotlin == clazz) { "Property not from declaring class." }
            return listOf(Clause("${+this.name} BETWEEN ? AND ?", listOf(value.first, value.second)))
        }
        @SqlDSL3 infix fun <T> KProperty<T>.notBetween(value: Pair<T, T>): List<Clause> {
            require(this.javaGetter!!.declaringClass.kotlin == clazz) { "Property not from declaring class." }
            return listOf(Clause("${+this.name} NOT BETWEEN ? AND ?", listOf(value.first, value.second)))
        }
        @SqlDSL3 infix fun KProperty<String>.like(value: String): List<Clause> {
            require(this.javaGetter!!.declaringClass.kotlin == clazz) { "Property not from declaring class." }
            return listOf(Clause("${+this.name} LIKE ?", listOf(value)))
        }
        @SqlDSL3 infix fun KProperty<String>.notLike(value: String): List<Clause> {
            require(this.javaGetter!!.declaringClass.kotlin == clazz) { "Property not from declaring class." }
            return listOf(Clause("${+this.name} NOT LIKE ?", listOf(value)))
        }

        @SqlDSL3 infix fun KProperty<*>.isIn(value: SQLStatement): List<Clause> {
            require(this.javaGetter!!.declaringClass.kotlin == clazz) { "Property not from declaring class." }
            return if (value.isQuery) listOf(Clause("${+this.name} IN (${value.queryString})", value.data)) else error("Not a query.")
        }
        @SqlDSL3 infix fun <T> KProperty<T>.isIn(value: List<T>): List<Clause> {
            require(this.javaGetter!!.declaringClass.kotlin == clazz) { "Property not from declaring class." }
            return listOf(Clause("${+this.name} IN (${value.joinToString(", ") { "?" }})", value))
        }
        @SqlDSL3 infix fun KProperty<*>.notIn(value: SQLStatement): List<Clause> {
            require(this.javaGetter!!.declaringClass.kotlin == clazz) { "Property not from declaring class." }
            return if (value.isQuery) listOf(Clause("${+this.name} NOT IN (${value.queryString})", value.data)) else error("Not a query.")
        }
        @SqlDSL3 infix fun <T> KProperty<T>.notIn(value: List<T>): List<Clause> {
            require(this.javaGetter!!.declaringClass.kotlin == clazz) { "Property not from declaring class." }
            return listOf(Clause("${+this.name} NOT IN (${value.joinToString(", ") { "?" }})", value))
        }

        @SqlDSL3 infix fun List<Clause>.and(other: List<Clause>): List<Clause> {
            return listOf(Clause(" (")) + this + Clause(") AND (") + other + Clause(") ")
        }
        @SqlDSL3 infix fun List<Clause>.or(other: List<Clause>): List<Clause> {
            return listOf(Clause(" (")) + this + Clause(") OR (") + other + Clause(") ")
        }

        class Clause(val queryString: String, val data: List<Any?> = emptyList())

    }

}

fun ResultSet.getValue(key: String, type: KClass<*>): Any? {
    return when (type) {
        String::class -> getString(key)
        Int::class -> getInt(key)
        Long::class -> getLong(key)
        ByteArray::class -> getBytes(key)
        Boolean::class -> getBoolean(key)
        Float::class -> getFloat(key)
        Double::class -> getDouble(key)
        else -> return null
    }.let { if (wasNull()) null else it }
}

val <T> KProperty<T>.declaringClass: KClass<*>
    get() = javaGetter?.returnType?.kotlin ?: error("Unexpected state.")
