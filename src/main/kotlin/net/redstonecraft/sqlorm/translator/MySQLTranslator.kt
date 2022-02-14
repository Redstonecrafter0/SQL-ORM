package net.redstonecraft.sqlorm.translator

import net.redstonecraft.sqlorm.SQLType
import net.redstonecraft.sqlorm.SQLTypeTranslator

/**
 * The translator instance for MySQL/MariaDB
 * */
object MySQLTranslator : SQLTypeTranslator {

    override val autoIncrement = "AUTO_INCREMENT"

    override fun translate(type: SQLType, n: Int?) = when (type) {
        SQLType.CHAR -> "CHAR($n)"
        SQLType.VARCHAR -> "VARCHAR($n)"
        SQLType.TEXT -> "TEXT"
        SQLType.INT -> "INT"
        SQLType.BIGINT -> "BIGINT"
        SQLType.BLOB -> "BLOB"
        SQLType.BOOLEAN -> "INT"
        SQLType.FLOAT -> "FLOAT"
        SQLType.DOUBLE -> "DOUBLE"
    }

}
