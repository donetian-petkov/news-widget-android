package com.ainews.android.data

import org.json.JSONArray
import java.io.File

/** Keeps the newest [keep] briefings as HTML files plus an index.json in [dir]. */
class BriefingStore(private val dir: File, private val keep: Int = 96) {
    private val indexFile = File(dir, "index.json")

    @Volatile
    private var records: List<BriefingRecord> = load()

    private fun load(): List<BriefingRecord> = runCatching {
        val array = JSONArray(indexFile.readText())
        (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(BriefingRecord::fromJson) }
    }.getOrDefault(emptyList())

    fun list(): List<BriefingRecord> = records

    fun latest(): BriefingRecord? = records.firstOrNull()

    fun readHtml(id: String): String? {
        if (!BriefingEngine.isValidId(id) || records.none { it.id == id }) return null
        return runCatching { File(dir, "$id.html").readText() }.getOrNull()
    }

    @Synchronized
    fun save(briefing: Briefing) {
        dir.mkdirs()
        File(dir, "${briefing.record.id}.html").writeText(briefing.html)
        val record = briefing.record
        // A scheduled run replaces an earlier scheduled run for the same hour.
        val replaced = if (record.scheduled) {
            records.filter { it.scheduled && it.windowStartMs == record.windowStartMs }
        } else {
            emptyList()
        }
        val all = (listOf(record) + (records - replaced.toSet())).sortedByDescending { it.generatedAtMs }
        (replaced + all.drop(keep)).forEach { File(dir, "${it.id}.html").delete() }
        records = all.take(keep)
        writeIndex()
    }

    @Synchronized
    fun delete(id: String) {
        if (records.none { it.id == id }) return
        File(dir, "$id.html").delete()
        records = records.filterNot { it.id == id }
        writeIndex()
    }

    private fun writeIndex() {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        indexFile.writeText(array.toString())
    }
}
