package com.mytodo.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mytodo.android.R
import com.mytodo.android.data.TodayTodoNode

@Composable
fun TodoItem(
    node: TodayTodoNode,
    depth: Int,
    expanded: Boolean,
    onExpandToggle: (String) -> Unit,
    onCheckedChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val todo = node.todo
    val hasChildren = node.hasChildren
    val titleColor = if (todo.completed) {
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
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = todo.content,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (hasChildren) FontWeight.Medium else FontWeight.Normal,
                    color = titleColor,
                    textDecoration = if (todo.completed) TextDecoration.LineThrough else null,
                )
                if (hasChildren) {
                    Text(
                        text = if (expanded) stringResource(id = R.string.todo_collapse_hint) else stringResource(id = R.string.todo_expand_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TodoCheckBox(
                checked = todo.completed,
                onClick = { onCheckedChange(todo.id) },
            )
        }
    }
}

@Composable
private fun TodoCheckBox(
    checked: Boolean,
    onClick: () -> Unit,
) {
    Checkbox(
        checked = checked,
        onCheckedChange = { onClick() },
        modifier = Modifier
            .size(44.dp)
            .semantics {
                contentDescription = if (checked) {
                    stringResource(id = R.string.todo_mark_incomplete_action)
                } else {
                    stringResource(id = R.string.todo_mark_complete_action)
                }
            },
    )
}
