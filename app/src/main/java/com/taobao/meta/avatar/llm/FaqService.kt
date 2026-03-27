package com.taobao.meta.avatar.llm

import android.content.Context
import android.util.Log
import com.taobao.meta.avatar.rag.fasttext.FastTextRagEngine

/**
 * FAQ-based answer service using FastText semantic search.
 * Replaces LLM: returns pre-written answers from qa_database.txt
 * or a default "no answer" message when no match is found.
 */
class FaqService(context: Context) {

    companion object {
        private const val TAG = "FaqService"
        const val NO_ANSWER = "Xin lỗi, tôi chưa có thông tin về câu hỏi này."
        private const val QA_FILE     = "qa_database.txt"
        private const val VECTOR_FILE = "vi_fasttext_pruned.vec"
    }

    private val ragEngine = FastTextRagEngine(context)

    suspend fun init(): Boolean {
        return try {
            Log.i(TAG, "Initializing FAQ engine...")
            ragEngine.initialize(QA_FILE, VECTOR_FILE)
            Log.i(TAG, "FAQ engine ready: ${ragEngine.isInitialized}")
            ragEngine.isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "init failed", e)
            false
        }
    }

    fun isReady(): Boolean = ragEngine.isInitialized

    /**
     * Returns the best-matching answer from the FAQ database,
     * or [NO_ANSWER] if no confident match is found.
     */
    suspend fun getAnswer(query: String): String {
        val answers = ragEngine.search(query)
        return if (!answers.isNullOrEmpty()) {
            Log.i(TAG, "FAQ hit for '$query': ${answers.first().take(60)}")
            answers.first()
        } else {
            Log.i(TAG, "FAQ miss for '$query'")
            NO_ANSWER
        }
    }

    fun release() {
        ragEngine.release()
    }
}
