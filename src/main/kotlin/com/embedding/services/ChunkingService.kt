package com.embedding.services

/**
 * Сервис для разбиения текста на чанки с перекрытием.
 * 
 * Параметры по умолчанию:
 * - Размер чанка: 500-1000 токенов (примерно 4 символа = 1 токен)
 * - Перекрытие: 50-100 токенов между чанками
 */
class ChunkingService(
    private val minChunkTokens: Int = 500,
    private val maxChunkTokens: Int = 1000,
    private val overlapTokens: Int = 75 // среднее между 50 и 100
) {
    // Примерное соотношение: 1 токен ≈ 4 символа (для английского/русского текста)
    private val charsPerToken = 4
    
    private val minChunkChars = minChunkTokens * charsPerToken
    private val maxChunkChars = maxChunkTokens * charsPerToken
    private val overlapChars = overlapTokens * charsPerToken
    
    data class Chunk(
        val index: Int,
        val text: String,
        val startPosition: Int,
        val endPosition: Int,
        val estimatedTokens: Int
    )
    
    /**
     * Разбивает текст на чанки с перекрытием.
     * Если текст меньше минимального размера чанка, возвращает его целиком.
     */
    fun chunkText(text: String): List<Chunk> {
        val trimmedText = text.trim()
        
        // Если текст короткий, возвращаем как есть
        if (trimmedText.length <= maxChunkChars) {
            return listOf(
                Chunk(
                    index = 0,
                    text = trimmedText,
                    startPosition = 0,
                    endPosition = trimmedText.length,
                    estimatedTokens = estimateTokens(trimmedText)
                )
            )
        }
        
        val chunks = mutableListOf<Chunk>()
        var currentPosition = 0
        var chunkIndex = 0
        
        while (currentPosition < trimmedText.length) {
            // Определяем конец чанка
            var endPosition = minOf(currentPosition + maxChunkChars, trimmedText.length)
            
            // Пытаемся закончить на границе предложения или слова
            if (endPosition < trimmedText.length) {
                endPosition = findBestBreakPoint(trimmedText, currentPosition, endPosition)
            }
            
            val chunkText = trimmedText.substring(currentPosition, endPosition).trim()
            
            if (chunkText.isNotEmpty()) {
                chunks.add(
                    Chunk(
                        index = chunkIndex,
                        text = chunkText,
                        startPosition = currentPosition,
                        endPosition = endPosition,
                        estimatedTokens = estimateTokens(chunkText)
                    )
                )
                chunkIndex++
            }
            
            // Следующий чанк начинается с учётом перекрытия
            currentPosition = endPosition - overlapChars
            
            // Защита от бесконечного цикла
            if (currentPosition <= chunks.lastOrNull()?.startPosition ?: -1) {
                currentPosition = endPosition
            }
        }
        
        return chunks
    }
    
    /**
     * Находит лучшую точку для разрыва текста (конец предложения или слова).
     */
    private fun findBestBreakPoint(text: String, start: Int, maxEnd: Int): Int {
        // Ищем конец предложения
        val sentenceEnd = findLastSentenceEnd(text, start + minChunkChars, maxEnd)
        if (sentenceEnd > start + minChunkChars) {
            return sentenceEnd
        }
        
        // Если не нашли, ищем конец слова
        val wordEnd = findLastWordEnd(text, maxEnd)
        if (wordEnd > start + minChunkChars) {
            return wordEnd
        }
        
        return maxEnd
    }
    
    private fun findLastSentenceEnd(text: String, start: Int, end: Int): Int {
        val sentenceEnders = listOf(". ", "! ", "? ", ".\n", "!\n", "?\n")
        var lastEnd = -1
        
        for (ender in sentenceEnders) {
            val pos = text.lastIndexOf(ender, end)
            if (pos >= start && pos > lastEnd) {
                lastEnd = pos + ender.length
            }
        }
        
        return lastEnd
    }
    
    private fun findLastWordEnd(text: String, end: Int): Int {
        var pos = end
        while (pos > 0 && !text[pos - 1].isWhitespace()) {
            pos--
        }
        return if (pos > 0) pos else end
    }
    
    private fun estimateTokens(text: String): Int {
        return (text.length / charsPerToken).coerceAtLeast(1)
    }
}
