package com.lighttodo.android.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Build
import android.appwidget.AppWidgetManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.ToggleableStateKey
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider as glanceColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lighttodo.android.MainActivity as AppMainActivity
import com.lighttodo.android.R
import com.lighttodo.android.data.RepeatMode
import com.lighttodo.android.data.TodayTodoNode
import com.lighttodo.android.data.TodoRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val TodoIdKey = ActionParameters.Key<String>("todo_id")
private val WidgetBlue = glanceColorProvider(day = Color(0xFF2563EB), night = Color(0xFF2563EB))
private val WidgetBackground = glanceColorProvider(day = Color(0xFFF8FAFC), night = Color(0xFF0F172A))
private val RowBackground = glanceColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFFFFFFFF))
private val CompletedRowBackground = glanceColorProvider(day = Color(0xFFECFDF5), night = Color(0xFFDCFCE7))
private val CelebratingRowBackground = glanceColorProvider(day = Color(0xFFFFF7ED), night = Color(0xFFFFEDD5))
private val WeekdaysBadgeBackground = glanceColorProvider(day = Color(0xFFFFF7ED), night = Color(0xFFFFEDD5))
private val WeekdaysBadgeText = glanceColorProvider(day = Color(0xFFC2410C), night = Color(0xFFC2410C))
private val MonthlyBadgeBackground = glanceColorProvider(day = Color(0xFFFCE7F3), night = Color(0xFFFBCFE8))
private val MonthlyBadgeText = glanceColorProvider(day = Color(0xFFBE185D), night = Color(0xFFBE185D))
private val CompletedBadgeBackground = glanceColorProvider(day = Color(0xFFDCFCE7), night = Color(0xFFDCFCE7))
private val CelebrationBadgeBackground = glanceColorProvider(day = Color(0xFFFFEDD5), night = Color(0xFFFFEDD5))
private val CelebrationText = glanceColorProvider(day = Color(0xFF9A3412), night = Color(0xFF9A3412))
private val PrimaryText = glanceColorProvider(day = Color(0xFF0F172A), night = Color(0xFF0F172A))
private val CompletedText = glanceColorProvider(day = Color(0xFF166534), night = Color(0xFF166534))
private val OnAccentText = glanceColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFFFFFFFF))
private val CheckBoxTouchSize = 36.dp
private val WidgetActionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class TodoWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadWidgetSnapshot(context.applicationContext)

        provideContent {
            TodoWidgetContent(
                rows = snapshot.rows,
                progressLabel = snapshot.progressLabel,
            )
        }
    }
}

private suspend fun loadWidgetSnapshot(context: Context): TodoWidgetSnapshot {
    val cachedSnapshot = TodoWidgetCache.read(context)
    val shouldReload = cachedSnapshot == null || TodoWidgetCache.isReloadRequested(context)
    if (!shouldReload) {
        return cachedSnapshot
    }

    val freshSnapshot = runCatching {
        val entryPoint = EntryPointAccessors.fromApplication(context, TodoWidgetEntryPoint::class.java)
        val todoRepository = entryPoint.todoRepository()
        withContext(Dispatchers.IO) { todoRepository.getTodayDataSnapshot() }
    }.map { todayData ->
        TodoWidgetSnapshot(
            rows = buildWidgetRowsFromNodes(todayData.tree),
            progressLabel = "${todayData.completedCount}/${todayData.totalCount}",
        ).withCelebration(TodoWidgetCache.activeCelebrationTodoId(context))
    }.getOrNull()

    if (freshSnapshot != null) {
        TodoWidgetCache.write(context, freshSnapshot)
        TodoWidgetCache.setReloadRequested(context, false)
        return freshSnapshot
    }

    return cachedSnapshot ?: TodoWidgetSnapshot.Empty
}

