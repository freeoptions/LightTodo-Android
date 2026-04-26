package com.mytodo.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "todos",
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["repeatMode"]),
        Index(value = ["disabled"]),
        Index(value = ["completed"]),
    ],
)
data class TodoEntity(
    @PrimaryKey
    val id: String,
    val content: String,
    val parentId: String? = null,
    val sortOrder: Int = 0,
    val repeatMode: String = RepeatMode.NONE.value,
    val weekdays: String? = null,
    val intervalDays: Int? = null,
    val specificDates: String? = null,
    val anchorDate: String? = null,
    val expiryDate: String? = null,
    val completed: Boolean = false,
    val completedAt: String? = null,
    val disabled: Boolean = false,
    val expanded: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)
