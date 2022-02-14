package net.redstonecraft.sqlorm

import net.redstonecraft.sqlorm.translator.MySQLTranslator
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.sql.SQLSyntaxErrorException

@Testcontainers
internal class MariaDBTest {

    @Container
    val container = MariaDBContainer("mariadb:latest")

    private fun setupMariaDb() = Database(DriverManager.getConnection(container.jdbcUrl, container.username, container.password), MySQLTranslator).makeCurrent()

    @Test
    fun mariaDb() {
        setupMariaDb().use {
            mySql<SQLSyntaxErrorException>()
        }
    }

}
