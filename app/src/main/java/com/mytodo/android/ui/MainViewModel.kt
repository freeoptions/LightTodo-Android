package com.mytodo.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mytodo.android.data.TodayTodoData
import com.mytodo.android.data.TodayTodoNode
import com.mytodo.android.data.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

    val uiState: StateFlow<MainUiState> =
        combine(todoRepository.observeToday(), pendingParentCompletion) { todayData, pendingRequest ->
            todayData.toUiState(pendingRequest = pendingRequest)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainUiState(),
        )

    fun onTodoToggle(todoId: String) {
        viewModelScope.launch {
            if (todoRepository.hasChildren(todoId)) {
                val todayData = uiState.value.todayData
                val node = todayData?.findNode(todoId)
                if (node != null && !node.todo.completed) {
                    pendingParentCompletion.value =
                        ParentCompletionRequest(
                            todoId = node.todo.id,
                            title = node.todo.content,
                            childCount = node.children.size,
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
    val progressLabel: String = "0/0",
    val progress: Float = 0f,
    val pendingParentCompletion: ParentCompletionRequest? = null,
    val isEmpty: Boolean = true,
)

data class ParentCompletionRequest(
    val todoId: String,
    val title: String,
    val childCount: Int,
)

private fun TodayTodoData.toUiState(pendingRequest: ParentCompletionRequest?): MainUiState =
    MainUiState(
        todayData = this,
        dateLabel = date.format(TITLE_DATE_FORMATTER),
        secondaryDateLabel = date.format(SECONDARY_DATE_FORMATTER),
        progressLabel = "$completedCount/$totalCount",
        progress = progress,
        pendingParentCompletion = pendingRequest,
        isEmpty = totalCount == 0,
    )

private val TITLE_DATE_FORMATTER = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val SECONDARY_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE", Locale.CHINA)

private fun TodayTodoData.findNode(todoId: String): TodayTodoNode? {
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
