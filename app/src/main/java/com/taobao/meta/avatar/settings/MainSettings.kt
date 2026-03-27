package com.taobao.meta.avatar.settings

import android.content.Context
import android.preference.PreferenceManager
import com.taobao.meta.avatar.R


object MainSettings {

    fun getLlmPrompt(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val prompt = prefs.getString("llm_prompt", null)
        return if (prompt.isNullOrEmpty()) context.getString(R.string.llm_prompt_default) else prompt
    }

    fun isShowDebugInfo(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean("show_debug_info", true)
}
