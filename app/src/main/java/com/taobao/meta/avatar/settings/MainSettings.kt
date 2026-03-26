// Created by ruoyi.sjd on 2025/3/26.
// Copyright (c) 2024 Alibaba Group Holding Limited All rights reserved.

package com.taobao.meta.avatar.settings

import android.content.Context
import android.preference.PreferenceManager
import com.taobao.meta.avatar.R


object MainSettings {

    private const val KEY_LLM_MODEL_NAME = "llm_model_name"

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
     * Returns the previously saved LLM model filename (.litertlm), or null if not set.
     * This is just the filename (e.g. "model.litertlm"), not a full path.
     * The engine will search for it in standard locations (/sdcard/Download/, etc.).
     */
    fun getLlmModelName(context: Context): String? =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getString(KEY_LLM_MODEL_NAME, null)
            ?.takeIf { it.isNotBlank() }

    /**
     * Persist the LLM model filename so it survives app restarts.
     */
    fun setLlmModelName(context: Context, name: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(KEY_LLM_MODEL_NAME, name)
            .apply()
    }
}
