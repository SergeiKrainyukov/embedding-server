package com.embedding.services

import com.embedding.models.OllamaEmbedRequest
import com.embedding.models.OllamaEmbedResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Клиент для взаимодействия с Ollama API.
 */
class OllamaClient(
    private val baseUrl: String = "http://localhost:11434",
    private val model: String = "nomic-embed-text",
    private val chatModel: String = "qwen2.5:1.5b"
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }

        engine {
            requestTimeout = 300_000 // 5 минут на запрос
            endpoint {
                connectTimeout = 30_000 // 30 секунд на подключение
                socketTimeout = 300_000 // 5 минут на чтение
            }
        }
    }
    
    /**
     * Получает эмбеддинг для текста.
     */
    suspend fun getEmbedding(text: String): List<Double> {
        try {
            println("[OllamaClient] Requesting embedding for text of length: ${text.length} chars (~${text.length / 4} tokens)")

            val response = client.post("$baseUrl/api/embed") {
                contentType(ContentType.Application.Json)
                setBody(OllamaEmbedRequest(model = model, input = text))
            }

            if (!response.status.isSuccess()) {
                val errorBody = try {
                    response.body<String>()
                } catch (e: Exception) {
                    "Unable to read error body"
                }
                println("[OllamaClient] Error response from Ollama: ${response.status}")
                println("[OllamaClient] Error body: $errorBody")
                throw RuntimeException("Ollama API error: ${response.status} - $errorBody")
            }

            val ollamaResponse: OllamaEmbedResponse = response.body()

            return ollamaResponse.embeddings.firstOrNull()
                ?: throw RuntimeException("No embeddings returned from Ollama")
        } catch (e: Exception) {
            println("[OllamaClient] Exception while getting embedding: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    /**
     * Получает эмбеддинги для нескольких текстов.
     */
    suspend fun getEmbeddings(texts: List<String>): List<List<Double>> {
        return texts.map { getEmbedding(it) }
    }
    
    /**
     * Генерирует ответ на вопрос, опционально с контекстом.
     */
    suspend fun generateAnswer(question: String, context: String? = null): String {
        try {
            val systemMessage = if (context != null) {
                "Ты помощник, который отвечает на вопросы на основе предоставленного контекста. " +
                "Если информации в контексте недостаточно, скажи об этом честно. " +
                "Контекст:\n$context"
            } else {
                "Ты полезный помощник. Отвечай на вопросы, используя свои знания."
            }

            println("[OllamaClient] Generating answer for question: ${question.take(100)}...")
            if (context != null) {
                println("[OllamaClient] Using context of length: ${context.length} chars")
            }

            val messages = listOf(
                com.embedding.models.OllamaMessage(role = "system", content = systemMessage),
                com.embedding.models.OllamaMessage(role = "user", content = question)
            )

            val response = client.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(com.embedding.models.OllamaChatRequest(
                    model = chatModel,
                    messages = messages,
                    stream = false
                ))
            }

            if (!response.status.isSuccess()) {
                val errorBody = try {
                    response.body<String>()
                } catch (e: Exception) {
                    "Unable to read error body"
                }
                println("[OllamaClient] Error response from Ollama: ${response.status}")
                println("[OllamaClient] Error body: $errorBody")
                throw RuntimeException("Ollama API error: ${response.status} - $errorBody")
            }

            // Ollama возвращает NDJSON (несколько JSON объектов, каждый на своей строке)
            // Нужно собрать весь контент из всех строк
            val responseText: String = response.body()
            val json = Json { ignoreUnknownKeys = true }

            // Парсим каждую строку как отдельный JSON объект
            val allResponses = responseText.trim().lines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    try {
                        json.decodeFromString<com.embedding.models.OllamaChatResponse>(line)
                    } catch (e: Exception) {
                        println("[OllamaClient] Failed to parse line: ${line.take(100)}")
                        null
                    }
                }

            // Собираем весь контент из всех ответов
            val fullContent = allResponses.joinToString("") { it.message.content }

            println("[OllamaClient] Answer generated successfully ($fullContent.length chars)")
            return fullContent

        } catch (e: Exception) {
            println("[OllamaClient] Exception while generating answer: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Проверяет доступность Ollama.
     */
    suspend fun isAvailable(): Boolean {
        return try {
            val response = client.get("$baseUrl/api/tags")
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    fun close() {
        client.close()
    }
}
