package com.lighttodo.android.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.appwidget.AppWidgetManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
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
import com.lighttodo.android.data.TodayTodoData
import com.lighttodo.android.data.TodoRepository
import com.lighttodo.android.utils.LunarUtils
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import org.json.JSONArray
import org.json.JSONObject

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
private val WidgetMutationMutex = Mutex()
private val WidgetInteractionMutex = Mutex()
private val WidgetRefreshMutex = Mutex()
private val WidgetDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)
private val WidgetWeekdayFormatter = DateTimeFormatter.ofPattern("E", Locale.CHINA)
private const val WidgetHeaderHorizontalPaddingDp = 12f
private const val WidgetHeaderContentGapDp = 8f
private const val WidgetHeaderMinFontSp = 10f
private const val WidgetHeaderMaxFontSp = 18f
private const val WidgetCompletionSinkDelayMillis = 3_000L
private const val WidgetCompletionSinkExpiryAction = "com.lighttodo.android.widget.action.COMPLETION_SINK_EXPIRY"
private const val WidgetToggleTodoAction = "com.lighttodo.android.widget.action.TOGGLE_TODO"
private const val WidgetToggleTodoIdExtra = "todo_id"
private const val WidgetToggleTodoUriPrefix = "lighttodo://widget/toggle/"

class TodoWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val currentDate = LocalDate.now(ZoneId.systemDefault())
        Log.d(WidgetLogTag, "render:start glanceId=$id date=$currentDate")
        val repository = widgetRepository(appContext)
        val snapshot = loadWidgetSnapshot(appContext, currentDate, repository)
        Log.d(WidgetLogTag, "render:content glanceId=$id ${snapshot.logSummary()}")

        provideContent {
            var currentSnapshot by remember { mutableStateOf(snapshot) }
            var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }

            // Glance may keep this composition alive after updateAll() returns. Observe Room
            // inside the composition so a checkbox action changes the already-mounted widget,
            // instead of relying on provideGlance() being invoked again.
            repository?.let { todoRepository ->
                LaunchedEffect(todoRepository) {
                    Log.d(WidgetLogTag, "flow:start glanceId=$id")
                    todoRepository.observeToday()
                        .catch { error ->
                            Log.e(WidgetLogTag, "桌面小组件观察待办数据失败", error)
                        }
                        .collect { todayData ->
                            val nextSnapshot = todayData.toWidgetSnapshot()
                            Log.d(
                                WidgetLogTag,
                                "flow:emit glanceId=$id ${nextSnapshot.logSummary()}",
                            )
                            TodoWidgetCache.write(appContext, nextSnapshot)
                            scheduleCompletionSinkRefresh(appContext, nextSnapshot.rows)
                            currentSnapshot = nextSnapshot
                        }
                }
            }

            LaunchedEffect(currentSnapshot.rows) {
                // updateAll() cannot reliably restart an already-running Glance composition.
                // Keep the three-second sink transition observable here as well, so it still
                // happens when no second checkbox action or external refresh occurs.
                while (true) {
                    val nextExpiryMillis = currentSnapshot.rows
                        .asSequence()
                        .filter { row -> row.completed }
                        .mapNotNull { row ->
                            completionTimestampMillis(row.completedAt)
                                ?.plus(WidgetCompletionSinkDelayMillis)
                        }
                        .filter { expiryMillis -> expiryMillis > System.currentTimeMillis() }
                        .minOrNull()
                        ?: return@LaunchedEffect
                    delay((nextExpiryMillis - System.currentTimeMillis()).coerceAtLeast(1L))
                    nowMillis = System.currentTimeMillis()
                }
            }

            TodoWidgetContent(
                snapshot = currentSnapshot,
                nowMillis = nowMillis,
            )
        }
    }
}

private fun widgetRepository(context: Context): TodoRepository? =
    runCatching {
        EntryPointAccessors.fromApplication(context, TodoWidgetEntryPoint::class.java)
            .todoRepository()
    }.onFailure { error ->
        Log.e(WidgetLogTag, "桌面小组件获取待办仓库失败", error)
    }.getOrNull()

