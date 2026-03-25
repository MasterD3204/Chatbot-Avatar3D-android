package com.taobao.meta.avatar.tts

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Piper TTS engine using Sherpa-ONNX OfflineTts.
 *
 * Model files must be placed in assets/vits-piper-vi-huongly/:
 *   - huongly.onnx       (VITS model)
 *   - tokens.txt         (phoneme tokens)
 *   - espeak-ng-data/    (espeak-ng language data folder)
 *
 * Native library required in jniLibs/<abi>/:
 *   - libsherpa-onnx-jni.so
 *
 * Usage:
 *   val engine = PiperTtsEngine(context)
 *   engine.init()
 *   val (samples, sampleRate) = engine.generate("Xin chào")
 *   engine.destroy()
 */
class PiperTtsEngine(private val context: Context) {

    companion object {
        private const val TAG = "PiperTtsEngine"

        // Assets folder containing model files
        const val ASSETS_MODEL_DIR  = "vits-piper-vi-huongly"
        const val MODEL_NAME        = "huongly.onnx"
        const val TOKENS_NAME       = "tokens.txt"
        const val ESPEAK_DATA_DIR   = "vits-piper-vi-huongly/espeak-ng-data"

        // Synthesis params
        private const val SPEAKER_ID = 0
        private const val SPEED      = 0.85f
        private const val NUM_THREADS = 2
    }

    @Volatile private var tts: OfflineTts? = null
    @Volatile private var initialized = false

    /**
     * Initialize the TTS engine. Must be called before generate().
     * Safe to call multiple times (idempotent).
     * Should be called from a background thread (IO-bound).
     */
    fun init(): Boolean {
        if (initialized) return true
        return try {
            // 1. Copy espeak-ng-data from assets to external storage (needed by libespeak-ng at runtime)
            val espeakExternalPath = copyEspeakDataFromAssets()
            Log.i(TAG, "espeak-ng-data path: $espeakExternalPath")

            // 2. Build Sherpa-ONNX config
            //    model/tokens are read via assetManager (prefix = ASSETS_MODEL_DIR/)
            //    dataDir must be a real filesystem path (espeak-ng opens files directly)
            val vitsConfig = OfflineTtsVitsModelConfig(
                model   = "$ASSETS_MODEL_DIR/$MODEL_NAME",
                tokens  = "$ASSETS_MODEL_DIR/$TOKENS_NAME",
                dataDir = espeakExternalPath,
            )
            val modelConfig = OfflineTtsModelConfig(
                vits       = vitsConfig,
                numThreads = NUM_THREADS,
                provider   = "cpu",
                debug      = false,
            )
            val config = OfflineTtsConfig(model = modelConfig)

            // 3. Create OfflineTts using assetManager (reads .onnx from assets/)
            tts = OfflineTts(assetManager = context.assets, config = config)

            val sr = tts!!.sampleRate()
            if (sr <= 0) {
                Log.e(TAG, "Invalid sampleRate=$sr after init")
                tts!!.free(); tts = null
                return false
            }

            initialized = true
            Log.i(TAG, "PiperTtsEngine ready: sampleRate=$sr Hz")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "libsherpa-onnx-jni.so not found — add to jniLibs/<abi>/", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "PiperTtsEngine init failed", e)
            false
        }
    }

    /**
     * Synthesize [text] and return (FloatArray samples, sampleRate).
     * Samples are in range [-1.0, 1.0].
     * Returns (empty array, 0) on failure.
     */
    fun generate(text: String): Pair<FloatArray, Int> {
        val engine = tts
        if (engine == null || !initialized) {
            Log.w(TAG, "generate() called before init()")
            return Pair(FloatArray(0), 0)
        }
        if (text.isBlank()) return Pair(FloatArray(0), 0)

        return try {
            val audio = engine.generate(text = text, sid = SPEAKER_ID, speed = SPEED)
            Pair(audio.samples, audio.sampleRate)
        } catch (e: Exception) {
            Log.e(TAG, "generate() failed for text='${text.take(40)}'", e)
            Pair(FloatArray(0), 0)
        }
    }

    /**
     * Returns the sample rate of the loaded model, or 0 if not initialized.
     */
    fun sampleRate(): Int = tts?.sampleRate() ?: 0

    /**
     * Free native resources. Call when the engine is no longer needed.
     */
    fun destroy() {
        tts?.free()
        tts = null
        initialized = false
        Log.i(TAG, "PiperTtsEngine destroyed")
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Copy espeak-ng-data from assets to getExternalFilesDir().
     * Skips files that already exist (only copies once).
     *
     * @return Absolute path to the copied espeak-ng-data directory
     */
    private fun copyEspeakDataFromAssets(): String {
        val destRoot = context.getExternalFilesDir(null)!!.absolutePath
        copyAssetsRecursive(ESPEAK_DATA_DIR)
        return "$destRoot/$ESPEAK_DATA_DIR"
    }

    private fun copyAssetsRecursive(assetPath: String) {
        val list = try {
            context.assets.list(assetPath)
        } catch (e: IOException) {
            Log.e(TAG, "assets.list($assetPath) failed", e); null
        } ?: return

        if (list.isEmpty()) {
            // Leaf file — copy it
            copyAssetFile(assetPath)
        } else {
            // Directory — ensure dest dir exists, then recurse
            val destDir = File(context.getExternalFilesDir(null)!!, assetPath)
            destDir.mkdirs()
            for (child in list) {
                copyAssetsRecursive("$assetPath/$child")
            }
        }
    }

    private fun copyAssetFile(assetPath: String) {
        val destFile = File(context.getExternalFilesDir(null)!!, assetPath)
        // Skip if already present and non-empty
        if (destFile.exists() && destFile.length() > 0L) return
        destFile.parentFile?.mkdirs()
        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "copyAssetFile($assetPath) failed", e)
        }
    }
}
