package com.anasikus.lifeassistant.settings

import org.junit.Assert.*
import org.junit.Test

class SectionOrderTest {
    @Test fun invalidOldOrDuplicatePreferencesKeepEverySectionExactlyOnce() {
        val result = SectionOrder.normalize("FINANCE,UNKNOWN,FINANCE,TASKS")
        assertEquals(listOf("FINANCE", "TASKS", "CALENDAR", "LECTURES", "SETTINGS"), result)
        assertEquals(SectionOrder.known.toSet(), result.toSet())
    }
    @Test fun movingASectionPreservesOthersAndRespectsBounds() {
        val result = SectionOrder.move(SectionOrder.known, "FINANCE", -1)
        assertEquals(listOf("CALENDAR", "FINANCE", "TASKS", "LECTURES", "SETTINGS"), result)
        assertEquals(result, SectionOrder.move(result, "CALENDAR", -1))
        assertEquals(result, SectionOrder.move(result, "SETTINGS", 1))
    }
    @Test fun translatedTemplateNeverReinterpretsPlaceholdersInsideUserText() {
        assertEquals("A{arg1} · B", AppText.get("{arg0} · {arg1}", "A{arg1}", "B"))
    }
}
