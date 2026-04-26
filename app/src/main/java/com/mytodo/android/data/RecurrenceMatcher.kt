package com.mytodo.android.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object RecurrenceMatcher {
    fun isScheduledOn(todo: TodoEntity, targetDate: LocalDate): Boolean {
        if (todo.disabled) {
            return false
        }

        val expiry = todo.expiryDate.toLocalDateOrNull()
        if (expiry != null && targetDate.isAfter(expiry)) {
            return false
        }

        return when (RepeatMode.fromValue(todo.repeatMode)) {
            RepeatMode.NONE -> true
            RepeatMode.DAILY -> true
            RepeatMode.WEEKLY -> targetDate.dayOfWeek.value in todo.weekdays.toIsoDaySet()
            RepeatMode.INTERVAL_DAYS -> matchesIntervalDays(todo, targetDate)
            RepeatMode.SPECIFIC_DATES -> targetDate.toString() in todo.specificDates.toIsoDateSet()
        }
    }

    private fun matchesIntervalDays(todo: TodoEntity, targetDate: LocalDate): Boolean {
        val anchorDate = todo.anchorDate.toLocalDateOrNull() ?: return false
        val intervalDays = todo.intervalDays?.takeIf { it > 0 } ?: return false
        val distance = ChronoUnit.DAYS.between(anchorDate, targetDate)

        return distance >= 0 && distance % intervalDays == 0L
    }
}

fun TodoEntity.isScheduledOn(targetDate: LocalDate): Boolean =
    RecurrenceMatcher.isScheduledOn(this, targetDate)

private fun String?.toLocalDateOrNull(): LocalDate? =
    runCatching {
        this?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
    }.getOrNull()

private fun String?.toIsoDaySet(): Set<Int> =
    this
        ?.split(',')
        ?.mapNotNull { token -> token.trim().toIntOrNull()?.takeIf { it in 1..7 } }
        ?.toSet()
        .orEmpty()

private fun String?.toIsoDateSet(): Set<String> =
    this
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        .orEmpty()
