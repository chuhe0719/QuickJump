package com.chuhe.quickmax

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CodeItem(
    val id: Long,
    val code: String,
    val taken: Boolean,
    val source: String,
    val time: Long,
    val note: String = "",
    val platform: String = "",
    val station: String = "",
    val codeType: String = ""
)

object CodeStore {
    private const val PREFS = "pickup_codes"
    private const val KEY = "items"
    private const val TAG_PREFS = "tag_options"

    fun load(context: Context): MutableList<CodeItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<CodeItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    CodeItem(
                        id = o.optLong("id", i.toLong()),
                        code = o.optString("code", ""),
                        taken = o.optBoolean("taken", false),
                        source = o.optString("source", "手动"),
                        time = o.optLong("time", 0L),
                        note = o.optString("note", ""),
                        platform = o.optString("platform", ""),
                        station = o.optString("station", ""),
                        codeType = o.optString("codeType", "")
                    )
                )
            }
            list.filter { it.code.isNotBlank() }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun save(context: Context, items: List<CodeItem>) {
        val arr = JSONArray()
        for (item in items) {
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("code", item.code)
                    .put("taken", item.taken)
                    .put("source", item.source)
                    .put("time", item.time)
                    .put("note", item.note)
                    .put("platform", item.platform)
                    .put("station", item.station)
                    .put("codeType", item.codeType)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun addAll(context: Context, codes: List<String>, source: String, platform: String = ""): Pair<Int, Int> {
        val items = load(context)
        val existing = items.map { it.code }.toHashSet()
        var nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1
        val now = System.currentTimeMillis()
        var added = 0
        var duplicated = 0
        for (code in codes) {
            if (code.isBlank()) continue
            if (!existing.add(code)) {
                duplicated++
                continue
            }
            items.add(CodeItem(nextId++, code, false, source, now + added, platform = platform))
            added++
        }
        if (added > 0) save(context, items)
        return added to duplicated
    }

    fun loadTagOptions(context: Context, key: String, defaults: List<String>): MutableList<String> {
        val raw = context.getSharedPreferences(TAG_PREFS, Context.MODE_PRIVATE)
            .getString(key, null) ?: return defaults.toMutableList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val value = arr.optString(i, "").trim()
                if (value.isNotEmpty()) list.add(value)
            }
            list
        } catch (e: Exception) {
            defaults.toMutableList()
        }
    }

    fun saveTagOptions(context: Context, key: String, options: List<String>) {
        val arr = JSONArray()
        options.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { arr.put(it) }
        context.getSharedPreferences(TAG_PREFS, Context.MODE_PRIVATE)
            .edit().putString(key, arr.toString()).apply()
    }

    fun addTagOption(context: Context, key: String, defaults: List<String>, value: String) {
        val options = loadTagOptions(context, key, defaults)
        val trimmed = value.trim()
        if (trimmed.isNotEmpty() && !options.contains(trimmed)) {
            options.add(trimmed)
            saveTagOptions(context, key, options)
        }
    }

    fun removeTagOption(context: Context, key: String, defaults: List<String>, value: String) {
        val options = loadTagOptions(context, key, defaults)
        if (options.remove(value)) saveTagOptions(context, key, options)
    }
}
