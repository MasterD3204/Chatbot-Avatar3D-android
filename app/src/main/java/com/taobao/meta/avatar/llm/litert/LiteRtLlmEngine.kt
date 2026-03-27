package com.taobao.meta.avatar.llm.litert

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.*
import com.taobao.meta.avatar.llm.LlmEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CancellationException

/** Thrown when GPU/OpenCL inference fails at runtime; triggers CPU fallback. */
private class GpuFallbackException(message: String) : Exception(message)

/**
 * LLM engine backed by Google LiteRT (.litertlm format).
 * - Searches common storage paths for [modelName].
 * - Tries GPU first, falls back to CPU if GPU init OR inference fails.
 * - Limits conversation history to [maxHistoryTurns] turns.
 */
class LiteRtLlmEngine(
    private val context: Context,
    private val modelName: String,
    private val systemPrompt: String = "Bạn là trợ lý ảo trả lời ngắn gọn tất cả câu hỏi.",
    private val maxTokens: Int = 1024,
    private val temperature: Float = 0.1f,
    private val topK: Int = 8,
    private val topP: Float = 0.95f,
    private val maxHistoryTurns: Int = 2,
    private val noThink: Boolean = false
) : LlmEngine {

    companion object {
        private const val TAG = "LiteRtLlmEngine"
    }

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var initialized = false
    private var currentBackendName: String = "Unknown"

    private val manualHistory = mutableListOf<Pair<String, String>>()
    private val currentReplyBuf = StringBuilder()

    override fun isReady() = initialized && conversation != null

    // ── Init ─────────────────────────────────────────────────────────────────

    override suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "===== START MODEL INITIALIZATION =====")
        Log.d(TAG, "Looking for model: '$modelName'")

        val path = findModelPath() ?: run {
            Log.e(TAG, "❌ Model '$modelName' not found in any search path")
            return@withContext false
        }

        Log.i(TAG, "✅ Model found at: $path | size=${File(path).length() / (1024 * 1024)} MB")

        if (tryInit(path, Backend.GPU())) {
            currentBackendName = "GPU"
            return@withContext true
        }

        Log.w(TAG, "⚠️ GPU init failed, falling back to CPU...")
        if (tryInit(path, Backend.CPU())) {
            currentBackendName = "CPU"
            return@withContext true
        }

        Log.e(TAG, "❌ Both GPU and CPU initialization failed!")
        false
    }

    private fun findModelPath(): String? {
        val externalDir = context.getExternalFilesDir(null)?.absolutePath ?: ""
        val searchPaths = listOf(
            "/sdcard/$modelName",
            "/sdcard/Download/$modelName",
            "/storage/emulated/0/$modelName",
            "/storage/emulated/0/Download/$modelName",
            "$externalDir/$modelName"
        )

        Log.d(TAG, "--- Scanning ${searchPaths.size} paths ---")
        for (path in searchPaths) {
            val file = File(path)
            Log.d(TAG, "  $path | exists=${file.exists()} readable=${file.canRead()}" +
                    if (file.exists()) " | ${file.length() / (1024 * 1024)} MB" else "")
        }

        val found = searchPaths.firstOrNull { File(it).exists() }
        Log.d(TAG, if (found != null) "✅ Selected: $found" else "❌ No valid path found")
        return found
    }

    private fun tryInit(modelPath: String, backend: Backend): Boolean = try {
        val backendName = backendLabel(backend)
        Log.i(TAG, "tryInit: backend=$backendName path=$modelPath")

        val cfg = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            maxNumTokens = maxTokens,
            cacheDir = if (modelPath.startsWith("/data/local/tmp"))
                context.getExternalFilesDir(null)?.absolutePath else null
        )

        Log.d(TAG, "  Creating Engine...")
        val eng = Engine(cfg)
        eng.initialize()
        Log.d(TAG, "  ✅ Engine initialized")

        val conv = eng.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(topK, topP.toDouble(), temperature.toDouble()),
                systemInstruction = Contents.of(Content.Text(systemPrompt))
            )
        )
        Log.d(TAG, "  ✅ Conversation created")

        engine = eng; conversation = conv; initialized = true
        Log.i(TAG, "🚀 LiteRT ready on $backendName")
        true
    } catch (e: Exception) {
        Log.e(TAG, "❌ tryInit(${ backendLabel(backend)}) failed: ${e.javaClass.simpleName}: ${e.message}")
        false
    }

    // ── Chat stream ───────────────────────────────────────────────────────────

    /**
     * Stream tokens for [query].
     * If GPU inference fails at runtime (e.g. missing OpenCL), automatically
     * reinitializes with CPU and retries the same query transparently.
     */
    override fun chatStream(query: String): Flow<String> {
        Log.i(TAG, "chatStream: query='${query.take(80)}' backend=$currentBackendName initialized=$initialized")

        if (!initialized || conversation == null) {
            Log.e(TAG, "chatStream: engine not ready (initialized=$initialized conv=${conversation != null})")
            return flowOf("[Lỗi: Model chưa sẵn sàng]")
        }

        return buildSendFlow(query, allowGpuFallback = true)
            .catch { e ->
                if (e is GpuFallbackException) {
                    Log.w(TAG, "GPU inference failed: ${e.message} — reinitializing with CPU...")

                    // Cleanup GPU resources on IO thread
                    val modelPath = withContext(Dispatchers.IO) {
                        runCatching { conversation?.close() }
                        runCatching { engine?.close() }
                        initialized = false; engine = null; conversation = null
                        findModelPath()
                    }

                    if (modelPath == null) {
                        emit("\n[Lỗi: Không tìm thấy model để CPU fallback]")
                        return@catch
                    }

                    val cpuOk = withContext(Dispatchers.IO) { tryInit(modelPath, Backend.CPU()) }
                    if (!cpuOk) {
                        emit("\n[Lỗi: CPU fallback thất bại]")
                        return@catch
                    }

                    currentBackendName = "CPU"
                    Log.i(TAG, "✅ CPU fallback ready — retrying query")
                    emitAll(buildSendFlow(query, allowGpuFallback = false))
                } else {
                    throw e
                }
            }
    }

    private fun buildSendFlow(query: String, allowGpuFallback: Boolean): Flow<String> =
        if (noThink) buildNoThinkFlow(query, allowGpuFallback)
        else buildNormalFlow(query, allowGpuFallback)

    private fun buildNormalFlow(query: String, allowGpuFallback: Boolean): Flow<String> = callbackFlow {
        val conv = conversation ?: run { close(); return@callbackFlow }
        Log.d(TAG, "buildNormalFlow: sending on backend=$currentBackendName allowFallback=$allowGpuFallback")

        conv.sendMessageAsync(Contents.of(Content.Text(query)), object : MessageCallback {
            override fun onMessage(msg: Message) {
                Log.v(TAG, "token: '${msg.toString().take(20)}'")
                trySend(msg.toString())
            }

            override fun onDone() {
                Log.i(TAG, "generation done (backend=$currentBackendName)")
                close()
            }

            override fun onError(t: Throwable) {
                Log.e(TAG, "inference error (backend=$currentBackendName): ${t.javaClass.simpleName}: ${t.message}")
                when {
                    allowGpuFallback && currentBackendName == "GPU" && isGpuError(t) ->
                        close(GpuFallbackException(t.message ?: "GPU inference error"))
                    t is CancellationException -> close()
                    else -> { trySend("\n[Lỗi: ${t.message}]"); close() }
                }
            }
        })
        awaitClose { Log.d(TAG, "buildNormalFlow awaitClose") }
    }

    private fun buildNoThinkFlow(query: String, allowGpuFallback: Boolean): Flow<String> = callbackFlow {
        val freshConv = createFreshConversation() ?: run {
            Log.e(TAG, "buildNoThinkFlow: createFreshConversation returned null")
            trySend("[Lỗi: Không thể tạo conversation]"); close(); return@callbackFlow
        }
        conversation = freshConv
        currentReplyBuf.clear()
        val fullPrompt = buildReplayPrompt(query)
        Log.d(TAG, "buildNoThinkFlow: prompt len=${fullPrompt.length} backend=$currentBackendName")

        freshConv.sendMessageAsync(Contents.of(Content.Text(fullPrompt)), object : MessageCallback {
            override fun onMessage(msg: Message) {
                val token = msg.toString()
                currentReplyBuf.append(token)
                trySend(token)
            }

            override fun onDone() {
                val cleanReply = stripThinkTags(currentReplyBuf.toString())
                addToHistory(query, cleanReply)
                Log.i(TAG, "noThink done. history=${manualHistory.size}/$maxHistoryTurns")
                close()
            }

            override fun onError(t: Throwable) {
                Log.e(TAG, "noThink error (backend=$currentBackendName): ${t.message}")
                when {
                    allowGpuFallback && currentBackendName == "GPU" && isGpuError(t) ->
                        close(GpuFallbackException(t.message ?: "GPU inference error"))
                    t is CancellationException -> close()
                    else -> { trySend("\n[Lỗi: ${t.message}]"); close() }
                }
            }
        })
        awaitClose()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun release() {
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        initialized = false; engine = null; conversation = null
        manualHistory.clear()
        Log.i(TAG, "released")
    }

    override fun resetHistory() {
        val eng = engine ?: return
        runCatching { conversation?.close() }
        manualHistory.clear()
        currentReplyBuf.clear()
        conversation = try {
            eng.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK, topP.toDouble(), temperature.toDouble()),
                    systemInstruction = Contents.of(Content.Text(systemPrompt))
                )
            ).also { Log.i(TAG, "history reset on $currentBackendName") }
        } catch (e: Exception) {
            Log.e(TAG, "resetHistory failed", e); null
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isGpuError(t: Throwable): Boolean {
        val msg = t.message ?: return false
        return msg.contains("OpenCL", ignoreCase = true) ||
                msg.contains("Can not find", ignoreCase = true) ||
                msg.contains("GPU", ignoreCase = true)
    }

    private fun backendLabel(backend: Backend): String = when (backend) {
        is Backend.GPU -> "GPU"
        is Backend.CPU -> "CPU"
        else -> backend.javaClass.simpleName
    }

    private fun addToHistory(userMsg: String, assistantMsg: String) {
        manualHistory.add(Pair(userMsg, assistantMsg))
        while (manualHistory.size > maxHistoryTurns) manualHistory.removeAt(0)
    }

    private fun createFreshConversation(): Conversation? {
        val eng = engine ?: return null
        runCatching { conversation?.close() }
        return try {
            eng.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK, topP.toDouble(), temperature.toDouble()),
                    systemInstruction = Contents.of(Content.Text(systemPrompt))
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "createFreshConversation failed", e); null
        }
    }

    private fun buildReplayPrompt(currentQuery: String): String {
        if (manualHistory.isEmpty()) return currentQuery
        val sb = StringBuilder()
        for ((user, assistant) in manualHistory) {
            sb.append("User: $user\nAssistant: $assistant\n")
        }
        sb.append("User: $currentQuery")
        return sb.toString()
    }

    private fun stripThinkTags(text: String): String {
        var result = text
        while (result.contains("<think>")) {
            val open = result.indexOf("<think>")
            val close = result.indexOf("</think>", open)
            result = if (close == -1) result.substring(0, open)
            else result.substring(0, open) + result.substring(close + "</think>".length)
        }
        return result.trim()
    }
}
