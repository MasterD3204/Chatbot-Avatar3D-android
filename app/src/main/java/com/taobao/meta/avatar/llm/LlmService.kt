package com.taobao.meta.avatar.llm

import android.content.Context
import android.util.Log
import com.taobao.meta.avatar.llm.litert.LiteRtLlmEngine
import com.taobao.meta.avatar.settings.MainSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * LlmService wraps [LiteRtLlmEngine] and exposes the same API surface
 * previously provided by the MNN-based ChatSession/ChatService.
 *
 * The output Flow<Pair<String?, String>> matches what MainActivity expects:
 *   - first  = partial token (null signals end of stream — never emitted here)
 *   - second = full accumulated text so far
 */
class LlmService(private val context: Context) {

    private var llmEngine: LlmEngine? = null
    private var stopRequested = false

    /** Init engine. [modelName] is the .litertlm filename to search for. */
    suspend fun init(modelName: String?): Boolean {
        if (modelName.isNullOrBlank()) {
            Log.e(TAG, "modelName is null or blank — cannot init LLM")
            return false
        }
        val prompt = MainSettings.getLlmPrompt(context)
        val engine = LiteRtLlmEngine(
            context = context,
            modelName = modelName,
            systemPrompt = prompt,
            maxTokens = 1024,
            temperature = 0.1f,
            topK = 8,
            topP = 0.95f,
            maxHistoryTurns = 2,
            noThink = false
        )
        val ok = engine.init()
        if (ok) llmEngine = engine
        return ok
    }

    /** Reset context window / history for a new chat session. */
    fun startNewSession() {
        llmEngine?.resetHistory()
    }

    /**
     * Stream LLM tokens for [text].
     * Emits pairs of (partialToken, accumulatedText).
     */
    fun generate(text: String): Flow<Pair<String?, String>> {
        stopRequested = false
        val result = StringBuilder()
        return (llmEngine?.chatStream(text) ?: emptyFlow())
            .onEach { token ->
                result.append(token)
            }
            .map { token ->
                Pair(token, result.toString())
            }
            .cancellable()
    }

    fun requestStop() {
        stopRequested = true
    }

    fun unload() {
        llmEngine?.release()
        llmEngine = null
    }

    companion object {
        private const val TAG = "LlmService"
    }
}
