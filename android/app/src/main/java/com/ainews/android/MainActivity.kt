package com.ainews.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ainews.android.data.NewsRepository
import com.ainews.android.ui.AiNewsApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        intent?.getStringExtra(EXTRA_STORY_ID)?.let(NewsRepository::selectStory)
    }

    companion object {
        const val EXTRA_STORY_ID = "com.ainews.android.extra.STORY_ID"
    }
}
