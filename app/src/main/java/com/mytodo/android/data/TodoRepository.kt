package com.mytodo.android.data

import android.content.Context
import com.mytodo.android.widget.TodoWidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class TodoRepository @Inject constructor(
    private val todoDao: TodoDao,
    private val clock: Clock,
    @ApplicationContext private val appContext: Context,
) {
    fun observeToday(): Flow<TodayTodoData> =
        todoDao.observeAll().map { todos ->
            val today = LocalDate.now(clock)
            buildTodayTodoData(todos = todos, targetDate = today)
        }

    suspend fun addTodo(
        content: String,
        repeatMode: RepeatMode = RepeatMode.NONE,
        weekdays: List<Int> = emptyList(),
        intervalDays: Int? = null,
        anchorDate: LocalDate? = null,
        specificDates: List<LocalDate> = emptyList(),
        subtasks: List<String> = emptyList(),
    ) {
        val allTodos = todoDao.getAll()
        val now = nowTimestamp()
        val parentId = java.util.UUID.randomUUID().toString()
        val rootSortOrder = nextSortOrder(parentId = null, todos = allTodos)
        val parentTodo = TodoEntity(
            id = parentId,
            content = content,
            parentId = null,
            sortOrder = rootSortOrder,
            repeatMode = repeatMode.value,
            weekdays = weekdays.takeIf { it.isNotEmpty() }?.joinToString(","),
            intervalDays = intervalDays,
            specificDates = specificDates.takeIf { it.isNotEmpty() }?.joinToString(",") { it.toString() },
            anchorDate = anchorDate?.toString(),
            createdAt = now,
            updatedAt = now,
        )
        val childTodos = subtasks.mapIndexed { index, title ->
            TodoEntity(
                id = java.util.UUID.randomUUID().toString(),
                content = title,
                parentId = parentId,
                sortOrder = index,
                repeatMode = RepeatMode.NONE.value,
                createdAt = now,
                updatedAt = now,
            )
        }
        todoDao.upsertAll(listOf(parentTodo) + childTodos)
        TodoWidgetUpdater.refreshAll(appContext)
    }

    suspend fun toggleTodo(todoId: String) {
        val todo = todoDao.getById(todoId) ?: return
        updateSingleTodoCompletion(todo = todo, completed = !todo.completed)
        TodoWidgetUpdater.refreshAll(appContext)
    }

    suspend fun completeParentSubtree(parentId: String) {
        val allTodos = todoDao.getAll()
        allTodos.firstOrNull { it.id == parentId } ?: return
        val now = nowTimestamp()
        val completedAt = nowDateTimeString()
        val descendantsByParent = allTodos.groupBy { it.parentId }
        val subtreeIds = collectSubtreeIds(rootId = parentId, descendantsByParent = descendantsByParent)

        subtreeIds.forEach { id ->
            val todo = allTodos.firstOrNull { it.id == id } ?: return@forEach
            if (!todo.completed) {
                todoDao.updateCompletion(
                    id = todo.id,
                    completed = true,
                    completedAt = completedAt,
                    updatedAt = now,
                )
            }
        }
        TodoWidgetUpdater.refreshAll(appContext)

    }

    suspend fun hasChildren(todoId: String): Boolean =
        todoDao.getAll().any { it.parentId == todoId }

    internal fun buildTodayTodoData(
        todos: List<TodoEntity>,
        targetDate: LocalDate,
    ): TodayTodoData {
        val visibleTodos = todos.filter { it.isScheduledOn(targetDate) }
        val visibleIds = visibleTodos.map { it.id }.toSet()
        val nodesByParent = visibleTodos
            .filter { it.parentId == null || it.parentId in visibleIds }
            .groupBy { it.parentId }

        val tree = nodesByParent[null]
            .orEmpty()
            .map { todo -> todo.toTreeNode(nodesByParent) }

        val flattened = flatten(tree)
        val totalCount = flattened.size
        val completedCount = flattened.count { it.todo.completed }

        return TodayTodoData(
            date = targetDate,
            tree = tree,
            totalCount = totalCount,
            completedCount = completedCount,
            progress = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount.toFloat(),
        )
    }

    internal fun collectSubtreeIds(
        rootId: String,
        descendantsByParent: Map<String?, List<TodoEntity>>,
    ): Set<String> {
        val result = linkedSetOf<String>()
        val stack = ArrayDeque<String>()
        stack.add(rootId)

        while (stack.isNotEmpty()) {
            val currentId = stack.removeLast()
            if (!result.add(currentId)) {
                continue
            }

            descendantsByParent[currentId]
                .orEmpty()
                .asReversed()
                .forEach { child -> stack.add(child.id) }
        }

        return result
    }

    private suspend fun updateSingleTodoCompletion(todo: TodoEntity, completed: Boolean) {
        val now = nowTimestamp()
        todoDao.updateCompletion(
            id = todo.id,
            completed = completed,
            completedAt = if (completed) nowDateTimeString() else null,
            updatedAt = now,
        )
    }

    private fun TodoEntity.toTreeNode(
        nodesByParent: Map<String?, List<TodoEntity>>,
    ): TodayTodoNode =
        TodayTodoNode(
            todo = this,
            children = nodesByParent[id].orEmpty().map { child -> child.toTreeNode(nodesByParent) },
        )

    private fun flatten(nodes: List<TodayTodoNode>): List<TodayTodoNode> =
        buildList {
            fun visit(node: TodayTodoNode) {
                add(node)
                node.children.forEach(::visit)
            }

            nodes.forEach(::visit)
        }

    private fun nextSortOrder(parentId: String?, todos: List<TodoEntity>): Int =
        todos
            .asSequence()
            .filter { it.parentId == parentId }
            .map { it.sortOrder }
            .maxOrNull()
            ?.plus(1)
            ?: 0

    private fun nowTimestamp(): Long = clock.millis()

    private fun nowDateTimeString(): String = OffsetDateTime.now(clock).toString()
}

data class TodayTodoData(
    val date: LocalDate,
    val tree: List<TodayTodoNode>,
    val totalCount: Int,
    val completedCount: Int,
    val progress: Float,
) {
    fun findNode(todoId: String): TodayTodoNode? {
        fun search(nodes: List<TodayTodoNode>): TodayTodoNode? {
            nodes.forEach { node ->
                if (node.todo.id == todoId) {
                    return node
                }

                val childResult = search(node.children)
                if (childResult != null) {
                    return childResult
                }
            }
            return null
        }

        return search(tree)
    }
}

data class TodayTodoNode(
    val todo: TodoEntity,
    val children: List<TodayTodoNode>,
) {
    val hasChildren: Boolean = children.isNotEmpty()

    fun unfinishedDescendantCount(): Int =
        children.sumOf { child ->
            val selfCount = if (child.todo.completed) 0 else 1
            selfCount + child.unfinishedDescendantCount()
        }
}
