package org.p2er1n.termcat

import android.content.Context

object AppPrefs {
    private const val PREFS_NAME = "termcat_prefs"
    private const val KEY_OVERLAY_RUNNING = "overlay_running"

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
}
