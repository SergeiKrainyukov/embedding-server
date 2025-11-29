package com.embedding.services

import com.embedding.database.DocumentRepository
import com.embedding.models.*

/**
 * Сервис для работы с документами.
 * Обрабатывает загрузку, разбиение на чанки, векторизацию и поиск по документам.
 */
class DocumentService(
    private val ollamaClient: OllamaClient = OllamaClient(),
    private val chunkingService: ChunkingService = ChunkingService(),
    private val normalizationService: NormalizationService = NormalizationService(),
    private val repository: DocumentRepository = DocumentRepository()
) {

    /**
     * Загружает документ, разбивает на чанки, векторизует и сохраняет в БД.
     */
    suspend fun uploadDocument(fileName: String, content: String): DocumentUploadResponse {
        val fileSize = content.length.toLong()

        // Сохраняем документ
        val documentId = repository.saveDocument(fileName, content, fileSize)

        // Разбиваем на чанки
        val chunks = chunkingService.chunkText(content)

        // Векторизуем каждый чанк и сохраняем
        for (chunk in chunks) {
            // Получаем эмбеддинг от Ollama
            val rawEmbedding = ollamaClient.getEmbedding(chunk.text)

            // Нормализуем к [-1, 1]
            val normalizedEmbedding = normalizationService.normalize(rawEmbedding)

            // Сохраняем чанк с эмбеддингом
            repository.saveChunk(
                documentId = documentId,
                chunkIndex = chunk.index,
                text = chunk.text,
                embedding = normalizedEmbedding,
                startPosition = chunk.startPosition,
                endPosition = chunk.endPosition,
                tokenCount = chunk.estimatedTokens
            )
        }

        val documentInfo = repository.findDocumentById(documentId)
            ?: throw IllegalStateException("Document not found after creation")

        return DocumentUploadResponse(
            documentId = documentId,
            fileName = fileName,
            fileSize = fileSize,
            chunksCreated = chunks.size,
            createdAt = documentInfo.createdAt
        )
    }

    /**
     * Получает список всех документов.
     */
    fun getAllDocuments(): List<DocumentInfo> {
        return repository.findAllDocuments()
    }

    /**
     * Получает информацию о документе по ID.
     */
    fun getDocumentById(id: Long): DocumentInfo? {
        return repository.findDocumentById(id)
    }

    /**
     * Получает все чанки документа.
     */
    fun getDocumentChunks(documentId: Long): List<DocumentChunkInfo> {
        return repository.findChunksByDocumentId(documentId)
    }

    /**
     * Удаляет документ и все его чанки.
     */
    fun deleteDocument(id: Long): Boolean {
        return repository.deleteDocument(id)
    }

    /**
     * RAG поиск по документам с возвратом кликабельных ссылок на источники.
     */
    suspend fun answerQuestionWithSources(
        question: String,
        topK: Int = 3,
        serverUrl: String = "http://localhost:8080"
    ): DocumentRAGResponse {
        // Получаем эмбеддинг запроса
        val rawEmbedding = ollamaClient.getEmbedding(question)
        val queryEmbedding = normalizationService.normalize(rawEmbedding)

        // Ищем похожие чанки
        val similarChunks = repository.findSimilarChunks(queryEmbedding, topK)

        if (similarChunks.isEmpty()) {
            // Если контекст не найден, отвечаем без RAG
            val answer = ollamaClient.generateAnswer(question, null)
            return DocumentRAGResponse(
                question = question,
                answer = answer,
                sources = emptyList()
            )
        }

        // Формируем контекст из найденных чанков
        val contextText = similarChunks.joinToString("\n\n") { (chunkInfo, similarity, _) ->
            """
            Документ: ${chunkInfo.documentName}
            Чанк: ${chunkInfo.chunkIndex + 1}
            Сходство: ${"%.1f".format(similarity * 100)}%
            Текст: ${chunkInfo.text}
            """.trimIndent()
        }

        // Генерируем ответ с контекстом
        val answer = ollamaClient.generateAnswer(question, contextText)

        // Формируем источники с кликабельными ссылками
        val sources = similarChunks.map { (chunkInfo, similarity, documentName) ->
            DocumentSource(
                documentId = chunkInfo.documentId,
                documentName = documentName,
                chunkIndex = chunkInfo.chunkIndex,
                text = chunkInfo.text.take(300) + if (chunkInfo.text.length > 300) "..." else "",
                similarity = similarity,
                similarityPercent = "${"%.1f".format(similarity * 100)}%",
                link = "$serverUrl/api/documents/${chunkInfo.documentId}/chunks/${chunkInfo.chunkIndex}"
            )
        }

        return DocumentRAGResponse(
            question = question,
            answer = answer,
            sources = sources
        )
    }

    /**
     * Статистика по документам.
     */
    fun getStats(): DocumentStatsResponse {
        return DocumentStatsResponse(
            totalDocuments = repository.countDocuments(),
            totalChunks = repository.countChunks()
        )
    }
}