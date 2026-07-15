package com.lighttodo.android.ui.components

import com.lighttodo.android.data.RepeatMode
import com.lighttodo.android.data.TodoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AddTodoSheetTest {

    @Test
    fun `editing interval repeat todo keeps original anchor date`() {
        val todo = createTodo(
            repeatMode = RepeatMode.INTERVAL_DAYS.value,
            anchorDate = "2026-05-05",
        )

        val result = resolveAnchorDateForSubmit(
            todo = todo,
            repeatMode = RepeatMode.INTERVAL_DAYS,
            today = LocalDate.parse("2026-05-10"),
        )

        assertEquals(LocalDate.parse("2026-05-05"), result)
    }

    @Test
    fun `new interval repeat todo uses current day as anchor date`() {
        val result = resolveAnchorDateForSubmit(
            todo = null,
            repeatMode = RepeatMode.INTERVAL_DAYS,
            today = LocalDate.parse("2026-05-10"),
        )

        assertEquals(LocalDate.parse("2026-05-10"), result)
    }

    @Test
    fun `non interval repeat todo does not submit anchor date`() {
        val todo = createTodo(
            repeatMode = RepeatMode.INTERVAL_DAYS.value,
            anchorDate = "2026-05-05",
        )

        val result = resolveAnchorDateForSubmit(
            todo = todo,
            repeatMode = RepeatMode.DAILY,
            today = LocalDate.parse("2026-05-10"),
        )

        assertNull(result)
    }

    @Test
    fun `switching existing todo to interval repeat starts from edit day`() {
        val todo = createTodo(repeatMode = RepeatMode.DAILY.value)

        val result = resolveAnchorDateForSubmit(
            todo = todo,
            repeatMode = RepeatMode.INTERVAL_DAYS,
            today = LocalDate.parse("2026-05-10"),
        )

        assertEquals(LocalDate.parse("2026-05-10"), result)
    }

    private fun createTodo(
        repeatMode: String,
        anchorDate: String? = null,
    ) = TodoEntity(
        id = "todo-1",
        content = "Test todo",
        repeatMode = repeatMode,
        anchorDate = anchorDate,
        createdAt = 0,
        updatedAt = 0,
    )
}
