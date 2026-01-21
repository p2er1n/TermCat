package org.p2er1n.termcat

import android.content.Context

object AppPrefs {
    private const val PREFS_NAME = "termcat_prefs"
    private const val KEY_OVERLAY_RUNNING = "overlay_running"
    private const val KEY_LLM_ENDPOINT = "llm_endpoint"
    private const val KEY_LLM_MODEL = "llm_model"
    private const val KEY_LLM_API_KEY = "llm_api_key"

    fun setOverlayRunning(context: Context, running: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_OVERLAY_RUNNING, running)
            .apply()
    }

    fun isOverlayRunning(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OVERLAY_RUNNING, false)
    }

    fun getLlmEndpoint(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LLM_ENDPOINT, "") ?: ""
    }

    fun setLlmEndpoint(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LLM_ENDPOINT, value)
            .apply()
    }

    fun getLlmModel(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LLM_MODEL, "") ?: ""
    }

    fun setLlmModel(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LLM_MODEL, value)
            .apply()
    }

    fun getLlmApiKey(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LLM_API_KEY, "") ?: ""
    }

    fun setLlmApiKey(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LLM_API_KEY, value)
            .apply()
    }
}
