// Created by ruoyi.sjd on 2025/3/12.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package com.taobao.meta.avatar.debug

import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.taobao.meta.avatar.MHConfig
import com.taobao.meta.avatar.MainActivity
import com.taobao.meta.avatar.R
import com.taobao.meta.avatar.a2bs.A2BSService
import com.taobao.meta.avatar.a2bs.AudioBlendShapePlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DebugModule {

    companion object {
        private const val TAG = "DebugModule"
        const val DEBUG_DISABLE_A2BS = false
        const val DEBUG_DISABLE_NNR = false
        const val DEBUG_DISABLE_SERVICE_AUTO_START = false
        const val DEBUG_USE_PRIVATE = false
    }

    private lateinit var a2bsButton: View
    private lateinit var llmButton: View
    private lateinit var a2BSService: A2BSService
    private lateinit var activity: MainActivity

    fun setupDebug(activity: MainActivity) {
        if (!MHConfig.DEBUG_MODE) {
            return
        }
        if (MHConfig.DEBUG_SCREEN_SHOT) {
            activity.mainView.viewMask.visibility = View.GONE
            return
        }
        this.activity = activity
        activity.findViewById<View>(R.id.debug_layout).visibility = View.VISIBLE
        a2bsButton = activity.findViewById(R.id.test_a2bs_button)
        llmButton = activity.findViewById(R.id.test_llm_button)
        a2BSService = activity.getA2bsService()
        activity.findViewById<View>(R.id.test_asr_button).setOnClickListener { testAsr() }
        llmButton.setOnClickListener { testLlm() }
        a2bsButton.setOnClickListener {
            Log.d(TAG, "testA2bs begin")
            activity.lifecycleScope.launch { testA2bs() }
        }
    }

    private fun testAsr() {}

    private fun testLlm() {}

    private suspend fun testA2bs() {
        Log.d(TAG, "testA2bs begin")
        a2BSService.waitForInitComplete()
        Log.d(TAG, "testA2bs a2bs init completed")
        while (!activity.serviceInitialized()) {
            delay(200)
        }
        Log.d(TAG, "testA2bs all service init completed")
        val audioBsPlayer = activity.getAudioBlendShapePlayer() ?: return
        testAudioBlendShapePlayer(audioBsPlayer, listOf(
            "Xin chào",
            "Tôi là trợ lý ảo",
        ))
    }

    fun testAudioBlendShapePlayer(audioBsPlayer: AudioBlendShapePlayer, texts: List<String>) {
        audioBsPlayer.playSession(System.currentTimeMillis(), texts)
    }
}
