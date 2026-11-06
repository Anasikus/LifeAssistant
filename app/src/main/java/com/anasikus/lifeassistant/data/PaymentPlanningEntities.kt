package com.anasikus.lifeassistant.data

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
    val categoryId: Int? = null
)