private suspend fun loadWidgetSnapshot(
    context: Context,
    currentDate: LocalDate,
    repository: TodoRepository?,
): TodoWidgetSnapshot {
    // Room is the source of truth for every render. The cache is intentionally only a
    // failure fallback: a launcher can apply a checkbox change optimistically while an
    // older RemoteViews render is still in flight, and reusing that cache would restore
    // the previous checked state.
    val cachedSnapshot = TodoWidgetCache.read(context)
    Log.d(
        WidgetLogTag,
        "snapshot:start date=$currentDate cache=${cachedSnapshot?.logSummary() ?: "none"}",
    )
    val freshSnapshot = WidgetMutationMutex.withLock {
        runCatching {
            val todoRepository = checkNotNull(repository) { "TodoRepository unavailable" }
            withContext(Dispatchers.IO) { todoRepository.getTodayDataSnapshot() }
        }.map { todayData ->
            todayData.toWidgetSnapshot()
        }.onSuccess { snapshot ->
            Log.d(WidgetLogTag, "snapshot:room ${snapshot.logSummary()}")
        }.onFailure { error ->
            Log.e(WidgetLogTag, "桌面小组件读取今日待办失败", error)
        }.getOrNull()?.also { snapshot ->
            // Keep cache publication in the same critical section as the Room read so an
            // older render cannot overwrite the snapshot produced after a checkbox action.
            TodoWidgetCache.write(context, snapshot)
        }
    }

    if (freshSnapshot != null) {
        scheduleCompletionSinkRefresh(context, freshSnapshot.rows)
        return freshSnapshot
    }

    val fallbackSnapshot = cachedSnapshot
        ?.takeIf { snapshot -> snapshot.snapshotDate == currentDate }
        ?: TodoWidgetSnapshot.empty(currentDate)
    Log.w(WidgetLogTag, "snapshot:fallback ${fallbackSnapshot.logSummary()}")
    scheduleCompletionSinkRefresh(context, fallbackSnapshot.rows)
    return fallbackSnapshot
}

private fun TodayTodoData.toWidgetSnapshot(): TodoWidgetSnapshot =
    TodoWidgetSnapshot(
        rows = buildWidgetRowsFromNodes(tree),
        progressLabel = "$completedCount/$totalCount",
        snapshotDate = date,
    )

private fun TodoWidgetSnapshot.logSummary(): String =
    "date=$snapshotDate rows=${rows.size} progress=$progressLabel states=${rows.joinToString(",") { row ->
        "${row.todoId}=${if (row.completed) "done" else "open"}"
    }}"

internal fun sortWidgetRows(
    rows: List<TodoWidgetRow>,
    nowMillis: Long,
): List<TodoWidgetRow> =
    rows
        .map { row -> row.copy(celebrating = isWidgetCompletionPending(row, nowMillis)) }
        .sortedWith(
            compareBy<TodoWidgetRow> { it.completed && !it.celebrating }
                .thenBy { it.sortIndex },
        )

internal fun isWidgetCompletionPending(row: TodoWidgetRow, nowMillis: Long): Boolean {
    if (!row.completed) {
        return false
    }
    val completedAtMillis = completionTimestampMillis(row.completedAt) ?: return false
    val elapsedMillis = nowMillis - completedAtMillis
    return elapsedMillis in 0L until WidgetCompletionSinkDelayMillis
}

