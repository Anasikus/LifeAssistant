package com.anasikus.lifeassistant.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "finance_inbox", indices = [
    Index(value = ["eventKey"], unique = true),
    Index(value = ["source", "sender", "status"]),
    Index(value = ["status", "proposedAmountCents", "receivedAt"])
])
data class FinanceInboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventKey: String,
    val source: String,
    val sender: String,
    val title: String,
    val body: String,
    val receivedAt: Long,
    val ruleKey: String,
    val status: String = "PENDING",
    val proposedIncome: Boolean? = null,
    val proposedAmountCents: Long? = null,
    val expenseCategoryId: Int? = null,
    val incomeSourceKey: String? = null,
    val incomeSourceName: String? = null,
    val automatic: Boolean = false,
    val possibleDuplicate: Boolean = false,
    val transactionDate: String? = null,
    @ColumnInfo(defaultValue = "0") val proposedTransfer: Boolean = false
)

@Entity(tableName = "finance_import_rules")
data class FinanceImportRuleEntity(
    @PrimaryKey val key: String,
    val source: String,
    val sender: String,
    val template: String,
    /** IGNORE, KEEP, REVIEW, AUTO, FINANCIAL, BLOCK_SENDER */
    val action: String,
    val income: Boolean? = null,
    val amountOrdinal: Int? = null,
    val expenseCategoryId: Int? = null,
    val incomeSourceKey: String? = null,
    val incomeSourceName: String? = null,
    val bankName: String? = null,
    val textFilter: String? = null,
    /** Null means a legacy two-type rule; use the new parser suggestion. */
    val transfer: Boolean? = null
)
