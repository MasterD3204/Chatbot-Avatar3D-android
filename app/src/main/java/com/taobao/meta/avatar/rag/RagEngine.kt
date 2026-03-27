package com.taobao.meta.avatar.rag

/**
 * Port for FAQ matching engines.
 * Implementations: FastTextRagEngine
 */
interface RagEngine {
    /**
     * Load [qaFile] (question|answer pairs) and [vectorFile] (word embeddings).
     * Both are Android asset paths relative to assets/ folder.
     */
    suspend fun initialize(qaFile: String, vectorFile: String)

    /**
     * Search for best-matching answers for [query].
     * @return top matching answers above confidence threshold, or null if no match.
     */
    suspend fun search(query: String): List<String>?

    val isInitialized: Boolean

    fun release()
}