private fun completionTimestampMillis(completedAt: String?): Long? {
    if (completedAt.isNullOrBlank()) {
        return null
    }
    return runCatching { OffsetDateTime.parse(completedAt).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(completedAt)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
}

@Composable
private fun TodoWidgetContent(
    snapshot: TodoWidgetSnapshot,
    nowMillis: Long,
) {
    val context = LocalContext.current
    val rows = snapshot.rows
    val sortedRows = sortWidgetRows(rows, nowMillis)
    val solarDateLabel = formatWidgetSolarDate(snapshot.snapshotDate)
    val lunarDateLabel = LunarUtils.getLunarInfo(snapshot.snapshotDate)

    LazyColumn(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(24.dp)
            .background(WidgetBackground),
    ) {
        item(itemId = -3L) {
            WidgetHeader(
                solarDateLabel = solarDateLabel,
                lunarDateLabel = lunarDateLabel,
                progressText = snapshot.progressLabel,
            )
        }

        if (rows.isEmpty()) {
            item(itemId = -4L) {
                Text(
                    text = context.getString(R.string.widget_empty),
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
        } else {
            items(
                items = sortedRows,
                // Keep the RemoteViews collection identity attached to the logical todo.
                // The row can move when its completion state changes, but its action must still
                // carry the same business id after the reorder.
                itemId = { row -> row.stableItemId() },
            ) { row ->
                // Keep the composed row identity tied to the business id while the visible
                // position changes after the three-second completion window.
                key(row.todoId) {
                    TodoWidgetRowItem(row = row)
                }
            }
        }
    }
}

@Composable
private fun WidgetHeader(
    solarDateLabel: String,
    lunarDateLabel: String,
    progressText: String,
) {
    val dateText = "$solarDateLabel · $lunarDateLabel"
    val headerFontSize = calculateWidgetHeaderFontSize(
        widgetWidthDp = LocalSize.current.width.value,
        dateText = dateText,
        progressText = progressText,
    )

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity(appLaunchIntent(LocalContext.current)))
            .background(WidgetBlue)
            .padding(horizontal = WidgetHeaderHorizontalPaddingDp.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = dateText,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                color = OnAccentText,
                fontWeight = FontWeight.Medium,
                fontSize = headerFontSize.sp,
            ),
            maxLines = 1,
        )
        Spacer(modifier = GlanceModifier.width(WidgetHeaderContentGapDp.dp))
        Text(
            text = progressText,
            style = TextStyle(
                color = OnAccentText,
                fontWeight = FontWeight.Bold,
                fontSize = headerFontSize.sp,
            ),
            maxLines = 1,
        )
    }
}

internal fun formatWidgetSolarDate(date: LocalDate): String =
    "${date.format(WidgetDateFormatter)} · ${date.format(WidgetWeekdayFormatter)}"

internal fun calculateWidgetHeaderFontSize(
    widgetWidthDp: Float,
    dateText: String,
    progressText: String,
): Float {
    val availableTextWidth = (
        widgetWidthDp -
            WidgetHeaderHorizontalPaddingDp * 2f -
            WidgetHeaderContentGapDp
        ).coerceAtLeast(1f)
    val estimatedTextUnits = (dateText + progressText)
        .sumOf { character -> character.estimatedWidthUnits().toDouble() }
        .toFloat()
        .coerceAtLeast(1f)
    val fittedSize = availableTextWidth / estimatedTextUnits
    return (floor(fittedSize * 2f) / 2f)
        .coerceIn(WidgetHeaderMinFontSp, WidgetHeaderMaxFontSp)
}

