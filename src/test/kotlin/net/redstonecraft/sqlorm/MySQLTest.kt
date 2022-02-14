package net.redstonecraft.sqlorm

import com.mysql.cj.jdbc.exceptions.MysqlDataTruncation
import net.redstonecraft.sqlorm.translator.MySQLTranslator
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager

@Testcontainers
internal class MySQLTest {

    @Container
    val container = MySQLContainer("mysql:latest")

    private fun setupMySQL() = Database(DriverManager.getConnection(container.jdbcUrl, container.username, container.password), MySQLTranslator).makeCurrent()

    @Test
    fun mysql() {
        setupMySQL().use {
            mySql<MysqlDataTruncation>()
        }
    }

}
