package com.embedding.models

import kotlinx.serialization.Serializable

// === Запросы ===

@Serializable
data class EmbedRequest(
    val text: String
)

@Serializable
data class EmbedBatchRequest(
    val texts: List<String>
)

// === Ответы ===

@Serializable
data class EmbedResponse(
    val id: Long? = null,
    val text: String,
    val embedding: List<Double>,
    val chunks: List<ChunkInfo>? = null
)

@Serializable
data class ChunkInfo(
    val index: Int,
    val text: String,
    val tokenCount: Int,
    val embedding: List<Double>
)

@Serializable
data class EmbedBatchResponse(
    val results: List<EmbedResponse>
)

@Serializable
data class StoredEmbedding(
    val id: Long,
    val text: String,
    val embedding: List<Double>,
    val createdAt: String
)

@Serializable
data class SearchRequest(
    val query: String,
    val topK: Int = 5
)

@Serializable
data class SearchResult(
    val id: Long,
    val text: String,
    val similarity: Double
)

@Serializable
data class SearchResponse(
    val query: String,
    val results: List<SearchResult>
)

@Serializable
data class ErrorResponse(
    val error: String,
    val details: String? = null
)

@Serializable
data class HealthResponse(
    val status: String,
    val ollama: Boolean? = null,
    val database: Boolean? = null,
    val error: String? = null
)

@Serializable
data class ChunkSettings(
    val minTokens: Int,
    val maxTokens: Int,
    val overlapTokens: Int
)

@Serializable
data class StatsResponse(
    val totalEmbeddings: Long,
    val chunkSettings: ChunkSettings,
    val normalization: String
)

@Serializable
data class DeleteResponse(
    val deleted: Boolean,
    val id: Long
)

// === RAG (Retrieval-Augmented Generation) ===

@Serializable
data class RAGRequest(
    val question: String,
    val useRAG: Boolean = true,
    val topK: Int = 3
)

@Serializable
data class RAGResponse(
    val question: String,
    val answer: String,
    val usedRAG: Boolean,
    val context: List<RAGContext>? = null
)

@Serializable
data class RAGContext(
    val id: Long,
    val text: String,
    val similarity: Double,
    val similarityPercent: String,
    val createdAt: String? = null
)

// === Ollama API ===

@Serializable
data class OllamaEmbedRequest(
    val model: String,
    val input: String
)

@Serializable
data class OllamaEmbedResponse(
    val embeddings: List<List<Double>>
)

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false
)

@Serializable
data class OllamaMessage(
    val role: String,
    val content: String
)

@Serializable
data class OllamaChatResponse(
    val model: String? = null,
    val created_at: String? = null,
    val message: OllamaMessage,
    val done: Boolean,
    val done_reason: String? = null,
    val total_duration: Long? = null,
    val load_duration: Long? = null,
    val prompt_eval_count: Int? = null,
    val prompt_eval_duration: Long? = null,
    val eval_count: Int? = null,
    val eval_duration: Long? = null
)

// === Document Management ===

@Serializable
data class DocumentUploadRequest(
    val fileName: String,
    val content: String
)

@Serializable
data class DocumentUploadResponse(
    val documentId: Long,
    val fileName: String,
    val fileSize: Long,
    val chunksCreated: Int,
    val createdAt: String
)

@Serializable
data class DocumentInfo(
    val id: Long,
    val fileName: String,
    val fileSize: Long,
    val chunksCount: Int,
    val createdAt: String
)

@Serializable
data class DocumentChunkInfo(
    val id: Long,
    val documentId: Long,
    val documentName: String,
    val chunkIndex: Int,
    val text: String,
    val startPosition: Int,
    val endPosition: Int,
    val tokenCount: Int
)

@Serializable
data class DocumentRAGResponse(
    val question: String,
    val answer: String,
    val sources: List<DocumentSource>
)

@Serializable
data class DocumentSource(
    val documentId: Long,
    val documentName: String,
    val chunkIndex: Int,
    val text: String,
    val similarity: Double,
    val similarityPercent: String,
    val link: String  // Ссылка для навигации к документу и чанку
)

@Serializable
data class DocumentStatsResponse(
    val totalDocuments: Long,
    val totalChunks: Long
)
