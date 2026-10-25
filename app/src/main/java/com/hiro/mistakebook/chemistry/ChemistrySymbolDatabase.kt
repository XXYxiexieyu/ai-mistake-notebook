package com.hiro.mistakebook.chemistry

import android.content.Context
import com.google.gson.Gson
import com.hiro.mistakebook.math.MathSymbolDocument

object ChemistrySymbolDatabase {
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

    private const val ASSET_NAME = "chemistry_symbols.json"
}
