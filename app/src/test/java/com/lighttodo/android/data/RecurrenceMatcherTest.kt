package com.lighttodo.android.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceMatcherTest {
    private val baseTime = 1_714_000_000_000L

    @Test
    fun `daily tasks are scheduled for any date before expiry`() {
        val todo = todo(
            repeatMode = RepeatMode.DAILY.value,
            expiryDate = "2026-04-30",
        )

        assertTrue(todo.isScheduledOn(LocalDate.parse("2026-04-26")))
        assertFalse(todo.isScheduledOn(LocalDate.parse("2026-05-01")))
    }

    @Test
    fun `weekly tasks only match configured iso weekdays`() {
        val todo = todo(
            repeatMode = RepeatMode.WEEKLY.value,
            weekdays = "1,3,5",
        )

        assertTrue(todo.isScheduledOn(LocalDate.parse("2026-04-27")))
        assertFalse(todo.isScheduledOn(LocalDate.parse("2026-04-28")))
    }

    @Test
    fun `weekdays tasks stay scheduled for the whole week`() {
        val todo = todo(repeatMode = RepeatMode.WEEKDAYS.value)

        assertTrue(todo.isScheduledOn(LocalDate.parse("2026-04-27")))
        assertTrue(todo.isScheduledOn(LocalDate.parse("2026-05-02")))
        assertTrue(todo.isScheduledOn(LocalDate.parse("2026-05-03")))
    }

    @Test
    fun `monthly tasks stay scheduled for the whole month`() {
        val todo = todo(repeatMode = RepeatMode.MONTHLY.value)

        assertTrue(todo.isScheduledOn(LocalDate.parse("2026-05-01")))
        assertTrue(todo.isScheduledOn(LocalDate.parse("2026-05-15")))
        assertTrue(todo.isScheduledOn(LocalDate.parse("2026-05-31")))
    }

    @Test
    fun `interval day tasks match anchor date cadence`() {
        val todo = todo(
            repeatMode = RepeatMode.INTERVAL_DAYS.value,
            intervalDays = 3,
            anchorDate = "2026-04-20",
        )

        assertTrue(todo.isScheduledOn(LocalDate.parse("2026-04-20")))
        assertTrue(todo.isScheduledOn(LocalDate.parse("2026-04-23")))
        assertTrue(todo.isScheduledOn(LocalDate.parse("2026-04-26")))
        assertFalse(todo.isScheduledOn(LocalDate.parse("2026-04-25")))
        assertFalse(todo.isScheduledOn(LocalDate.parse("2026-04-19")))
    }

    @Test
    fun `interval day tasks created yesterday stay hidden today when interval is two days`() {
        val todo = todo(
            repeatMode = RepeatMode.INTERVAL_DAYS.value,
            intervalDays = 2,
            anchorDate = "2026-05-05",
        )

        assertTrue(todo.isScheduledOn(LocalDate.parse("2026-05-05")))
        assertFalse(todo.isScheduledOn(LocalDate.parse("2026-05-06")))
        assertTrue(todo.isScheduledOn(LocalDate.parse("2026-05-07")))
    }

    @Test
    fun `interval day tasks reject invalid configuration`() {
        val missingAnchor = todo(
            repeatMode = RepeatMode.INTERVAL_DAYS.value,
            intervalDays = 2,
        )
        val invalidInterval = todo(
            repeatMode = RepeatMode.INTERVAL_DAYS.value,
            intervalDays = 0,
            anchorDate = "2026-04-20",
        )

        assertFalse(missingAnchor.isScheduledOn(LocalDate.parse("2026-04-26")))
        assertFalse(invalidInterval.isScheduledOn(LocalDate.parse("2026-04-26")))
    }

    @Test
    fun `specific date tasks match exact iso dates`() {
        val todo = todo(
            repeatMode = RepeatMode.SPECIFIC_DATES.value,
            specificDates = "2026-04-26, 2026-05-01",
        )

        assertTrue(todo.isScheduledOn(LocalDate.parse("2026-04-26")))
        assertFalse(todo.isScheduledOn(LocalDate.parse("2026-04-27")))
    }

    @Test
    fun `disabled tasks are never scheduled`() {
        val todo = todo(
            repeatMode = RepeatMode.DAILY.value,
            disabled = true,
        )

        assertFalse(todo.isScheduledOn(LocalDate.parse("2026-04-26")))
    }

    private fun todo(
        repeatMode: String = RepeatMode.NONE.value,
        weekdays: String? = null,
        intervalDays: Int? = null,
        specificDates: String? = null,
        anchorDate: String? = null,
        expiryDate: String? = null,
        disabled: Boolean = false,
    ): TodoEntity = TodoEntity(
        id = "todo-1",
        content = "Test todo",
        repeatMode = repeatMode,
        weekdays = weekdays,
        intervalDays = intervalDays,
        specificDates = specificDates,
        anchorDate = anchorDate,
        expiryDate = expiryDate,
        disabled = disabled,
        createdAt = baseTime,
        updatedAt = baseTime,
    )
}