private fun Char.estimatedWidthUnits(): Float = when {
    isWhitespace() -> 0.30f
    this == '·' -> 0.50f
    this == '-' -> 0.45f
    this == '/' -> 0.55f
    code <= 0x7F -> 0.58f
    else -> 1f
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
        WidgetCheckBox(
            checked = row.completed,
            todoId = row.todoId,
        )
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
private fun WidgetCheckBox(
    checked: Boolean,
    todoId: String,
) {
    val context = LocalContext.current
    Log.d(WidgetLogTag, "render:checkbox todoId=$todoId checked=$checked")
    Box(
        modifier = GlanceModifier
            .width(CheckBoxTouchSize)
            .height(CheckBoxTouchSize)
            .clickable(
                actionSendBroadcast(todoToggleIntent(context, todoId)),
            )
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(
                if (checked) R.drawable.ic_widget_todo_checked else R.drawable.ic_widget_todo_unchecked,
            ),
            contentDescription = context.getString(
                if (checked) R.string.widget_mark_incomplete else R.string.widget_mark_completed,
            ),
            modifier = GlanceModifier
                .fillMaxSize(),
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

private fun todoToggleIntent(context: Context, todoId: String): Intent {
    val intent = Intent(context, TodoWidgetToggleReceiver::class.java)
        .setAction(WidgetToggleTodoAction)
        // Intent.filterEquals includes data URI. This makes every checkbox's PendingIntent
        // distinct even if a launcher reuses the same RemoteViews click slot.
        .setData(Uri.parse("$WidgetToggleTodoUriPrefix${Uri.encode(todoId)}"))
        .putExtra(WidgetToggleTodoIdExtra, todoId)
        .setPackage(context.packageName)
    Log.d(WidgetLogTag, "render:action todoId=$todoId data=${intent.dataString}")
    return intent
}

class TodoWidgetToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(
            WidgetLogTag,
            "toggle:received action=${intent.action} component=${intent.component} " +
                "data=${intent.dataString} extras=${intent.extras?.keySet()?.joinToString() ?: "none"}",
        )
        if (intent.action != WidgetToggleTodoAction) {
            Log.w(WidgetLogTag, "toggle:ignored reason=unexpected_action")
            return
        }
        val todoId = intent.getStringExtra(WidgetToggleTodoIdExtra)
            ?.takeIf { it.isNotBlank() }
            ?: intent.data
            ?.takeIf { uri -> uri.scheme == "lighttodo" && uri.host == "widget" }
            ?.pathSegments
            ?.takeIf { segments -> segments.size == 2 && segments[0] == "toggle" }
            ?.get(1)
            ?.takeIf { it.isNotBlank() }
        if (todoId == null) {
            Log.e(WidgetLogTag, "toggle:rejected reason=missing_or_invalid_todo_id")
            return
        }

        val appContext = context.applicationContext
        Log.i(WidgetLogTag, "toggle:accepted todoId=$todoId")
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                WidgetInteractionMutex.withLock {
                    Log.d(WidgetLogTag, "toggle:queue_enter todoId=$todoId")
                    val persistenceResult = withContext(Dispatchers.IO) {
                        WidgetMutationMutex.withLock {
                            Log.d(WidgetLogTag, "toggle:room_begin todoId=$todoId")
                            runCatching {
                                val entryPoint = EntryPointAccessors.fromApplication(
                                    appContext,
                                    TodoWidgetEntryPoint::class.java,
                                )
                                val todoRepository = entryPoint.todoRepository()
                                todoRepository.toggleTodoFromWidget(todoId)
                            }
                        }
                    }
                    Log.d(
                        WidgetLogTag,
                        "toggle:room_end todoId=$todoId result=" +
                            persistenceResult.fold(
                                onSuccess = { result ->
                                    if (result.found) {
                                        "found before=${result.completedBefore} " +
                                            "target=${result.requestedCompleted} " +
                                            "after=${result.persistedCompleted} operation=${result.operation}"
                                    } else {
                                        "missing"
                                    }
                                },
                                onFailure = { error -> "error:${error.javaClass.simpleName}" },
                            ),
                    )
                    persistenceResult.exceptionOrNull()?.let { error ->
                        Log.e(WidgetLogTag, "桌面小组件更新待办状态失败：$todoId", error)
                    }
                    if (persistenceResult.isSuccess && persistenceResult.getOrNull()?.found == false) {
                        Log.w(WidgetLogTag, "桌面小组件中的待办已不存在：$todoId")
                    }
                    // A raw broadcast has no GlanceId. Refreshing all instances is deliberate:
                    // every instance reads Room, so no instance can retain the old checkbox.
                    Log.d(WidgetLogTag, "toggle:refresh_begin todoId=$todoId")
                    TodoWidgetUpdater.refreshAll(appContext)
                    Log.d(WidgetLogTag, "toggle:refresh_end todoId=$todoId")
                }
            } catch (error: Throwable) {
                Log.e(WidgetLogTag, "toggle:uncaught todoId=$todoId", error)
            } finally {
                Log.d(WidgetLogTag, "toggle:finish todoId=$todoId")
                pendingResult.finish()
            }
        }
    }
}

class TodoWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(
            WidgetLogTag,
            "refresh:received action=${intent.action} data=${intent.dataString ?: "none"}",
        )
        if (intent.action !in WidgetRefreshActions) {
            Log.w(WidgetLogTag, "refresh:ignored reason=unexpected_action")
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
        Log.d(WidgetLogTag, "receiver:enabled")
        scheduleNextDailyRefresh(context.applicationContext)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        Log.d(WidgetLogTag, "receiver:update count=${appWidgetIds.size}")
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
    val completedAt: String? = null,
    val celebrating: Boolean = false,
)

internal data class TodoWidgetSnapshot(
    val rows: List<TodoWidgetRow>,
    val progressLabel: String,
    val snapshotDate: LocalDate,
) {
    companion object {
        fun empty(date: LocalDate) = TodoWidgetSnapshot(
            rows = emptyList(),
            progressLabel = "0/0",
            snapshotDate = date,
        )
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
                    completedAt = node.todo.completedAt,
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
        WidgetRefreshMutex.withLock {
            val appContext = context.applicationContext
            Log.d(WidgetLogTag, "refresh:begin")
            scheduleNextDailyRefresh(appContext)
            runCatching { TodoWidget().updateAll(appContext) }
                .onSuccess { Log.d(WidgetLogTag, "refresh:update_all_success") }
                .onFailure { error ->
                    Log.e(WidgetLogTag, "桌面小组件刷新失败", error)
                }
            Log.d(WidgetLogTag, "refresh:end")
        }
    }

}

