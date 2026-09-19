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

    fun layoutMode(appWidgetId: Int): String? =
        prefs.getString(layoutKey(appWidgetId), null)

    fun saveLayoutMode(appWidgetId: Int, modeName: String?) {
        prefs.edit().apply {
            if (modeName == null) {
                remove(layoutKey(appWidgetId))
            } else {
                putString(layoutKey(appWidgetId), modeName)
            }
            putInt(stackKey(appWidgetId), 0)
        }.apply()
    }

    fun unreadOnly(appWidgetId: Int): Boolean =
        prefs.getBoolean(unreadKey(appWidgetId), false)

    fun saveUnreadOnly(appWidgetId: Int, unreadOnly: Boolean) {
        prefs.edit().putBoolean(unreadKey(appWidgetId), unreadOnly).apply()
    }

    fun pageIndex(appWidgetId: Int): Int =
        prefs.getInt(pageKey(appWidgetId), 0).coerceAtLeast(0)

    fun setPageIndex(appWidgetId: Int, index: Int) {
        prefs.edit().putInt(pageKey(appWidgetId), index.coerceAtLeast(0)).apply()
    }

    fun expandedStoryId(appWidgetId: Int): String? =
        prefs.getString(expandedKey(appWidgetId), null)

    fun toggleExpandedStory(appWidgetId: Int, storyId: String) {
        val current = expandedStoryId(appWidgetId)
        prefs.edit().apply {
            if (current == storyId) remove(expandedKey(appWidgetId)) else putString(expandedKey(appWidgetId), storyId)
        }.apply()
    }

    fun storyCount(appWidgetId: Int): Int =
        prefs.getInt(countKey(appWidgetId), 12).coerceIn(5, 20)

    fun saveStoryCount(appWidgetId: Int, count: Int) {
        prefs.edit()
            .putInt(countKey(appWidgetId), count.coerceIn(5, 20))
            .apply()
    }

    private fun unreadKey(appWidgetId: Int) = "unread-only-$appWidgetId"

    private fun expandedKey(appWidgetId: Int) = "expanded-story-$appWidgetId"

    private fun pageKey(appWidgetId: Int) = "page-index-$appWidgetId"

    fun moveStack(appWidgetId: Int, offset: Int) {
        prefs.edit()
            .putInt(stackKey(appWidgetId), stackIndex(appWidgetId) + offset)
            .apply()
    }

    fun clear(appWidgetId: Int) {
        prefs.edit()
            .remove(presetKey(appWidgetId))
            .remove(stackKey(appWidgetId))
            .remove(layoutKey(appWidgetId))
            .remove(countKey(appWidgetId))
            .apply()
    }

    private fun presetKey(appWidgetId: Int) = "widget_${appWidgetId}_preset"
    private fun stackKey(appWidgetId: Int) = "widget_${appWidgetId}_stack"
    private fun layoutKey(appWidgetId: Int) = "widget_${appWidgetId}_layout"
    private fun countKey(appWidgetId: Int) = "widget_${appWidgetId}_count"

    private companion object {
        const val PREFS_NAME = "widget_instances"
    }
}
