package com.hiro.mistakebook.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class BackupPayload(
    val schemaVersion: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val questions: List<QuestionEntity> = emptyList(),
    val retakeSessions: List<RetakeSessionEntity> = emptyList(),
    val retakeItems: List<RetakeItemEntity> = emptyList(),
    val chatMessages: List<QuestionChatMessageEntity> = emptyList()
)

data class ImportPreview(
    val questionCount: Int,
    val duplicateCount: Int,
    val newCount: Int,
    val imageCount: Int
)

data class ImportResult(
    val added: Int,
    val merged: Int,
    val imagesRestored: Int
)

class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val images: ImageStore
) {
    private val gson = Gson()

    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val questions = database.questionDao().getAll()
        val sessions = database.retakeDao().getAllSessions()
        val items = database.retakeDao().getAllItems()
        val chatMessages = database.questionChatDao().getAll()
        val payload = BackupPayload(
            questions = questions,
            retakeSessions = sessions,
            retakeItems = items,
            chatMessages = chatMessages
        )
        var count = 0
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            ZipOutputStream(stream).use { zip ->
                zip.putNextEntry(ZipEntry("backup.json"))
                zip.write(gson.toJson(payload).toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                val paths = questions.flatMap {
                    listOf(it.imagePath, it.sourceImagePath, it.originalAnswerImagePath)
                } + items.map { it.answerImagePath } + chatMessages.map { it.imagePath }
                paths.filter { it.isNotBlank() }.distinct().forEach { relative ->
                    val file = images.resolve(relative)
                    if (!file.isFile) return@forEach
                    zip.putNextEntry(ZipEntry(relative))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                }
            }
        } ?: error("无法打开导出文件")
        count
    }

    suspend fun previewImport(uri: Uri): ImportPreview = withContext(Dispatchers.IO) {
        val imported = readBackup(uri)
        val existing = database.questionDao().getAll()
        val duplicateCount = imported.payload.questions.count { candidate ->
            existing.any { it.isSameQuestion(candidate) }
        }
        val result = ImportPreview(
            questionCount = imported.payload.questions.size,
            duplicateCount = duplicateCount,
            newCount = imported.payload.questions.size - duplicateCount,
            imageCount = imported.imageCount
        )
        val cacheRoot = context.cacheDir.canonicalFile
        if (imported.extractedDir.canonicalFile.toPath().startsWith(cacheRoot.toPath())) {
            imported.extractedDir.parentFile?.deleteRecursively()
        }
        result
    }

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val imported = readBackup(uri)
        val existing = database.questionDao().getAll().toMutableList()
        val idMap = mutableMapOf<Long, Long>()
        var added = 0
        var merged = 0
        var imagesRestored = 0

        for (source in imported.payload.questions) {
            val duplicate = existing.firstOrNull { it.isSameQuestion(source) }
            if (duplicate != null) {
                val updated = duplicate.copy(
                    errorCount = duplicate.errorCount + source.errorCount,
                    retakeFailures = duplicate.retakeFailures + source.retakeFailures,
                    sourceImagePath = duplicate.sourceImagePath.ifBlank {
                        restoreImage(source.sourceImagePath, imported.extractedDir).ifBlank {
                            restoreImage(source.imagePath, imported.extractedDir)
                        }
                    },
                    originalAnswerImagePath = duplicate.originalAnswerImagePath.ifBlank {
                        restoreImage(source.originalAnswerImagePath, imported.extractedDir)
                    },
                    updatedAt = System.currentTimeMillis()
                )
                database.questionDao().update(updated)
                idMap[source.id] = duplicate.id
                merged++
            } else {
                val copy = source.copy(
                    id = 0,
                    imagePath = restoreImage(source.imagePath, imported.extractedDir),
                    sourceImagePath = restoreImage(source.sourceImagePath, imported.extractedDir),
                    originalAnswerImagePath = restoreImage(
                        source.originalAnswerImagePath,
                        imported.extractedDir
                    ),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                val newId = database.questionDao().insert(copy)
                idMap[source.id] = newId
                existing += copy.copy(id = newId)
                added++
            }
            imagesRestored = imported.extractedDir.walkTopDown().count { it.isFile }
        }

        for (session in imported.payload.retakeSessions) {
            val newSessionId = database.retakeDao().insertSession(
                session.copy(id = 0, createdAt = System.currentTimeMillis())
            )
            val mappedItems = imported.payload.retakeItems
                .filter { it.sessionId == session.id }
                .mapNotNull { item ->
                    val questionId = idMap[item.questionId] ?: return@mapNotNull null
                    item.copy(
                        id = 0,
                        sessionId = newSessionId,
                        questionId = questionId,
                        answerImagePath = restoreImage(item.answerImagePath, imported.extractedDir)
                    )
                }
            if (mappedItems.isNotEmpty()) database.retakeDao().insertItems(mappedItems)
        }

        for (message in imported.payload.chatMessages) {
            val mappedQuestionId = idMap[message.questionId] ?: continue
            database.questionChatDao().insert(
                message.copy(
                    id = 0,
                    questionId = mappedQuestionId,
                    imagePath = restoreImage(message.imagePath, imported.extractedDir)
                )
            )
        }

        val cacheRoot = context.cacheDir.canonicalFile
        if (imported.extractedDir.canonicalFile.toPath().startsWith(cacheRoot.toPath())) {
            imported.extractedDir.parentFile?.deleteRecursively()
        }
        ImportResult(added = added, merged = merged, imagesRestored = imagesRestored)
    }

    private fun readBackup(uri: Uri): ReadBackup {
        val tempDir = File(context.cacheDir, "import_${UUID.randomUUID()}").apply { mkdirs() }
        val zipFile = tempDir.resolve("backup.zip")
        context.contentResolver.openInputStream(uri)?.use { input ->
            zipFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取备份文件")

        var payload: BackupPayload? = null
        var imageCount = 0
        val extractedDir = tempDir.resolve("files").apply { mkdirs() }
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                if (entry.name == "backup.json") {
                    zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use {
                        val root = JsonParser.parseReader(it).asJsonObject
                        root.getAsJsonArray("questions")?.forEach { element ->
                            val question = element.asJsonObject
                            if (!question.has("wrongEvidence")) {
                                question.addProperty("wrongEvidence", "")
                            }
                            if (!question.has("evidenceType")) {
                                question.addProperty("evidenceType", "")
                            }
                            if (!question.has("knowledgeMappingJson")) {
                                question.addProperty("knowledgeMappingJson", "[]")
                            }
                        }
                        if (!root.has("chatMessages")) {
                            root.add("chatMessages", com.google.gson.JsonArray())
                        }
                        payload = gson.fromJson(root, BackupPayload::class.java)
                    }
                } else if (entry.name.startsWith("images/")) {
                    val target = File(extractedDir, entry.name)
                    val rootPath = extractedDir.canonicalFile.toPath()
                    require(target.canonicalFile.toPath().startsWith(rootPath)) {
                        "备份中包含非法文件路径"
                    }
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    imageCount++
                }
            }
        }
        val parsedPayload = payload ?: error("备份中缺少 backup.json")
        require(parsedPayload.schemaVersion in 1..2) { "不支持的备份版本" }
        return ReadBackup(
            payload = parsedPayload,
            imageCount = imageCount,
            extractedDir = extractedDir
        )
    }

    private fun restoreImage(path: String, extractedDir: File): String {
        if (path.isBlank()) return ""
        val source = File(extractedDir, path)
        return if (source.isFile) images.importFile(source) else ""
    }

    private fun QuestionEntity.isSameQuestion(other: QuestionEntity): Boolean {
        if (imageHash.isNotBlank() && other.imageHash.isNotBlank() && imageHash == other.imageHash) {
            return true
        }
        if (subject != other.subject) return false
        val textA = duplicateKey.ifBlank { questionText.replace(Regex("\\s+"), "") }
        val textB = other.duplicateKey.ifBlank { other.questionText.replace(Regex("\\s+"), "") }
        return textA.isNotBlank() && textA == textB
    }

    private data class ReadBackup(
        val payload: BackupPayload,
        val imageCount: Int,
        val extractedDir: File
    )
}
