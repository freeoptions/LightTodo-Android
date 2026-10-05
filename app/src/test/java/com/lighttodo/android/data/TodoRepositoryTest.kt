package com.lighttodo.android.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TodoRepositoryTest {

    private val todoDao = mockk<TodoDao>(relaxed = true)
    private val fixedClock = Clock.fixed(Instant.parse("2026-04-26T10:00:00Z"), ZoneId.of("UTC"))
    private val repository = TodoRepository(todoDao, fixedClock, WidgetRefresher {})

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
        assertTrue(result.disabledTree.any { it.todo.id == "2" })
        assertFalse(result.tree.any { it.todo.id == "4" })
    }

    @Test
    fun `toggleTodoDisabled persists the opposite state`() = runTest {
        val todo = createTodo("1", "Daily task", disabled = false)
        coEvery { todoDao.getById("1") } returns todo

        repository.toggleTodoDisabled("1")

        coVerify(exactly = 1) {
            todoDao.updateDisabled("1", true, any())
        }
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

        coVerify(exactly = 1) {
            todoDao.updateCompletions(
                match { ids -> ids.toSet() == setOf("1", "2", "3") },
                true,
                any(),
                any(),
            )
        }
        coVerify(exactly = 0) { todoDao.updateCompletion(any(), any(), any(), any()) }
    }

    @Test
    fun `toggleTodoFromWidget should resolve the clicked todo from the full table`() = runTest {
        val todo = createTodo("1", "Widget task", completed = false)
        coEvery { todoDao.getAll() } returns listOf(todo)
        coEvery { todoDao.getById("1") } returns todo.copy(completed = true)

        val result = repository.toggleTodoFromWidget("1")

        assertTrue(result.found)
        assertFalse(result.completedBefore!!)
        assertTrue(result.requestedCompleted!!)
        assertEquals(true, result.persistedCompleted)
        coVerify(exactly = 1) { todoDao.getAll() }
        coVerify(exactly = 0) { todoDao.getRecurrenceCandidates(any()) }
        coVerify(exactly = 1) { todoDao.updateCompletion("1", true, any(), any()) }
    }

    @Test
    fun `toggleTodoFromWidget toggles the current database state`() = runTest {
        val todo = createTodo("1", "Widget task", completed = true)
        coEvery { todoDao.getAll() } returns listOf(todo)
        coEvery { todoDao.getById("1") } returns todo.copy(completed = false)

        val result = repository.toggleTodoFromWidget("1")

        assertTrue(result.found)
        assertTrue(result.completedBefore!!)
        assertFalse(result.requestedCompleted!!)
        assertEquals(false, result.persistedCompleted)
        coVerify(exactly = 1) { todoDao.updateCompletion("1", false, null, any()) }
    }

    @Test
    fun `toggleTodoFromWidget reports stale widget rows`() = runTest {
        coEvery { todoDao.getAll() } returns emptyList()

        val result = repository.toggleTodoFromWidget("missing")

        assertFalse(result.found)
        coVerify(exactly = 0) { todoDao.updateCompletion(any(), any(), any(), any()) }
    }

    @Test
    fun `widget can clear completed parent subtree by toggling`() = runTest {
        val todos = listOf(
            createTodo("1", "Parent", completed = true, completedAt = "2026-04-26T09:00:00Z"),
            createTodo("2", "Child", parentId = "1", completed = true, completedAt = "2026-04-26T09:00:00Z"),
        )
        coEvery { todoDao.getAll() } returns todos
        coEvery { todoDao.getById("1") } returns todos.first().copy(completed = false)

        repository.toggleTodoFromWidget("1")

        coVerify(exactly = 1) {
            todoDao.updateCompletions(
                match { ids -> ids.toSet() == setOf("1", "2") },
                false,
                null,
                any(),
            )
        }
    }

    @Test
    fun `toggleTodo should clear completed parent subtree in one batch`() = runTest {
        val todos = listOf(
            createTodo("1", "Parent", completed = true),
            createTodo("2", "Child", parentId = "1", completed = true),
            createTodo("3", "Grandchild", parentId = "2", completed = true),
        )
        coEvery { todoDao.getAll() } returns todos

        repository.toggleTodo("1", refreshWidgets = false)

        coVerify(exactly = 1) {
            todoDao.updateCompletions(
                match { ids -> ids.toSet() == setOf("1", "2", "3") },
                false,
                null,
                any(),
            )
        }
        coVerify(exactly = 0) { todoDao.updateCompletion(any(), any(), any(), any()) }
    }

    @Test
    fun `weekdays repeat todo stays completed until next monday`() {
        val monday = LocalDate.parse("2026-04-27")
        val tuesday = LocalDate.parse("2026-04-28")
        val nextMonday = LocalDate.parse("2026-05-04")
        val todo = createTodo(
            id = "1",
            content = "Weekly once",
            repeatMode = RepeatMode.WEEKDAYS.value,
            completed = true,
            completedAt = "2026-04-27T09:00:00Z",
        )

        val tuesdayResult = repository.buildTodayTodoData(listOf(todo), tuesday)
        val nextMondayResult = repository.buildTodayTodoData(listOf(todo), nextMonday)

        assertTrue(repository.buildTodayTodoData(listOf(todo), monday).tree.first().todo.completed)
        assertTrue(tuesdayResult.tree.first().todo.completed)
        assertFalse(nextMondayResult.tree.first().todo.completed)
    }

    @Test
    fun `monthly repeat todo stays completed until next month`() {
        val firstDay = LocalDate.parse("2026-05-01")
        val middleDay = LocalDate.parse("2026-05-20")
        val nextMonth = LocalDate.parse("2026-06-01")
        val todo = createTodo(
            id = "1",
            content = "Monthly once",
            repeatMode = RepeatMode.MONTHLY.value,
            completed = true,
            completedAt = "2026-05-01T09:00:00Z",
        )

        val middleDayResult = repository.buildTodayTodoData(listOf(todo), middleDay)
        val nextMonthResult = repository.buildTodayTodoData(listOf(todo), nextMonth)

        assertTrue(repository.buildTodayTodoData(listOf(todo), firstDay).tree.first().todo.completed)
        assertTrue(middleDayResult.tree.first().todo.completed)
        assertFalse(nextMonthResult.tree.first().todo.completed)
    }

    @Test
    fun `repeat completion accepts legacy local datetime values`() {
        val todo = createTodo(
            id = "1",
            content = "Legacy repeat task",
            repeatMode = RepeatMode.DAILY.value,
            completed = true,
            completedAt = "2026-04-26T09:00:00",
        )

        val result = repository.buildTodayTodoData(listOf(todo), LocalDate.parse("2026-04-26"))

        assertTrue(result.tree.first().todo.completed)
    }

    @Test
    fun `buildTodayTodoData hides off-day interval repeat todos`() {
        val todo = createTodo(
            id = "1",
            content = "Every two days",
            repeatMode = RepeatMode.INTERVAL_DAYS.value,
            intervalDays = 2,
            anchorDate = "2026-05-05",
        )

        val result = repository.buildTodayTodoData(
            todos = listOf(todo),
            targetDate = LocalDate.parse("2026-05-06"),
        )

        assertTrue(result.tree.isEmpty())
        assertEquals(0, result.totalCount)
    }

    @Test
    fun `getTodayDataSnapshot uses recurrence candidates for widget data`() = runTest {
        val todo = createTodo("1", "Widget task")
        coEvery { todoDao.getRecurrenceCandidates("2026-04-26") } returns listOf(todo)

        val result = repository.getTodayDataSnapshot()

        assertEquals(1, result.totalCount)
        assertEquals("1", result.tree.first().todo.id)
        coVerify(exactly = 1) { todoDao.getRecurrenceCandidates("2026-04-26") }
        coVerify(exactly = 0) { todoDao.getAll() }
    }

    @Test
    fun `observeToday refreshes when calendar day changes`() = runTest {
        val clock = MutableClock(
            currentInstant = Instant.parse("2026-05-05T23:59:58Z"),
            currentZone = ZoneOffset.UTC,
        )
        val todosFlow = MutableStateFlow(
            listOf(
                createTodo(
                    id = "1",
                    content = "Every two days",
                    repeatMode = RepeatMode.INTERVAL_DAYS.value,
                    intervalDays = 2,
                    anchorDate = "2026-05-05",
                ),
            ),
        )
        val todoDao = mockk<TodoDao>()
        every { todoDao.observeAll() } returns todosFlow
        val dateAwareRepository = TodoRepository(todoDao, clock, WidgetRefresher {})
        val emissions = mutableListOf<TodayTodoData>()

        backgroundScope.launch {
            dateAwareRepository.observeToday().take(2).toList(emissions)
        }

        runCurrent()
        assertEquals(1, emissions.size)
        assertEquals(LocalDate.parse("2026-05-05"), emissions[0].date)
        assertEquals(1, emissions[0].totalCount)

        clock.setInstant(Instant.parse("2026-05-06T00:00:01Z"))
        advanceTimeBy(Duration.ofSeconds(2).toMillis())
        runCurrent()

        assertEquals(2, emissions.size)
        assertEquals(LocalDate.parse("2026-05-06"), emissions[1].date)
        assertEquals(0, emissions[1].totalCount)
    }

    @Test
    fun `importTodosJson should replace all todos from backup`() = runTest {
        var refreshed = false
        val importRepository = TodoRepository(
            todoDao = todoDao,
            clock = fixedClock,
            widgetRefresher = WidgetRefresher { refreshed = true },
        )
        val json = """
            {
              "schemaVersion": 1,
              "todos": [
                {
                  "id": "todo-1",
                  "content": "Restore me",
                  "parentId": null,
                  "sortOrder": 2,
                  "repeatMode": "monthly",
                  "weekdays": null,
                  "intervalDays": null,
                  "specificDates": null,
                  "anchorDate": null,
                  "expiryDate": null,
                  "completed": true,
                  "completedAt": "2026-05-01T09:00:00Z",
                  "disabled": false,
                  "expanded": true,
                  "createdAt": 100,
                  "updatedAt": 200
                }
              ]
            }
        """.trimIndent()

        importRepository.importTodosJson(json)

        coVerify(exactly = 1) {
            todoDao.replaceAll(
                match { todos ->
                    todos.size == 1 &&
                        todos.first().id == "todo-1" &&
                        todos.first().content == "Restore me" &&
                        todos.first().repeatMode == RepeatMode.MONTHLY.value &&
                        todos.first().completed
                },
            )
        }
        assertTrue(refreshed)
    }

    private fun createTodo(
        id: String,
        content: String,
        parentId: String? = null,
        repeatMode: String = RepeatMode.NONE.value,
        completed: Boolean = false,
        completedAt: String? = null,
        disabled: Boolean = false,
        expiryDate: String? = null,
        intervalDays: Int? = null,
        anchorDate: String? = null,
    ) = TodoEntity(
        id = id,
        content = content,
        parentId = parentId,
        repeatMode = repeatMode,
        intervalDays = intervalDays,
        anchorDate = anchorDate,
        completed = completed,
        completedAt = completedAt,
        disabled = disabled,
        expiryDate = expiryDate,
        createdAt = 0,
        updatedAt = 0,
    )

    private class MutableClock(
        private var currentInstant: Instant,
        private val currentZone: ZoneId,
    ) : Clock() {
        override fun getZone(): ZoneId = currentZone

        override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)

        override fun instant(): Instant = currentInstant

        fun setInstant(newInstant: Instant) {
            currentInstant = newInstant
        }
    }
}
