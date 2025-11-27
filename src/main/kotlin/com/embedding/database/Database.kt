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
 * Таблица для хранения документов.
 */
object Documents : LongIdTable("documents") {
    val fileName = varchar("file_name", 500)
    val fileSize = long("file_size")
    val content = text("content")
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}

/**
 * Таблица для хранения чанков документов с эмбеддингами.
 */
object DocumentChunks : LongIdTable("document_chunks") {
    val documentId = reference("document_id", Documents)
    val chunkIndex = integer("chunk_index")
    val text = text("text")
    val embedding = text("embedding") // JSON массив
    val startPosition = integer("start_position")
    val endPosition = integer("end_position")
    val tokenCount = integer("token_count")
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}

/**
 * Менеджер базы данных.
 */
object DatabaseManager {

    fun init() {
        // H2 файловая база данных (сохраняется между перезапусками)
        Database.connect(
            url = "jdbc:h2:file:./data/embeddings;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE",
            driver = "org.h2.Driver"
        )

        transaction {
            SchemaUtils.create(Embeddings, Documents, DocumentChunks)
        }

        println("✅ Database initialized (file-based at ./data/embeddings)")
    }
}
