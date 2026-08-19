package com.lighttodo.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lighttodo.android.R
import com.lighttodo.android.data.RepeatMode
import com.lighttodo.android.data.TodayTodoData
import com.lighttodo.android.data.TodoEntity
import com.lighttodo.android.data.TodoRepository
import com.lighttodo.android.utils.LunarUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MainViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val preferences = context.getSharedPreferences(EXPORT_PREFS_NAME, Context.MODE_PRIVATE)
    private val pendingParentCompletion = MutableStateFlow<ParentCompletionRequest?>(null)
    private val isAddSheetVisible = MutableStateFlow(false)
    private val editingTodo = MutableStateFlow<TodoEntity?>(null)
    private val exportDirectoryUri = MutableStateFlow(preferences.getString(EXPORT_DIRECTORY_URI_KEY, null))
    private val exportMessage = MutableStateFlow<String?>(null)
    private val exportState =
        combine(exportDirectoryUri, exportMessage) { directoryUri, message ->
            ExportState(
                directoryLabel = directoryUri?.let(::exportDirectoryLabel),
                message = message,
            )
        }

    val uiState: StateFlow<MainUiState> =
        combine(
            todoRepository.observeToday(),
            pendingParentCompletion,
            isAddSheetVisible,
            editingTodo,
            exportState,
        ) { todayData, pendingRequest, addSheetVisible, todoToEdit, export ->
            todayData.toUiState(
                pendingRequest = pendingRequest,
                isAddSheetVisible = addSheetVisible,
                editingTodo = todoToEdit,
                exportDirectoryLabel = export.directoryLabel,
                exportMessage = export.message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainUiState(),
        )

    fun showAddSheet() {
        editingTodo.value = null
        isAddSheetVisible.value = true
    }

    fun refreshWidgets() {
        viewModelScope.launch {
            todoRepository.refreshWidgets()
        }
    }

    fun dismissAddSheet() {
        isAddSheetVisible.value = false
        editingTodo.value = null
    }

    fun showEditSheet(todoId: String) {
        editingTodo.value = uiState.value.todayData?.findNode(todoId)?.todo
        isAddSheetVisible.value = false
    }

    fun submitTodo(input: AddTodoInput) {
        val trimmedContent = input.content.trim()
        if (trimmedContent.isEmpty()) {
            return
        }

        val intervalDays = input.intervalDays?.takeIf { it > 0 }
        val weekdays = if (input.repeatMode == RepeatMode.WEEKLY) input.weekdays.sorted() else emptyList()
        val specificDates = if (input.repeatMode == RepeatMode.SPECIFIC_DATES) input.specificDates.sorted() else emptyList()

        viewModelScope.launch {
            val normalizedIntervalDays = if (input.repeatMode == RepeatMode.INTERVAL_DAYS) intervalDays else null
            val normalizedAnchorDate = if (input.repeatMode == RepeatMode.INTERVAL_DAYS) input.anchorDate else null

            if (input.todoId == null) {
                todoRepository.addTodo(
                    content = trimmedContent,
                    repeatMode = input.repeatMode,
                    weekdays = weekdays,
                    intervalDays = normalizedIntervalDays,
                    anchorDate = normalizedAnchorDate,
                    specificDates = specificDates,
                )
            } else {
                todoRepository.updateTodo(
                    todoId = input.todoId,
                    content = trimmedContent,
                    repeatMode = input.repeatMode,
                    weekdays = weekdays,
                    intervalDays = normalizedIntervalDays,
                    anchorDate = normalizedAnchorDate,
                    specificDates = specificDates,
                )
            }
            isAddSheetVisible.value = false
            editingTodo.value = null
        }
    }

    fun deleteTodo(todoId: String) {
        viewModelScope.launch {
            todoRepository.deleteTodo(todoId)
            isAddSheetVisible.value = false
            editingTodo.value = null
            pendingParentCompletion.value = null
        }
    }

    fun onTodoToggle(todoId: String) {
        viewModelScope.launch {
            val todayData = uiState.value.todayData
            val node = todayData?.findNode(todoId)

            if (node != null && !node.todo.completed) {
                val unfinishedCount = node.unfinishedDescendantCount()
                if (unfinishedCount > 0) {
                    pendingParentCompletion.value =
                        ParentCompletionRequest(
                            todoId = node.todo.id,
                            title = node.todo.content,
                            childCount = unfinishedCount,
                        )
                    return@launch
                }
            }

            todoRepository.toggleTodo(todoId)
            pendingParentCompletion.value = null
        }
    }

    fun confirmParentCompletion() {
        val request = pendingParentCompletion.value ?: return
        viewModelScope.launch {
            todoRepository.completeParentSubtree(request.todoId)
            pendingParentCompletion.value = null
        }
    }

    fun dismissParentCompletion() {
        pendingParentCompletion.value = null
    }

    fun setExportDirectory(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }.onFailure {
            exportMessage.value = context.getString(R.string.export_select_location_failed)
            return
        }

        val uriString = uri.toString()
        preferences.edit().putString(EXPORT_DIRECTORY_URI_KEY, uriString).apply()
        exportDirectoryUri.value = uriString
        exportMessage.value = context.getString(
            R.string.export_select_location_success,
            exportDirectoryLabel(uriString),
        )
    }

    fun exportTodos() {
        val directoryUri = exportDirectoryUri.value
        if (directoryUri == null) {
            exportMessage.value = context.getString(R.string.export_missing_location)
            return
        }

        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    writeExportFile(directoryUri)
                }
            }

            exportMessage.value = result.fold(
                onSuccess = { fileName -> context.getString(R.string.export_success, fileName) },
                onFailure = { error ->
                    context.getString(
                        R.string.export_failed,
                        error.localizedMessage ?: error::class.java.simpleName,
                    )
                },
            )
        }
    }

    fun importTodos(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    readImportFile(uri)
                }
            }

            exportMessage.value = result.fold(
                onSuccess = {
                    context.getString(R.string.import_success)
                },
                onFailure = { error ->
                    context.getString(
                        R.string.import_failed,
                        error.localizedMessage ?: error::class.java.simpleName,
                    )
                },
            )
        }
    }

    fun consumeExportMessage() {
        exportMessage.value = null
    }

    private fun exportDirectoryLabel(uriString: String): String =
        DocumentFile.fromTreeUri(context, Uri.parse(uriString))?.name ?: uriString

    private suspend fun writeExportFile(directoryUriString: String): String {
        val directoryUri = Uri.parse(directoryUriString)
        val directory = DocumentFile.fromTreeUri(context, directoryUri)
            ?: error(context.getString(R.string.export_invalid_location))
        check(directory.canWrite()) { context.getString(R.string.export_location_not_writable) }

        val fileName = "LightTodo_config_${LocalDateTime.now().format(EXPORT_FILE_TIME_FORMATTER)}.json"
        val file = directory.createFile("application/json", fileName)
            ?: error(context.getString(R.string.export_create_file_failed))
        val json = todoRepository.exportTodosJson()

        context.contentResolver.openOutputStream(file.uri)?.use { stream ->
            stream.write(json.toByteArray(Charsets.UTF_8))
        } ?: error(context.getString(R.string.export_open_file_failed))

        return file.name ?: fileName
    }

    private suspend fun readImportFile(uri: Uri) {
        val json = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: error(context.getString(R.string.import_open_file_failed))

        todoRepository.importTodosJson(json)
    }
}

