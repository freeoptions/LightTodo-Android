package com.lighttodo.android.data

enum class RepeatMode(val value: String) {
    NONE("none"),
    DAILY("daily"),
    WEEKDAYS("weekdays"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    INTERVAL_DAYS("interval_days"),
    SPECIFIC_DATES("specific_dates");

    companion object {
        fun fromValue(value: String?): RepeatMode =
            entries.firstOrNull { it.value == value } ?: NONE
    }
}
