package com.lighttodo.android.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lighttodo.android.R
import com.lighttodo.android.data.TodayTodoNode
import com.lighttodo.android.ui.components.AddTodoSheet
import com.lighttodo.android.ui.components.TodoList

@Composable
fun MainRoute(
    viewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.setExportDirectory(uri)
        }
    }
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importTodos(uri)
        }
    }

    LaunchedEffect(uiState.exportMessage) {
        val message = uiState.exportMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.consumeExportMessage()
    }

    LaunchedEffect(Unit) {
        viewModel.refreshWidgets()
    }

    MainScreen(
        uiState = uiState,
        onToggleTodo = viewModel::onTodoToggle,
        onToggleTodoDisabled = viewModel::onTodoDisabledToggle,
        onConfirmParentCompletion = viewModel::confirmParentCompletion,
        onDismissParentCompletion = viewModel::dismissParentCompletion,
        onAddClick = viewModel::showAddSheet,
        onEditTodo = viewModel::showEditSheet,
        onChooseExportDirectory = { exportDirectoryLauncher.launch(null) },
        onExportTodos = viewModel::exportTodos,
        onImportTodos = { importFileLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        onDismissAddTodo = viewModel::dismissAddSheet,
        onSubmitTodo = viewModel::submitTodo,
        onDeleteTodo = viewModel::deleteTodo,
    )
}

@Composable
fun MainScreen(
    uiState: MainUiState,
    onToggleTodo: (String) -> Unit,
    onToggleTodoDisabled: (String) -> Unit,
    onConfirmParentCompletion: () -> Unit,
    onDismissParentCompletion: () -> Unit,
    onAddClick: () -> Unit,
    onEditTodo: (String) -> Unit,
    onChooseExportDirectory: () -> Unit,
    onExportTodos: () -> Unit,
    onImportTodos: () -> Unit,
    onDismissAddTodo: () -> Unit,
    onSubmitTodo: (AddTodoInput) -> Unit,
    onDeleteTodo: (String) -> Unit,
) {
    val expandedState = remember { mutableStateMapOf<String, Boolean>() }

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

    val todayTree = uiState.todayData?.tree.orEmpty()
    val disabledTree = uiState.todayData?.disabledTree.orEmpty()
    val incompleteNodes = todayTree.filter { !it.todo.completed }
    val completedNodes = todayTree.filter { it.todo.completed }

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
                    lunarLabel = uiState.lunarLabel,
                    progressLabel = uiState.progressLabel,
                    progress = uiState.progress,
                    exportDirectoryLabel = uiState.exportDirectoryLabel,
                    onChooseExportDirectory = onChooseExportDirectory,
                    onExportTodos = onExportTodos,
                    onImportTodos = onImportTodos,
                )
            }

            if (uiState.isEmpty) {
                item {
                    EmptyTodayCard()
                }
            } else if (todayTree.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(id = R.string.today_incomplete_section_title),
                        count = incompleteNodes.size,
                    )
                }
                if (incompleteNodes.isEmpty()) {
                    item {
                        SectionEmptyText(text = stringResource(id = R.string.today_no_incomplete))
                    }
                } else {
                    TodoList(
                        nodes = incompleteNodes,
                        isExpanded = ::isExpanded,
                        onExpandToggle = ::onExpandToggle,
                        onTodoClick = onEditTodo,
                        onCheckedChange = onToggleTodo,
                        onDisabledChange = onToggleTodoDisabled,
                    )
                }

                item {
                    SectionHeader(
                        title = stringResource(id = R.string.today_completed_section_title),
                        count = completedNodes.size,
                    )
                }
                if (completedNodes.isEmpty()) {
                    item {
                        SectionEmptyText(text = stringResource(id = R.string.today_no_completed))
                    }
                } else {
                    TodoList(
                        nodes = completedNodes,
                        isExpanded = ::isExpanded,
                        onExpandToggle = ::onExpandToggle,
                        onTodoClick = onEditTodo,
                        onCheckedChange = onToggleTodo,
                        onDisabledChange = onToggleTodoDisabled,
                    )
                }
            }

            if (disabledTree.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(id = R.string.today_disabled_section_title),
                        count = disabledTree.size,
                    )
                }
                TodoList(
                    nodes = disabledTree,
                    isExpanded = ::isExpanded,
                    onExpandToggle = ::onExpandToggle,
                    onTodoClick = onEditTodo,
                    onCheckedChange = onToggleTodo,
                    onDisabledChange = onToggleTodoDisabled,
                )
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

    if (uiState.isAddSheetVisible || uiState.editingTodo != null) {
        AddTodoSheet(
            todo = uiState.editingTodo,
            onDismiss = onDismissAddTodo,
            onSubmit = onSubmitTodo,
            onDelete = onDeleteTodo,
        )
    }
}

@Composable
private fun TodayHeader(
    dateLabel: String,
    secondaryDateLabel: String,
    lunarLabel: String,
    progressLabel: String,
    progress: Float,
    exportDirectoryLabel: String?,
    onChooseExportDirectory: () -> Unit,
    onExportTodos: () -> Unit,
    onImportTodos: () -> Unit,
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
                text = "$dateLabel · $secondaryDateLabel · $lunarLabel",
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
                Text(
                    text = if (exportDirectoryLabel == null) {
                        stringResource(id = R.string.export_location_unset)
                    } else {
                        stringResource(id = R.string.export_location_set, exportDirectoryLabel)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = onChooseExportDirectory,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(id = R.string.export_choose_location))
                    }
                    TextButton(
                        onClick = onExportTodos,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(id = R.string.export_json))
                    }
                    TextButton(
                        onClick = onImportTodos,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(id = R.string.import_json))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(id = R.string.today_section_count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionEmptyText(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
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