data class MainUiState(
    val todayData: TodayTodoData? = null,
    val dateLabel: String = "",
    val secondaryDateLabel: String = "",
    val lunarLabel: String = "",
    val progressLabel: String = "0/0",
    val progress: Float = 0f,
    val pendingParentCompletion: ParentCompletionRequest? = null,
    val isAddSheetVisible: Boolean = false,
    val editingTodo: TodoEntity? = null,
    val exportDirectoryLabel: String? = null,
    val exportMessage: String? = null,
    val isEmpty: Boolean = true,
)

data class ParentCompletionRequest(
    val todoId: String,
    val title: String,
    val childCount: Int,
)

data class AddTodoInput(
    val todoId: String? = null,
    val content: String,
    val repeatMode: RepeatMode = RepeatMode.DAILY,
    val weekdays: Set<Int> = emptySet(),
    val intervalDays: Int? = null,
    val anchorDate: LocalDate? = null,
    val specificDates: List<LocalDate> = emptyList(),
)

private data class ExportState(
    val directoryLabel: String?,
    val message: String?,
)

private fun TodayTodoData.toUiState(
    pendingRequest: ParentCompletionRequest?,
    isAddSheetVisible: Boolean,
    editingTodo: TodoEntity?,
    exportDirectoryLabel: String?,
    exportMessage: String?,
): MainUiState =
    MainUiState(
        todayData = this,
        dateLabel = date.format(TITLE_DATE_FORMATTER),
        secondaryDateLabel = date.format(SECONDARY_DATE_FORMATTER),
        lunarLabel = LunarUtils.getLunarInfo(date),
        progressLabel = "$completedCount/$totalCount",
        progress = progress,
        pendingParentCompletion = pendingRequest,
        isAddSheetVisible = isAddSheetVisible,
        editingTodo = editingTodo,
        exportDirectoryLabel = exportDirectoryLabel,
        exportMessage = exportMessage,
        isEmpty = totalCount == 0,
    )

private const val EXPORT_PREFS_NAME = "lighttodo_export"
private const val EXPORT_DIRECTORY_URI_KEY = "export_directory_uri"
private val EXPORT_FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.CHINA)
private val TITLE_DATE_FORMATTER = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val SECONDARY_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)
