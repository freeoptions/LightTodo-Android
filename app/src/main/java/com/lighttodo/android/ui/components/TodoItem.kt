package com.lighttodo.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.lighttodo.android.R
import com.lighttodo.android.data.RepeatMode
import com.lighttodo.android.data.TodayTodoNode

@Composable
fun TodoItem(
    node: TodayTodoNode,
    depth: Int,
    expanded: Boolean,
    onExpandToggle: (String) -> Unit,
    onClick: (String) -> Unit,
    onCheckedChange: (String) -> Unit,
    onDisabledChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val todo = node.todo
    val hasChildren = node.hasChildren
    val repeatMode = RepeatMode.fromValue(todo.repeatMode)
    val isWeekdaysRepeat = repeatMode == RepeatMode.WEEKDAYS
    val isMonthlyRepeat = repeatMode == RepeatMode.MONTHLY
    val weekdaysBadge = stringResource(id = R.string.todo_repeat_weekdays_badge)
    val monthlyBadge = stringResource(id = R.string.todo_repeat_monthly_badge)
    val disabledBadge = stringResource(id = R.string.todo_disabled_badge)
    val disabledActionDescription = stringResource(
        id = if (todo.disabled) R.string.todo_enable_action else R.string.todo_disable_action,
    )
    val expandHint = if (expanded) {
        stringResource(id = R.string.todo_collapse_hint)
    } else {
        stringResource(id = R.string.todo_expand_hint)
    }
    val titleColor = if (todo.completed || todo.disabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (12 + depth * 16).dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (hasChildren) {
                    IconButton(
                        onClick = { onExpandToggle(todo.id) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                            contentDescription = stringResource(
                                id = if (expanded) R.string.todo_collapse_action else R.string.todo_expand_action,
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onClick(todo.id) },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = todo.content,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (hasChildren) FontWeight.Medium else FontWeight.Normal,
                    color = titleColor,
                    textDecoration = if (todo.completed) TextDecoration.LineThrough else null,
                )
                if (hasChildren || isWeekdaysRepeat || isMonthlyRepeat || todo.disabled) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isWeekdaysRepeat) {
                            Text(
                                text = weekdaysBadge,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        if (isMonthlyRepeat) {
                            Text(
                                text = monthlyBadge,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        if (todo.disabled) {
                            Text(
                                text = disabledBadge,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (hasChildren) {
                            Text(
                                text = expandHint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Switch(
                checked = !todo.disabled,
                onCheckedChange = { onDisabledChange(todo.id) },
                modifier = Modifier
                    .scale(0.8f)
                    .semantics {
                        contentDescription = disabledActionDescription
                    },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )

            TodoCheckBox(
                checked = todo.completed,
                onClick = { onCheckedChange(todo.id) },
                enabled = !todo.disabled,
            )
        }
    }
}

@Composable
private fun TodoCheckBox(
    checked: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val checkedActionDescription = if (checked) {
        stringResource(id = R.string.todo_mark_incomplete_action)
    } else {
        stringResource(id = R.string.todo_mark_complete_action)
    }

    Checkbox(
        checked = checked,
        onCheckedChange = { onClick() },
        enabled = enabled,
        modifier = Modifier
            .size(44.dp)
            .semantics {
                contentDescription = checkedActionDescription
            },
    )
}
