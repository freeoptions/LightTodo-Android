package com.mytodo.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mytodo.android.R
import com.mytodo.android.data.RepeatMode
import com.mytodo.android.ui.AddTodoInput
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTodoSheet(
    onDismiss: () -> Unit,
    onSubmit: (AddTodoInput) -> Unit,
) {
    var content by remember { mutableStateOf("") }
    var repeatMode by remember { mutableStateOf(RepeatMode.NONE) }
    var subtasks by remember { mutableStateOf(listOf("")) }
    var intervalDays by remember { mutableStateOf<Int?>(null) }
    var selectedWeekdays by remember { mutableStateOf(setOf<Int>()) }
    var specificDatesInput by remember { mutableStateOf("") }

    val parsedSpecificDates = remember(specificDatesInput) {
        specificDatesInput
            .split(",")
            .mapNotNull { token ->
                token.trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let {
                        runCatching { LocalDate.parse(it) }.getOrNull()
                    }
            }
            .distinct()
    }
    val isSubmitEnabled = content.isNotBlank() && when (repeatMode) {
        RepeatMode.INTERVAL_DAYS -> (intervalDays ?: 0) > 0
        RepeatMode.WEEKLY -> selectedWeekdays.isNotEmpty()
        RepeatMode.SPECIFIC_DATES -> parsedSpecificDates.isNotEmpty()
        else -> true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(id = R.string.add_todo_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(id = R.string.add_todo_placeholder)) },
                shape = MaterialTheme.shapes.medium,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            RepeatModeSelector(
                selectedMode = repeatMode,
                onModeSelected = { repeatMode = it },
                intervalDays = intervalDays,
                onIntervalChange = { intervalDays = it },
                selectedWeekdays = selectedWeekdays,
                onWeekdayToggle = { day ->
                    selectedWeekdays = if (day in selectedWeekdays) {
                        selectedWeekdays - day
                    } else {
                        selectedWeekdays + day
                    }
                },
                specificDatesInput = specificDatesInput,
                onSpecificDatesInputChange = { specificDatesInput = it },
            )

            SubtaskSection(
                subtasks = subtasks,
                onSubtaskChange = { index, value ->
                    val newList = subtasks.toMutableList()
                    newList[index] = value
                    subtasks = newList
                },
                onAddSubtask = {
                    subtasks = subtasks + ""
                },
                onRemoveSubtask = { index ->
                    if (subtasks.size > 1) {
                        subtasks = subtasks.toMutableList().apply { removeAt(index) }
                    } else {
                        subtasks = listOf("")
                    }
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(id = R.string.add_todo_cancel))
                }
                Button(
                    onClick = {
                        onSubmit(
                            AddTodoInput(
                                content = content,
                                repeatMode = repeatMode,
                                weekdays = selectedWeekdays,
                                intervalDays = intervalDays,
                                anchorDate = LocalDate.now(),
                                specificDates = parsedSpecificDates,
                                subtasks = subtasks.filter { it.isNotBlank() },
                            ),
                        )
                    },
                    enabled = isSubmitEnabled,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(id = R.string.add_todo_submit))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatModeSelector(
    selectedMode: RepeatMode,
    onModeSelected: (RepeatMode) -> Unit,
    intervalDays: Int?,
    onIntervalChange: (Int?) -> Unit,
    selectedWeekdays: Set<Int>,
    onWeekdayToggle: (Int) -> Unit,
    specificDatesInput: String,
    onSpecificDatesInputChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Repeat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(id = R.string.add_todo_repeat_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RepeatMode.entries.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { onModeSelected(mode) },
                    label = { Text(mode.getDisplayName()) },
                )
            }
        }

        if (selectedMode == RepeatMode.INTERVAL_DAYS) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = intervalDays?.toString() ?: "",
                    onValueChange = { onIntervalChange(it.toIntOrNull()) },
                    modifier = Modifier.width(100.dp),
                    placeholder = { Text("2") },
                    suffix = { Text(stringResource(id = R.string.add_todo_interval_suffix)) },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Number,
                    ),
                    singleLine = true,
                )
                Text(
                    text = stringResource(id = R.string.add_todo_interval_label, intervalDays ?: 2),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (selectedMode == RepeatMode.WEEKLY) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf(1, 2, 3, 4, 5, 6, 7).forEach { day ->
                    val isSelected = day in selectedWeekdays
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable { onWeekdayToggle(day) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = day.toWeekdayShortName(),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        if (selectedMode == RepeatMode.SPECIFIC_DATES) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(id = R.string.add_todo_specific_dates_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                OutlinedTextField(
                    value = specificDatesInput,
                    onValueChange = onSpecificDatesInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(id = R.string.add_todo_specific_dates_placeholder)) },
                    supportingText = {
                        Text(text = stringResource(id = R.string.add_todo_specific_dates_hint))
                    },
                    singleLine = false,
                )
            }
        }
    }
}

@Composable
private fun SubtaskSection(
    subtasks: List<String>,
    onSubtaskChange: (Int, String) -> Unit,
    onAddSubtask: () -> Unit,
    onRemoveSubtask: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(id = R.string.add_todo_subtasks_title),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = onAddSubtask) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        subtasks.forEachIndexed { index, subtask ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = subtask,
                    onValueChange = { onSubtaskChange(index, it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(id = R.string.add_todo_subtask_placeholder)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    singleLine = true,
                )
                IconButton(onClick = { onRemoveSubtask(index) }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun RepeatMode.getDisplayName(): String = when (this) {
    RepeatMode.NONE -> stringResource(id = R.string.repeat_none)
    RepeatMode.DAILY -> stringResource(id = R.string.repeat_daily)
    RepeatMode.WEEKLY -> stringResource(id = R.string.repeat_weekly)
    RepeatMode.INTERVAL_DAYS -> stringResource(id = R.string.repeat_interval)
    RepeatMode.SPECIFIC_DATES -> stringResource(id = R.string.repeat_specific)
}

private fun Int.toWeekdayShortName(): String = when (this) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    7 -> "日"
    else -> ""
}
