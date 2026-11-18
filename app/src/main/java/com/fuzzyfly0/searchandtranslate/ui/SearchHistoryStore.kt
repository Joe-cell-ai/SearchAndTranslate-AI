package com.fuzzyfly0.searchandtranslate.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stores only action labels and timestamps; screenshots, OCR text and query contents are never saved. */
internal object SearchHistoryStore {
    private const val PREFS = "private_search_history"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 50

    @Synchronized
    fun record(context: Context, action: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val entries = runCatching { JSONArray(prefs.getString(KEY_ENTRIES, "[]")) }.getOrDefault(JSONArray())
        val updated = JSONArray()
        updated.put(JSONObject().put("action", action).put("time", System.currentTimeMillis()))
        for (i in 0 until minOf(entries.length(), MAX_ENTRIES - 1)) updated.put(entries.getJSONObject(i))
        prefs.edit().putString(KEY_ENTRIES, updated.toString()).apply()
    }

    fun read(context: Context): List<Pair<String, Long>> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val entries = runCatching { JSONArray(prefs.getString(KEY_ENTRIES, "[]")) }.getOrDefault(JSONArray())
        return buildList {
            for (i in 0 until entries.length()) {
                val item = entries.optJSONObject(i) ?: continue
                val action = item.optString("action").takeIf { it.isNotBlank() } ?: continue
                val time = item.optLong("time", 0L)
                if (time > 0L) add(action to time)
            }
        }
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ENTRIES).apply()
    }
}
