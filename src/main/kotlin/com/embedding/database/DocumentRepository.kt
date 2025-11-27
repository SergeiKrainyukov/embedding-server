package com.embedding.database

import com.embedding.models.DocumentInfo
import com.embedding.models.DocumentChunkInfo
import com.embedding.services.NormalizationService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.format.DateTimeFormatter

/**
 * Репозиторий для работы с документами и их чанками.
 */
class DocumentRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val normalizationService = NormalizationService()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * Сохраняет документ в базу данных.
     */
    fun saveDocument(fileName: String, content: String, fileSize: Long): Long {
        return transaction {
            Documents.insertAndGetId {
                it[Documents.fileName] = fileName
                it[Documents.content] = content
                it[Documents.fileSize] = fileSize
            }.value
        }
    }

    /**
     * Сохраняет чанк документа с эмбеддингом.
     */
    fun saveChunk(
        documentId: Long,
        chunkIndex: Int,
        text: String,
        embedding: List<Double>,
        startPosition: Int,
        endPosition: Int,
        tokenCount: Int
    ): Long {
        return transaction {
            DocumentChunks.insertAndGetId {
                it[DocumentChunks.documentId] = documentId
                it[DocumentChunks.chunkIndex] = chunkIndex
                it[DocumentChunks.text] = text
                it[DocumentChunks.embedding] = json.encodeToString(embedding)
                it[DocumentChunks.startPosition] = startPosition
                it[DocumentChunks.endPosition] = endPosition
                it[DocumentChunks.tokenCount] = tokenCount
            }.value
        }
    }

    /**
     * Получает информацию о документе по ID.
     */
    fun findDocumentById(id: Long): DocumentInfo? {
        return transaction {
            Documents.select { Documents.id eq id }
                .map { row ->
                    val documentId = row[Documents.id].value
                    val chunksCount = DocumentChunks.select { DocumentChunks.documentId eq documentId }.count().toInt()

                    DocumentInfo(
                        id = documentId,
                        fileName = row[Documents.fileName],
                        fileSize = row[Documents.fileSize],
                        chunksCount = chunksCount,
                        createdAt = row[Documents.createdAt].format(dateFormatter)
                    )
                }
                .firstOrNull()
        }
    }

    /**
     * Получает все документы.
     */
    fun findAllDocuments(): List<DocumentInfo> {
        return transaction {
            Documents.selectAll().map { row ->
                val documentId = row[Documents.id].value
                val chunksCount = DocumentChunks.select { DocumentChunks.documentId eq documentId }.count().toInt()

                DocumentInfo(
                    id = documentId,
                    fileName = row[Documents.fileName],
                    fileSize = row[Documents.fileSize],
                    chunksCount = chunksCount,
                    createdAt = row[Documents.createdAt].format(dateFormatter)
                )
            }
        }
    }

    /**
     * Получает все чанки документа.
     */
    fun findChunksByDocumentId(documentId: Long): List<DocumentChunkInfo> {
        return transaction {
            (DocumentChunks innerJoin Documents)
                .select { DocumentChunks.documentId eq documentId }
                .orderBy(DocumentChunks.chunkIndex to SortOrder.ASC)
                .map { row ->
                    DocumentChunkInfo(
                        id = row[DocumentChunks.id].value,
                        documentId = row[DocumentChunks.documentId].value,
                        documentName = row[Documents.fileName],
                        chunkIndex = row[DocumentChunks.chunkIndex],
                        text = row[DocumentChunks.text],
                        startPosition = row[DocumentChunks.startPosition],
                        endPosition = row[DocumentChunks.endPosition],
                        tokenCount = row[DocumentChunks.tokenCount]
                    )
                }
        }
    }

    /**
     * Поиск похожих чанков документов по косинусному сходству.
     */
    fun findSimilarChunks(queryEmbedding: List<Double>, topK: Int = 5): List<Triple<DocumentChunkInfo, Double, String>> {
        return transaction {
            val chunks = (DocumentChunks innerJoin Documents)
                .selectAll()
                .map { row ->
                    val embedding = json.decodeFromString<List<Double>>(row[DocumentChunks.embedding])
                    val similarity = normalizationService.cosineSimilarity(queryEmbedding, embedding)

                    val chunkInfo = DocumentChunkInfo(
                        id = row[DocumentChunks.id].value,
                        documentId = row[DocumentChunks.documentId].value,
                        documentName = row[Documents.fileName],
                        chunkIndex = row[DocumentChunks.chunkIndex],
                        text = row[DocumentChunks.text],
                        startPosition = row[DocumentChunks.startPosition],
                        endPosition = row[DocumentChunks.endPosition],
                        tokenCount = row[DocumentChunks.tokenCount]
                    )

                    Triple(chunkInfo, similarity, row[Documents.fileName])
                }
                .sortedByDescending { it.second }
                .take(topK)

            chunks
        }
    }

    /**
     * Удаляет документ и все его чанки.
     */
    fun deleteDocument(id: Long): Boolean {
        return transaction {
            DocumentChunks.deleteWhere { DocumentChunks.documentId eq id }
            Documents.deleteWhere { Documents.id eq id } > 0
        }
    }

    /**
     * Количество документов в базе.
     */
    fun countDocuments(): Long {
        return transaction {
            Documents.selectAll().count()
        }
    }

    /**
     * Количество чанков в базе.
     */
    fun countChunks(): Long {
        return transaction {
            DocumentChunks.selectAll().count()
        }
    }
}