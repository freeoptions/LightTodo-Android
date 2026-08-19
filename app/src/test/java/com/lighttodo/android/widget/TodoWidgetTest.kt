package com.lighttodo.android.widget

import com.lighttodo.android.data.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TodoWidgetTest {
    @Test
    fun `progress counts leaf todos only`() {
        val rows = listOf(
            widgetRow(todoId = "parent", title = "Parent", completed = true, hasChildren = true, depth = 0),
            widgetRow(todoId = "child-1", title = "Child 1", completed = true, depth = 1),
            widgetRow(todoId = "child-2", title = "Child 2", depth = 1),
        )

        val result = progressLabelForRows(rows)

        assertEquals("1/2", result)
    }

    @Test
    fun `clearing celebration removes stale optimistic row state`() {
        val snapshot = TodoWidgetSnapshot(
            rows = listOf(widgetRow(todoId = "todo", title = "Todo").copy(celebrating = true)),
            progressLabel = "1/1",
        )

        val result = snapshot.withCelebration(null)

        assertFalse(result.rows.single().celebrating)
    }

    private fun widgetRow(
        todoId: String,
        title: String,
        completed: Boolean = false,
        hasChildren: Boolean = false,
        depth: Int = 0,
    ): TodoWidgetRow =
        TodoWidgetRow(
            todoId = todoId,
            title = title,
            completed = completed,
            hasChildren = hasChildren,
            depth = depth,
            repeatMode = RepeatMode.NONE,
            sortIndex = 0,
        )
}
