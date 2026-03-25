package com.taobao.meta.avatar.tts

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import com.k2fsa.sherpa.mnn.GeneratedAudio
import com.taobao.meta.avatar.MHConfig
import com.taobao.meta.avatar.debug.DebugModule
import com.taobao.meta.avatar.settings.MainSettings
import com.taobao.meta.avatar.utils.AppUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.json.JSONObject

class TtsService {

    val useSherpaTts
        get() = DebugModule.TTS_USE_SHERPA

    private var sherpaTts: SherpaTts? = null
    private var ttsServiceNative: Long = 0
    @Volatile
    private var isLoaded = false
    private var initDeferred: Deferred<Boolean>? = null
    private var sharedPreferences: SharedPreferences? = null
    private var applicationContext: Context? = null
    private var currentSpeakerId: String? = null
    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "tts_speaker_id") {
            handleSpeakerIdChange()
        }
    }

    init {
        ttsServiceNative = nativeCreateTTS(if (AppUtils.isChinese())  "zh" else "en")
    }

    fun destroy() {
        sharedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        sharedPreferences = null
        applicationContext = null
        nativeDestroy(ttsServiceNative)
        ttsServiceNative = 0
    }

    suspend fun init(modelDir: String?, context: Context? = null): Boolean {
        if (isLoaded) return true
        if (initDeferred == null) {
            initDeferred = CoroutineScope(Dispatchers.IO).async {
                if (useSherpaTts) {
                    sherpaTts = SherpaTts()
                    sherpaTts?.init(null)
                    return@async true
                }

                // Select model directory by language
                val isVietnamese = AppUtils.isVietnamese()
                val isChinese = AppUtils.isChinese()
                val actualModelDir = when {
                    isVietnamese -> MHConfig.TTS_MODEL_DIR_VI
                    isChinese    -> MHConfig.TTS_MODEL_DIR
                    else         -> MHConfig.TTS_MODEL_DIR_EN
                }

                // Build override params (only for supertonic/English)
                val overrideParams = mutableMapOf<String, String>()
                context?.let {
                    if (!isChinese && !isVietnamese) {
                        val speakerId = MainSettings.getTtsSpeakerId(it)
                        if (speakerId.isNotEmpty()) {
                            overrideParams["speaker_id"] = speakerId
                        }
                    }
                }

                val paramsJson = if (overrideParams.isEmpty()) "{}"
                else JSONObject(overrideParams as Map<*, *>).toString()

                Log.d(TAG, "Loading TTS from: $actualModelDir with params: $paramsJson")

                nativeLoadResourcesFromFile(
                    ttsServiceNative,
                    actualModelDir,
                    "",
                    "",
                    paramsJson
                )
                true
            }
        }
        val result = initDeferred!!.await()
        if (result) {
            isLoaded = true
            context?.let {
                registerPreferenceListener(it)
                if (!AppUtils.isChinese() && !AppUtils.isVietnamese()) {
                    currentSpeakerId = MainSettings.getTtsSpeakerId(it)
                }
            }
        }
        return result
    }

    private fun registerPreferenceListener(context: Context) {
        if (sharedPreferences == null) {
            applicationContext = context.applicationContext
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            sharedPreferences?.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        }
    }

    private fun handleSpeakerIdChange() {
        if (!isLoaded) return
        if (AppUtils.isChinese() || AppUtils.isVietnamese()) return
        applicationContext?.let { ctx ->
            val newSpeakerId = MainSettings.getTtsSpeakerId(ctx)
            if (newSpeakerId != currentSpeakerId && newSpeakerId.isNotEmpty()) {
                try {
                    setSpeakerId(newSpeakerId)
                    currentSpeakerId = newSpeakerId
                    Log.d(TAG, "Speaker ID changed to: $newSpeakerId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set speaker ID: $newSpeakerId", e)
                }
            }
        }
    }

    suspend fun waitForInitComplete(): Boolean {
        if (isLoaded) return true
        initDeferred?.let { return it.await() }
        return isLoaded
    }

    fun setCurrentIndex(index: Int) {
        nativeSetCurrentIndex(ttsServiceNative, index)
    }

    fun setSpeakerId(speakerId: String) {
        nativeSetSpeakerId(ttsServiceNative, speakerId)
    }

    /** Returns PCM int16 samples at the model's native sample rate.
     *  Call getSampleRate() to know the actual rate. */
    fun process(text: String, id: Int): ShortArray {
        return nativeProcess(ttsServiceNative, text, id)
    }

    fun processSherpa(text: String, id: Int): GeneratedAudio? {
        Log.d(TAG, "processSherpa: $text $id")
        synchronized(this) {
            return sherpaTts?.process(text)
        }
    }

    /** Returns the sample rate of the loaded TTS model (e.g. 22050 for Piper, 44100 for BertVits2). */
    fun getSampleRate(): Int {
        return if (isLoaded) nativeGetSampleRate(ttsServiceNative) else 22050
    }

    // Native methods
    private external fun nativeSetCurrentIndex(ttsServiceNative: Long, index: Int)
    private external fun nativeCreateTTS(language: String): Long
    private external fun nativeDestroy(nativePtr: Long)
    private external fun nativeLoadResourcesFromFile(
        nativePtr: Long,
        resourceDir: String,
        modelName: String,
        mmapDir: String,
        paramsJson: String
    ): Boolean
    private external fun nativeSetSpeakerId(nativePtr: Long, speakerId: String)
    private external fun nativeProcess(nativePtr: Long, text: String, id: Int): ShortArray
    private external fun nativeGetSampleRate(nativePtr: Long): Int

    companion object {
        private const val TAG = "TtsService"
    }
}