@Composable
private fun TodoWidgetContent(
    rows: List<TodoWidgetRow>,
    progressLabel: String,
) {
    val context = LocalContext.current
    val sortedRows = rows.sortedWith(
        compareBy<TodoWidgetRow> { it.completed && !it.celebrating }
            .thenBy { it.sortIndex },
    )

    LazyColumn(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(24.dp)
            .background(WidgetBackground),
    ) {
        item(itemId = -3L) {
            WidgetHeader(
                title = context.getString(R.string.widget_today),
                progressText = context.getString(R.string.widget_progress, progressLabel),
            )
        }

        if (rows.isEmpty()) {
            item(itemId = -4L) {
                Text(
                    text = context.getString(R.string.widget_empty),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(actionRunCallback<ConsumeWidgetTouchAction>())
                        .padding(16.dp),
                )
            }
        } else {
            items(
                items = sortedRows,
                itemId = { row -> row.stableItemId() },
            ) { row ->
                TodoWidgetRowItem(row = row)
            }
        }
    }
}

@Composable
private fun WidgetHeader(
    title: String,
    progressText: String,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(appLaunchIntent(LocalContext.current)))
            .background(WidgetBlue)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = title,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                color = OnAccentText,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = progressText,
            style = TextStyle(
                color = OnAccentText,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

private fun appLaunchIntent(context: Context): Intent =
    Intent(context, AppMainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

@Composable
private fun TodoWidgetRowItem(row: TodoWidgetRow) {
    val context = LocalContext.current

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionRunCallback<ConsumeWidgetTouchAction>())
            .background(
                when {
                    row.celebrating -> CelebratingRowBackground
                    row.completed -> CompletedRowBackground
                    else -> RowBackground
                },
            )
            .padding(start = (14 + row.depth * 12).dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start,
    ) {
        WidgetCheckBox(checked = row.completed, todoId = row.todoId)
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = row.title,
                style = TextStyle(
                    color = if (row.completed) CompletedText else PrimaryText,
                    fontWeight = if (row.completed || row.hasChildren) FontWeight.Medium else FontWeight.Normal,
                ),
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            WidgetRowMetadata(context = context, row = row)
        }
    }
}

@Composable
private fun WidgetCheckBox(checked: Boolean, todoId: String) {
    Box(
        modifier = GlanceModifier
            .width(CheckBoxTouchSize)
            .height(CheckBoxTouchSize),
        contentAlignment = Alignment.Center,
    ) {
        CheckBox(
            checked = checked,
            onCheckedChange =
                actionRunCallback<ToggleTodoAction>(
                    actionParametersOf(TodoIdKey to todoId),
                ),
            modifier = GlanceModifier
                .width(CheckBoxTouchSize)
                .height(CheckBoxTouchSize),
            text = "",
        )
    }
}

@Composable
private fun WidgetRowMetadata(context: Context, row: TodoWidgetRow) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start,
    ) {
        if (row.celebrating) {
            WidgetChip(
                text = context.getString(R.string.widget_completed_celebration),
                background = CelebrationBadgeBackground,
                color = CelebrationText,
            )
        } else if (row.completed) {
            WidgetChip(
                text = context.getString(R.string.widget_completed),
                background = CompletedBadgeBackground,
                color = CompletedText,
            )
        }

        if (row.repeatMode == RepeatMode.WEEKDAYS || row.repeatMode == RepeatMode.MONTHLY) {
            if (row.completed || row.celebrating) {
                Spacer(modifier = GlanceModifier.width(6.dp))
            }
            WidgetChip(
                text = context.getString(
                    if (row.repeatMode == RepeatMode.MONTHLY) {
                        R.string.todo_repeat_monthly_badge
                    } else {
                        R.string.todo_repeat_weekdays_badge
                    },
                ),
                background = if (row.repeatMode == RepeatMode.MONTHLY) {
                    MonthlyBadgeBackground
                } else {
                    WeekdaysBadgeBackground
                },
                color = if (row.repeatMode == RepeatMode.MONTHLY) {
                    MonthlyBadgeText
                } else {
                    WeekdaysBadgeText
                },
            )
        }
    }
}

