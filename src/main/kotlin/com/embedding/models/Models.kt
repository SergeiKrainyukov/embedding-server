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
