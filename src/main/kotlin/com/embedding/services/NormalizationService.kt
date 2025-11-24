package com.embedding.services

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Сервис для нормализации векторов эмбеддингов.
 * Приводит значения к диапазону [-1, 1].
 */
class NormalizationService {
    
    /**
     * Нормализует вектор методом Min-Max к диапазону [-1, 1].
     */
    fun normalizeMinMax(embedding: List<Double>): List<Double> {
        if (embedding.isEmpty()) return emptyList()
        
        val min = embedding.minOrNull() ?: 0.0
        val max = embedding.maxOrNull() ?: 0.0
        
        // Если все значения одинаковые
        if (max == min) {
            return embedding.map { 0.0 }
        }
        
        // Нормализуем к [-1, 1]
        return embedding.map { value ->
            2.0 * (value - min) / (max - min) - 1.0
        }
    }
    
    /**
     * L2-нормализация (единичный вектор).
     * Значения автоматически попадают в [-1, 1].
     */
    fun normalizeL2(embedding: List<Double>): List<Double> {
        if (embedding.isEmpty()) return emptyList()
        
        val norm = sqrt(embedding.sumOf { it * it })
        
        if (norm == 0.0) {
            return embedding
        }
        
        return embedding.map { it / norm }
    }
    
    /**
     * Комбинированная нормализация: сначала L2, затем проверка диапазона.
     * Рекомендуется для эмбеддингов.
     */
    fun normalize(embedding: List<Double>): List<Double> {
        // L2 нормализация уже даёт значения в [-1, 1]
        val l2Normalized = normalizeL2(embedding)
        
        // Дополнительная проверка и clamp для гарантии
        return l2Normalized.map { value ->
            value.coerceIn(-1.0, 1.0)
        }
    }
    
    /**
     * Вычисляет косинусное сходство между двумя векторами.
     */
    fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
        require(a.size == b.size) { "Vectors must have the same dimension" }
        
        if (a.isEmpty()) return 0.0
        
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denominator = sqrt(normA) * sqrt(normB)
        
        return if (denominator == 0.0) 0.0 else dotProduct / denominator
    }
}
