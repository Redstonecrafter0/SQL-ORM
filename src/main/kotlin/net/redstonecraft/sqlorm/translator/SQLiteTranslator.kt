package net.redstonecraft.sqlorm.translator

import net.redstonecraft.sqlorm.SQLType
import net.redstonecraft.sqlorm.SQLTypeTranslator

/**
 * The translator instance for SQLite
 * */
object SQLiteTranslator : SQLTypeTranslator {

    override val autoIncrement = "AUTOINCREMENT"

    override fun translate(type: SQLType, n: Int?) = when (type) {
        SQLType.CHAR -> "TEXT"
        SQLType.VARCHAR -> "TEXT"
        SQLType.TEXT -> "TEXT"
        SQLType.INT -> "INTEGER"
        SQLType.BIGINT -> "INTEGER"
        SQLType.BLOB -> "BLOB"
        SQLType.BOOLEAN -> "INTEGER"
        SQLType.FLOAT -> "REAL"
        SQLType.DOUBLE -> "REAL"
    }

}
