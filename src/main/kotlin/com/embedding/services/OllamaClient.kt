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
    private val model: String = "nomic-embed-text"
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        
        engine {
            requestTimeout = 60_000 // 60 секунд на запрос
        }
    }
    
    /**
     * Получает эмбеддинг для текста.
     */
    suspend fun getEmbedding(text: String): List<Double> {
        val response = client.post("$baseUrl/api/embed") {
            contentType(ContentType.Application.Json)
            setBody(OllamaEmbedRequest(model = model, input = text))
        }
        
        if (!response.status.isSuccess()) {
            throw RuntimeException("Ollama API error: ${response.status}")
        }
        
        val ollamaResponse: OllamaEmbedResponse = response.body()
        
        return ollamaResponse.embeddings.firstOrNull()
            ?: throw RuntimeException("No embeddings returned from Ollama")
    }
    
    /**
     * Получает эмбеддинги для нескольких текстов.
     */
    suspend fun getEmbeddings(texts: List<String>): List<List<Double>> {
        return texts.map { getEmbedding(it) }
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
