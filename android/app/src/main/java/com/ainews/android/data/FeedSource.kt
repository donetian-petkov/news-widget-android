package com.ainews.android.data

data class FeedSource(
    val id: String,
    val title: String,
    val url: String,
    val fetchEnabled: Boolean = true,
    val aiEnabled: Boolean = true,
    val neutralTitlesEnabled: Boolean = true,
)

val defaultFeedSources = listOf(
    FeedSource(
        id = "dnevnik",
        title = "dnevnik.bg",
        url = "https://www.dnevnik.bg/rss/",
    ),
    FeedSource(
        id = "standartnews",
        title = "standartnews.com",
        url = "https://standartnews.com/rss?p=1",
    ),
    FeedSource(
        id = "bta",
        title = "BTA",
        url = "https://www.bta.bg/en/rss/free",
    ),
    FeedSource(
        id = "capital",
        title = "capital.bg",
        url = "https://www.capital.bg/rss/",
    ),
    FeedSource(
        id = "actualno",
        title = "actualno.com",
        url = "https://actualno.com/rss",
    ),
    FeedSource(
        id = "bbc-world",
        title = "BBC World",
        url = "https://feeds.bbci.co.uk/news/world/rss.xml",
    ),
    FeedSource(
        id = "nyt-world",
        title = "NYT World",
        url = "https://rss.nytimes.com/services/xml/rss/nyt/World.xml",
    ),
    FeedSource(
        id = "al-jazeera",
        title = "Al Jazeera",
        url = "https://www.aljazeera.com/xml/rss/all.xml",
    ),
    FeedSource(
        id = "hollywood-reporter",
        title = "Hollywood Reporter",
        url = "https://hollywoodreporter.com/feed",
    ),
    FeedSource(
        id = "thr-movies",
        title = "THR Movies",
        url = "https://hollywoodreporter.com/c/movies/feed",
    ),
    FeedSource(
        id = "thr-tv",
        title = "THR TV",
        url = "https://hollywoodreporter.com/c/tv/feed",
    ),
    FeedSource(
        id = "thr-music",
        title = "THR Music",
        url = "https://hollywoodreporter.com/c/music/feed",
    ),
    FeedSource(
        id = "npr-music",
        title = "NPR Music",
        url = "https://www.npr.org/rss/rss.php?id=1008",
    ),
    FeedSource(
        id = "npr-movies",
        title = "NPR Movies",
        url = "https://www.npr.org/rss/rss.php?id=1045",
    ),
    FeedSource(
        id = "ign",
        title = "IGN",
        url = "https://www.ign.com/rss/v2/articles/feed?categories=news",
    ),
    FeedSource(
        id = "variety",
        title = "Variety",
        url = "https://feeds.feedburner.com/variety/headlines",
    ),
    FeedSource(
        id = "rolling-stone",
        title = "Rolling Stone",
        url = "https://www.rollingstone.com/music/music-news/feed/",
    ),
    FeedSource(
        id = "nyt-movies",
        title = "NYT Movies",
        url = "https://rss.nytimes.com/services/xml/rss/nyt/Movies.xml",
    ),
    FeedSource(
        id = "svobodna-tochka",
        title = "Svobodna Tochka",
        url = "https://svobodnatochka.bg/feed/",
    ),
)

fun mergeWithDefaultFeedSources(feedSources: List<FeedSource>): List<FeedSource> {
    val savedByUrl = feedSources.associateBy { it.url.normalizeFeedUrl() }
    val defaultUrls = defaultFeedSources.map { it.url.normalizeFeedUrl() }.toSet()
    val customFeeds = feedSources.filterNot { it.url.normalizeFeedUrl() in defaultUrls }
    val defaults = defaultFeedSources.map { source ->
        val saved = savedByUrl[source.url.normalizeFeedUrl()] ?: return@map source
        source.copy(
            fetchEnabled = saved.fetchEnabled,
            aiEnabled = saved.aiEnabled,
            neutralTitlesEnabled = saved.neutralTitlesEnabled,
        )
    }
    return defaults + customFeeds
}

private fun String.normalizeFeedUrl(): String =
    trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .removeSuffix("/")
