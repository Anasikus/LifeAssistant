package com.anasikus.lifeassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anasikus.lifeassistant.ui.AppColors

/** The save action validates exactly as the form's regular Save button does. */
@Composable
fun rememberDismissGuard(dirty: Boolean, onSave: () -> Unit, onDismiss: () -> Unit): () -> Unit {
    var warning by remember { mutableStateOf(false) }
    val currentDirty by rememberUpdatedState(dirty)
    val save by rememberUpdatedState(onSave)
    val dismiss by rememberUpdatedState(onDismiss)
    if (warning) AlertDialog(
        onDismissRequest = { warning = false },
        containerColor = AppColors.Card,
        title = { Text("Выйти из формы?", color = AppColors.MainText) },
        text = { Text("Есть несохранённые изменения.", color = AppColors.SecondaryText) },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { warning = false; save() }, modifier = Modifier.fillMaxWidth()) { Text("Да, сохранить") }
                TextButton(onClick = { warning = false; dismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Да, не сохранять") }
                TextButton(onClick = { warning = false }, modifier = Modifier.fillMaxWidth()) { Text("Вернуться") }
            }
        }
    )
    return remember { { if (currentDirty) warning = true else dismiss() } }
}

/** Lets embedded editors protect closing their parent dialog or changing its tab. */
class FormDraftRegistry {
    var dirty by mutableStateOf(false)
    var save: ((() -> Unit) -> Unit) = { it() }
}
val LocalFormDraftRegistry = staticCompositionLocalOf<FormDraftRegistry?> { null }

@Composable
fun RegisterFormDraft(dirty: Boolean, onSave: (() -> Unit) -> Unit) {
    val registry = LocalFormDraftRegistry.current
    SideEffect { if (registry != null) { registry.dirty = dirty; registry.save = onSave } }
    DisposableEffect(registry) {
        onDispose { if (registry != null) { registry.dirty = false; registry.save = { it() } } }
    }
}

@Composable
fun rememberGuardedAction(registry: FormDraftRegistry): (() -> Unit) -> Unit {
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val currentRegistry by rememberUpdatedState(registry)
    if (pending != null) AlertDialog(onDismissRequest = { pending = null },
        containerColor = AppColors.Card, title = { Text("Выйти из формы?", color = AppColors.MainText) },
        text = { Text("Есть несохранённые изменения.", color = AppColors.SecondaryText) },
        confirmButton = {
            Column {
                Button(onClick = {
                    val action = pending; pending = null
                    currentRegistry.save { action?.invoke() }
                }, modifier = Modifier.fillMaxWidth()) { Text("Да, сохранить") }
                TextButton(onClick = { val action = pending; pending = null; action?.invoke() }) { Text("Да, не сохранять") }
                TextButton(onClick = { pending = null }) { Text("Вернуться") }
            }
        })
    return remember { { action -> if (currentRegistry.dirty) pending = action else action() } }
}

val LocalFormSwitch = staticCompositionLocalOf<((() -> Unit) -> Unit)?> { null }
