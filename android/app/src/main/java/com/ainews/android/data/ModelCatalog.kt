package com.ainews.android.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.Locale

/*
 * The models each AI provider offers right now, with their prices. Nothing here names a
 * model: the lists come from the providers' own model endpoints, prices from OpenRouter's
 * public catalogue (which also lists OpenAI and Anthropic models at their own prices), and
 * defaults are picked by price and age, so new models show up and retired ones drop out.
 * A port of the Mac widget's backend/apps/api/src/modelCatalog.ts; keep the two in step.
 */

enum class CatalogProvider(val key: String) {
    OpenAI("openai"),
    Anthropic("claude"),
    ;

    companion object {
        fun of(provider: AiProvider): CatalogProvider? = when (provider) {
            AiProvider.OpenAI -> OpenAI
            AiProvider.Anthropic -> Anthropic
            AiProvider.LocalOnly -> null
        }
    }
}

/** What a model is used for: each gets its own choice. */
enum class ModelKind(val key: String, val label: String, val detail: String) {
    Summary("summary", "Story summaries", "Runs on every story, so the default is the newest cheap model"),
    Ask("ask", "Quick tasks", "Translations and neutral titles; the default is the newest cheap model"),
    Research("research", "Research and briefings", "The default is the newest mid-priced model; pick a cheaper one here to save"),
}

data class CatalogModel(val id: String, val createdMs: Long)

data class ModelPrice(val inputPerM: Double, val outputPerM: Double)

data class ModelInfo(
    val id: String,
    val createdMs: Long,
    /** USD per million input / output tokens, when known. */
    val inputPerM: Double? = null,
    val outputPerM: Double? = null,
    /** 1 cheap … 4 most expensive, from the output price. */
    val costTier: Int? = null,
    /** Short text for menus, e.g. "$ · $0.10 in / $0.50 out per 1M tokens". */
    val costLabel: String,
)

data class ModelChoice(val model: String, val byUser: Boolean)

/** [replaced]: the model the user had picked, which the provider no longer offers. */
data class ResolvedChoice(val choice: ModelChoice, val replaced: String? = null)

object ModelCatalog {
    // Ids that are not general text models (speech, images, embeddings, coding agents), dated
    // snapshots of a model that is listed anyway, and "-pro" variants, which only run through
    // background requests. Patterns describe kinds of models, not particular ones.
    private val NOT_FOR_TEXT = Regex(
        "(audio|realtime|tts|transcribe|whisper|image|dall-e|sora|search|embed|moderation|codex|computer-use|live|instruct|babbage|davinci)",
        RegexOption.IGNORE_CASE,
    )
    private val DATED_SNAPSHOT = Regex("-(\\d{4}-?\\d{2}-?\\d{2}|\\d{4})$")
    private val PRO_VARIANT = Regex("-pro(-|$)", RegexOption.IGNORE_CASE)
    private val OPENAI_FAMILY = Regex("^(gpt-|o\\d|chatgpt-)", RegexOption.IGNORE_CASE)
    private val CHAT_LATEST = Regex("chat-latest$", RegexOption.IGNORE_CASE)
    private val CLAUDE_FAMILY = Regex("^claude-", RegexOption.IGNORE_CASE)
    private val ANTHROPIC_DATE = Regex("-\\d{8}$")

    fun isTextModel(provider: CatalogProvider, id: String): Boolean {
        val value = id.trim()
        if (value.isEmpty()) return false
        if (NOT_FOR_TEXT.containsMatchIn(value) || PRO_VARIANT.containsMatchIn(value) || ':' in value) return false
        return when (provider) {
            CatalogProvider.OpenAI ->
                OPENAI_FAMILY.containsMatchIn(value) && !DATED_SNAPSHOT.containsMatchIn(value) && !CHAT_LATEST.containsMatchIn(value)
            CatalogProvider.Anthropic -> CLAUDE_FAMILY.containsMatchIn(value)
        }
    }

    /** "anthropic/claude-haiku-5.5" and Anthropic's "claude-haiku-5-5" give the same key. */
    fun priceKey(provider: CatalogProvider, id: String): String {
        val prefix = if (provider == CatalogProvider.OpenAI) "openai/" else "anthropic/"
        val bare = id.trim().lowercase(Locale.ROOT).replace(ANTHROPIC_DATE, "")
        return "$prefix$bare".replace('.', '-')
    }

    fun costTier(outputPerM: Double?): Int? = when {
        outputPerM == null || !outputPerM.isFinite() -> null
        outputPerM <= 1 -> 1
        outputPerM <= 5 -> 2
        outputPerM <= 20 -> 3
        else -> 4
    }

