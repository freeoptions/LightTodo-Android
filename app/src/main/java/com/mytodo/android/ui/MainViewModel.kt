package com.mytodo.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytodo.android.data.RepeatMode
import com.mytodo.android.data.TodayTodoData
import com.mytodo.android.data.TodayTodoNode
import com.mytodo.android.data.TodoRepository
import com.mytodo.android.utils.LunarUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
) : ViewModel() {

    private val pendingParentCompletion = MutableStateFlow<ParentCompletionRequest?>(null)
    private val isAddSheetVisible = MutableStateFlow(false)

    val uiState: StateFlow<MainUiState> =
        combine(
            todoRepository.observeToday(),
            pendingParentCompletion,
            isAddSheetVisible,
        ) { todayData, pendingRequest, addSheetVisible ->
            todayData.toUiState(
                pendingRequest = pendingRequest,
                isAddSheetVisible = addSheetVisible,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainUiState(),
        )

    fun showAddSheet() {
        isAddSheetVisible.value = true
    }

    fun dismissAddSheet() {
        isAddSheetVisible.value = false
    }

    fun submitTodo(input: AddTodoInput) {
        val trimmedContent = input.content.trim()
        if (trimmedContent.isEmpty()) {
            return
        }

        val subtaskTitles = input.subtasks
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val intervalDays = input.intervalDays?.takeIf { it > 0 }
        val weekdays = if (input.repeatMode == RepeatMode.WEEKLY) input.weekdays.sorted() else emptyList()
        val specificDates = if (input.repeatMode == RepeatMode.SPECIFIC_DATES) input.specificDates.sorted() else emptyList()

        viewModelScope.launch {
            todoRepository.addTodo(
                content = trimmedContent,
                repeatMode = input.repeatMode,
                weekdays = weekdays,
                intervalDays = if (input.repeatMode == RepeatMode.INTERVAL_DAYS) intervalDays else null,
                anchorDate = if (input.repeatMode == RepeatMode.INTERVAL_DAYS) input.anchorDate else null,
                specificDates = specificDates,
                subtasks = subtaskTitles,
            )
            isAddSheetVisible.value = false
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
    val isEmpty: Boolean = true,
)

data class ParentCompletionRequest(
    val todoId: String,
    val title: String,
    val childCount: Int,
)

data class AddTodoInput(
    val content: String,
    val repeatMode: RepeatMode,
    val weekdays: Set<Int> = emptySet(),
    val intervalDays: Int? = null,
    val anchorDate: LocalDate? = null,
    val specificDates: List<LocalDate> = emptyList(),
    val subtasks: List<String> = emptyList(),
)

private fun TodayTodoData.toUiState(
    pendingRequest: ParentCompletionRequest?,
    isAddSheetVisible: Boolean,
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
        isEmpty = totalCount == 0,
    )

private val TITLE_DATE_FORMATTER = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val SECONDARY_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)
