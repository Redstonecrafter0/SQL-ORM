package net.redstonecraft.sqlorm

import net.redstonecraft.sqlorm.translator.SQLiteTranslator
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.sql.DriverManager

internal class SQLiteTest {

    private fun setupSqlite() = Database(DriverManager.getConnection("jdbc:sqlite::memory:"), SQLiteTranslator).makeCurrent()

    @Test
    fun sqlite() {
        setupSqlite().use {
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
            Database += entity3
            println(Database.query<TestModel>())
            Database -= entity3
            assertEquals(entity2, Database.query<TestModel> { (TestModel::text eq "lol") and (TestModel::id eq 2L) }.first())
            assertEquals(listOf(entity, entity2), Database.query<TestModel> { (TestModel::id eq entity.id) or (TestModel::id eq entity2.id) })
            assertations(entity, entity2)
        }
    }

}
