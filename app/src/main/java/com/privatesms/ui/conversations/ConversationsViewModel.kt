package com.privatesms.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privatesms.data.sms.SmsSync
import com.privatesms.domain.model.Conversation
import com.privatesms.domain.usecase.DeleteConversationUseCase
import com.privatesms.domain.usecase.GetConversationsUseCase
import com.privatesms.domain.repository.SmsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ConversationsUiState {
    object Loading : ConversationsUiState()
    data class Success(val conversations: List<Conversation>) : ConversationsUiState()
    data class Error(val message: String) : ConversationsUiState()
}

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val getConversationsUseCase: GetConversationsUseCase,
    private val deleteConversationUseCase: DeleteConversationUseCase,
    private val smsRepository: SmsRepository,
    val smsSync: SmsSync
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConversationsUiState>(ConversationsUiState.Loading)
    val uiState: StateFlow<ConversationsUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private var currentArchived = false
    private var currentIsPrivate = false
    private var currentIsSpam = false

    fun loadConversations(archived: Boolean, isPrivate: Boolean, isSpam: Boolean) {
        currentArchived = archived
        currentIsPrivate = isPrivate
        currentIsSpam = isSpam
        
        viewModelScope.launch {
            _uiState.value = ConversationsUiState.Loading
            
            getConversationsUseCase(archived, isPrivate)
                .combine(_searchQuery) { list, query ->
                    val filtered = list.filter {
                        if (isSpam) it.isSpam else !it.isSpam
                    }
                    if (query.isBlank()) {
                        filtered
                    } else {
                        filtered.filter { 
                            it.snippet.contains(query, ignoreCase = true) || 
                            it.contactName?.contains(query, ignoreCase = true) == true || 
                            it.address.contains(query) 
                        }
                    }
                }
                .catch { e ->
                    _uiState.value = ConversationsUiState.Error(e.message ?: "Unknown error")
                }
                .collect { conversations ->
                    _uiState.value = ConversationsUiState.Success(conversations)
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteConversation(threadId: Long) {
        viewModelScope.launch {
            deleteConversationUseCase(threadId)
        }
    }

    fun archiveConversation(threadId: Long, archive: Boolean) {
        viewModelScope.launch {
            smsRepository.archiveConversation(threadId, archive)
        }
    }

    fun markConversationPrivate(threadId: Long, isPrivate: Boolean) {
        viewModelScope.launch {
            smsRepository.markConversationPrivate(threadId, isPrivate)
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            smsSync.syncAll()
        }
    }

    fun bulkDelete(threadIds: List<Long>) {
        viewModelScope.launch {
            threadIds.forEach { deleteConversationUseCase(it) }
        }
    }

    fun bulkArchive(threadIds: List<Long>, archive: Boolean) {
        viewModelScope.launch {
            threadIds.forEach { smsRepository.archiveConversation(it, archive) }
        }
    }
}
