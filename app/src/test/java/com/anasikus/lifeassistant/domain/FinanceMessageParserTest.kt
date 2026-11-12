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
}