@Composable
private fun WidgetChip(
    text: String,
    background: ColorProvider,
    color: ColorProvider,
) {
    Box(
        modifier = GlanceModifier
            .cornerRadius(8.dp)
            .background(background)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = color,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

class ToggleTodoAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val todoId = parameters[TodoIdKey] ?: return
        val appContext = context.applicationContext
        val checked = parameters[ToggleableStateKey]

        val optimisticallyUpdated = withContext(Dispatchers.IO) {
            TodoWidgetCache.toggleOptimistically(
                context = appContext,
                todoId = todoId,
                checked = checked,
            )
        }
        if (optimisticallyUpdated) {
            TodoWidget().update(appContext, glanceId)
        }

        WidgetActionScope.launch {
            runCatching {
                val entryPoint = EntryPointAccessors.fromApplication(appContext, TodoWidgetEntryPoint::class.java)
                val todoRepository = entryPoint.todoRepository()
                todoRepository.toggleTodoFromWidget(todoId)
            }
            TodoWidgetUpdater.refreshAfterInteraction(
                context = appContext,
                glanceId = glanceId,
                forceDataReload = true,
            )

            delay(WidgetCelebrationDurationMs)
            if (TodoWidgetCache.clearCelebration(appContext, todoId)) {
                TodoWidget().update(appContext, glanceId)
            }
        }
    }
}

class ConsumeWidgetTouchAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) = Unit
}

class TodoWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in WidgetRefreshActions) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                TodoWidgetUpdater.refreshAll(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleNextDailyRefresh(context.applicationContext)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        scheduleNextDailyRefresh(context.applicationContext)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}

internal data class TodoWidgetRow(
    val todoId: String,
    val title: String,
    val completed: Boolean,
    val hasChildren: Boolean,
    val depth: Int,
    val repeatMode: RepeatMode,
    val sortIndex: Int,
    val celebrating: Boolean = false,
)

internal data class TodoWidgetSnapshot(
    val rows: List<TodoWidgetRow>,
    val progressLabel: String,
) {
    fun withCelebration(todoId: String?): TodoWidgetSnapshot =
        if (todoId == null) {
            this
        } else {
            copy(rows = rows.map { row -> row.copy(celebrating = row.todoId == todoId) })
        }

    companion object {
        val Empty = TodoWidgetSnapshot(rows = emptyList(), progressLabel = "0/0")
    }
}

private fun TodoWidgetRow.stableItemId(): Long =
    todoId.hashCode().toLong() + Int.MAX_VALUE.toLong() + 1L

internal fun buildWidgetRowsFromNodes(
    nodes: List<TodayTodoNode>,
    depth: Int = 0,
    sortIndexStart: Int = 0,
): List<TodoWidgetRow> =
    buildList {
        var nextSortIndex = sortIndexStart
        nodes.forEach { node ->
            val rowSortIndex = nextSortIndex++
            add(
                TodoWidgetRow(
                    todoId = node.todo.id,
                    title = node.todo.content,
                    completed = node.todo.completed,
                    hasChildren = node.children.isNotEmpty(),
                    depth = depth,
                    repeatMode = RepeatMode.fromValue(node.todo.repeatMode),
                    sortIndex = rowSortIndex,
                ),
            )
            val childRows = buildWidgetRowsFromNodes(
                nodes = node.children,
                depth = depth + 1,
                sortIndexStart = nextSortIndex,
            )
            addAll(childRows)
            nextSortIndex += childRows.size
        }
    }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TodoWidgetEntryPoint {
    fun todoRepository(): TodoRepository
}

