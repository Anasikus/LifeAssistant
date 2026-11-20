package com.anasikus.lifeassistant.domain

import com.anasikus.lifeassistant.settings.AppText

enum class WorkPay(private val labelKey: String) {
    HOURLY("Почасовая ставка"),
    SHIFT("Ставка за смену"),
    ADVANCE("Аванс"),
    SALARY("Зарплата");

    val label: String get() = AppText.get(labelKey)

    companion object {
        fun from(value: String?): WorkPay = entries.find { it.name == value } ?: HOURLY
    }
}
