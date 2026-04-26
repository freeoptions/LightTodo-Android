package com.mytodo.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mytodo.android.data.TodayTodoNode

@Composable
fun TodoList(
    nodes: List<TodayTodoNode>,
    isExpanded: (TodayTodoNode) -> Boolean,
    onExpandToggle: (String) -> Unit,
    onCheckedChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        nodes.forEachIndexed { index, node ->
            TodoBranch(
                node = node,
                depth = 0,
                isExpanded = isExpanded,
                onExpandToggle = onExpandToggle,
                onCheckedChange = onCheckedChange,
            )

            if (index != nodes.lastIndex) {
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun TodoBranch(
    node: TodayTodoNode,
    depth: Int,
    isExpanded: (TodayTodoNode) -> Boolean,
    onExpandToggle: (String) -> Unit,
    onCheckedChange: (String) -> Unit,
) {
    val expanded = isExpanded(node)

    TodoItem(
        node = node,
        depth = depth,
        expanded = expanded,
        onExpandToggle = onExpandToggle,
        onCheckedChange = onCheckedChange,
    )

    if (node.hasChildren && expanded) {
        node.children.forEach { child ->
            TodoBranch(
                node = child,
                depth = depth + 1,
                isExpanded = isExpanded,
                onExpandToggle = onExpandToggle,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}
