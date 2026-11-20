package com.anasikus.lifeassistant.calendar

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.anasikus.lifeassistant.ui.components.TaskEditorDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UnsavedTaskFormTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelOffersReturnSaveAndDiscardWithoutLosingDraft() {
        var open by mutableStateOf(true)
        var saved: String? = null
        compose.setContent { MaterialTheme {
            if (open) TaskEditorDialog(null, onDismiss = { open = false },
                onSave = { title, _, _, _, _, _, _, _ -> saved = title; open = false })
        } }
        compose.onNodeWithText("Название").performTextInput("Сайт смен")
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Да, сохранить").assertExists()
        compose.onNodeWithText("Да, не сохранять").assertExists()
        compose.onNodeWithText("Вернуться").performClick()
        compose.onNodeWithText("Сайт смен").assertExists()
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Да, сохранить").performClick()
        compose.runOnIdle { assertEquals("Сайт смен", saved); assertFalse(open) }
    }

    @Test fun invalidDraftStaysOpenAndCanBeDiscarded() {
        var open by mutableStateOf(true)
        var saved = false
        compose.setContent { MaterialTheme {
            if (open) TaskEditorDialog(null, onDismiss = { open = false }, onSave = { _, _, _, _, _, _, _, _ -> saved = true })
        } }
        compose.onNodeWithText("Описание").performTextInput("Идея без названия")
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Да, сохранить").performClick()
        compose.onNodeWithText("Введите название задачи").assertExists()
        compose.runOnIdle { assertTrue(open); assertFalse(saved) }
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Да, не сохранять").performClick()
        compose.runOnIdle { assertFalse(open); assertFalse(saved) }
    }
}
