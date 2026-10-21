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
        QuestionChatMessageEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun retakeDao(): RetakeDao
    abstract fun pageScanDao(): PageScanDao
    abstract fun questionChatDao(): QuestionChatDao

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

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "mistake_book.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
