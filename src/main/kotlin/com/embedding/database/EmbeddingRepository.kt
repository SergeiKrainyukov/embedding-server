package com.embedding.database

import com.embedding.models.StoredEmbedding
import com.embedding.services.NormalizationService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.format.DateTimeFormatter

/**
 * Репозиторий для работы с эмбеддингами в базе данных.
 */
class EmbeddingRepository {
    
    private val json = Json { ignoreUnknownKeys = true }
    private val normalizationService = NormalizationService()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    
    /**
     * Сохраняет эмбеддинг в базу данных.
     */
    fun save(text: String, embedding: List<Double>): Long {
        return transaction {
            Embeddings.insertAndGetId {
                it[Embeddings.text] = text
                it[Embeddings.embedding] = json.encodeToString(embedding)
            }.value
        }
    }
    
    /**
     * Получает эмбеддинг по ID.
     */
    fun findById(id: Long): StoredEmbedding? {
        return transaction {
            Embeddings.select { Embeddings.id eq id }
                .map { row ->
                    StoredEmbedding(
                        id = row[Embeddings.id].value,
                        text = row[Embeddings.text],
                        embedding = json.decodeFromString<List<Double>>(row[Embeddings.embedding]),
                        createdAt = row[Embeddings.createdAt].format(dateFormatter)
                    )
                }
                .firstOrNull()
        }
    }
    
    /**
     * Получает все эмбеддинги.
     */
    fun findAll(): List<StoredEmbedding> {
        return transaction {
            Embeddings.selectAll()
                .map { row ->
                    StoredEmbedding(
                        id = row[Embeddings.id].value,
                        text = row[Embeddings.text],
                        embedding = json.decodeFromString<List<Double>>(row[Embeddings.embedding]),
                        createdAt = row[Embeddings.createdAt].format(dateFormatter)
                    )
                }
        }
    }
    
    /**
     * Удаляет эмбеддинг по ID.
     */
    fun delete(id: Long): Boolean {
        return transaction {
            Embeddings.deleteWhere { Embeddings.id eq id } > 0
        }
    }
    
    /**
     * Поиск похожих эмбеддингов по косинусному сходству.
     */
    fun findSimilar(queryEmbedding: List<Double>, topK: Int = 5): List<Pair<StoredEmbedding, Double>> {
        val allEmbeddings = findAll()
        
        return allEmbeddings
            .map { stored ->
                val similarity = normalizationService.cosineSimilarity(queryEmbedding, stored.embedding)
                stored to similarity
            }
            .sortedByDescending { it.second }
            .take(topK)
    }
    
    /**
     * Количество записей в базе.
     */
    fun count(): Long {
        return transaction {
            Embeddings.selectAll().count()
        }
    }
    
    /**
     * Очистка базы данных.
     */
    fun clear() {
        transaction {
            Embeddings.deleteAll()
        }
    }
}
