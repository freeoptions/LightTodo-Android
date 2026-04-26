package com.mytodo.android.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TodoRepositoryTest {

    private val todoDao = mockk<TodoDao>(relaxed = true)
    private val fixedClock = Clock.fixed(Instant.parse("2026-04-26T10:00:00Z"), ZoneId.of("UTC"))
    private val repository = TodoRepository(todoDao, fixedClock)

    @Test
    fun `buildTodayTodoData should filter correctly for current date`() {
        val today = LocalDate.parse("2026-04-26")
        val todos = listOf(
            createTodo("1", "Today task"),
            createTodo("2", "Disabled task", disabled = true),
            createTodo("3", "Future expiry", expiryDate = "2026-04-27"),
            createTodo("4", "Past expiry", expiryDate = "2025-12-31"),
        )

        val result = repository.buildTodayTodoData(todos, today)

        assertEquals(2, result.totalCount)
        assertTrue(result.tree.any { it.todo.id == "1" })
        assertTrue(result.tree.any { it.todo.id == "3" })
        assertFalse(result.tree.any { it.todo.id == "2" })
        assertFalse(result.tree.any { it.todo.id == "4" })
    }

    @Test
    fun `buildTodayTodoData should build tree structure`() {
        val today = LocalDate.parse("2026-04-26")
        val todos = listOf(
            createTodo("1", "Parent"),
            createTodo("2", "Child", parentId = "1"),
        )

        val result = repository.buildTodayTodoData(todos, today)

        assertEquals(1, result.tree.size)
        assertEquals("1", result.tree[0].todo.id)
        assertEquals(1, result.tree[0].children.size)
        assertEquals("2", result.tree[0].children[0].todo.id)
    }

    @Test
    fun `collectSubtreeIds should find all descendants`() {
        val todos = listOf(
            createTodo("1", "Root"),
            createTodo("2", "Child 1", parentId = "1"),
            createTodo("3", "Child 2", parentId = "1"),
            createTodo("4", "Grandchild", parentId = "2"),
        )
        val byParent = todos.groupBy { it.parentId }

        val result = repository.collectSubtreeIds("1", byParent)

        assertEquals(setOf("1", "2", "3", "4"), result)
    }

    @Test
    fun `completeParentSubtree should update parent and all children`() = runTest {
        val todos = listOf(
            createTodo("1", "Parent", completed = false),
            createTodo("2", "Child", parentId = "1", completed = false),
            createTodo("3", "Grandchild", parentId = "2", completed = false),
        )
        coEvery { todoDao.getAll() } returns todos

        repository.completeParentSubtree("1")

        coVerify(exactly = 1) { todoDao.updateCompletion("1", true, any(), any()) }
        coVerify(exactly = 1) { todoDao.updateCompletion("2", true, any(), any()) }
        coVerify(exactly = 1) { todoDao.updateCompletion("3", true, any(), any()) }
    }

    private fun createTodo(
        id: String,
        content: String,
        parentId: String? = null,
        completed: Boolean = false,
        disabled: Boolean = false,
        expiryDate: String? = null,
    ) = TodoEntity(
        id = id,
        content = content,
        parentId = parentId,
        completed = completed,
        disabled = disabled,
        expiryDate = expiryDate,
        createdAt = 0,
        updatedAt = 0,
    )
}
