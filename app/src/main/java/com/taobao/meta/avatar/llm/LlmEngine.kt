package com.taobao.meta.avatar.llm

import kotlinx.coroutines.flow.Flow

/**
 * Port for Large Language Model engines.
 * Implementations: LiteRtLlmEngine
 */
interface LlmEngine {
    /** Heavy initialisation (load model). Call once, off main thread. */
    suspend fun init(): Boolean

    /** True after [init] succeeds */
    fun isReady(): Boolean

    /**
     * Stream tokens for [query].
     * Each emitted String is a partial token/chunk to append to the UI.
     * Flow completes when generation is done.
     */
    fun chatStream(query: String): Flow<String>

    /** Release resources (model weights, etc.) */
    fun release()

    /**
     * Xóa lịch sử hội thoại, bắt đầu phiên mới.
     * Default no-op cho engines không giữ state.
     */
    fun resetHistory() {}
}
