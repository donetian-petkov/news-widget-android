package com.ainews.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.ainews.android.data.NewsRepository
import com.ainews.android.data.StoryDetailSection
import com.ainews.android.ui.AiNewsApp

class MainActivity : ComponentActivity() {
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        selectIntentStory()
        setContent {
            AiNewsApp()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        selectIntentStory()
    }

    private fun selectIntentStory() {
        intent?.getStringExtra(EXTRA_STORY_ID)?.let { storyId ->
            val section = intent
                ?.getStringExtra(EXTRA_STORY_SECTION)
                ?.let { runCatching { StoryDetailSection.valueOf(it) }.getOrNull() }
                ?: StoryDetailSection.Story
            NewsRepository.selectStory(storyId, section)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_STORY_ID = "com.ainews.android.extra.STORY_ID"
        const val EXTRA_STORY_SECTION = "com.ainews.android.extra.STORY_SECTION"
    }
}
