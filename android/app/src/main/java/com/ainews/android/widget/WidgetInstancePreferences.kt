package com.ainews.android.widget

import android.content.Context

class WidgetInstancePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun presetId(appWidgetId: Int): String? =
        prefs.getString(presetKey(appWidgetId), null)

    fun savePresetId(appWidgetId: Int, presetId: String?) {
        prefs.edit().apply {
            if (presetId == null) {
                remove(presetKey(appWidgetId))
            } else {
                putString(presetKey(appWidgetId), presetId)
            }
            putInt(stackKey(appWidgetId), 0)
        }.apply()
    }

    fun stackIndex(appWidgetId: Int): Int =
        prefs.getInt(stackKey(appWidgetId), 0).coerceAtLeast(0)

    fun moveStack(appWidgetId: Int, offset: Int) {
        prefs.edit()
            .putInt(stackKey(appWidgetId), stackIndex(appWidgetId) + offset)
            .apply()
    }

    fun clear(appWidgetId: Int) {
        prefs.edit()
            .remove(presetKey(appWidgetId))
            .remove(stackKey(appWidgetId))
            .apply()
    }

    private fun presetKey(appWidgetId: Int) = "widget_${appWidgetId}_preset"
    private fun stackKey(appWidgetId: Int) = "widget_${appWidgetId}_stack"

    private companion object {
        const val PREFS_NAME = "widget_instances"
    }
}
