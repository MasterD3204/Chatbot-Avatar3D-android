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
            Log.e(TAG, "init FAILED: modelName is null or blank")
            return false
        }
        Log.i(TAG, "init: modelName='$modelName'")
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
        if (ok) {
            llmEngine = engine
            Log.i(TAG, "✅ LLM engine ready")
        } else {
            Log.e(TAG, "❌ LLM engine init FAILED")
        }
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
        if (llmEngine == null) {
            Log.e(TAG, "generate() called but llmEngine is NULL — LLM not initialized!")
            return emptyFlow()
        }
        Log.i(TAG, "generate: query='${text.take(80)}'")
        val result = StringBuilder()
        return llmEngine!!.chatStream(text)
            .onEach { token ->
                result.append(token)
            }
            .map { token ->
                Pair(token, result.toString())
            }
            .cancellable()
    }

    fun isEngineReady(): Boolean = llmEngine?.isReady() == true

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
