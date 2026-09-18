package com.ainews.android.data

data class FeedSource(
    val id: String,
    val title: String,
    val url: String,
)

val defaultFeedSources = listOf(
    FeedSource(
        id = "bbc-world",
        title = "BBC World",
        url = "https://feeds.bbci.co.uk/news/world/rss.xml",
    ),
    FeedSource(
        id = "google-bg",
        title = "Bulgaria Headlines",
        url = "https://news.google.com/rss/search?q=Bulgaria&hl=en-US&gl=US&ceid=US:en",
    ),
    FeedSource(
        id = "google-ai-policy",
        title = "AI Policy",
        url = "https://news.google.com/rss/search?q=AI%20policy&hl=en-US&gl=US&ceid=US:en",
    ),
)
