package com.hiro.mistakebook

import android.app.Application
import com.hiro.mistakebook.ai.DeepSeekClient
import com.hiro.mistakebook.data.AppDatabase
import com.hiro.mistakebook.data.BackupManager
import com.hiro.mistakebook.data.ImageStore
import com.hiro.mistakebook.data.KnowledgeCatalog
import com.hiro.mistakebook.data.PdfReportExporter
import com.hiro.mistakebook.data.QuestionRepository
import com.hiro.mistakebook.data.SettingsStore

class MistakeBookApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    val database = AppDatabase.create(application)
    val settings = SettingsStore(application)
    val images = ImageStore(application)
    val catalog = KnowledgeCatalog(application)
    val repository = QuestionRepository(
        application,
        database,
        settings,
        images,
        catalog
    )
    val backupManager = BackupManager(application, database, images)
    val pdfExporter = PdfReportExporter(application)
    val aiClient = DeepSeekClient(
        apiKeyProvider = settings::apiKey,
        modelProvider = { settings.model }
    )
}
