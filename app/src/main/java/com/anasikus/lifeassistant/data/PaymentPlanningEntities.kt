package com.anasikus.lifeassistant.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expense_categories")
data class ExpenseCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(tableName = "payment_plans")
data class PaymentPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val amountCents: Long,
    val startDate: String,
    val everyMonths: Int,
    val reminderDays: Int,
    val categoryId: Int? = null,
    @ColumnInfo(defaultValue = "'MONTH'") val repeatUnit: String = "MONTH",
    @ColumnInfo(defaultValue = "0") val repeatEvery: Int = 0,
    @ColumnInfo(defaultValue = "540") val reminderMinute: Int = 540,
    @ColumnInfo(defaultValue = "0") val reminderRepeatDays: Int = 0
)

@Entity(tableName = "income_categories")
data class IncomeCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)
