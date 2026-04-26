package com.mytodo.android.data

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
) {
    fun observeToday(): Flow<TodayTodoData> =
        todoDao.observeAll().map { todos ->
            val today = LocalDate.now(clock)
            buildTodayTodoData(todos = todos, targetDate = today)
        }

    suspend fun toggleTodo(todoId: String) {
        val todo = todoDao.getById(todoId) ?: return
        updateSingleTodoCompletion(todo = todo, completed = !todo.completed)
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

    private fun nowTimestamp(): Long = clock.millis()

    private fun nowDateTimeString(): String = OffsetDateTime.now(clock).toString()
}

data class TodayTodoData(
    val date: LocalDate,
    val tree: List<TodayTodoNode>,
    val totalCount: Int,
    val completedCount: Int,
    val progress: Float,
)

data class TodayTodoNode(
    val todo: TodoEntity,
    val children: List<TodayTodoNode>,
) {
    val hasChildren: Boolean = children.isNotEmpty()
}
