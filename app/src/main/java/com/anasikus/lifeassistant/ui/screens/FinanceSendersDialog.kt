package com.anasikus.lifeassistant.ui.screens

import com.anasikus.lifeassistant.settings.AppText
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anasikus.lifeassistant.data.FinanceImportRuleEntity
import com.anasikus.lifeassistant.data.FinanceInboxEntity
import com.anasikus.lifeassistant.domain.FinanceMessageParser
import com.anasikus.lifeassistant.ui.AppColors
import com.anasikus.lifeassistant.ui.components.rememberDismissGuard

private data class InboxSender(val source: String, val sender: String, val count: Int)

@Composable
internal fun FinanceSendersDialog(
    messages: List<FinanceInboxEntity>, rules: List<FinanceImportRuleEntity>, error: String?,
    onSettings: (String, String) -> Unit, onBlock: (String, String) -> Unit, onDismiss: () -> Unit
) {
    var packageName by remember { mutableStateOf("") }
    val groups = remember(messages, rules) {
        val counts = linkedMapOf<Pair<String, String>, Int>()
        fun scope(source: String, sender: String): Pair<String, String> =
            FinanceMessageParser.appPackage(source, sender)?.let { "APP" to it } ?: (source to sender)
        messages.filter { it.source != "MANUAL" }.forEach { message ->
            val key = scope(message.source, message.sender)
            counts[key] = (counts[key] ?: 0) + if (message.status != "POSTED") 1 else 0
        }
        rules.filter { it.action in listOf("BLOCK_SENDER", "FINANCIAL") && it.source != "MANUAL" }.forEach {
            counts.putIfAbsent(scope(it.source, it.sender), 0)
        }
        counts.map { (key, count) -> InboxSender(key.first, key.second, count) }
            .sortedWith(compareByDescending<InboxSender> { it.count }.thenBy { it.sender })
    }
    val profiles = remember(rules) { rules.associateBy { it.key } }
    var validation by remember { mutableStateOf(false) }
    val dismiss = rememberDismissGuard(packageName.isNotBlank(), {
        if (packageName.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+"))) { onBlock("APP", packageName); packageName = ""; onDismiss() }
        else validation = true
    }, onDismiss)
    AlertDialog(onDismissRequest = dismiss, title = { Text(AppText.get("Отправители")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(AppText.get("Блокировка приложения действует на все его уведомления, независимо от текста. Неучтённые сообщения удалятся, учтённые доходы и расходы сохранятся."))
            OutlinedTextField(packageName, { packageName = it.trim() }, singleLine = true,
                label = { Text(AppText.get("Заблокировать приложение по имени пакета")) },
                placeholder = { Text("com.xiaomi.hm.health") })
            TextButton(enabled = packageName.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")),
                onClick = { onBlock("APP", packageName); packageName = "" }) { Text(AppText.get("Заблокировать приложение")) }
            if (validation) Text(AppText.get("Проверь имя пакета приложения."), color = AppColors.Red)
            error?.let { Text(it, color = AppColors.Red) }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (groups.isEmpty()) item { Text(AppText.get("Отправителей пока нет")) }
                items(groups, key = { it.source + "\n" + it.sender }) { group ->
                    val rule = profiles[FinanceMessageParser.senderKey(group.source, group.sender)]
                        ?: if (group.source == "APP") profiles[FinanceMessageParser.senderKey("NOTIFICATION", group.sender)] else null
                    val blocked = rule?.action == "BLOCK_SENDER"
                    Column {
                        Text(group.sender, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(AppText.get("{arg0} · {arg1} неучтённых · {arg2}", if (group.source == "APP") AppText.get("Приложение") else group.source, group.count, if (blocked) AppText.get("заблокировано") else AppText.get("сбор включён")), color = AppColors.SecondaryText)
                        Row {
                            TextButton(onClick = { onSettings(group.source, group.sender) }) { Text(AppText.get("Настроить")) }
                            TextButton(enabled = !blocked, onClick = { onBlock(group.source, group.sender) }) { Text(AppText.get("Не собирать и очистить")) }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text(AppText.get("Готово")) } })
}
