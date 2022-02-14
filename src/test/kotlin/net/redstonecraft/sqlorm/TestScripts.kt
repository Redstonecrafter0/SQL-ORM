package net.redstonecraft.sqlorm

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import kotlin.math.roundToInt

inline fun <reified E: Throwable> mySql() {
    Database.createTable<TestModel>()
    val entity = TestModel(
        char = "chars",
        varchar = "char",
        text = "text",
        int = 5,
        bigInt = Int.MAX_VALUE + 10L,
        blob = byteArrayOf(4, 2, 0),
        boolean = true,
        float = .2F,
        double = .2
    )
    Database += entity
    val entity2 = TestModel(
        char = "chars",
        varchar = "char",
        text = "lol",
        int = 9,
        bigInt = Int.MAX_VALUE + 10L,
        blob = byteArrayOf(4, 2, 0),
        boolean = true,
        float = .5F,
        double = .4
    )
    Database += entity2
    assertEquals(entity.id + 1, entity2.id) { "Primary Key did not increase" }
    val entity3 = TestModel(
        char = "way to long",
        varchar = "char",
        text = "text",
        int = 5,
        bigInt = Int.MAX_VALUE + 10L,
        blob = byteArrayOf(4, 2, 0),
        boolean = true,
        float = .5F,
        double = .2
    )
    assertThrows<E> { Database += entity3 }
    assertations(entity, entity2)
}

inline fun assertations(entity: TestModel, entity2: TestModel) {
    assertEquals(entity2, Database.get<TestModel>(entity2.id)) { "Entity data is not equal" }
    assertEquals(2, Database.count<TestModel>()) { "Wrong count" }
    assertEquals(1, Database.count<TestModel> { TestModel::id eq 1 }) { "Count of existing id must be 1" }
    assertEquals(entity2, Database.query<TestModel> { (TestModel::text eq "lol") and (TestModel::id eq 2L) }.first())
    assertEquals(listOf(entity, entity2), Database.query<TestModel> { (TestModel::id eq entity.id) or (TestModel::id eq entity2.id) })
    assertEquals(1, Database.count<TestModel> { TestModel::id greater entity.id })
    assertEquals(1, Database.count<TestModel> { TestModel::id greaterEq (entity2.id) })
    assertEquals(1, Database.count<TestModel> { TestModel::id less entity2.id })
    assertEquals(1, Database.count<TestModel> { TestModel::id lessEq (entity.id) })
    assertEquals(1, Database.count<TestModel> { TestModel::int between (3 to 6) })
    assertEquals(1, Database.count<TestModel> { TestModel::int notBetween (3 to 6) })
    assertEquals(1, Database.count<TestModel> { TestModel::text like "l%" })
    assertEquals(1, Database.count<TestModel> { TestModel::text notLike "l%" })
    assertEquals(1, Database.count<TestModel> { TestModel::int isIn listOf(9) })
    assertEquals(1, Database.count<TestModel> { TestModel::int notIn listOf(9) })
    assertEquals(14.0, Database.sum<TestModel>(TestModel::int))
    assertEquals(9.0, Database.sum<TestModel>(TestModel::int) { TestModel::id eq entity2.id })
    assertEquals(3, (Database.avg<TestModel>(TestModel::double) * 10).roundToInt())
    assertEquals(4, (Database.avg<TestModel>(TestModel::double) { TestModel::id eq entity2.id } * 10).roundToInt())
}

data class TestModel(
    @PrimaryKey var id: Long = -1,
    @SQLChar(5) var char: String,
    @SQLVarchar(5) var varchar: String,
    var text: String,
    var int: Int,
    var bigInt: Long,
    var blob: ByteArray?,
    var boolean: Boolean,
    var float: Float,
    var double: Double
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TestModel

        if (id != other.id) return false
        if (char != other.char) return false
        if (varchar != other.varchar) return false
        if (text != other.text) return false
        if (int != other.int) return false
        if (bigInt != other.bigInt) return false
        if (blob != null) {
            if (other.blob == null) return false
            if (!blob.contentEquals(other.blob)) return false
        } else if (other.blob != null) return false
        if (boolean != other.boolean) return false
        if (float != other.float) return false
        if (double != other.double) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + char.hashCode()
        result = 31 * result + varchar.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + int
        result = 31 * result + bigInt.hashCode()
        result = 31 * result + (blob?.contentHashCode() ?: 0)
        result = 31 * result + boolean.hashCode()
        result = 31 * result + float.hashCode()
        result = 31 * result + double.hashCode()
        return result
    }
}
