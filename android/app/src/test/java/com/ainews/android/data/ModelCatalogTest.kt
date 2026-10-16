package com.ainews.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files

/** A port of the Mac widget's modelCatalog.test.ts. */
class ModelCatalogTest {
    private fun day(n: Int): Long = 1_767_225_600_000L + n * 86_400_000L

    private fun model(id: String, created: Int, outputPerM: Double? = null, inputPerM: Double = (outputPerM ?: 0.0) / 5): ModelInfo =
        ModelCatalog.withPrices(
            CatalogProvider.OpenAI,
            listOf(CatalogModel(id, day(created))),
            if (outputPerM == null) emptyMap() else mapOf(ModelCatalog.priceKey(CatalogProvider.OpenAI, id) to ModelPrice(inputPerM, outputPerM)),
        ).single()

    @Test
    fun keepsTextModelsAndDropsSpeechImagesSnapshotsAndProVariants() {
        val ids = listOf("gpt-6-luna", "gpt-6-luna-pro", "gpt-4o-realtime", "gpt-image-2", "gpt-5.4-nano-2026-03-17", "gpt-4-0613", "text-embedding-3-small", "gpt-5.3-codex", "o3")
        assertEquals(listOf("gpt-6-luna", "o3"), ids.filter { ModelCatalog.isTextModel(CatalogProvider.OpenAI, it) })
        assertTrue(ModelCatalog.isTextModel(CatalogProvider.Anthropic, "claude-haiku-5-5"))
    }

    @Test
    fun matchesAnthropicIdsToOpenRouterPriceKeys() {
        assertEquals("anthropic/claude-haiku-5-5", ModelCatalog.priceKey(CatalogProvider.Anthropic, "claude-haiku-5-5"))
        assertEquals("anthropic/claude-opus-5", ModelCatalog.priceKey(CatalogProvider.Anthropic, "claude-opus-5-20260101"))
        assertEquals("$ · $0.10 in / $0.50 out per 1M tokens", ModelCatalog.costLabel(0.1, 0.5))
        assertEquals("$$$ · $2 in / $10 out per 1M tokens", ModelCatalog.costLabel(2.0, 10.0))
        assertEquals("price unknown", ModelCatalog.costLabel(null, null))
    }

    @Test
    fun balancedPicksTheNewestCheapModelForSummariesAndTheNewestMidPricedOneForResearch() {
        val models = listOf(model("flagship", 30, 50.0), model("mid-new", 29, 10.0), model("cheap-new", 28, 0.5), model("cheap-old", 1, 0.4), model("mid-old", 2, 8.0))
        val balanced = ModelPolicy.Balanced
        assertEquals("cheap-new", ModelCatalog.pickDefault(ModelKind.Summary, models, balanced))
        assertEquals("cheap-new", ModelCatalog.pickDefault(ModelKind.Ask, models, balanced))
        assertEquals("mid-new", ModelCatalog.pickDefault(ModelKind.Research, models, balanced))
        assertEquals("b", ModelCatalog.pickDefault(ModelKind.Summary, listOf(model("a", 1), model("b", 2)), balanced))
    }

    @Test
    fun byDefaultGivesEveryTaskTheCheapestModelOfTheNewestGeneration() {
        val models = listOf(
            model("gpt-6.1-sol", 30, 10.0), model("gpt-6-astra", 28, 50.0), model("gpt-6-luna", 27, 0.5),
            model("gpt-5-nano", 1, 0.4), model("gpt-5.6-terra", 20, 12.0),
        )
        assertEquals(ModelPolicy.Cheapest, ModelPolicy.DEFAULT)
        ModelKind.entries.forEach { kind -> assertEquals("gpt-6-luna", ModelCatalog.pickDefault(kind, models)) }
        assertEquals("gpt-6.1-sol", ModelCatalog.pickDefault(ModelKind.Research, models, ModelPolicy.Balanced))
        // A newer generation takes over once it is listed.
        assertEquals("gpt-7-mini", ModelCatalog.pickDefault(ModelKind.Summary, models + model("gpt-7-mini", 40, 3.0)))
        assertEquals(6, ModelCatalog.generationOf("openai/gpt-6.1-sol"))
        assertEquals(5, ModelCatalog.generationOf("claude-haiku-5-5"))
    }

