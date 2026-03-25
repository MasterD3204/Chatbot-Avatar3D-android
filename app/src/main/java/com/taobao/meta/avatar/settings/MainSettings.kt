// Created by ruoyi.sjd on 2025/3/26.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package com.taobao.meta.avatar.settings

import android.content.Context
import android.preference.PreferenceManager
import com.taobao.meta.avatar.R


object MainSettings {

    private const val KEY_LLM_MODEL_PATH = "llm_model_path"

    fun getLlmPrompt(context: Context): String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val llmPrompt = sharedPreferences.getString("llm_prompt", null)
        return if (llmPrompt.isNullOrEmpty()) {
            context.getString(R.string.llm_prompt_default)
        } else {
            llmPrompt
        }
    }

    fun isShowDebugInfo(context: Context): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPreferences.getBoolean("show_debug_info", true)
    }

    /**
     * Returns the previously saved LLM model directory path, or null if not set.
     * The path is auto-populated when a model is found in Downloads.
     */
    fun getLlmModelPath(context: Context): String? =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(KEY_LLM_MODEL_PATH, null)
            ?.takeIf { it.isNotBlank() }

    /**
     * Persist the LLM model directory path so it survives app restarts.
     */
    fun setLlmModelPath(context: Context, path: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(KEY_LLM_MODEL_PATH, path)
            .apply()
    }
}