object TodoWidgetUpdater {
    suspend fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        scheduleNextDailyRefresh(appContext)
        TodoWidgetCache.setReloadRequested(appContext, true)
        repeat(WidgetRefreshRetryCount) { index ->
            runCatching {
                TodoWidget().updateAll(appContext)
            }
            if (index < WidgetRefreshRetryCount - 1) {
                delay(WidgetRefreshRetryDelayMs)
            }
        }
    }

    suspend fun refreshAfterInteraction(
        context: Context,
        glanceId: GlanceId,
        forceDataReload: Boolean = false,
    ) {
        val appContext = context.applicationContext
        if (forceDataReload) {
            TodoWidgetCache.setReloadRequested(appContext, true)
        }
        runCatching {
            TodoWidget().update(appContext, glanceId)
        }
    }
}

private fun scheduleNextDailyRefresh(context: Context) {
    val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
    val zoneId = ZoneId.systemDefault()
    val triggerAtMillis = LocalDate.now(zoneId)
        .plusDays(1)
        .atStartOfDay(zoneId)
        .plusMinutes(1)
        .toInstant()
        .toEpochMilli()
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        WidgetDailyRefreshRequestCode,
        Intent(context, TodoWidgetRefreshReceiver::class.java)
            .setAction(WidgetDailyRefreshAction)
            .setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }.getOrElse {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    } else {
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }
}

private val WidgetRefreshActions = setOf(
    WidgetDailyRefreshAction,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_BOOT_COMPLETED,
)
private const val WidgetDailyRefreshAction = "com.lighttodo.android.widget.action.DAILY_REFRESH"
private const val WidgetDailyRefreshRequestCode = 1001
private const val WidgetRefreshRetryCount = 2
private const val WidgetRefreshRetryDelayMs = 700L
private const val WidgetCelebrationDurationMs = 1500L

private object TodoWidgetCache {
    private const val PreferencesName = "lighttodo_widget_cache"
    private const val RowsKey = "rows"
    private const val ProgressLabelKey = "progress_label"
    private const val CelebrationTodoIdKey = "celebration_todo_id"
    private const val CelebrationExpiresAtKey = "celebration_expires_at"
    private const val ReloadRequestedKey = "reload_requested"

    fun read(context: Context): TodoWidgetSnapshot? {
        val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        val rowsJson = preferences.getString(RowsKey, null) ?: return null
        val rows = runCatching { rowsFromJson(rowsJson) }.getOrNull() ?: return null
        val progressLabel = preferences.getString(ProgressLabelKey, null) ?: progressLabelForRows(rows)
        return TodoWidgetSnapshot(rows = rows, progressLabel = progressLabel)
            .withCelebration(activeCelebrationTodoId(context))
    }

    fun write(context: Context, snapshot: TodoWidgetSnapshot) {
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(RowsKey, rowsToJson(snapshot.rows))
            .putString(ProgressLabelKey, snapshot.progressLabel)
            .commit()
    }

    fun toggleOptimistically(
        context: Context,
        todoId: String,
        checked: Boolean?,
    ): Boolean {
        val currentSnapshot = read(context) ?: return false
        val newCompleted = checked ?: currentSnapshot.rows.firstOrNull { it.todoId == todoId }
            ?.completed
            ?.not()
            ?: return false
        val updatedRows = applyOptimisticToggle(
            rows = currentSnapshot.rows,
            todoId = todoId,
            completed = newCompleted,
        ) ?: return false
        val celebrationTodoId = todoId.takeIf { newCompleted }
        val celebrationExpiresAt = if (newCompleted) {
            System.currentTimeMillis() + WidgetCelebrationDurationMs
        } else {
            0L
        }

        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(RowsKey, rowsToJson(updatedRows))
            .putString(ProgressLabelKey, progressLabelForRows(updatedRows))
            .putString(CelebrationTodoIdKey, celebrationTodoId)
            .putLong(CelebrationExpiresAtKey, celebrationExpiresAt)
            .putBoolean(ReloadRequestedKey, false)
            .commit()

        return true
    }

