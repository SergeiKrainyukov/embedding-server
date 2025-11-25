package com.embedding.routes

import com.embedding.models.*
import com.embedding.services.EmbeddingService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.embeddingRoutes(embeddingService: EmbeddingService) {
    
    route("/api") {
        
        // === Эмбеддинги ===
        
        /**
         * POST /api/embed - Получить эмбеддинг для текста
         */
        post("/embed") {
            try {
                val request = call.receive<EmbedRequest>()
                
                if (request.text.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Text cannot be empty"))
                    return@post
                }
                
                val response = embeddingService.embed(request.text)
                call.respond(HttpStatusCode.OK, response)
                
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to create embedding", e.message)
                )
            }
        }
        
        /**
         * POST /api/embed/batch - Получить эмбеддинги для нескольких текстов
         */
        post("/embed/batch") {
            try {
                val request = call.receive<EmbedBatchRequest>()
                
                if (request.texts.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Texts list cannot be empty"))
                    return@post
                }
                
                val response = embeddingService.embedBatch(request.texts)
                call.respond(HttpStatusCode.OK, response)
                
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to create embeddings", e.message)
                )
            }
        }
        
        /**
         * POST /api/embed/query - Получить эмбеддинг без сохранения в БД
         */
        post("/embed/query") {
            try {
                val request = call.receive<EmbedRequest>()
                
                if (request.text.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Text cannot be empty"))
                    return@post
                }
                
                val response = embeddingService.embed(request.text, saveToDb = false)
                call.respond(HttpStatusCode.OK, response)
                
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to create embedding", e.message)
                )
            }
        }
        
        // === Поиск ===
        
        /**
         * POST /api/search - Семантический поиск по сохранённым эмбеддингам
         */
        post("/search") {
            try {
                val request = call.receive<SearchRequest>()

                if (request.query.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Query cannot be empty"))
                    return@post
                }

                val response = embeddingService.search(request.query, request.topK)
                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Search failed", e.message)
                )
            }
        }

        /**
         * POST /api/rag - RAG (Retrieval-Augmented Generation) - отвечает на вопрос
         */
        post("/rag") {
            try {
                val request = call.receive<RAGRequest>()

                if (request.question.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Question cannot be empty"))
                    return@post
                }

                val response = embeddingService.answerQuestion(
                    question = request.question,
                    useRAG = request.useRAG,
                    topK = request.topK
                )
                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("RAG request failed", e.message)
                )
            }
        }
        
        // === Хранилище ===
        
        /**
         * GET /api/embeddings - Получить все сохранённые эмбеддинги
         */
        get("/embeddings") {
            try {
                val embeddings = embeddingService.getAllStored()
                call.respond(HttpStatusCode.OK, embeddings)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to fetch embeddings", e.message)
                )
            }
        }
        
        /**
         * GET /api/embeddings/{id} - Получить эмбеддинг по ID
         */
        get("/embeddings/{id}") {
            try {
                val id = call.parameters["id"]?.toLongOrNull()
                
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid ID"))
                    return@get
                }
                
                val embedding = embeddingService.getById(id)
                
                if (embedding == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Embedding not found"))
                    return@get
                }
                
                call.respond(HttpStatusCode.OK, embedding)
                
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to fetch embedding", e.message)
                )
            }
        }
        
        /**
         * DELETE /api/embeddings/{id} - Удалить эмбеддинг
         */
        delete("/embeddings/{id}") {
            try {
                val id = call.parameters["id"]?.toLongOrNull()
                
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid ID"))
                    return@delete
                }
                
                val deleted = embeddingService.delete(id)
                
                if (!deleted) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Embedding not found"))
                    return@delete
                }

                call.respond(HttpStatusCode.OK, DeleteResponse(deleted = true, id = id))
                
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to delete embedding", e.message)
                )
            }
        }
        
        // === Служебные ===
        
        /**
         * GET /api/health - Проверка здоровья сервиса
         */
        get("/health") {
            try {
                val ollamaAvailable = embeddingService.checkOllama()

                val status = if (ollamaAvailable) "healthy" else "degraded"
                val httpStatus = if (ollamaAvailable) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable

                call.respond(httpStatus, HealthResponse(
                    status = status,
                    ollama = ollamaAvailable,
                    database = true
                ))

            } catch (e: Exception) {
                call.respond(HttpStatusCode.ServiceUnavailable, HealthResponse(
                    status = "unhealthy",
                    error = e.message
                ))
            }
        }
        
        /**
         * GET /api/stats - Статистика сервиса
         */
        get("/stats") {
            try {
                val stats = embeddingService.getStats()
                call.respond(HttpStatusCode.OK, stats)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to get stats", e.message)
                )
            }
        }
    }
}
