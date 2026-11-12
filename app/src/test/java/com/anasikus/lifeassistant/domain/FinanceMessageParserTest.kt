package com.anasikus.lifeassistant.domain

import org.junit.Assert.*
import org.junit.Test

class FinanceMessageParserTest {
    @Test fun purchaseAmountIsSeparatedFromRemainingBalance() {
        val parsed = FinanceMessageParser.parse("Покупка 1 234,56 руб. в SHOP. Баланс 9 000,00 руб.")
        assertEquals(false, parsed.income)
        assertEquals(123456L, parsed.preferredAmount!!)
        assertEquals(true, parsed.amounts.last().balance)
    }

    @Test fun stableTemplateKeepsMerchantAndDirectionButDropsChangingAmountsAndTime() {
        val first = FinanceMessageParser.ruleKey("SMS", "Bank", "Bank", "Покупка 100,00 RUB в SHOP в 10:10. Баланс 900,00 RUB")
        val second = FinanceMessageParser.ruleKey("SMS", "Bank", "Bank", "Покупка 200,00 RUB в SHOP в 11:20. Баланс 700,00 RUB")
        assertEquals(first, second)
        assertNotEquals(first, FinanceMessageParser.ruleKey("SMS", "Bank", "Bank", "Покупка 200,00 RUB в CAFE в 11:20. Баланс 700,00 RUB"))
        assertNotEquals(first, FinanceMessageParser.ruleKey("SMS", "OtherBank", "Bank", "Покупка 200,00 RUB в SHOP в 11:20. Баланс 700,00 RUB"))
    }

    @Test fun multipleOperationsAndForeignCurrencyNeedManualAmount() {
        assertNull(FinanceMessageParser.parse("Оплата 100 RUB и покупка 200 RUB").preferredAmount)
        assertNull(FinanceMessageParser.parse("Покупка 10 USD. Баланс 500 RUB").preferredAmount)
        assertNull(FinanceMessageParser.parse("Код 12345").preferredAmount)
    }

    @Test fun ambiguousDirectionIsNotInvented() {
        assertNull(FinanceMessageParser.parse("Перевод 500 RUB").income)
        assertEquals(true, FinanceMessageParser.parse("Зачисление 500 RUB. Баланс 1500 RUB").income)
    }
    @Test fun notificationTitleCanContainOperationAmountAboveBalanceInBody() {
        val parsed = FinanceMessageParser.message("Покупка 500 RUB", "Баланс 1500 RUB")
        assertEquals(50000L, parsed.preferredAmount!!)
        assertEquals(false, parsed.income)
    }

    @Test fun bankSmsExamplesSupportGluedCurrencyAndExcludeBalance() {
        val cases = listOf(
            Triple("Счет карты 1234 12:30 перевод по СБП из Т-Банк +50р от Иванова Баланс: 1500р", true, 5000L),
            Triple("Счет карты 1234 12:30 перевод 50р Баланс: 0р", false, 5000L),
            Triple("Счет карты 1234 12:30 Зачисление зарплаты: 25000Р Баланс: 30000Р", true, 2500000L),
            Triple("Счет карты 1234 12:30 Покупка 249р К&Б Баланс: 251р", false, 24900L),
            Triple("Счёт карты 1234 12:30 перевод -50р Баланс: 150р", false, 5000L)
        )
        cases.forEach { (text, income, cents) ->
            val parsed = FinanceMessageParser.parse(text)
            assertEquals(text, income, parsed.income)
            assertEquals(text, cents, parsed.preferredAmount!!)
            assertTrue(parsed.amounts.last().balance)
        }
    }

    @Test fun receiptUsesSubjectRatherThanItemPricesInPreview() {
        val title = "Чек на 702.90Р от 12.11.2026, ООО \"АГРОТОРГ\""
        val subject = FinanceMessageParser.emailSubject("Пятёрочка", title + "\nМолоко 90р")
        assertEquals(title, subject)
        val parsed = FinanceMessageParser.message(subject, "Молоко 90р и хлеб 50р", "EMAIL")
        assertEquals(false, parsed.income)
        assertEquals(70290L, parsed.preferredAmount!!)
        assertNull(FinanceMessageParser.message("Ozon: чек заказа", "Скачайте чек. Баланс бонусов 500р", "EMAIL").preferredAmount)
    }

    @Test fun incomingAndOutgoingTransfersCannotShareTemplate() {
        assertNotEquals(
            FinanceMessageParser.ruleKey("SMS", "900", "900", "Перевод +50р Баланс 100р"),
            FinanceMessageParser.ruleKey("SMS", "900", "900", "Перевод 50р Баланс 100р")
        )
    }

    @Test fun textFilterIsLiteralCaseInsensitiveAndCannotMatchEmptyText() {
        assertTrue(FinanceMessageParser.matchesFilter("из Т-Банка", "900", "Перевод из т-банка +50р"))
        assertFalse(FinanceMessageParser.matchesFilter("из Т-Банка", "900", "Перевод из Альфа-Банка +50р"))
        assertTrue(FinanceMessageParser.matchesFilter("из Т-Банка\nот Иванова", "900", "Перевод из Т-Банка +50р от Иванова"))
        assertFalse(FinanceMessageParser.matchesFilter("из Т-Банка\nот Иванова", "900", "Перевод из Т-Банка +50р от Петрова"))
        assertFalse(FinanceMessageParser.matchesFilter(".*", "900", "Перевод +50р"))
        assertFalse(FinanceMessageParser.matchesFilter(" ", "900", "Перевод +50р"))
    }
}
