package com.hiro.mistakebook.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions ORDER BY captureTime DESC, id DESC")
    fun observeAll(): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE id = :id")
    fun observeById(id: Long): Flow<QuestionEntity?>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getById(id: Long): QuestionEntity?

    @Query("SELECT * FROM questions ORDER BY captureTime DESC, id DESC")
    suspend fun getAll(): List<QuestionEntity>

    @Query(
        """
        SELECT * FROM questions
        WHERE status = 'REVIEWED'
          AND subject = :subject
          AND captureTime BETWEEN :start AND :end
        ORDER BY captureTime ASC, id ASC
        """
    )
    suspend fun getReviewedInRange(subject: String, start: Long, end: Long): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id IN (:ids) ORDER BY captureTime ASC, id ASC")
    suspend fun getByIds(ids: List<Long>): List<QuestionEntity>

    @Query(
        """
        SELECT * FROM questions
        WHERE (:hash <> '' AND imageHash = :hash)
           OR (
                subject = :subject
                AND questionText <> ''
                AND questionText = :normalizedText
           )
        LIMIT 20
        """
    )
    suspend fun findDuplicateCandidates(
        hash: String,
        subject: String,
        normalizedText: String
    ): List<QuestionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(question: QuestionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuestionEntity>): List<Long>

    @Update
    suspend fun update(question: QuestionEntity)

    @Delete
    suspend fun delete(question: QuestionEntity)

    @Query("DELETE FROM questions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE questions SET status = :status, passedAt = :passedAt, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, passedAt: Long?, now: Long)

    @Query(
        """
        UPDATE questions
        SET status = 'REVIEWED',
            retakeFailures = retakeFailures + 1,
            lastRetakeAt = :now,
            passedAt = NULL,
            updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun markRetakeFailed(id: Long, now: Long)

    @Query("SELECT COUNT(*) FROM questions")
    fun observeCount(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE id <> :questionId
          AND (sourceImagePath = :path OR imagePath = :path OR originalAnswerImagePath = :path)
        """
    )
    suspend fun countOtherImageReferences(questionId: Long, path: String): Int
}

@Dao
interface RetakeDao {
    @Query("SELECT * FROM retake_sessions ORDER BY createdAt DESC")
    fun observeSessions(): Flow<List<RetakeSessionEntity>>

    @Query("SELECT * FROM retake_sessions WHERE id = :sessionId")
    fun observeSession(sessionId: Long): Flow<RetakeSessionEntity?>

    @Query("SELECT * FROM retake_sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): RetakeSessionEntity?

    @Query("SELECT * FROM retake_sessions ORDER BY createdAt DESC")
    suspend fun getAllSessions(): List<RetakeSessionEntity>

    @Query("SELECT * FROM retake_items WHERE sessionId = :sessionId ORDER BY orderIndex ASC")
    fun observeItems(sessionId: Long): Flow<List<RetakeItemEntity>>

    @Query("SELECT * FROM retake_items WHERE sessionId = :sessionId ORDER BY orderIndex ASC")
    suspend fun getItems(sessionId: Long): List<RetakeItemEntity>

    @Query("SELECT * FROM retake_items ORDER BY sessionId DESC, orderIndex ASC")
    suspend fun getAllItems(): List<RetakeItemEntity>

    @Query("SELECT * FROM retake_items WHERE id = :itemId")
    suspend fun getItem(itemId: Long): RetakeItemEntity?

    @Insert
    suspend fun insertSession(session: RetakeSessionEntity): Long

    @Insert
    suspend fun insertItems(items: List<RetakeItemEntity>)

    @Update
    suspend fun updateItem(item: RetakeItemEntity)

    @Query(
        """
        UPDATE retake_items
        SET answerText = :answerText,
            answerImagePath = :answerImagePath,
            elapsedMs = :elapsedMs,
            answeredAt = :answeredAt
        WHERE id = :itemId
        """
    )
    suspend fun saveAnswer(
        itemId: Long,
        answerText: String,
        answerImagePath: String,
        elapsedMs: Long,
        answeredAt: Long
    )

    @Query(
        """
        UPDATE retake_items
        SET aiGrade = :grade,
            aiFeedback = :feedback,
            analysisState = :analysisState
        WHERE id = :itemId
        """
    )
    suspend fun saveAiGrade(
        itemId: Long,
        grade: String,
        feedback: String,
        analysisState: String
    )

    @Query(
        """
        UPDATE retake_items
        SET finalGrade = :grade,
            finalFeedback = :feedback
        WHERE id = :itemId
        """
    )
    suspend fun saveFinalGrade(itemId: Long, grade: String, feedback: String)

    @Update
    suspend fun updateSession(session: RetakeSessionEntity)

    @Query("DELETE FROM retake_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM retake_items WHERE sessionId = :sessionId")
    suspend fun deleteSessionItems(sessionId: Long)

    @Query("DELETE FROM retake_items WHERE questionId = :questionId")
    suspend fun deleteItemsForQuestion(questionId: Long)

    @Query("DELETE FROM retake_sessions WHERE id NOT IN (SELECT DISTINCT sessionId FROM retake_items)")
    suspend fun deleteEmptySessions()
}

@Dao
interface PageScanDao {
    @Insert
    suspend fun insert(scan: PageScanEntity): Long

    @Update
    suspend fun update(scan: PageScanEntity)

    @Query("SELECT * FROM page_scans WHERE id = :id")
    suspend fun getById(id: Long): PageScanEntity?

    @Query("SELECT * FROM page_scans WHERE id = :id")
    fun observeById(id: Long): Flow<PageScanEntity?>

    @Query("SELECT * FROM page_scans WHERE status IN ('PENDING', 'FAILED')")
    suspend fun getPendingOrFailed(): List<PageScanEntity>

    @Query("SELECT COUNT(*) FROM page_scans WHERE sourceImagePath = :path")
    suspend fun countBySourcePath(path: String): Int
}

@Dao
interface QuestionChatDao {
    @Insert
    suspend fun insert(message: QuestionChatMessageEntity): Long

    @Update
    suspend fun update(message: QuestionChatMessageEntity)

    @Query("SELECT * FROM question_chat_messages WHERE id = :id")
    suspend fun getById(id: Long): QuestionChatMessageEntity?

    @Query(
        """
        SELECT * FROM question_chat_messages
        WHERE questionId = :questionId
        ORDER BY createdAt ASC, id ASC
        """
    )
    fun observeForQuestion(questionId: Long): Flow<List<QuestionChatMessageEntity>>

    @Query(
        """
        SELECT * FROM question_chat_messages
        WHERE questionId = :questionId AND status = 'DONE'
        ORDER BY createdAt ASC, id ASC
        """
    )
    suspend fun getCompletedForQuestion(questionId: Long): List<QuestionChatMessageEntity>

    @Query("SELECT * FROM question_chat_messages WHERE questionId = :questionId")
    suspend fun getForQuestion(questionId: Long): List<QuestionChatMessageEntity>

    @Query("DELETE FROM question_chat_messages WHERE questionId = :questionId")
    suspend fun deleteForQuestion(questionId: Long)

    @Query("SELECT * FROM question_chat_messages ORDER BY questionId, createdAt, id")
    suspend fun getAll(): List<QuestionChatMessageEntity>

    @Query(
        """
        SELECT * FROM question_chat_messages
        WHERE role = 'ASSISTANT' AND status IN ('PENDING', 'FAILED')
        ORDER BY createdAt
        """
    )
    suspend fun getPendingOrFailedReplies(): List<QuestionChatMessageEntity>
}
