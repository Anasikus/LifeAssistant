package com.anasikus.lifeassistant.ui.components

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anasikus.lifeassistant.ui.AppColors

/** Поле выбора, а не TextField: вся поверхность гарантированно открывает часы. */
@Composable
fun TimePickerField(
    label: String,
    minute: Int,
    onTimeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    endOfDayAtMidnight: Boolean = false
) {
    val context = LocalContext.current
    OutlinedButton(
        modifier = modifier.fillMaxWidth(),
        onClick = {
            val clockMinute = minute.coerceIn(0, 1440) % 1440
            TimePickerDialog(
                context,
                { _, hour, selectedMinute ->
                    val selected = hour * 60 + selectedMinute
                    onTimeSelected(if (endOfDayAtMidnight && selected == 0) 1440 else selected)
                },
                clockMinute / 60,
                clockMinute % 60,
                true
            ).show()
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(label, color = AppColors.SecondaryText, fontSize = 12.sp)
            Text("%02d:%02d".format(minute / 60, minute % 60), color = AppColors.MainText, fontSize = 18.sp)
        }
    }
}
