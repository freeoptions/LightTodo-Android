package com.mytodo.android.data

enum class RepeatMode(val value: String) {
    NONE("none"),
    DAILY("daily"),
    WEEKLY("weekly"),
    INTERVAL_DAYS("interval_days"),
    SPECIFIC_DATES("specific_dates");

    companion object {
        fun fromValue(value: String?): RepeatMode =
            entries.firstOrNull { it.value == value } ?: NONE
    }
}
