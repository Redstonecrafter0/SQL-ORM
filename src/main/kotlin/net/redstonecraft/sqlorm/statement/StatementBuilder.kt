package net.redstonecraft.sqlorm.statement

import net.redstonecraft.sqlorm.*
import java.sql.PreparedStatement
import java.sql.Statement
import java.sql.Types
import kotlin.reflect.jvm.jvmName

class SQLStatement(val queryString: String, val data: List<Any?> = emptyList()) {

    val isQuery by lazy { queryString.startsWith("SELECT ") }

    fun build(db: Database = Database.current): PreparedStatement {
        println(queryString)
        return (db.connection.prepareStatement(queryString, Statement.RETURN_GENERATED_KEYS)).also {
            for ((i, v) in data.withIndex()) {
                it.setValue(i, v)
            }
        }
    }

    fun PreparedStatement.setValue(i: Int, v: Any?) = when (v) {
        is String -> setString(i + 1, v)
        is Int -> setInt(i + 1, v)
        is Long -> setLong(i + 1, v)
        is ByteArray -> setBytes(i + 1, v)
        is Boolean -> setBoolean(i + 1, v)
        is Float -> setFloat(i + 1, v)
        is Double -> setDouble(i + 1, v)
        null -> setNull(i + 1, Types.NULL)
        else -> error("Unsupported type ${v::class.jvmName}")
    }
}

fun List<SQLStatement>.combine(): SQLStatement {
    return SQLStatement(joinToString("\n") { it.queryString }, map { it.data }.flatten())
}

@SqlDSL2
fun create(database: Database = Database.current, block: StatementBuilder.Create.() -> Unit): List<SQLStatement> {
    val builder = StatementBuilder.Create(database)
    builder.block()
    return builder.statements
}

@SqlDSL2
fun insert(database: Database = Database.current, block: StatementBuilder.Insert.() -> Unit): List<SQLStatement> {
    val builder = StatementBuilder.Insert(database)
    builder.block()
    return builder.statements
}

@SqlDSL2
fun update(database: Database = Database.current, block: StatementBuilder.Update.() -> Unit): List<SQLStatement> {
    val builder = StatementBuilder.Update(database)
    builder.block()
    return builder.statements
}

@SqlDSL2
fun delete(database: Database = Database.current, block: StatementBuilder.Delete.() -> Unit): List<SQLStatement> {
    val builder = StatementBuilder.Delete(database)
    builder.block()
    return builder.statements
}

@SqlDSL2
fun select(database: Database = Database.current, block: StatementBuilder.Select.() -> Unit): List<SQLStatement> {
    val builder = StatementBuilder.Select(database)
    builder.block()
    return builder.statements
}

open class StatementBuilder private constructor(val database: Database) {

    val statements = mutableListOf<SQLStatement>()

    companion object {
        fun escape(s: String) = "`${s.replace(" `", "` `")}`"
        operator fun String.unaryPlus() = escape(this)
    }

    class Create(database: Database) : StatementBuilder(database) {

        @SqlDSL1
        operator fun String.invoke(block: Data.() -> Unit) {
            val data = Data()
            data.block()
            val foreign = data.data.map { it.foreignKeys }
            statements += SQLStatement("CREATE TABLE IF NOT EXISTS ${+this} (${
                data.data.joinToString(", ") { "${+it.name} ${it.type}${it.attributes}" }
            }${if (foreign.flatten().isNotEmpty()) ", ${foreign.flatten().joinToString(", ")}" else ""});")
        }