    fun clearCelebration(context: Context, todoId: String): Boolean {
        val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        if (preferences.getString(CelebrationTodoIdKey, null) != todoId) {
            return false
        }

        preferences.edit()
            .remove(CelebrationTodoIdKey)
            .remove(CelebrationExpiresAtKey)
            .commit()
        return true
    }

    fun activeCelebrationTodoId(context: Context): String? {
        val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        val todoId = preferences.getString(CelebrationTodoIdKey, null) ?: return null
        val expiresAt = preferences.getLong(CelebrationExpiresAtKey, 0L)
        return todoId.takeIf { expiresAt > System.currentTimeMillis() }
    }

    fun isReloadRequested(context: Context): Boolean =
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .getBoolean(ReloadRequestedKey, false)

    fun setReloadRequested(context: Context, requested: Boolean) {
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ReloadRequestedKey, requested)
            .commit()
    }

    private fun rowsToJson(rows: List<TodoWidgetRow>): String =
        JSONArray().apply {
            rows.forEach { row ->
                put(
                    JSONObject()
                        .put("todoId", row.todoId)
                        .put("title", row.title)
                        .put("completed", row.completed)
                        .put("hasChildren", row.hasChildren)
                        .put("depth", row.depth)
                        .put("repeatMode", row.repeatMode.value)
                        .put("sortIndex", row.sortIndex),
                )
            }
        }.toString()

    private fun rowsFromJson(value: String): List<TodoWidgetRow> {
        val array = JSONArray(value)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    TodoWidgetRow(
                        todoId = item.getString("todoId"),
                        title = item.getString("title"),
                        completed = item.getBoolean("completed"),
                        hasChildren = item.getBoolean("hasChildren"),
                        depth = item.getInt("depth"),
                        repeatMode = RepeatMode.fromValue(item.getString("repeatMode")),
                        sortIndex = item.getInt("sortIndex"),
                    ),
                )
            }
        }
    }
}

internal fun applyOptimisticToggle(
    rows: List<TodoWidgetRow>,
    todoId: String,
    completed: Boolean,
): List<TodoWidgetRow>? {
    val targetIndex = rows.indexOfFirst { it.todoId == todoId }
    if (targetIndex < 0) {
        return null
    }

    val updatedRows = rows.toMutableList()
    val target = updatedRows[targetIndex]
    updatedRows[targetIndex] = target.copy(completed = completed, celebrating = completed)

    if (target.hasChildren) {
        var index = targetIndex + 1
        while (index < updatedRows.size && updatedRows[index].depth > target.depth) {
            updatedRows[index] = updatedRows[index].copy(completed = completed, celebrating = false)
            index++
        }
    }

    return normalizeParentCompletion(updatedRows)
}

internal fun progressLabelForRows(rows: List<TodoWidgetRow>): String {
    val leafRows = rows.filterNot { it.hasChildren }
    val totalCount = leafRows.size
    val completedCount = leafRows.count { it.completed }
    return "$completedCount/$totalCount"
}

private fun normalizeParentCompletion(rows: List<TodoWidgetRow>): List<TodoWidgetRow> {
    val updatedRows = rows.toMutableList()
    for (index in updatedRows.indices.reversed()) {
        val row = updatedRows[index]
        if (!row.hasChildren) {
            continue
        }

        val children = directChildrenOf(updatedRows, index)
        if (children.isNotEmpty()) {
            updatedRows[index] = row.copy(completed = children.all { child -> child.completed })
        }
    }
    return updatedRows
}

private fun directChildrenOf(rows: List<TodoWidgetRow>, parentIndex: Int): List<TodoWidgetRow> {
    val parentDepth = rows[parentIndex].depth
    val directChildDepth = parentDepth + 1
    val children = mutableListOf<TodoWidgetRow>()
    var index = parentIndex + 1
    while (index < rows.size && rows[index].depth > parentDepth) {
        if (rows[index].depth == directChildDepth) {
            children += rows[index]
        }
        index++
    }
    return children
}
