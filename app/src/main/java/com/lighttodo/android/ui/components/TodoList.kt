package com.lighttodo.android.ui.components

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lighttodo.android.data.TodayTodoNode

fun androidx.compose.foundation.lazy.LazyListScope.TodoList(
    nodes: List<TodayTodoNode>,
    isExpanded: (TodayTodoNode) -> Boolean,
    onExpandToggle: (String) -> Unit,
    onTodoClick: (String) -> Unit,
    onCheckedChange: (String) -> Unit,
    onDisabledChange: (String) -> Unit,
) {
    val visibleRows = buildVisibleRows(nodes = nodes, isExpanded = isExpanded)

    items(
        items = visibleRows,
        key = { row -> row.node.todo.id },
    ) { row ->
        TodoRowItem(
            row = row,
            isLast = row == visibleRows.last(),
            onExpandToggle = onExpandToggle,
            onTodoClick = onTodoClick,
            onCheckedChange = onCheckedChange,
            onDisabledChange = onDisabledChange,
        )
    }
}

@Composable
private fun LazyItemScope.TodoRowItem(
    row: VisibleTodoRow,
    isLast: Boolean,
    onExpandToggle: (String) -> Unit,
    onTodoClick: (String) -> Unit,
    onCheckedChange: (String) -> Unit,
    onDisabledChange: (String) -> Unit,
) {
    TodoItem(
        node = row.node,
        depth = row.depth,
        expanded = row.expanded,
        onExpandToggle = onExpandToggle,
        onClick = onTodoClick,
        onCheckedChange = onCheckedChange,
        onDisabledChange = onDisabledChange,
        modifier = Modifier.animateItem(),
    )

    if (!isLast) {
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private data class VisibleTodoRow(
    val node: TodayTodoNode,
    val depth: Int,
    val expanded: Boolean,
)

private fun buildVisibleRows(
    nodes: List<TodayTodoNode>,
    isExpanded: (TodayTodoNode) -> Boolean,
): List<VisibleTodoRow> {
    val rows = mutableListOf<VisibleTodoRow>()

    fun append(nodes: List<TodayTodoNode>, depth: Int) {
        nodes.forEach { node ->
            val expanded = isExpanded(node)
            rows += VisibleTodoRow(node = node, depth = depth, expanded = expanded)
            if (node.hasChildren && expanded) {
                append(node.children, depth + 1)
            }
        }
    }

    append(nodes, depth = 0)
    return rows
}
