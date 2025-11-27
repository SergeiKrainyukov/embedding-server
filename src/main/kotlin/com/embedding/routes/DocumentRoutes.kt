package com.embedding.routes

import com.embedding.models.*
import com.embedding.services.DocumentService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.documentRoutes(documentService: DocumentService) {

    route("/api/documents") {

        /**
         * POST /api/documents/upload - Загрузить MD-документ
         */
        post("/upload") {
            try {
                val request = call.receive<DocumentUploadRequest>()

                if (request.fileName.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("File name cannot be empty"))
                    return@post
                }

                if (request.content.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Content cannot be empty"))
                    return@post
                }

                // Проверяем, что это MD-файл
                if (!request.fileName.endsWith(".md", ignoreCase = true)) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Only .md files are supported"))
                    return@post
                }

                val response = documentService.uploadDocument(request.fileName, request.content)
                call.respond(HttpStatusCode.Created, response)

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to upload document", e.message)
                )
            }
        }

        /**
         * GET /api/documents - Получить список всех документов
         */
        get {
            try {
                val documents = documentService.getAllDocuments()
                call.respond(HttpStatusCode.OK, documents)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to fetch documents", e.message)
                )
            }
        }

        /**
         * GET /api/documents/{id} - Получить информацию о документе
         */
        get("/{id}") {
            try {
                val id = call.parameters["id"]?.toLongOrNull()

                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid document ID"))
                    return@get
                }

                val document = documentService.getDocumentById(id)

                if (document == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Document not found"))
                    return@get
                }

                call.respond(HttpStatusCode.OK, document)

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to fetch document", e.message)
                )
            }
        }

        /**
         * GET /api/documents/{id}/chunks - Получить все чанки документа
         */
        get("/{id}/chunks") {
            try {
                val id = call.parameters["id"]?.toLongOrNull()

                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid document ID"))
                    return@get
                }

                val chunks = documentService.getDocumentChunks(id)
                call.respond(HttpStatusCode.OK, chunks)

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to fetch chunks", e.message)
                )
            }
        }

        /**
         * GET /api/documents/{id}/chunks/{chunkIndex} - Получить конкретный чанк документа
         */
        get("/{id}/chunks/{chunkIndex}") {
            try {
                val id = call.parameters["id"]?.toLongOrNull()
                val chunkIndex = call.parameters["chunkIndex"]?.toIntOrNull()

                if (id == null || chunkIndex == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid parameters"))
                    return@get
                }

                val chunks = documentService.getDocumentChunks(id)
                val chunk = chunks.find { it.chunkIndex == chunkIndex }

                if (chunk == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Chunk not found"))
                    return@get
                }

                call.respond(HttpStatusCode.OK, chunk)

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to fetch chunk", e.message)
                )
            }
        }

        /**
         * DELETE /api/documents/{id} - Удалить документ
         */
        delete("/{id}") {
            try {
                val id = call.parameters["id"]?.toLongOrNull()

                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid document ID"))
                    return@delete
                }

                val deleted = documentService.deleteDocument(id)

                if (!deleted) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Document not found"))
                    return@delete
                }

                call.respond(HttpStatusCode.OK, DeleteResponse(deleted = true, id = id))

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to delete document", e.message)
                )
            }
        }

        /**
         * POST /api/documents/ask - RAG поиск с возвратом источников
         */
        post("/ask") {
            try {
                val request = call.receive<RAGRequest>()

                if (request.question.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Question cannot be empty"))
                    return@post
                }

                // Формируем URL сервера
                val host = call.request.local.serverHost
                val port = call.request.local.serverPort
                val serverUrl = "http://$host:$port"

                val response = documentService.answerQuestionWithSources(
                    question = request.question,
                    topK = request.topK,
                    serverUrl = serverUrl
                )

                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Failed to answer question", e.message)
                )
            }
        }

        /**
         * GET /api/documents/stats - Статистика по документам
         */
        get("/stats") {
            try {
                val stats = documentService.getStats()
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