package com.hanenashi.k1gal.util

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

internal fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
    for (i in 0 until length()) optJSONObject(i)?.let(block)
}

internal fun JSONArray.toStringList(): List<String> =
    (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