        inner class Data {
            val data = mutableListOf<Attributes>()
            @SqlDSL4 fun char(n: Int, name: String): Attributes { data += Attributes(name, database.dbType.translate(SQLType.CHAR, n)); return data.last() }
            @SqlDSL4 fun varchar(n: Int, name: String): Attributes { data += Attributes(name, database.dbType.translate(SQLType.VARCHAR, n)); return data.last() }
            @SqlDSL4 fun text(name: String): Attributes { data += Attributes(name, database.dbType.translate(SQLType.TEXT)); return data.last() }
            @SqlDSL4 fun int(name: String): Attributes { data += Attributes(name, database.dbType.translate(SQLType.INT)); return data.last() }
            @SqlDSL4 fun bigint(name: String): Attributes { data += Attributes(name, database.dbType.translate(SQLType.BIGINT)); return data.last() }
            @SqlDSL4 fun blob(name: String): Attributes { data += Attributes(name, database.dbType.translate(SQLType.BLOB)); return data.last() }
            @SqlDSL4 fun boolean(name: String): Attributes { data += Attributes(name, database.dbType.translate(SQLType.BOOLEAN)); return data.last() }
            @SqlDSL4 fun float(name: String): Attributes { data += Attributes(name, database.dbType.translate(SQLType.FLOAT)); return data.last() }
            @SqlDSL4 fun double(name: String): Attributes { data += Attributes(name, database.dbType.translate(SQLType.DOUBLE)); return data.last() }

            inner class Attributes(val name: String, val type: String) {

                var attributes = ""
                    private set

                val foreignKeys = mutableListOf<String>()

                @SqlDSL3
                fun primaryKey(): Attributes {
                    if ("NOT NULL" !in attributes) {
                        attributes += " PRIMARY KEY ${database.dbType.autoIncrement}"
                    }
                    return this
                }

                @SqlDSL3
                fun notNull(): Attributes {
                    if ("PRIMARY KEY" !in attributes) {
                        attributes += " NOT NULL"
                    }
                    return this
                }

                @SqlDSL3
                fun unique(): Attributes {
                    attributes += " UNIQUE"
                    return this
                }

                @SqlDSL3
                fun foreignKey(table: String, key: String): Attributes {
                    foreignKeys += "FOREIGN KEY (${+name}) REFERENCES ${+table}(${+key})"
                    return this
                }
            }
        }
    }

    class Insert(database: Database) : StatementBuilder(database) {

        @SqlDSL1
        operator fun String.invoke(block: Data.() -> Unit) {
            val data = Data()
            data.block()
            statements += SQLStatement("INSERT INTO ${+this} (${
                data.data.keys.joinToString(", ") { +it }
            }) VALUES (${
                data.data.values.joinToString(", ") { "?" }
            });", data.data.values.toList())
        }

        class Data {
            val data = mutableMapOf<String, Any?>()
            operator fun String.invoke(value: Any?) { data += this to value }
        }
    }

    class Update(database: Database) : StatementBuilder(database) {

        @SqlDSL1
        operator fun String.invoke(block: Data.() -> Unit) {
            val data = Data()
            data.block()
            statements += SQLStatement("UPDATE ${+this} SET ${
                data.data.keys.joinToString(", ") { "${+it} = ?" }
            }${data.where};", data.data.values + data.whereData)
        }

        class Data {
            val data = mutableMapOf<String, Any?>()
            var where = ""
            var whereData = listOf<Any?>()
            operator fun String.invoke(value: Any?) { data += this to value }

            @SqlDSL4
            fun where(block: Where.() -> List<Where.Clause>) {
                val (sql, data) = Where.where(block)
                where = " WHERE $sql"
                whereData = data
            }
        }
    }

    class Delete(database: Database) : StatementBuilder(database) {

        @SqlDSL1
        operator fun String.invoke(block: Data.() -> Unit) {
            val data = Data()
            data.block()
            statements += SQLStatement("DELETE FROM ${+this}${data.where};", data.whereData)
        }

        class Data {
            var where = ""
            var whereData = listOf<Any?>()

            @SqlDSL4
            fun where(block: Where.() -> List<Where.Clause>) {
                val (sql, data) = Where.where(block)
                where = " WHERE $sql"
                whereData = data
            }
        }
    }

    class Select(database: Database) : StatementBuilder(database) {

        @SqlDSL1
        @UnescapedSQL
        operator fun String.invoke(vararg fields: String = arrayOf("*"), block: Data.() -> Unit) {
            val data = Data()
            data.block()
            statements += SQLStatement("SELECT ${fields.joinToString(", ")} FROM ${+this}${data.where};", data.whereData)
        }

        class Data {
            var where = ""
            var whereData = listOf<Any?>()

            @SqlDSL4
            fun where(block: Where.() -> List<Where.Clause>) {
                val (sql, data) = Where.where(block)
                where = " WHERE $sql"
                whereData = data
            }
        }
    }

    class Where {

        companion object {
            fun where(block: Where.() -> List<Clause>): Pair<String, List<Any?>> {
                val data = Where()
                val clauses = data.block()
                return clauses.joinToString("") { it.queryString } to clauses.map { it.data }.flatten()
            }
        }

        @SqlDSL3 infix fun String.eq(value: Any?) = listOf(Clause("${+this} = ?", listOf(value)))
        @SqlDSL3 infix fun String.notEq(value: Any?) = listOf(Clause("${+this} != ?", listOf(value)))
        @SqlDSL3 infix fun String.greater(value: Any?) = listOf(Clause("${+this} > ?", listOf(value)))
        @SqlDSL3 infix fun String.less(value: Any?) = listOf(Clause("${+this} < ?", listOf(value)))
        @SqlDSL3 infix fun String.greaterEq(value: Any?) = listOf(Clause("${+this} >= ?", listOf(value)))
        @SqlDSL3 infix fun String.lessEq(value: Any?) = listOf(Clause("${+this} <= ?", listOf(value)))
        @SqlDSL3 infix fun String.between(value: Pair<Number?, Number?>) = listOf(Clause("${+this} BETWEEN ? AND ?", listOf(value.first, value.second)))
        @SqlDSL3 infix fun String.notBetween(value: Pair<Number?, Number?>) = listOf(Clause("${+this} NOT BETWEEN ? AND ?", listOf(value.first, value.second)))
        @SqlDSL3 infix fun String.like(value: String) = listOf(Clause("${+this} LIKE ${+value}"))
        @SqlDSL3 infix fun String.notLike(value: String) = listOf(Clause("${+this} NOT LIKE ${+value}"))

        @SqlDSL3 infix fun String.isIn(value: SQLStatement) = if (value.isQuery) listOf(Clause("${+this} IN (${value.queryString})", value.data)) else error("Not a query.")
        @SqlDSL3 infix fun String.isIn(value: List<Any?>) = listOf(Clause("${+this} IN (${value.joinToString(", ") { "?" }})", value))
        @SqlDSL3 infix fun String.notIn(value: SQLStatement) = if (value.isQuery) listOf(Clause("${+this} NOT IN (${value.queryString})", value.data)) else error("Not a query.")
        @SqlDSL3 infix fun String.notIn(value: List<Any?>) = listOf(Clause("${+this} NOT IN (${value.joinToString(", ") { "?" }})", value))

        @SqlDSL3 infix fun List<Clause>.and(other: List<Clause>) = listOf(Clause(" (")) + this + Clause(") AND (") + other + Clause(") ")
        @SqlDSL3 infix fun List<Clause>.or(other: List<Clause>) = listOf(Clause(" (")) + this + Clause(") OR (") + other + Clause(") ")

        class Clause(val queryString: String, val data: List<Any?> = emptyList())

    }

}
