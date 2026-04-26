package com.mytodo.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mytodo.android.R
import com.mytodo.android.data.TodayTodoNode
import com.mytodo.android.ui.components.TodoList

@Composable
fun MainRoute(
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MainScreen(
        uiState = uiState,
        onToggleTodo = viewModel::onTodoToggle,
        onConfirmParentCompletion = viewModel::confirmParentCompletion,
        onDismissParentCompletion = viewModel::dismissParentCompletion,
        onAddClick = {},
    )
}

@Composable
fun MainScreen(
    uiState: MainUiState,
    onToggleTodo: (String) -> Unit,
    onConfirmParentCompletion: () -> Unit,
    onDismissParentCompletion: () -> Unit,
    onAddClick: () -> Unit,
) {
    val expandedState = remember(uiState.todayData?.tree) { mutableStateMapOf<String, Boolean>() }

    fun isExpanded(node: TodayTodoNode): Boolean = expandedState[node.todo.id] ?: node.todo.expanded

    fun findExpandedState(nodes: List<TodayTodoNode>, todoId: String): Boolean? {
        nodes.forEach { node ->
            if (node.todo.id == todoId) {
                return node.todo.expanded
            }

            findExpandedState(node.children, todoId)?.let { return it }
        }
        return null
    }

    fun onExpandToggle(todoId: String) {
        val current = expandedState[todoId]
            ?: findExpandedState(uiState.todayData?.tree.orEmpty(), todoId)
            ?: true
        expandedState[todoId] = !current
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(
                    text = stringResource(id = R.string.todo_add_symbol),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 24.dp,
                bottom = innerPadding.calculateBottomPadding() + 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TodayHeader(
                    dateLabel = uiState.dateLabel,
                    secondaryDateLabel = uiState.secondaryDateLabel,
                    progressLabel = uiState.progressLabel,
                    progress = uiState.progress,
                )
            }

            if (uiState.isEmpty) {
                item {
                    EmptyTodayCard()
                }
            } else {
                item {
                    Text(
                        text = stringResource(id = R.string.today_task_section_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                item {
                    TodoList(
                        nodes = uiState.todayData?.tree.orEmpty(),
                        isExpanded = ::isExpanded,
                        onExpandToggle = ::onExpandToggle,
                        onCheckedChange = onToggleTodo,
                    )
                }
            }
        }
    }

    val pendingRequest = uiState.pendingParentCompletion
    if (pendingRequest != null) {
        AlertDialog(
            onDismissRequest = onDismissParentCompletion,
            title = {
                Text(text = stringResource(id = R.string.parent_completion_dialog_title))
            },
            text = {
                Text(
                    text = stringResource(
                        id = R.string.parent_completion_dialog_message,
                        pendingRequest.title,
                        pendingRequest.childCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmParentCompletion) {
                    Text(text = stringResource(id = R.string.parent_completion_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissParentCompletion) {
                    Text(text = stringResource(id = R.string.parent_completion_dialog_dismiss))
                }
            },
        )
    }
}

@Composable
private fun TodayHeader(
    dateLabel: String,
    secondaryDateLabel: String,
    progressLabel: String,
    progress: Float,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(id = R.string.today_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$dateLabel · $secondaryDateLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(id = R.string.today_progress_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = progressLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                )
            }
        }
    }
}

@Composable
private fun EmptyTodayCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(id = R.string.today_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(id = R.string.today_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
