package com.anasikus.lifeassistant

import android.app.Application
import com.anasikus.lifeassistant.settings.AppPreferences

class LifeAssistantApplication : Application() {
    override fun onCreate() { super.onCreate(); AppPreferences.initialize(this) }
}
