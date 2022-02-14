package net.redstonecraft.sqlorm

enum class SQLType {
    CHAR, VARCHAR, TEXT, INT, BIGINT, BLOB, BOOLEAN, FLOAT, DOUBLE
}

interface SQLTypeTranslator {

    fun translate(type: SQLType, n: Int? = null): String
    val autoIncrement: String

}
