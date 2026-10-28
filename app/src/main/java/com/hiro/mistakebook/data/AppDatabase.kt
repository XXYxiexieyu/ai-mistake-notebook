package com.hiro.mistakebook.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        QuestionEntity::class,
        RetakeSessionEntity::class,
        RetakeItemEntity::class,
        PageScanEntity::class,
        QuestionChatMessageEntity::class,
        WordEntryEntity::class,
        WordLookupTaskEntity::class
    ],
    version = 10,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun retakeDao(): RetakeDao
    abstract fun pageScanDao(): PageScanDao
    abstract fun questionChatDao(): QuestionChatDao
    abstract fun wordDao(): WordDao
    abstract fun wordLookupTaskDao(): WordLookupTaskDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN wrongEvidence TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE questions ADD COLUMN evidenceType TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS page_scans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceImagePath TEXT NOT NULL,
                        subject TEXT NOT NULL,
                        captureTime INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        detectedCount INTEGER NOT NULL,
                        error TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_page_scans_status ON page_scans(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_page_scans_createdAt ON page_scans(createdAt)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE questions ADD COLUMN knowledgeMappingJson TEXT NOT NULL DEFAULT '[]'"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS question_chat_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        questionId INTEGER NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        imagePath TEXT NOT NULL,
                        status TEXT NOT NULL,
                        error TEXT NOT NULL,
                        model TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_question_chat_messages_questionId ON question_chat_messages(questionId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_question_chat_messages_createdAt ON question_chat_messages(createdAt)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE questions SET errorCauseState = 'AI_ACCEPTED' WHERE errorCauseState = 'AI_CANDIDATE'"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE page_scans ADD COLUMN stage TEXT NOT NULL DEFAULT 'QUEUED'"
                )
                db.execSQL(
                    "ALTER TABLE page_scans ADD COLUMN progress INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE questions ADD COLUMN sourceImagePathsJson TEXT NOT NULL DEFAULT '[]'"
                )
                db.execSQL(
                    "ALTER TABLE page_scans ADD COLUMN sourceImagePathsJson TEXT NOT NULL DEFAULT '[]'"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE questions ADD COLUMN reviewState TEXT NOT NULL DEFAULT 'APPROVED'"
                )
                db.execSQL(
                    "ALTER TABLE questions ADD COLUMN scanJobId INTEGER"
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS word_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word TEXT NOT NULL,
                        normalizedWord TEXT NOT NULL,
                        lemma TEXT NOT NULL,
                        phonetic TEXT NOT NULL,
                        partOfSpeech TEXT NOT NULL,
                        meaningChinese TEXT NOT NULL,
                        contextSentence TEXT NOT NULL,
                        markType TEXT NOT NULL,
                        sourceImagePath TEXT NOT NULL,
                        occurrences INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_word_entries_normalizedWord ON word_entries(normalizedWord)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_word_entries_createdAt ON word_entries(createdAt)"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS word_lookup_tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        status TEXT NOT NULL,
                        pagePathsJson TEXT NOT NULL,
                        recognizedWordsJson TEXT NOT NULL,
                        excludedIndicesJson TEXT NOT NULL,
                        progress INTEGER NOT NULL,
                        recognizedCount INTEGER NOT NULL,
                        wordCount INTEGER NOT NULL,
                        error TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_word_lookup_tasks_status ON word_lookup_tasks(status)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_word_lookup_tasks_createdAt ON word_lookup_tasks(createdAt)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_word_lookup_tasks_updatedAt ON word_lookup_tasks(updatedAt)"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE word_entries ADD COLUMN otherMeanings TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE word_entries ADD COLUMN collocations TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE word_entries ADD COLUMN wordForms TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE word_entries ADD COLUMN exampleSentence TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE word_entries ADD COLUMN deletedAt INTEGER"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_word_entries_deletedAt ON word_entries(deletedAt)"
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "mistake_book.db")
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10
                )
                .build()
    }
}
