package com.taobao.meta.avatar.llm

import android.content.Context
import android.util.Log
import com.taobao.meta.avatar.llm.litert.LiteRtLlmEngine
import com.taobao.meta.avatar.settings.MainSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

class LlmService(private val context: Context) {

    private var llmEngine: LlmEngine? = null
    private var loadedModelName: String? = null
    private var stopRequested = false

    /**
     * Init (hoặc re-init) engine với model mới.
     * Nếu đã có engine cũ, unload trước.
     * Tự động bật [noThink] khi model là Qwen3 (thêm /no_think prefix vào query).
     */
    suspend fun init(modelName: String?): Boolean {
        if (modelName.isNullOrBlank()) {
            Log.e(TAG, "init FAILED: modelName is null or blank")
            return false
        }

        // Unload engine cũ nếu có
        if (llmEngine != null) {
            Log.i(TAG, "Unloading previous engine (model='$loadedModelName')")
            withContext(Dispatchers.IO) { llmEngine?.release() }
            llmEngine = null
            loadedModelName = null
        }

        val isQwen3 = isQwen3Model(modelName)
        Log.i(TAG, "init: model='$modelName' noThink=$isQwen3")

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
            noThink = isQwen3
        )
        val ok = engine.init()
        if (ok) {
            llmEngine = engine
            loadedModelName = modelName
            Log.i(TAG, "✅ LLM engine ready (model=$modelName, noThink=$isQwen3)")
        } else {
            Log.e(TAG, "❌ LLM engine init FAILED (model=$modelName)")
        }
        return ok
    }

    /** True nếu [newModelName] khác với model đang chạy. */
    fun isModelChanged(newModelName: String): Boolean = newModelName != loadedModelName

    fun startNewSession() {
        llmEngine?.resetHistory()
    }

    fun generate(text: String): Flow<Pair<String?, String>> {
        stopRequested = false
        if (llmEngine == null) {
            Log.e(TAG, "generate() called but llmEngine is NULL — LLM not initialized!")
            return emptyFlow()
        }
        Log.i(TAG, "generate: query='${text.take(80)}'")
        val result = StringBuilder()
        return llmEngine!!.chatStream(text)
            .onEach { token -> result.append(token) }
            .map { token -> Pair(token, result.toString()) }
            .cancellable()
    }

    fun isEngineReady(): Boolean = llmEngine?.isReady() == true

    fun requestStop() {
        stopRequested = true
    }

    fun unload() {
        llmEngine?.release()
        llmEngine = null
        loadedModelName = null
    }

    companion object {
        private const val TAG = "LlmService"

        /**
         * Nhận dạng Qwen3 model để bật chế độ /no_think.
         * Match: qwen3, qwen-3 (case-insensitive).
         */
        fun isQwen3Model(modelName: String): Boolean {
            val lower = modelName.lowercase()
            return lower.contains("qwen3") || lower.contains("qwen-3")
        }
    }
}
