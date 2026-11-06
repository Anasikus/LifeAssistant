package com.anasikus.lifeassistant.domain

enum class WorkPay(val label: String) {
    HOURLY("Почасовая ставка"),
    SHIFT("Ставка за смену"),
    ADVANCE("Аванс"),
    SALARY("Зарплата");

    companion object {
        fun from(value: String?): WorkPay = entries.find { it.name == value } ?: HOURLY
    }
}
