package org.p2er1n.termcat

import android.content.Context

object AppPrefs {
    private const val PREFS_NAME = "termcat_prefs"
    private const val KEY_OVERLAY_RUNNING = "overlay_running"
    private const val KEY_LLM_ENDPOINT = "llm_endpoint"
    private const val KEY_LLM_MODEL = "llm_model"
    private const val KEY_LLM_API_KEY = "llm_api_key"
    private const val KEY_OCR_ENGINE = "ocr_engine"
    private const val KEY_MAX_CAPTURE_PAGES = "max_capture_pages"
    private const val DEFAULT_MAX_CAPTURE_PAGES = 30
    private const val MIN_CAPTURE_PAGES = 1
    private const val MAX_CAPTURE_PAGES = 120

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

    fun getMaxCapturePages(context: Context): Int {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MAX_CAPTURE_PAGES, DEFAULT_MAX_CAPTURE_PAGES)
        return value.coerceIn(MIN_CAPTURE_PAGES, MAX_CAPTURE_PAGES)
    }

    fun setMaxCapturePages(context: Context, value: Int) {
        val clamped = value.coerceIn(MIN_CAPTURE_PAGES, MAX_CAPTURE_PAGES)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MAX_CAPTURE_PAGES, clamped)
            .apply()
    }

    fun getOcrEngine(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_OCR_ENGINE, OCR_ENGINE_MLKIT) ?: OCR_ENGINE_MLKIT
    }

    fun setOcrEngine(context: Context, value: String) {
        val safeValue = if (value == OCR_ENGINE_PADDLE) OCR_ENGINE_PADDLE else OCR_ENGINE_MLKIT
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_OCR_ENGINE, safeValue)
            .apply()
    }

    const val OCR_ENGINE_MLKIT = "mlkit"
    const val OCR_ENGINE_PADDLE = "paddle"
}
