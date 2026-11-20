package com.anasikus.lifeassistant.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import com.anasikus.lifeassistant.domain.CalendarDateUtils
import java.util.Calendar
import java.util.Locale

@Composable
fun OutlinedDateField(label: String, date: String, onSelected: (String) -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(CalendarDateUtils.formatDate(date), {}, readOnly = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
        Box(Modifier.matchParentSize().semantics { contentDescription = label }.clickable(role = Role.Button) {
            val c = CalendarDateUtils.parseCalendar(date)
            DatePickerDialog(context, { _, y, m, d -> onSelected("%04d-%02d-%02d".format(Locale.US, y, m + 1, d)) },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        })
    }
}

@Composable
fun OutlinedTimeField(label: String, minute: Int, onSelected: (Int) -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField("%02d:%02d".format(minute / 60, minute % 60), {}, readOnly = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
        Box(Modifier.matchParentSize().semantics { contentDescription = label }.clickable(role = Role.Button) {
            TimePickerDialog(context, { _, h, m -> onSelected(h * 60 + m) }, minute / 60, minute % 60, true).show()
        })
    }
}
