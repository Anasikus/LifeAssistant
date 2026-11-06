package com.anasikus.lifeassistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "finance_transactions")
data class FinanceTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val income: Boolean,
    val amountCents: Long,
    val note: String,
    val sourceKey: String? = null,
    val sourceName: String? = null
)
