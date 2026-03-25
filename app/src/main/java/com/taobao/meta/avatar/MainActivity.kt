package com.taobao.meta.avatar

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.alibaba.mls.api.ApplicationProvider
import com.taobao.meta.avatar.MHConfig.A2BS_MODEL_DIR
import com.taobao.meta.avatar.a2bs.A2BSService
import com.taobao.meta.avatar.a2bs.AudioBlendShapePlayer
import com.taobao.meta.avatar.asr.AndroidSttService
import com.taobao.meta.avatar.debug.DebugModule
import com.taobao.meta.avatar.download.DownloadCallback
import com.taobao.meta.avatar.download.DownloadModule
import com.taobao.meta.avatar.llm.LlmPresenter
import com.taobao.meta.avatar.llm.LlmService
import com.taobao.meta.avatar.nnr.AvatarTextureView
import com.taobao.meta.avatar.nnr.NnrAvatarRender
import com.taobao.meta.avatar.record.RecordPermission
import com.taobao.meta.avatar.record.RecordPermission.REQUEST_RECORD_AUDIO_PERMISSION
import com.taobao.meta.avatar.settings.MainSettings
import com.taobao.meta.avatar.tts.PiperTtsEngine
import com.taobao.meta.avatar.utils.MemoryMonitor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.exitProcess


enum class ChatStatus {
    STATUS_IDLE,
    STATUS_INITIALIZING,
    STATUS_CALLING,
}

