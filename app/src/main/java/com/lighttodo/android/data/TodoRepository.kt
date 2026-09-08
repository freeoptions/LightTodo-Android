package com.lighttodo.android.data

import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject

data class WidgetToggleResult(
    val todoId: String,
    val found: Boolean,
    val completedBefore: Boolean? = null,
    val requestedCompleted: Boolean? = null,
    val operation: String? = null,
    val persistedCompleted: Boolean? = null,
)

@Singleton
class TodoRepository @Inject constructor(
    private val todoDao: TodoDao,
    private val clock: Clock,
    private val widgetRefresher: WidgetRefresher,
) {
    fun observeToday(): Flow<TodayTodoData> =
        combine(
            todoDao.observeAll(),
            observeCurrentDate(),
        ) { todos, today ->
            buildTodayTodoData(todos = todos, targetDate = today)
        }

    suspend fun getTodayDataSnapshot(): TodayTodoData {
        val targetDate = LocalDate.now(clock)
        val candidates = todoDao.getRecurrenceCandidates(targetDate.toString())
        return buildTodayTodoData(todos = candidates, targetDate = targetDate)
    }

    suspend fun refreshWidgets() {
        widgetRefresher.refreshAll()
    }

    suspend fun addTodo(
        content: String,
        repeatMode: RepeatMode = RepeatMode.DAILY,
        weekdays: List<Int> = emptyList(),
        intervalDays: Int? = null,
        anchorDate: LocalDate? = null,
        specificDates: List<LocalDate> = emptyList(),
    ) {
        val allTodos = todoDao.getAll()
        val now = nowTimestamp()
        val rootSortOrder = nextSortOrder(parentId = null, todos = allTodos)
        val todo = TodoEntity(
            id = java.util.UUID.randomUUID().toString(),
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
        todoDao.upsert(todo)
        widgetRefresher.refreshAll()
    }

    suspend fun updateTodo(
        todoId: String,
        content: String,
        repeatMode: RepeatMode,
        weekdays: List<Int> = emptyList(),
        intervalDays: Int? = null,
        anchorDate: LocalDate? = null,
        specificDates: List<LocalDate> = emptyList(),
    ) {
        val current = todoDao.getById(todoId) ?: return
        val updated = current.copy(
            content = content,
            repeatMode = repeatMode.value,
            weekdays = weekdays.takeIf { it.isNotEmpty() }?.joinToString(","),
            intervalDays = intervalDays,
            specificDates = specificDates.takeIf { it.isNotEmpty() }?.joinToString(",") { it.toString() },
            anchorDate = anchorDate?.toString(),
            updatedAt = nowTimestamp(),
        )

        todoDao.update(updated)
        widgetRefresher.refreshAll()
    }

    suspend fun deleteTodo(todoId: String) {
        val allTodos = todoDao.getAll()
        allTodos.firstOrNull { it.id == todoId } ?: return
        val descendantsByParent = allTodos.groupBy { it.parentId }
        collectSubtreeIds(rootId = todoId, descendantsByParent = descendantsByParent)
            .forEach { id -> todoDao.deleteById(id) }
        widgetRefresher.refreshAll()
    }

    suspend fun exportTodosJson(): String {
        val todos = todoDao.getAll()
        val items = JSONArray()
        todos.forEach { todo ->
            items.put(todo.toJsonObject())
        }

        return JSONObject()
            .put("schemaVersion", 1)
            .put("exportedAt", nowDateTimeString())
            .put("todos", items)
            .toString(2)
    }

    suspend fun importTodosJson(json: String) {
        val root = runCatching { JSONObject(json) }
            .getOrElse { error -> throw IllegalArgumentException("导入文件不是有效的 JSON：${error.message}", error) }
        val items = root.optJSONArray("todos")
            ?: throw IllegalArgumentException("导入文件缺少 todos 数据")
        val importedTodos = buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index)
                    ?: throw IllegalArgumentException("第 ${index + 1} 条待办格式无效")
                add(item.toTodoEntity(index))
            }
        }

        todoDao.replaceAll(importedTodos)
        widgetRefresher.refreshAll()
    }

    suspend fun toggleTodo(todoId: String, refreshWidgets: Boolean = true) {
        val targetDate = LocalDate.now(clock)
        val allTodos = todoDao.getAll()
        toggleTodoWithSnapshot(
            todoId = todoId,
            allTodos = allTodos,
            targetDate = targetDate,
        )
        if (refreshWidgets) {
            widgetRefresher.refreshAll()
        }
    }

    /**
     * Toggles a todo from a widget action using the current database state.
     *
     * The widget deliberately sends only the business id. This keeps the action independent of
     * launcher-side optimistic view state and reuses the same subtree/ancestor rules as the app.
     */
    suspend fun toggleTodoFromWidget(todoId: String): WidgetToggleResult {
        val targetDate = LocalDate.now(clock)
        val allTodos = todoDao.getAll()
        val todo = allTodos.firstOrNull { it.id == todoId }
            ?: return WidgetToggleResult(todoId = todoId, found = false)
        val todoById = allTodos.associateBy { it.id }
        val descendantsByParent = allTodos.groupBy { it.parentId }
        val currentlyCompleted = todo.isCompletedForDate(
            targetDate = targetDate,
            repeatMode = todo.completionRepeatMode(todoById),
        )
        val targetCompleted = !currentlyCompleted
        val hasChildren = descendantsByParent[todo.id].orEmpty().isNotEmpty()
        val hasUnfinishedChildren = targetCompleted && hasChildren &&
            hasUnfinishedDescendants(todo.id, descendantsByParent, todoById, targetDate)
        val operation = if (hasChildren && targetCompleted && hasUnfinishedChildren) {
            "complete_subtree"
        } else {
            "toggle"
        }

        if (
            hasChildren &&
            targetCompleted &&
            hasUnfinishedChildren
        ) {
            completeParentSubtreeWithSnapshot(
                parentId = todoId,
                allTodos = allTodos,
                descendantsByParent = descendantsByParent,
            )
        } else {
            toggleTodoWithSnapshot(
                todoId = todoId,
                allTodos = allTodos,
                targetDate = targetDate,
                targetCompleted = targetCompleted,
            )
        }
        val persistedTodo = todoDao.getById(todoId)
        return WidgetToggleResult(
            todoId = todoId,
            found = true,
            completedBefore = currentlyCompleted,
            requestedCompleted = targetCompleted,
            operation = operation,
            persistedCompleted = persistedTodo?.completed,
        )
    }

    suspend fun completeParentSubtree(parentId: String, refreshWidgets: Boolean = true) {
        val allTodos = todoDao.getAll()
        completeParentSubtreeWithSnapshot(
            parentId = parentId,
            allTodos = allTodos,
            descendantsByParent = allTodos.groupBy { it.parentId },
        )
        if (refreshWidgets) {
            widgetRefresher.refreshAll()
        }
    }

    suspend fun hasChildren(todoId: String): Boolean =
        todoDao.getAll().any { it.parentId == todoId }

    internal fun buildTodayTodoData(
        todos: List<TodoEntity>,
        targetDate: LocalDate,
    ): TodayTodoData {
        val visibleRawTodos = todos.filter { it.isScheduledOn(targetDate) }
        val visibleIds = visibleRawTodos.map { it.id }.toSet()
        val todosById = todos.associateBy { it.id }
        val normalizedTodos = visibleRawTodos.map { todo ->
            val completionRepeatMode = todo.completionRepeatMode(todosById)
            val completedForDate = todo.isCompletedForDate(targetDate, completionRepeatMode)
            todo.copy(
                completed = completedForDate,
                completedAt = if (completedForDate) todo.completedAt else null,
            )
        }
        val nodesByParent = normalizedTodos
            .filter { it.parentId == null || it.parentId in visibleIds }
            .groupBy { it.parentId }

        val tree = nodesByParent[null]
            .orEmpty()
            .map { todo -> todo.toTreeNode(nodesByParent).deriveParentCompletion() }

        val progressItems = collectProgressItems(tree)
        val totalCount = progressItems.size
        val completedCount = progressItems.count { it.todo.completed }

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

    private suspend fun updateCompletionForDate(todo: TodoEntity, completed: Boolean) {
        todoDao.updateCompletion(
            id = todo.id,
            completed = completed,
            completedAt = if (completed) nowDateTimeString() else null,
            updatedAt = nowTimestamp(),
        )
    }

    private suspend fun updateCompletionForIds(
        ids: Collection<String>,
        completed: Boolean,
        completedAt: String?,
        updatedAt: Long,
    ) {
        if (ids.isEmpty()) {
            return
        }

        todoDao.updateCompletions(
            ids = ids.toList(),
            completed = completed,
            completedAt = completedAt,
            updatedAt = updatedAt,
        )
    }

    private suspend fun toggleTodoWithSnapshot(
        todoId: String,
        allTodos: List<TodoEntity>,
        targetDate: LocalDate,
        targetCompleted: Boolean? = null,
    ) {
        val todo = allTodos.firstOrNull { it.id == todoId } ?: return
        val descendantsByParent = allTodos.groupBy { it.parentId }
        val todoById = allTodos.associateBy { it.id }
        val completionStateById = allTodos.associate { current ->
            current.id to current.isCompletedForDate(targetDate, current.completionRepeatMode(todoById))
        }.toMutableMap()
        val hasChildren = descendantsByParent[todo.id].orEmpty().isNotEmpty()
        val currentlyCompleted = completionStateById[todo.id] == true
        val newCompleted = targetCompleted ?: !currentlyCompleted

        if (newCompleted == currentlyCompleted) {
            return
        }

        if (hasChildren && !newCompleted) {
            val subtreeIds = collectSubtreeIds(rootId = todo.id, descendantsByParent = descendantsByParent)
            val updatedAt = nowTimestamp()
            updateCompletionForIds(
                ids = subtreeIds,
                completed = false,
                completedAt = null,
                updatedAt = updatedAt,
            )
            subtreeIds.forEach { id -> completionStateById[id] = false }
        } else {
            updateCompletionForDate(todo = todo, completed = newCompleted)
            completionStateById[todo.id] = newCompleted
        }

        syncAncestors(
            parentId = todo.parentId,
            targetDate = targetDate,
            todoById = todoById,
            descendantsByParent = descendantsByParent,
            completionStateById = completionStateById,
        )
    }

    private suspend fun completeParentSubtreeWithSnapshot(
        parentId: String,
        allTodos: List<TodoEntity>,
        descendantsByParent: Map<String?, List<TodoEntity>>,
    ) {
        allTodos.firstOrNull { it.id == parentId } ?: return
        val completedAt = nowDateTimeString()
        val updatedAt = nowTimestamp()
        val subtreeIds = collectSubtreeIds(rootId = parentId, descendantsByParent = descendantsByParent)

        updateCompletionForIds(
            ids = subtreeIds,
            completed = true,
            completedAt = completedAt,
            updatedAt = updatedAt,
        )
    }

    private suspend fun syncAncestors(
        parentId: String?,
        targetDate: LocalDate,
        todoById: Map<String, TodoEntity>,
        descendantsByParent: Map<String?, List<TodoEntity>>,
        completionStateById: MutableMap<String, Boolean>,
    ) {
        var currentParentId = parentId
        while (currentParentId != null) {
            val parent = todoById[currentParentId] ?: return
            val children = descendantsByParent[currentParentId].orEmpty()
            val allChildrenDone = children.isNotEmpty() && children.all { child ->
                child.disabled || completionStateById[child.id] == true
            }
            val parentCompleted = completionStateById[parent.id]
                ?: parent.isCompletedForDate(
                    targetDate = targetDate,
                    repeatMode = parent.completionRepeatMode(todoById),
                )

            if (parentCompleted != allChildrenDone) {
                updateCompletionForDate(todo = parent, completed = allChildrenDone)
                completionStateById[parent.id] = allChildrenDone
            }

            currentParentId = parent.parentId
        }
    }

    private fun hasUnfinishedDescendants(
        rootId: String,
        descendantsByParent: Map<String?, List<TodoEntity>>,
        todoById: Map<String, TodoEntity>,
        targetDate: LocalDate,
    ): Boolean {
        val stack = ArrayDeque<TodoEntity>()
        descendantsByParent[rootId]
            .orEmpty()
            .asReversed()
            .forEach(stack::addLast)

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val completed = current.isCompletedForDate(
                targetDate = targetDate,
                repeatMode = current.completionRepeatMode(todoById),
            )
            if (!completed) {
                return true
            }

            descendantsByParent[current.id]
                .orEmpty()
                .asReversed()
                .forEach(stack::addLast)
        }

        return false
    }

    private fun TodoEntity.toTreeNode(
        nodesByParent: Map<String?, List<TodoEntity>>,
    ): TodayTodoNode =
        TodayTodoNode(
            todo = this,
            children = nodesByParent[id].orEmpty().map { child -> child.toTreeNode(nodesByParent) },
        )

    private fun TodayTodoNode.deriveParentCompletion(): TodayTodoNode {
        val normalizedChildren = children.map { it.deriveParentCompletion() }
        val normalizedTodo = if (normalizedChildren.isNotEmpty()) {
            todo.copy(completed = normalizedChildren.all { it.todo.completed || it.todo.disabled })
        } else {
            todo
        }

        return copy(todo = normalizedTodo, children = normalizedChildren)
    }

    private fun collectProgressItems(nodes: List<TodayTodoNode>): List<TodayTodoNode> =
        buildList {
            fun visit(node: TodayTodoNode) {
                if (node.children.isEmpty()) {
                    add(node)
                } else {
                    node.children.forEach(::visit)
                }
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

    private fun observeCurrentDate(): Flow<LocalDate> =
        flow {
            while (true) {
                val currentDate = LocalDate.now(clock)
                emit(currentDate)

                val now = clock.instant()
                val nextDayStart = currentDate
                    .plusDays(1)
                    .atStartOfDay(clock.zone)
                    .toInstant()
                val delayMillis = Duration.between(now, nextDayStart)
                    .toMillis()
                    .coerceIn(1L, CurrentDatePollIntervalMillis)
                delay(delayMillis)
            }
        }
            .distinctUntilChanged()
}

private const val CurrentDatePollIntervalMillis = 60_000L

private fun TodoEntity.toJsonObject(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("content", content)
        .putNullable("parentId", parentId)
        .put("sortOrder", sortOrder)
        .put("repeatMode", repeatMode)
        .putNullable("weekdays", weekdays)
        .putNullable("intervalDays", intervalDays)
        .putNullable("specificDates", specificDates)
        .putNullable("anchorDate", anchorDate)
        .putNullable("expiryDate", expiryDate)
        .put("completed", completed)
        .putNullable("completedAt", completedAt)
        .put("disabled", disabled)
        .put("expanded", expanded)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
    put(name, value ?: JSONObject.NULL)

private fun JSONObject.toTodoEntity(index: Int): TodoEntity {
    val id = optString("id").takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("第 ${index + 1} 条待办缺少 id")
    val content = optString("content").takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("第 ${index + 1} 条待办缺少内容")
    val createdAt = optLong("createdAt", System.currentTimeMillis())
    val updatedAt = optLong("updatedAt", createdAt)

    return TodoEntity(
        id = id,
        content = content,
        parentId = optNullableString("parentId"),
        sortOrder = optInt("sortOrder", 0),
        repeatMode = optString("repeatMode").takeIf { it.isNotBlank() } ?: RepeatMode.NONE.value,
        weekdays = optNullableString("weekdays"),
        intervalDays = optNullableInt("intervalDays"),
        specificDates = optNullableString("specificDates"),
        anchorDate = optNullableString("anchorDate"),
        expiryDate = optNullableString("expiryDate"),
        completed = optBoolean("completed", false),
        completedAt = optNullableString("completedAt"),
        disabled = optBoolean("disabled", false),
        expanded = optBoolean("expanded", true),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

private fun JSONObject.optNullableString(name: String): String? =
    opt(name)?.takeUnless { it == JSONObject.NULL }?.toString()?.takeIf { it.isNotBlank() }

private fun JSONObject.optNullableInt(name: String): Int? =
    opt(name)?.takeUnless { it == JSONObject.NULL }?.toString()?.toIntOrNull()

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

private fun TodoEntity.completionRepeatMode(todosById: Map<String, TodoEntity>): RepeatMode {
    var current: TodoEntity? = this

    while (current != null) {
        val mode = RepeatMode.fromValue(current.repeatMode)
        if (mode != RepeatMode.NONE) {
            return mode
        }
        current = current.parentId?.let(todosById::get)
    }

    return RepeatMode.NONE
}

private fun TodoEntity.isCompletedForDate(targetDate: LocalDate, repeatMode: RepeatMode): Boolean {
    if (repeatMode == RepeatMode.NONE) {
        return completed
    }

    val completionDate = completedAt?.toCompletionDateOrNull()

    if (!completed || completionDate == null) {
        return false
    }

    return when (repeatMode) {
        RepeatMode.WEEKDAYS -> completionDate.isInSameWeekAs(targetDate)
        RepeatMode.MONTHLY -> completionDate.isInSameMonthAs(targetDate)
        RepeatMode.NONE -> completed
        else -> completionDate == targetDate
    }
}

private fun String.toCompletionDateOrNull(): LocalDate? =
    runCatching { OffsetDateTime.parse(this).toLocalDate() }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(this).toLocalDate() }.getOrNull()
        ?: runCatching { LocalDate.parse(this) }.getOrNull()

private fun LocalDate.isInSameWeekAs(other: LocalDate): Boolean =
    with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) ==
        other.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private fun LocalDate.isInSameMonthAs(other: LocalDate): Boolean =
    year == other.year && monthValue == other.monthValue

fun interface WidgetRefresher {
    suspend fun refreshAll()
}
