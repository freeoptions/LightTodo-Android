package com.mytodo.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionRunCallback
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.material3.Text
import androidx.glance.material3.WidgetTheme
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import com.mytodo.android.R
import com.mytodo.android.data.TodayTodoNode
import com.mytodo.android.data.TodoEntity
import com.mytodo.android.data.TodoRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val TodoIdKey = ActionParameters.Key<String>("todo_id")

class TodoWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, TodoWidgetEntryPoint::class.java)
        val todoRepository = entryPoint.todoRepository()
        val todayData = withContext(Dispatchers.IO) { todoRepository.observeToday().first() }
        val todayRows = buildWidgetRowsFromNodes(todayData.tree)

        provideContent {
            WidgetTheme(colorProviders = ColorProviders()) {
                TodoWidgetContent(rows = todayRows)
            }
        }
    }
}

@Composable
private fun TodoWidgetContent(rows: List<TodoWidgetRow>) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(androidx.glance.color.ColorProvider(day = 0xFFF7F7F9.toInt(), night = 0xFFF7F7F9.toInt()))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = context.getString(R.string.today_title),
            style = TextStyle(fontWeight = FontWeight.Bold),
        )
        Spacer(modifier = GlanceModifier.height(12.dp))

        if (rows.isEmpty()) {
            Text(text = context.getString(R.string.today_empty_title))
        } else {
            rows.take(8).forEach { row ->
                TodoWidgetRowItem(row = row)
                Spacer(modifier = GlanceModifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TodoWidgetRowItem(row: TodoWidgetRow) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = row.title,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(fontWeight = if (row.hasChildren) FontWeight.Medium else FontWeight.Normal),
        )
        Spacer(modifier = GlanceModifier.width(12.dp))
        Box(
            modifier = GlanceModifier
                .clickable(
                    actionRunCallback<ToggleTodoAction>(
                        actionParametersOf(TodoIdKey to row.todoId),
                    ),
                )
                .background(
                    androidx.glance.color.ColorProvider(
                        day = if (row.completed) 0xFF4CAF50.toInt() else 0xFFE0E0E0.toInt(),
                        night = if (row.completed) 0xFF4CAF50.toInt() else 0xFFE0E0E0.toInt(),
                    ),
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = if (row.completed) "✓" else "○")
        }
    }
}

class ToggleTodoAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val todoId = parameters[TodoIdKey] ?: return
        val entryPoint = EntryPointAccessors.fromApplication(context, TodoWidgetEntryPoint::class.java)
        val todoRepository = entryPoint.todoRepository()
        val todayData = withContext(Dispatchers.IO) { todoRepository.observeToday().first() }
        val node = todayData.findNode(todoId)

        withContext(Dispatchers.IO) {
            if (node != null && !node.todo.completed && node.unfinishedDescendantCount() > 0) {
                todoRepository.completeParentSubtree(todoId)
            } else {
                todoRepository.toggleTodo(todoId)
            }
        }
        TodoWidget().updateAll(context)
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget()
}

internal data class TodoWidgetRow(
    val todoId: String,
    val title: String,
    val completed: Boolean,
    val hasChildren: Boolean,
)

internal fun buildWidgetRowsFromNodes(
    nodes: List<TodayTodoNode>,
    depth: Int = 0,
): List<TodoWidgetRow> =
    nodes.flatMap { node ->
        val titlePrefix = if (depth == 0) "" else "  ".repeat(depth)
        listOf(
            TodoWidgetRow(
                todoId = node.todo.id,
                title = titlePrefix + node.todo.content,
                completed = node.todo.completed,
                hasChildren = node.children.isNotEmpty(),
            ),
        ) + buildWidgetRowsFromNodes(node.children, depth + 1)
    }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TodoWidgetEntryPoint {
    fun todoRepository(): TodoRepository
}

object TodoWidgetUpdater {
    suspend fun refreshAll(context: Context) {
        TodoWidget().updateAll(context)
    }
}