    @Test
    fun keepsAChosenModelWhileItIsOfferedAndReplacesItWhenItIsRemoved() {
        val models = listOf(model("cheap", 5, 0.5), model("mid", 4, 10.0))
        assertEquals(ResolvedChoice(ModelChoice("mid", true)), ModelCatalog.resolveChoice(ModelKind.Summary, ModelChoice("mid", true), models))
        assertEquals(
            ResolvedChoice(ModelChoice("cheap", false), replaced = "gone"),
            ModelCatalog.resolveChoice(ModelKind.Summary, ModelChoice("gone", true), models),
        )
        // An automatic choice moves to the newest default.
        assertEquals(ResolvedChoice(ModelChoice("mid", false)), ModelCatalog.resolveChoice(ModelKind.Research, ModelChoice("old", false), models, ModelPolicy.Balanced))
    }

    @Test
    fun readsTheProviderListNewestFirst() {
        val body = """{"data":[{"id":"gpt-old","created":10},{"id":"gpt-new","created":20},{"id":"whisper-1","created":30}]}"""
        val (models, _) = ModelCatalog.fetch(CatalogProvider.OpenAI, "k", { url, headers ->
            if (url == ModelCatalog.PRICES_URL) error("offline")
            assertEquals("Bearer k", headers["Authorization"])
            body
        })
        assertEquals(listOf("gpt-new", "gpt-old"), models.map { it.id })
        try {
            ModelCatalog.fetch(CatalogProvider.OpenAI, "bad", { _, _ -> error("401 Unauthorized") })
            fail("a refused list must not pass as an empty one")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("401"))
        }
    }

    @Test
    fun readsOpenRouterPricesPerMillionTokensAndAnthropicDates() {
        val prices = ModelCatalog.parsePrices(
            """{"data":[{"id":"openai/gpt-6.1-luna","pricing":{"prompt":"0.0000001","completion":"0.0000005"}},
               {"id":"x/free:free","pricing":{"prompt":"0","completion":"0"}},{"id":"bad","pricing":{"prompt":"-1","completion":"1"}}]}""",
        )
        assertEquals(setOf("openai/gpt-6-1-luna"), prices.keys)
        assertEquals(0.5, prices.getValue("openai/gpt-6-1-luna").outputPerM, 1e-9)
        val claude = ModelCatalog.parseModels(
            CatalogProvider.Anthropic,
            """{"data":[{"id":"claude-a","created_at":"2026-01-01T00:00:00Z"},{"id":"claude-b","created_at":"2026-03-01T00:00:00Z"}]}""",
        )
        assertEquals(listOf("claude-b", "claude-a"), claude.map { it.id })
    }

    @Test
    fun storeKeepsTheListPricesAndChoicesForOfflineStarts() {
        val file = Files.createTempDirectory("models").resolve("model-catalog.json").toFile()
        val store = ModelCatalogStore(file)
        assertTrue(store.load().models.isEmpty())
        val models = listOf(CatalogModel("gpt-a", 2), CatalogModel("gpt-b", 1))
        val prices = mapOf("openai/gpt-a" to ModelPrice(0.1, 0.5), "openai/unrelated" to ModelPrice(1.0, 2.0))
        assertEquals(ModelPolicy.Cheapest, store.load().policy)
        store.save(ModelCatalogStore.Saved(CatalogProvider.OpenAI, models, prices, mapOf(ModelKind.Research to ModelChoice("gpt-b", true)), 42, ModelPolicy.Balanced))
        val loaded = ModelCatalogStore(file).load()
        assertEquals(CatalogProvider.OpenAI, loaded.provider)
        assertEquals(models, loaded.models)
        assertEquals(setOf("openai/gpt-a"), loaded.prices.keys)
        assertEquals(ModelChoice("gpt-b", true), loaded.choices[ModelKind.Research])
        assertEquals(42, loaded.fetchedAtMs)
        assertEquals(ModelPolicy.Balanced, loaded.policy)
        assertFalse(file.readText().contains("unrelated"))
    }
}
