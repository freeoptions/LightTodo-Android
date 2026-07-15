package com.lighttodo.android.widget

import com.lighttodo.android.data.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoWidgetTest {
    @Test
    fun `applyOptimisticToggle should update row and progress immediately`() {
        val rows = listOf(
            widgetRow(todoId = "parent", title = "Parent", hasChildren = true, depth = 0),
            widgetRow(todoId = "child-1", title = "Child 1", depth = 1),
            widgetRow(todoId = "child-2", title = "Child 2", depth = 1),
        )

        val result = applyOptimisticToggle(rows, todoId = "child-1", completed = true)

        requireNotNull(result)
        assertTrue(result.first { it.todoId == "child-1" }.completed)
        assertTrue(result.first { it.todoId == "child-1" }.celebrating)
        assertFalse(result.first { it.todoId == "parent" }.completed)
        assertEquals("1/2", progressLabelForRows(result))
    }

    @Test
    fun `applyOptimisticToggle should update parent subtree immediately`() {
        val rows = listOf(
            widgetRow(todoId = "parent", title = "Parent", hasChildren = true, depth = 0),
            widgetRow(todoId = "child-1", title = "Child 1", depth = 1),
            widgetRow(todoId = "child-2", title = "Child 2", depth = 1),
        )

        val result = applyOptimisticToggle(rows, todoId = "parent", completed = true)

        requireNotNull(result)
        assertTrue(result.all { it.completed })
        assertTrue(result.first { it.todoId == "parent" }.celebrating)
        assertFalse(result.first { it.todoId == "child-1" }.celebrating)
        assertEquals("2/2", progressLabelForRows(result))
    }

    @Test
    fun `applyOptimisticToggle should return null for unknown row`() {
        val rows = listOf(widgetRow(todoId = "known", title = "Known"))

        val result = applyOptimisticToggle(rows, todoId = "missing", completed = true)

        assertNull(result)
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