class MainActivity : AppCompatActivity(),
    MainView.MainViewCallback, DownloadCallback {

    private lateinit var avatarTextureView: AvatarTextureView
    private var a2bsService: A2BSService? = null
    private lateinit var llmService: LlmService
    private lateinit var llmPresenter: LlmPresenter
    private lateinit var piperTtsEngine: PiperTtsEngine
    private var memoryMonitor: MemoryMonitor? = null
    private var audioBendShapePlayer: AudioBlendShapePlayer? = null
    private lateinit var nnrAvatarRender: NnrAvatarRender
    private lateinit var sttService: AndroidSttService
    private var callingSessionId = System.currentTimeMillis()
    private var serviceInitializing = false
    private var answerSession = System.currentTimeMillis()
    private val initComplete = CompletableDeferred<Boolean>()
    private var chatStatus = ChatStatus.STATUS_IDLE
    private var chatSessionJobs = mutableSetOf<Job>()
    lateinit var mainView: MainView
    private lateinit var downloadManager: DownloadModule

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApplicationProvider.set(application)
        setContentView(R.layout.activity_main)
        downloadManager = DownloadModule(this)
        downloadManager.setDownloadCallback(this)
        MHConfig.BASE_DIR = downloadManager.getDownloadPath()
        mainView = MainView(this, this)
        memoryMonitor = MemoryMonitor(this)
        memoryMonitor!!.startMonitoring()
        avatarTextureView = findViewById(R.id.surface_view)
        avatarTextureView.setPlaceHolderView(findViewById(R.id.img_place_holder))
        a2bsService = A2BSService()
        piperTtsEngine = PiperTtsEngine(this)
        llmPresenter = LlmPresenter(mainView.textResponse)
        llmService = LlmService()
        nnrAvatarRender = NnrAvatarRender(avatarTextureView, MHConfig.NNR_MODEL_DIR)
        val debugModule = DebugModule()
        debugModule.setupDebug(this)
        sttService = AndroidSttService(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (downloadManager.isDownloadComplete() && !DebugModule.DEBUG_DISABLE_SERVICE_AUTO_START) {
            lifecycleScope.launch {
                setupServices()
            }
        }
        mainView.updateDownloadStatus(downloadManager.isDownloadComplete())
    }

    private fun stopAnswer() {
        Log.d(TAG, "stopAnswer")
        llmService.requestStop()
        llmPresenter.stop()
        nnrAvatarRender.reset()
        audioBendShapePlayer?.stop()
    }

    private fun cancelAllJobs() {
        chatSessionJobs.apply {
            forEach { it.cancel() }
            clear()
        }
    }

    override fun onEndCall() {
        Log.d(TAG, "onEndCall")
        chatStatus = ChatStatus.STATUS_IDLE
        showSystemBarsCompat()
        cancelAllJobs()
        stopAnswer()
        stopRecord()
        llmPresenter.reset()
        mainView.onCallEnded()
        avatarTextureView.enableGestures = false
        llmPresenter.onEndCall()
        audioBendShapePlayer?.stop()
    }

    override fun onStopAnswerClicked() {
        stopAnswer()
    }

    override fun onStartButtonClicked() {
        if (ActivityCompat.checkSelfPermission(
                this,
                RecordPermission.permissions[0]
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            handleStartChatInner()
        } else {
            ActivityCompat.requestPermissions(
                this,
                RecordPermission.permissions,
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        }
    }

    private fun handleStartChatInner() {
        chatStatus = ChatStatus.STATUS_INITIALIZING
        mainView.setInitialiing()
        lifecycleScope.launch {
            setupServices()
            if (chatStatus == ChatStatus.STATUS_INITIALIZING) {
                chatStatus = ChatStatus.STATUS_CALLING
                hideSystemBarsCompat()
                callingSessionId++
                llmPresenter.setCurrentSessionId(callingSessionId)
                onChatServiceStarted()
            }
        }
    }

    override fun onDownloadClicked() {
        lifecycleScope.launch {
            downloadManager.download()
        }
    }

    private fun onChatServiceStarted() {
        mainView.onChatServiceStarted()
        llmService.startNewSession()
        lifecycleScope.launch {
            delay(2000)
            mainView.viewRotateHint.visibility = View.GONE
            val welcomeText = getString(R.string.llm_welcome_text)
            ensureActive()
            llmPresenter.onLlmTextUpdate(welcomeText, callingSessionId)
            audioBendShapePlayer?.playSession(answerSession, welcomeText.split("[,，]"))
        }.apply {
            chatSessionJobs.add(this)
        }
        avatarTextureView.enableGestures = true
    }

    fun hideSystemBarsCompat() {
        val decorView = window.decorView
        val insetsController = WindowInsetsControllerCompat(window, decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun showSystemBarsCompat() {
        val decorView = window.decorView
        val insetsController = WindowInsetsControllerCompat(window, decorView)
        insetsController.show(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }

    private suspend fun setupServices() {
        if (initComplete.isCompleted) return
        if (serviceInitializing) {
            initComplete.await()
        }
        serviceInitializing = true
        lifecycleScope.async {
            val taskA2BS = async {
                val t = System.currentTimeMillis()
                loadA2BSModel()
                Log.i(TAG, "Task A2BS completed in ${System.currentTimeMillis() - t} ms")
            }
            val taskTTS = async {
                Log.i(TAG, "Task TTS init begin")
                val t = System.currentTimeMillis()
                loadTTSModel()
                Log.i(TAG, "Task TTS completed in ${System.currentTimeMillis() - t} ms")
            }
            val taskNNR = async {
                val t = System.currentTimeMillis()
                loadNNRModel()
                Log.i(TAG, "Task NNR completed in ${System.currentTimeMillis() - t} ms")
            }
            val taskLLM = async {
                val t = System.currentTimeMillis()
                loadLLMModel()
                Log.i(TAG, "Task LLM completed in ${System.currentTimeMillis() - t} ms")
            }
            val taskStt = async {
                val t = System.currentTimeMillis()
                setupSttService()
                Log.i(TAG, "Task STT completed in ${System.currentTimeMillis() - t} ms")
            }
            awaitAll(taskA2BS, taskTTS, taskNNR, taskLLM, taskStt)
            Log.i(TAG, "All services have been initialized")

            // Wire up STT → processAsrText
            sttService.onRecognizeText = { text ->
                if (chatStatus == ChatStatus.STATUS_CALLING) {
                    stopRecord()
                    lifecycleScope.launch {
                        processAsrText(text)
                    }
                }
            }
        }.await()
        initComplete.complete(true)
        mainView.updateDebugInfo()
        serviceInitializing = false
    }

    fun serviceInitialized(): Boolean = initComplete.isCompleted

    private fun processAsrText(text: String) {
        answerSession++
        Log.d(TAG, "onRecognizeText: $text sessionId: $answerSession")
        lifecycleScope.launch {
            llmPresenter.onUserTextUpdate(text)
            mainView.textStatus.setText(R.string.click_to_stop)
        }
        llmPresenter.start()
        audioBendShapePlayer?.startNewSession(answerSession)
        lifecycleScope.launch {
            val callingSessionId = this@MainActivity.callingSessionId
            llmService.generate(text).collect { pair ->
                audioBendShapePlayer?.playStreamText(pair.first)
                if (pair.first != null) {
                    llmPresenter.onLlmTextUpdate(pair.first!!, callingSessionId)
                }
            }
        }.apply {
            chatSessionJobs.add(this)
        }
    }

    fun getAudioBlendShapePlayer(): AudioBlendShapePlayer? = audioBendShapePlayer

    private fun createAudioBlendShapePlayer() {
        audioBendShapePlayer = AudioBlendShapePlayer(nnrAvatarRender, this@MainActivity)
        audioBendShapePlayer!!.addListener(object : AudioBlendShapePlayer.Listener {
            override fun onPlayStart() {
                stopRecord()
            }

            override fun onPlayEnd() {
                if (chatStatus == ChatStatus.STATUS_CALLING) {
                    startRecord()
                }
            }
        })
    }

    private suspend fun setupSttService() {
        sttService.initRecognizer()
    }

    fun getA2bsService(): A2BSService = a2bsService!!

    fun getPiperTtsEngine(): PiperTtsEngine = piperTtsEngine

    override fun onStart() {
        super.onStart()
        if (serviceInitialized()) {
            mainView.updateDebugInfo()
        }
    }

    override fun onStop() {
        Log.d(TAG, "onStop")
        super.onStop()
        if (chatStatus == ChatStatus.STATUS_CALLING) {
            onEndCall()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sttService.destroy()
        piperTtsEngine.destroy()
        if (memoryMonitor != null) {
            memoryMonitor!!.stopMonitoring()
        }
        exitProcess(0)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "onConfigurationChanged")
        recreate()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    handleStartChatInner()
                } else {
                    Toast.makeText(this, R.string.record_permission_denied, Toast.LENGTH_SHORT).show()
                    chatStatus = ChatStatus.STATUS_IDLE
                }
            }
        }
    }

    private suspend fun loadNNRModel() {
        nnrAvatarRender.waitForInitComplete()
    }

    /**
     * Load LLM model.
     * Priority: saved path from Settings → scanned from Downloads → fallback MHConfig.LLM_MODEL_DIR
     */
    private suspend fun loadLLMModel() {
        val modelDir = resolveLlmModelDir()
        Log.i(TAG, "Loading LLM from: $modelDir")
        llmService.init(modelDir)
    }

    private suspend fun resolveLlmModelDir(): String = withContext(Dispatchers.IO) {
        // 1. Use previously saved path if still valid
        val savedPath = MainSettings.getLlmModelPath(this@MainActivity)
        if (savedPath != null && File(savedPath, "config.json").exists()) {
            return@withContext savedPath
        }

        // 2. Scan /sdcard/Download/ for folders containing config.json
        val found = scanLlmModelsInDownloads()
        if (found.isNotEmpty()) {
            val path = found.first().absolutePath
            MainSettings.setLlmModelPath(this@MainActivity, path)
            Log.i(TAG, "Auto-selected LLM model from Downloads: $path")
            return@withContext path
        }

        // 3. Fallback to default ModelScope path
        MHConfig.LLM_MODEL_DIR
    }

    /**
     * Scan the public Downloads directory for folders that contain a config.json.
     * These are treated as valid MNN LLM model directories.
     */
    fun scanLlmModelsInDownloads(): List<File> {
        val downloadDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS)
        return try {
            downloadDir.listFiles()
                ?.filter { it.isDirectory && File(it, "config.json").exists() }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "scanLlmModelsInDownloads failed", e)
            emptyList()
        }
    }

    private suspend fun loadTTSModel() {
        withContext(Dispatchers.IO) {
            piperTtsEngine.init()
        }
    }

    private suspend fun loadA2BSModel() {
        a2bsService!!.init(A2BS_MODEL_DIR, this)
        createAudioBlendShapePlayer()
    }

    fun stopRecord() {
        sttService.stopRecord()
    }

    fun startRecord() {
        mainView.textStatus.text = getString(R.string.listening)
        sttService.startRecord()
    }

    fun getNnrRuntime(): NnrAvatarRender = nnrAvatarRender

    override fun onDownloadStart() {
        Log.d(TAG, "Download started")
    }

    override fun onDownloadProgress(
        progress: Double, currentBytes: Long, totalBytes: Long, speedInfo: String,
    ) {
        lifecycleScope.launch {
            mainView.updateDownloadProgress(currentBytes, totalBytes, speedInfo)
        }
    }

    override fun onDownloadComplete(success: Boolean, file: File?) {
        Log.d(TAG, "Download completed: $success")
        lifecycleScope.launch {
            setupServices()
            mainView.updateDownloadStatus(success && downloadManager.isDownloadComplete())
        }
    }

    override fun onDownloadError(error: Exception?) {
        Log.e(TAG, "Download error", error)
        mainView.onDownloadError(error)
    }

    companion object {
        private const val TAG = "MainActivity"
        init {
            System.loadLibrary("taoavatar")
        }
    }
}
