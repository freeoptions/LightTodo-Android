package com.mytodo.android.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE parentId IS NULL ORDER BY sortOrder ASC, createdAt ASC")
    fun observeRootTodos(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE parentId = :parentId ORDER BY sortOrder ASC, createdAt ASC")
    fun observeChildren(parentId: String): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TodoEntity?

    @Query(
        """
        SELECT * FROM todos
        WHERE disabled = 0
          AND (expiryDate IS NULL OR expiryDate = '' OR expiryDate >= :targetDate)
          AND (
                repeatMode = 'none'
                OR repeatMode = 'daily'
                OR (repeatMode = 'weekly' AND weekdays IS NOT NULL AND weekdays != '')
                OR (repeatMode = 'interval_days' AND intervalDays IS NOT NULL AND intervalDays > 0 AND anchorDate IS NOT NULL AND anchorDate != '' AND anchorDate <= :targetDate)
                OR (repeatMode = 'specific_dates' AND specificDates IS NOT NULL AND specificDates != '')
              )
        ORDER BY parentId IS NOT NULL, sortOrder ASC, createdAt ASC
        """
    )
    suspend fun getRecurrenceCandidates(targetDate: String): List<TodoEntity>

    @Query("SELECT * FROM todos ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAll(): List<TodoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(todo: TodoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(todos: List<TodoEntity>)

    @Update
    suspend fun update(todo: TodoEntity)

    @Delete
    suspend fun delete(todo: TodoEntity)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE todos SET completed = :completed, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCompletion(
        id: String,
        completed: Boolean,
        completedAt: String?,
        updatedAt: Long,
    )

    @Query("UPDATE todos SET disabled = :disabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDisabled(
        id: String,
        disabled: Boolean,
        updatedAt: Long,
    )
}
