package com.lighttodo.android.widget

import com.lighttodo.android.data.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class TodoWidgetTest {
    @Test
    fun `widget header formats full date and chinese weekday`() {
        val result = formatWidgetSolarDate(LocalDate.parse("2026-08-19"))

        assertEquals("2026-08-19 · 周三", result)
    }

    @Test
    fun `widget header font grows with available width and shrinks as a whole`() {
        val dateText = "2026-08-19 · 周三 · 七月初七 · 七夕"
        val narrowSize = calculateWidgetHeaderFontSize(
            widgetWidthDp = 280f,
            dateText = dateText,
            progressText = "2/7",
        )
        val wideSize = calculateWidgetHeaderFontSize(
            widgetWidthDp = 360f,
            dateText = dateText,
            progressText = "2/7",
        )

        assertTrue(wideSize > narrowSize)
        assertTrue(narrowSize >= 10f)
        assertTrue(wideSize <= 18f)
    }

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
    fun `completed task stays in place during the three second sink window`() {
        val nowMillis = 1_000_000L
        val completedAt = OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(nowMillis - 1_000L),
            ZoneOffset.UTC,
        ).toString()
        val rows = listOf(
            widgetRow(
                todoId = "completed",
                title = "Completed",
                completed = true,
                completedAt = completedAt,
                sortIndex = 0,
            ),
            widgetRow(todoId = "open", title = "Open", sortIndex = 1),
        )

        val pendingRows = sortWidgetRows(rows, nowMillis)
        assertEquals(listOf("completed", "open"), pendingRows.map { it.todoId })
        assertTrue(pendingRows.first().celebrating)

        val settledRows = sortWidgetRows(rows, nowMillis + 3_000L)
        assertEquals(listOf("open", "completed"), settledRows.map { it.todoId })
        assertFalse(settledRows.last().celebrating)
    }

    @Test
    fun `multiple completed tasks keep independent sink windows`() {
        val nowMillis = 2_000_000L
        val firstCompletedAt = OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(nowMillis - 2_500L),
            ZoneOffset.UTC,
        ).toString()
        val secondCompletedAt = OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(nowMillis - 500L),
            ZoneOffset.UTC,
        ).toString()
        val rows = listOf(
            widgetRow(
                todoId = "first",
                title = "First",
                completed = true,
                completedAt = firstCompletedAt,
                sortIndex = 0,
            ),
            widgetRow(
                todoId = "second",
                title = "Second",
                completed = true,
                completedAt = secondCompletedAt,
                sortIndex = 1,
            ),
        )

        val result = sortWidgetRows(rows, nowMillis + 1_000L)

        assertEquals(listOf("second", "first"), result.map { it.todoId })
        assertTrue(result.first().celebrating)
        assertFalse(result.last().celebrating)
    }

    private fun widgetRow(
        todoId: String,
        title: String,
        completed: Boolean = false,
        completedAt: String? = null,
        hasChildren: Boolean = false,
        depth: Int = 0,
        sortIndex: Int = 0,
    ): TodoWidgetRow =
        TodoWidgetRow(
            todoId = todoId,
            title = title,
            completed = completed,
            completedAt = completedAt,
            hasChildren = hasChildren,
            depth = depth,
            repeatMode = RepeatMode.NONE,
            sortIndex = sortIndex,
        )
}
