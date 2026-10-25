package com.hiro.mistakebook.math

import android.content.Context
import com.google.gson.Gson
import java.util.Locale

data class MathSymbolEntry(
    val symbol: String = "",
    val name: String = "",
    val insert: String = "",
    val latexAliases: List<String> = emptyList(),
    val keywords: List<String> = emptyList()
) {
    val insertionText: String get() = insert.ifBlank { symbol }
    val searchableText: String
        get() = (listOf(name, symbol, insertionText) + keywords + latexAliases)
            .joinToString(" ")
            .lowercase(Locale.ROOT)
}

data class MathSymbolCategory(
    val id: String = "",
    val name: String = "",
    val symbols: List<MathSymbolEntry> = emptyList()
)

data class MathSymbolDocument(
    val version: Int = 1,
    val categories: List<MathSymbolCategory> = emptyList()
) {
    val entries: List<MathSymbolEntry>
        get() = categories.flatMap { it.symbols }

    val latexSymbolMap: Map<String, String>
        get() = entries
            .flatMap { entry ->
                entry.latexAliases.map { alias -> alias to entry.insertionText }
            }
            .filter { (alias, _) -> alias.startsWith("\\") }
            .sortedByDescending { (alias, _) -> alias.length }
            .toMap()

    fun search(query: String, categoryId: String? = null): List<MathSymbolEntry> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        return entries.filter { entry ->
            (categoryId == null || categories.any {
                it.id == categoryId && entry in it.symbols
            }) && (normalizedQuery.isBlank() || entry.searchableText.contains(normalizedQuery))
        }
    }
}

object MathSymbolDatabase {
    private val gson = Gson()
    @Volatile
    private var cached: MathSymbolDocument? = null

    fun load(context: Context): MathSymbolDocument {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: runCatching {
                context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
                    gson.fromJson(reader, MathSymbolDocument::class.java)
                }
            }.getOrElse {
                MathSymbolDocument()
            }.also { cached = it }
        }
    }

    fun parse(json: String): MathSymbolDocument =
        gson.fromJson(json, MathSymbolDocument::class.java)

    private const val ASSET_NAME = "math_symbols.json"
}
