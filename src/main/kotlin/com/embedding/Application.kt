package com.embedding

import com.embedding.database.DatabaseManager
import com.embedding.routes.embeddingRoutes
import com.embedding.routes.documentRoutes
import com.embedding.services.EmbeddingService
import com.embedding.services.DocumentService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Инициализация БД
    DatabaseManager.init()

    // Сервисы
    val embeddingService = EmbeddingService()
    val documentService = DocumentService()
    
    // Настройка сериализации JSON
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    
    // CORS для доступа из браузера
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        anyHost()
    }
    
    // Обработка ошибок
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(
                text = "500: ${cause.message}",
                status = HttpStatusCode.InternalServerError
            )
        }
    }
    
    // Роутинг
    routing {
        // Swagger UI
        swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")
        
        // Главная страница
        get("/") {
            call.respondText(
                """
                🚀 Embedding Server v1.0.0

                API Endpoints:

                Embeddings:
                - POST /api/embed         - Create embedding for text
                - POST /api/embed/batch   - Create embeddings for multiple texts
                - POST /api/embed/query   - Create embedding without saving
                - POST /api/search        - Semantic search
                - GET  /api/embeddings    - List all stored embeddings
                - GET  /api/embeddings/{id} - Get embedding by ID
                - DELETE /api/embeddings/{id} - Delete embedding

                Documents (MD):
                - POST /api/documents/upload - Upload MD document
                - GET  /api/documents        - List all documents
                - GET  /api/documents/{id}   - Get document info
                - GET  /api/documents/{id}/chunks - Get document chunks
                - GET  /api/documents/{id}/chunks/{index} - Get specific chunk
                - DELETE /api/documents/{id} - Delete document
                - POST /api/documents/ask    - Ask question with RAG (returns sources)
                - GET  /api/documents/stats  - Documents statistics

                System:
                - GET  /api/health        - Health check
                - GET  /api/stats         - Service statistics

                Swagger UI: http://localhost:8080/swagger

                Configuration:
                - Chunk size: 100-256 tokens
                - Overlap: 25 tokens
                - Normalization: L2 to [-1, 1]
                - Model: nomic-embed-text (Ollama)
                """.trimIndent(),
                ContentType.Text.Plain
            )
        }
        
        // API роуты
        embeddingRoutes(embeddingService)
        documentRoutes(documentService)
    }
    
    println("""
        
        ╔══════════════════════════════════════════════════════════╗
        ║         🚀 Embedding Server Started!                     ║
        ╠══════════════════════════════════════════════════════════╣
        ║  Server:     http://localhost:8080                       ║
        ║  Swagger:    http://localhost:8080/swagger               ║
        ║  Health:     http://localhost:8080/api/health            ║
        ╠══════════════════════════════════════════════════════════╣
        ║  Settings:                                               ║
        ║  - Chunks:   500-1000 tokens                             ║
        ║  - Overlap:  50-100 tokens                               ║
        ║  - Normalize: [-1, 1]                                    ║
        ║  - Model:    nomic-embed-text                            ║
        ╚══════════════════════════════════════════════════════════╝
        
    """.trimIndent())
}
