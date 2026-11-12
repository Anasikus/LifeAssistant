package com.anasikus.lifeassistant.domain

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FinanceMessageParser {
    data class Amount(val cents: Long, val balance: Boolean)
    data class Parsed(val income: Boolean?, val amounts: List<Amount>, val preferredOrdinal: Int?) {
        val preferredAmount: Long? get() = preferredOrdinal?.let { amounts.getOrNull(it)?.cents }
    }

    private val money = Regex(
        "(?iu)([+-]?\\d(?:[\\d \\u00a0\\u202f]*\\d)?(?:[.,]\\d{1,2})?)\\s*(?:₽|руб(?:лей|ля|ль|\\.)?|RUB\\b|RUR\\b|р\\.)"
    )
    private val incoming = Regex("(?iu)зачисл|поступил|поступлен|пополнен|возврат|получен.*перевод|перевод от|refund|credited|received")
    private val outgoing = Regex("(?iu)покупк|списан|оплат|расход|снятие|purchase|debited|paid|withdraw")
    private val balance = Regex("(?iu)баланс|остаток|доступно|balance|available")

    fun parse(text: String): Parsed {
        val matches = money.findAll(text).toList()
        val amounts = matches.map { match ->
            val digits = match.groupValues[1].replace(Regex("[ \\u00a0\\u202f]"), "").removePrefix("+").removePrefix("-")
            val prefix = text.substring(maxOf(0, match.range.first - 24), match.range.first)
                .substringAfterLast('\n').substringAfterLast(';')
            Amount(ShiftRules.cents(digits) ?: 0L, balance.containsMatchIn(prefix))
        }
        val primaryText = balance.find(text)?.range?.first?.let { text.take(it) } ?: text
        val incomingMatch = incoming.containsMatchIn(primaryText)
        val outgoingMatch = outgoing.containsMatchIn(primaryText)
        val income = when {
            incomingMatch && !outgoingMatch -> true
            outgoingMatch && !incomingMatch -> false
            else -> null
        }
        val primary = amounts.indices.filter { !amounts[it].balance && amounts[it].cents > 0L }
        return Parsed(income, amounts, primary.singleOrNull())
    }

    fun message(title: String, body: String): Parsed {
        val fromBody = parse(body)
        val combined = parse(title + "\n" + body)
        val chosen = if (fromBody.amounts.any { !it.balance }) fromBody else combined
        return chosen.copy(income = chosen.income ?: combined.income)
    }

    fun template(title: String, body: String): String = (title + "\n" + body)
        .lowercase(Locale.ROOT)
        .replace(money, "<money>")
        .replace(Regex("\\d{1,4}[./:-]\\d{1,2}(?:[./:-]\\d{1,4})?"), "<date>")
        .replace(Regex("\\d+"), "<n>")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun ruleKey(source: String, sender: String, title: String, body: String): String =
        hash(source + "\n" + sender.lowercase(Locale.ROOT) + "\n" + template(title, body))

    fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

    fun date(receivedAt: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(receivedAt))
}
