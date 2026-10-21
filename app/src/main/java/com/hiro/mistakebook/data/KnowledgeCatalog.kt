package com.hiro.mistakebook.data

import android.content.Context
import com.google.gson.Gson

data class KnowledgeTreeData(
    val schemaVersion: Int = 1,
    val catalogVersion: String = "",
    val source: String = "",
    val books: List<KnowledgeBook> = emptyList()
)

class KnowledgeCatalog(context: Context) {
    val data: KnowledgeTreeData = context.assets.open("knowledge_tree.json").bufferedReader().use {
        Gson().fromJson(it, KnowledgeTreeData::class.java)
    }

    val books: List<KnowledgeBook> = data.books

    fun booksFor(subject: String): List<KnowledgeBook> = books.filter { it.subject == subject }

    fun findBook(subject: String, title: String): KnowledgeBook? =
        books.firstOrNull { it.subject == subject && it.title == title }

    fun units(subject: String, bookTitle: String): List<KnowledgeUnit> =
        findBook(subject, bookTitle)?.units.orEmpty()

    fun sections(subject: String, bookTitle: String, unitTitle: String): List<String> =
        units(subject, bookTitle).firstOrNull { it.title == unitTitle }?.sections.orEmpty()
}