private fun scheduleCompletionSinkRefresh(
    context: Context,
    rows: List<TodoWidgetRow>,
) {
    val nowMillis = System.currentTimeMillis()
    var pendingCount = 0
    rows.forEach { row ->
        if (!isWidgetCompletionPending(row, nowMillis)) {
            return@forEach
        }
        val completedAtMillis = completionTimestampMillis(row.completedAt) ?: return@forEach
        pendingCount++
        scheduleCompletionSinkExpiry(
            context = context,
            todoId = row.todoId,
            triggerAtMillis = completedAtMillis + WidgetCompletionSinkDelayMillis,
        )
    }
    if (pendingCount > 0) {
        Log.d(WidgetLogTag, "sink:scheduled count=$pendingCount")
    }
}

private fun scheduleCompletionSinkExpiry(
    context: Context,
    todoId: String,
    triggerAtMillis: Long,
) {
    val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
    val pendingIntent = completionSinkPendingIntent(context, todoId)

    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }.getOrElse {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }.onSuccess {
        Log.d(
            WidgetLogTag,
            "sink:alarm_set todoId=$todoId inMs=${(triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)}",
        )
    }.onFailure { error ->
        Log.e(WidgetLogTag, "完成任务延迟下沉刷新失败：$todoId", error)
    }
}

private fun completionSinkPendingIntent(context: Context, todoId: String): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        todoId.hashCode(),
        Intent(context, TodoWidgetRefreshReceiver::class.java)
            .setAction(WidgetCompletionSinkExpiryAction)
            .setData(Uri.parse("lighttodo://widget/completion/${Uri.encode(todoId)}"))
            .setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

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
    WidgetCompletionSinkExpiryAction,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_BOOT_COMPLETED,
)
private const val WidgetDailyRefreshAction = "com.lighttodo.android.widget.action.DAILY_REFRESH"
private const val WidgetDailyRefreshRequestCode = 1001
private const val WidgetLogTag = "LightTodoWidget"

private object TodoWidgetCache {
    private const val PreferencesName = "lighttodo_widget_cache"
    private const val RowsKey = "rows"
    private const val ProgressLabelKey = "progress_label"
    private const val SnapshotDateKey = "snapshot_date"
    private var memorySnapshot: TodoWidgetSnapshot? = null

    @Synchronized
    fun read(context: Context): TodoWidgetSnapshot? {
        memorySnapshot?.let { snapshot -> return snapshot }

        val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        val rowsJson = preferences.getString(RowsKey, null) ?: return null
        val snapshotDate = preferences.getString(SnapshotDateKey, null)
            ?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
            ?: return null
        val rows = runCatching { rowsFromJson(rowsJson) }.getOrNull() ?: return null
        val progressLabel = preferences.getString(ProgressLabelKey, null) ?: progressLabelForRows(rows)
        return TodoWidgetSnapshot(
            rows = rows,
            progressLabel = progressLabel,
            snapshotDate = snapshotDate,
        )
            .also { memorySnapshot = it }
    }

    @Synchronized
    fun write(context: Context, snapshot: TodoWidgetSnapshot) {
        memorySnapshot = snapshot
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(RowsKey, rowsToJson(snapshot.rows))
            .putString(ProgressLabelKey, snapshot.progressLabel)
            .putString(SnapshotDateKey, snapshot.snapshotDate.toString())
            .apply()
    }

    private fun rowsToJson(rows: List<TodoWidgetRow>): String =
        JSONArray().apply {
            rows.forEach { row ->
                put(
                    JSONObject()
                        .put("todoId", row.todoId)
                        .put("title", row.title)
                        .put("completed", row.completed)
                        .put("completedAt", row.completedAt)
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
                        completedAt = item.optString("completedAt").takeIf { it.isNotBlank() },
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

internal fun progressLabelForRows(rows: List<TodoWidgetRow>): String {
    val leafRows = rows.filterNot { it.hasChildren }
    val totalCount = leafRows.size
    val completedCount = leafRows.count { it.completed }
    return "$completedCount/$totalCount"
}
