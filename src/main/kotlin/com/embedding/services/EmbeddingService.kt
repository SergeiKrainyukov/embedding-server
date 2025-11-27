package com.embedding.services

import com.embedding.database.EmbeddingRepository
import com.embedding.models.*

/**
 * Главный сервис для работы с эмбеддингами.
 * Объединяет чанкинг, запросы к Ollama, нормализацию и хранение.
 */
class EmbeddingService(
    private val ollamaClient: OllamaClient = OllamaClient(),
    private val chunkingService: ChunkingService = ChunkingService(),
    private val normalizationService: NormalizationService = NormalizationService(),
    private val repository: EmbeddingRepository = EmbeddingRepository()
) {
    
    /**
     * Получает эмбеддинг для текста.
     * Если текст большой, разбивает на чанки и усредняет эмбеддинги.
     */
    suspend fun embed(text: String, saveToDb: Boolean = true): EmbedResponse {
        val chunks = chunkingService.chunkText(text)
        
        val chunkInfos = mutableListOf<ChunkInfo>()
        val allEmbeddings = mutableListOf<List<Double>>()
        
        for (chunk in chunks) {
            // Получаем эмбеддинг от Ollama
            val rawEmbedding = ollamaClient.getEmbedding(chunk.text)
            
            // Нормализуем к [-1, 1]
            val normalizedEmbedding = normalizationService.normalize(rawEmbedding)
            
            allEmbeddings.add(normalizedEmbedding)
            
            chunkInfos.add(
                ChunkInfo(
                    index = chunk.index,
                    text = chunk.text.take(200) + if (chunk.text.length > 200) "..." else "",
                    tokenCount = chunk.estimatedTokens,
                    embedding = normalizedEmbedding.take(10) // Первые 10 для превью
                )
            )
        }
        
        // Усредняем эмбеддинги всех чанков
        val finalEmbedding = if (allEmbeddings.size == 1) {
            allEmbeddings.first()
        } else {
            averageEmbeddings(allEmbeddings)
        }
        
        // Сохраняем в БД если нужно
        val id = if (saveToDb) {
            repository.save(text, finalEmbedding)
        } else null
        
        return EmbedResponse(
            id = id,
            text = text.take(500) + if (text.length > 500) "..." else "",
            embedding = finalEmbedding,
            chunks = if (chunks.size > 1) chunkInfos else null
        )
    }
    
    /**
     * Получает эмбеддинги для нескольких текстов.
     */
    suspend fun embedBatch(texts: List<String>, saveToDb: Boolean = true): EmbedBatchResponse {
        val results = texts.map { embed(it, saveToDb) }
        return EmbedBatchResponse(results)
    }
    
    /**
     * Поиск похожих текстов по запросу.
     */
    suspend fun search(query: String, topK: Int = 5, truncateText: Boolean = true): SearchResponse {
        // Получаем эмбеддинг запроса (не сохраняем)
        val queryResponse = embed(query, saveToDb = false)

        // Ищем похожие в базе
        val similar = repository.findSimilar(queryResponse.embedding, topK)

        return SearchResponse(
            query = query,
            results = similar.map { (stored, similarity) ->
                SearchResult(
                    id = stored.id,
                    text = if (truncateText) {
                        stored.text.take(300) + if (stored.text.length > 300) "..." else ""
                    } else {
                        stored.text
                    },
                    similarity = similarity
                )
            }
        )
    }
    
    /**
     * Получает все сохранённые эмбеддинги.
     */
    fun getAllStored(): List<StoredEmbedding> {
        return repository.findAll()
    }
    
    /**
     * Получает эмбеддинг по ID.
     */
    fun getById(id: Long): StoredEmbedding? {
        return repository.findById(id)
    }
    
    /**
     * Удаляет эмбеддинг.
     */
    fun delete(id: Long): Boolean {
        return repository.delete(id)
    }
    
    /**
     * Статистика.
     */
    fun getStats(): StatsResponse {
        return StatsResponse(
            totalEmbeddings = repository.count(),
            chunkSettings = ChunkSettings(
                minTokens = 100,
                maxTokens = 256,
                overlapTokens = 25
            ),
            normalization = "L2 to [-1, 1]"
        )
    }
    
    /**
     * RAG (Retrieval-Augmented Generation) - отвечает на вопрос с использованием контекста из БД или без него.
     */
    suspend fun answerQuestion(question: String, useRAG: Boolean = true, topK: Int = 3): RAGResponse {
        return if (useRAG) {
            // Получаем эмбеддинг запроса
            val queryResponse = embed(question, saveToDb = false)

            // Ищем похожие документы напрямую через repository
            val similar = repository.findSimilar(queryResponse.embedding, topK)

            if (similar.isEmpty()) {
                // Если контекст не найден, отвечаем без RAG
                val answer = ollamaClient.generateAnswer(question, null)
                RAGResponse(
                    question = question,
                    answer = answer,
                    usedRAG = false,
                    context = null
                )
            } else {
                // Формируем контекст из найденных документов
                val contextText = similar.joinToString("\n\n") { (stored, similarity) ->
                    "Документ ${stored.id} (сходство: ${"%.1f".format(similarity * 100)}%):\n${stored.text}"
                }

                // Генерируем ответ с контекстом
                val answer = ollamaClient.generateAnswer(question, contextText)

                RAGResponse(
                    question = question,
                    answer = answer,
                    usedRAG = true,
                    context = similar.map { (stored, similarity) ->
                        RAGContext(
                            id = stored.id,
                            text = stored.text,
                            similarity = similarity,
                            similarityPercent = "${"%.1f".format(similarity * 100)}%",
                            createdAt = stored.createdAt
                        )
                    }
                )
            }
        } else {
            // Без RAG - просто генерируем ответ
            val answer = ollamaClient.generateAnswer(question, null)
            RAGResponse(
                question = question,
                answer = answer,
                usedRAG = false,
                context = null
            )
        }
    }

    /**
     * Проверка доступности Ollama.
     */
    suspend fun checkOllama(): Boolean {
        return ollamaClient.isAvailable()
    }
    
    /**
     * Усредняет несколько эмбеддингов.
     */
    private fun averageEmbeddings(embeddings: List<List<Double>>): List<Double> {
        if (embeddings.isEmpty()) return emptyList()
        if (embeddings.size == 1) return embeddings.first()
        
        val dimensions = embeddings.first().size
        val result = MutableList(dimensions) { 0.0 }
        
        for (embedding in embeddings) {
            for (i in embedding.indices) {
                result[i] += embedding[i]
            }
        }
        
        val count = embeddings.size.toDouble()
        for (i in result.indices) {
            result[i] /= count
        }
        
        // Повторная нормализация после усреднения
        return normalizationService.normalize(result)
    }
}
