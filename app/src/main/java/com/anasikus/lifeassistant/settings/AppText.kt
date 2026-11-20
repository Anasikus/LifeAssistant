package com.anasikus.lifeassistant.settings

import android.content.Context
import org.json.JSONObject

/** Only explicit interface templates are translated; stored user text is never processed. */
object AppText {
    private var english: Map<String, String> = emptyMap()
    fun initialize(context: Context) {
        val json = JSONObject(context.assets.open("ui-text.json").bufferedReader().use { it.readText() })
        english = json.keys().asSequence().associateWith { json.getString(it) }
    }
    fun get(template: String, vararg arguments: Any?): String {
        var value = if (AppPreferences.language == "en") english[template] ?: template else template
        return Regex("\\{arg(\\d+)\\}").replace(value) { match ->
            arguments.getOrNull(match.groupValues[1].toInt()).toString()
        }
    }
}
