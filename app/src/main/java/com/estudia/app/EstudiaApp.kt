package com.estudia.app

import android.app.Application
import com.estudia.app.data.AppDatabase
import com.estudia.app.data.Repository
import com.estudia.app.data.Settings
import com.estudia.app.update.AppUpdater

class EstudiaApp : Application() {
    val repository: Repository by lazy { Repository(this, AppDatabase.build(this)) }
    val settings: Settings by lazy { Settings(this) }
    val updater: AppUpdater by lazy { AppUpdater(this, BuildConfig.VERSION_NAME) }
}
