package com.mytodo.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
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
                    Text(
                        text = if (expanded) "⌄" else ">",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable { onExpandToggle(todo.id) }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
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
                        text = if (expanded) "点击左侧收起子任务" else "点击左侧展开子任务",
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
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(
                    color = if (checked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text(
                    text = "✓",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
