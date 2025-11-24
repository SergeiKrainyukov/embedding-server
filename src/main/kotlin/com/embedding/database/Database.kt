package com.embedding.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

/**
 * Таблица для хранения эмбеддингов.
 */
object Embeddings : LongIdTable("embeddings") {
    val text = text("text")
    val embedding = text("embedding") // JSON массив
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}

/**
 * Менеджер базы данных.
 */
object DatabaseManager {
    
    fun init() {
        // H2 in-memory база данных для простоты
        Database.connect(
            url = "jdbc:h2:mem:embeddings;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        
        transaction {
            SchemaUtils.create(Embeddings)
        }
        
        println("✅ Database initialized")
    }
}