    private fun money(value: Double): String = when {
        value == 0.0 -> "$0"
        value < 1 -> "$" + String.format(Locale.ROOT, "%.2f", value)
        else -> "$" + BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    fun costLabel(inputPerM: Double?, outputPerM: Double?): String {
        if (inputPerM == null || outputPerM == null) return "price unknown"
        if (inputPerM == 0.0 && outputPerM == 0.0) return "free"
        val tier = "$".repeat(costTier(outputPerM) ?: 1)
        return "$tier · ${money(inputPerM)} in / ${money(outputPerM)} out per 1M tokens"
    }

    fun withPrices(provider: CatalogProvider, models: List<CatalogModel>, prices: Map<String, ModelPrice>): List<ModelInfo> =
        models.map { model ->
            val price = prices[priceKey(provider, model.id)]
            ModelInfo(
                id = model.id,
                createdMs = model.createdMs,
                inputPerM = price?.inputPerM,
                outputPerM = price?.outputPerM,
                costTier = costTier(price?.outputPerM),
                costLabel = costLabel(price?.inputPerM, price?.outputPerM),
            )
        }

    private val newestFirst = compareByDescending<ModelInfo> { it.createdMs }.thenBy { it.id.length }.thenBy { it.id }

    /**
     * Summaries and quick tasks run on every story, so they get the newest cheap model
     * (at most $1 per million output tokens). Research and briefings get the newest
     * mid-priced one (up to $15), not the most expensive flagship. Without prices: the
     * newest model.
     */
    fun pickDefault(kind: ModelKind, models: List<ModelInfo>): String? {
        val sorted = models.sortedWith(newestFirst)
        val priced = sorted.filter { it.outputPerM != null }
        if (priced.isEmpty()) return sorted.firstOrNull()?.id
        fun out(model: ModelInfo) = model.outputPerM!!
        val pick = if (kind == ModelKind.Research) {
            priced.firstOrNull { out(it) > 1 && out(it) <= 15 }
        } else {
            priced.firstOrNull { out(it) <= 1 }
        }
        if (pick != null) return pick.id
        // Nothing in that price band: the cheapest (or for research the dearest) of the newest few.
        val recent = priced.take(6).sortedWith(
            if (kind == ModelKind.Research) compareByDescending { out(it) } else compareBy { out(it) },
        )
        return recent.firstOrNull()?.id
    }

    /**
     * Keeps a model the user chose while the provider still offers it. When it is gone, or
     * the choice was automatic, the default for that kind takes over; [ResolvedChoice.replaced]
     * says what was dropped so the user can be told.
     */
    fun resolveChoice(kind: ModelKind, current: ModelChoice?, models: List<ModelInfo>): ResolvedChoice {
        val offered = models.map { it.id }.toSet()
        if (current != null && current.model.isNotEmpty() && current.byUser && current.model in offered) return ResolvedChoice(current)
        val fallback = pickDefault(kind, models) ?: current?.model.orEmpty()
        if (current != null && current.byUser && current.model.isNotEmpty() && current.model !in offered) {
            return ResolvedChoice(ModelChoice(fallback, byUser = false), replaced = current.model)
        }
        return ResolvedChoice(ModelChoice(fallback, byUser = false))
    }

    /** OpenRouter's public catalogue: USD per million tokens by price key. */
    fun parsePrices(body: String): Map<String, ModelPrice> {
        val data = runCatching { JSONObject(body).optJSONArray("data") }.getOrNull() ?: return emptyMap()
        val prices = mutableMapOf<String, ModelPrice>()
        for (index in 0 until data.length()) {
            val model = data.optJSONObject(index) ?: continue
            val id = model.optString("id")
            val pricing = model.optJSONObject("pricing") ?: continue
            val input = pricing.optString("prompt").toDoubleOrNull()
            val output = pricing.optString("completion").toDoubleOrNull()
            if (id.isEmpty() || ':' in id || input == null || output == null || !input.isFinite() || !output.isFinite() || input < 0 || output < 0) continue
            prices[id.lowercase(Locale.ROOT).replace('.', '-')] = ModelPrice(input * 1e6, output * 1e6)
        }
        return prices
    }

    /** The provider's own list of models, text models only, newest first. */
    fun parseModels(provider: CatalogProvider, body: String): List<CatalogModel> {
        val data = runCatching { JSONObject(body).optJSONArray("data") }.getOrNull() ?: return emptyList()
        val rows = (0 until data.length()).mapNotNull { index ->
            val model = data.optJSONObject(index) ?: return@mapNotNull null
            val created = when (provider) {
                CatalogProvider.OpenAI -> model.optLong("created") * 1000
                CatalogProvider.Anthropic -> runCatching { Instant.parse(model.optString("created_at")).toEpochMilli() }.getOrDefault(0L)
            }
            CatalogModel(model.optString("id"), created)
        }
        val seen = mutableSetOf<String>()
        return rows
            .filter { isTextModel(provider, it.id) && seen.add(it.id) }
            .sortedByDescending { it.createdMs }
    }

    /** Where each provider lists its models, and the headers it wants for this key. */
    fun modelsRequest(provider: CatalogProvider, apiKey: String): Pair<String, Map<String, String>> = when (provider) {
        CatalogProvider.OpenAI -> "https://api.openai.com/v1/models" to mapOf("Authorization" to "Bearer $apiKey")
        CatalogProvider.Anthropic -> "https://api.anthropic.com/v1/models?limit=1000" to
            mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01")
    }

    const val PRICES_URL = "https://openrouter.ai/api/v1/models"

    /**
     * Reads the provider's list and the prices through [get], which returns a body or throws
     * on a refused request. Prices are optional: without them the list still loads, with
     * "price unknown" labels and the newest model as each default.
     */
    fun fetch(
        provider: CatalogProvider,
        apiKey: String,
        get: (url: String, headers: Map<String, String>) -> String,
        knownPrices: Map<String, ModelPrice> = emptyMap(),
    ): Pair<List<CatalogModel>, Map<String, ModelPrice>> {
        val (url, headers) = modelsRequest(provider, apiKey)
        val models = parseModels(provider, get(url, headers))
        val prices = runCatching { parsePrices(get(PRICES_URL, emptyMap())) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: knownPrices
        return models to prices
    }
}

/** What the app knows about the provider's models, as the settings screen shows it. */
data class ModelCatalogState(
    val provider: CatalogProvider? = null,
    val models: List<ModelInfo> = emptyList(),
    val choices: Map<ModelKind, ModelChoice> = emptyMap(),
    val fetchedAtMs: Long = 0,
    val refreshing: Boolean = false,
    val error: String? = null,
) {
    fun modelFor(kind: ModelKind): String? = choices[kind]?.model?.takeIf { it.isNotBlank() }

    fun info(id: String?): ModelInfo? = models.firstOrNull { it.id == id }
}

/**
 * Keeps the last model list, its prices and the reader's choices in one JSON file, so the
 * app starts with them offline and choices survive restarts.
 */
class ModelCatalogStore(private val file: File) {
    class Saved(
        val provider: CatalogProvider?,
        val models: List<CatalogModel>,
        val prices: Map<String, ModelPrice>,
        val choices: Map<ModelKind, ModelChoice>,
        val fetchedAtMs: Long,
    )

    fun load(): Saved = runCatching {
        val json = JSONObject(file.readText())
        val models = json.optJSONArray("models") ?: JSONArray()
        val prices = json.optJSONObject("prices") ?: JSONObject()
        val choices = json.optJSONObject("choices") ?: JSONObject()
        Saved(
            provider = CatalogProvider.entries.firstOrNull { it.key == json.optString("provider") },
            models = (0 until models.length()).mapNotNull { index ->
                models.optJSONObject(index)?.let { CatalogModel(it.optString("id"), it.optLong("createdMs")) }
            }.filter { it.id.isNotBlank() },
            prices = prices.keys().asSequence().mapNotNull { key ->
                prices.optJSONObject(key)?.let { key to ModelPrice(it.optDouble("in"), it.optDouble("out")) }
            }.toMap(),
            choices = ModelKind.entries.mapNotNull { kind ->
                choices.optJSONObject(kind.key)?.let { kind to ModelChoice(it.optString("model"), it.optBoolean("byUser")) }
            }.toMap(),
            fetchedAtMs = json.optLong("fetchedAtMs"),
        )
    }.getOrDefault(Saved(null, emptyList(), emptyMap(), emptyMap(), 0))

    @Synchronized
    fun save(saved: Saved) {
        val json = JSONObject()
            .put("provider", saved.provider?.key.orEmpty())
            .put("fetchedAtMs", saved.fetchedAtMs)
            .put("models", JSONArray().also { array -> saved.models.forEach { array.put(JSONObject().put("id", it.id).put("createdMs", it.createdMs)) } })
            // Only the prices of listed models: OpenRouter's whole catalogue is large.
            .put(
                "prices",
                JSONObject().also { obj ->
                    saved.models.forEach { model ->
                        saved.provider?.let { provider ->
                            val key = ModelCatalog.priceKey(provider, model.id)
                            saved.prices[key]?.let { obj.put(key, JSONObject().put("in", it.inputPerM).put("out", it.outputPerM)) }
                        }
                    }
                },
            )
            .put(
                "choices",
                JSONObject().also { obj ->
                    saved.choices.forEach { (kind, choice) -> obj.put(kind.key, JSONObject().put("model", choice.model).put("byUser", choice.byUser)) }
                },
            )
        file.parentFile?.mkdirs()
        file.writeText(json.toString())
    }
}
