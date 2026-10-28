package com.hiro.mistakebook.data

import com.google.gson.Gson
import com.hiro.mistakebook.ai.MarkedWordPayload

object WordLookupCodec {
    private val gson = Gson()

    fun pagePaths(json: String): List<String> = runCatching {
        gson.fromJson(json, Array<String>::class.java).toList()
    }.getOrDefault(emptyList())

    fun recognizedWords(json: String): List<MarkedWordPayload> = runCatching {
        gson.fromJson(json, Array<MarkedWordPayload>::class.java).toList()
    }.getOrDefault(emptyList())

    fun excludedIndices(json: String): Set<Int> = runCatching {
        gson.fromJson(json, Array<Int>::class.java).toSet()
    }.getOrDefault(emptySet())

    fun encodePagePaths(paths: List<String>): String = gson.toJson(paths.distinct())

    fun encodeRecognizedWords(words: List<MarkedWordPayload>): String = gson.toJson(words)

    fun encodeExcludedIndices(indices: Set<Int>): String = gson.toJson(indices.sorted())
}
