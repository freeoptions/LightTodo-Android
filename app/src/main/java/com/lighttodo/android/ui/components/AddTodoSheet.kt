package com.lighttodo.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lighttodo.android.R
import com.lighttodo.android.data.RepeatMode
import com.lighttodo.android.data.TodoEntity
import com.lighttodo.android.ui.AddTodoInput
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTodoSheet(
    todo: TodoEntity? = null,
    onDismiss: () -> Unit,
    onSubmit: (AddTodoInput) -> Unit,
    onDelete: (String) -> Unit = {},
) {
    val isEditing = todo != null
    var isDeleteDialogVisible by remember(todo?.id) { mutableStateOf(false) }
    var content by remember(todo?.id) { mutableStateOf(todo?.content.orEmpty()) }
    var repeatMode by remember(todo?.id) { mutableStateOf(RepeatMode.fromValue(todo?.repeatMode).takeIf { todo != null } ?: RepeatMode.DAILY) }
    var intervalDays by remember(todo?.id) { mutableStateOf(todo?.intervalDays) }
    var selectedWeekdays by remember(todo?.id) { mutableStateOf(todo?.weekdays.toIsoDaySet()) }
    var specificDatesInput by remember(todo?.id) { mutableStateOf(todo?.specificDates.orEmpty()) }

    val parsedSpecificDates = remember(specificDatesInput) {
        specificDatesInput
            .split(",")
            .mapNotNull { token ->
                token.trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
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
        modifier = Modifier.imePadding(),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(id = if (isEditing) R.string.edit_todo_title else R.string.add_todo_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(id = R.string.add_todo_placeholder)) },
                shape = MaterialTheme.shapes.medium,
                minLines = 3,
                maxLines = 6,
                singleLine = false,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
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
                                todoId = todo?.id,
                                content = content,
                                repeatMode = repeatMode,
                                weekdays = selectedWeekdays,
                                intervalDays = intervalDays,
                                anchorDate = resolveAnchorDateForSubmit(todo, repeatMode),
                                specificDates = parsedSpecificDates,
                            ),
                        )
                    },
                    enabled = isSubmitEnabled,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(id = if (isEditing) R.string.edit_todo_submit else R.string.add_todo_submit))
                }
            }

            if (isEditing && todo != null) {
                OutlinedButton(
                    onClick = { isDeleteDialogVisible = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = stringResource(id = R.string.delete_todo_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (isDeleteDialogVisible && todo != null) {
        AlertDialog(
            onDismissRequest = { isDeleteDialogVisible = false },
            title = {
                Text(text = stringResource(id = R.string.delete_todo_dialog_title))
            },
            text = {
                Text(text = stringResource(id = R.string.delete_todo_dialog_message, todo.content))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleteDialogVisible = false
                        onDelete(todo.id)
                    },
                ) {
                    Text(
                        text = stringResource(id = R.string.delete_todo_dialog_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { isDeleteDialogVisible = false }) {
                    Text(text = stringResource(id = R.string.delete_todo_dialog_cancel))
                }
            },
        )
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
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            )
                            .clickable { onWeekdayToggle(day) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = day.toWeekdayShortName(),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp),
                    placeholder = { Text(stringResource(id = R.string.add_todo_specific_dates_placeholder)) },
                    supportingText = {
                        Text(text = stringResource(id = R.string.add_todo_specific_dates_hint))
                    },
                    minLines = 3,
                    maxLines = 5,
                    singleLine = false,
                )
            }
        }
    }
}

@Composable
private fun RepeatMode.getDisplayName(): String = when (this) {
    RepeatMode.NONE -> stringResource(id = R.string.repeat_none)
    RepeatMode.DAILY -> stringResource(id = R.string.repeat_daily)
    RepeatMode.WEEKDAYS -> stringResource(id = R.string.repeat_weekdays)
    RepeatMode.WEEKLY -> stringResource(id = R.string.repeat_weekly)
    RepeatMode.MONTHLY -> stringResource(id = R.string.repeat_monthly)
    RepeatMode.INTERVAL_DAYS -> stringResource(id = R.string.repeat_interval)
    RepeatMode.SPECIFIC_DATES -> stringResource(id = R.string.repeat_specific)
}

private fun String?.toIsoDaySet(): Set<Int> =
    this
        ?.split(',')
        ?.mapNotNull { token -> token.trim().toIntOrNull()?.takeIf { it in 1..7 } }
        ?.toSet()
        .orEmpty()

internal fun resolveAnchorDateForSubmit(
    todo: TodoEntity?,
    repeatMode: RepeatMode,
    today: LocalDate = LocalDate.now(),
): LocalDate? {
    if (repeatMode != RepeatMode.INTERVAL_DAYS) {
        return null
    }

    val existingIntervalAnchor = todo
        ?.takeIf { RepeatMode.fromValue(it.repeatMode) == RepeatMode.INTERVAL_DAYS }
        ?.anchorDate
        .toLocalDateOrNull()

    return existingIntervalAnchor ?: today
}

private fun String?.toLocalDateOrNull(): LocalDate? =
    runCatching {
        this?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
    }.getOrNull()

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
