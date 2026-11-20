package com.anasikus.lifeassistant.settings

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

object AppPreferences {
    private lateinit var app: Context
    var dark by mutableStateOf(true); private set
    var accent by mutableStateOf("PURPLE"); private set
    var language by mutableStateOf("ru"); private set
    var order by mutableStateOf(listOf("CALENDAR", "TASKS", "FINANCE", "LECTURES", "SETTINGS")); private set
    fun initialize(context: Context) {
        app = context.applicationContext
        val p = app.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        dark = p.getBoolean("dark", true); accent = p.getString("accent", "PURPLE")?.takeIf { it in listOf("PURPLE", "BLUE", "GREEN", "ROSE", "ORANGE") } ?: "PURPLE"
        language = p.getString("language", "ru")?.takeIf { it in listOf("ru", "en") } ?: "ru"
        order = SectionOrder.normalize(p.getString("order", "").orEmpty())
        Locale.setDefault(Locale.forLanguageTag(language))
        AppText.initialize(app)
    }
    fun setDark(value: Boolean) { dark = value; app.getSharedPreferences("appearance", 0).edit().putBoolean("dark", value).apply() }
    fun setAccent(value: String) { accent = value; app.getSharedPreferences("appearance", 0).edit().putString("accent", value).apply() }
    fun setLanguage(value: String) { language = value; Locale.setDefault(Locale.forLanguageTag(value)); app.getSharedPreferences("appearance", 0).edit().putString("language", value).apply() }
    fun move(name: String, direction: Int) {
        order = SectionOrder.move(order, name, direction)
        app.getSharedPreferences("appearance", 0).edit().putString("order", order.joinToString(",")).apply()
    }
    fun localized(context: Context): Context {
        val language = context.getSharedPreferences("appearance", 0).getString("language", "ru") ?: "ru"
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        return context.createConfigurationContext(config)
    }
}
