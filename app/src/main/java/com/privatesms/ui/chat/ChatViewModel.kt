package com.privatesms.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privatesms.domain.model.Message
import com.privatesms.domain.usecase.BlockNumberUseCase
import com.privatesms.domain.usecase.GetMessagesUseCase
import com.privatesms.domain.usecase.SendSmsUseCase
import com.privatesms.domain.usecase.ScheduleMessageUseCase
import com.privatesms.domain.repository.SmsRepository
import com.privatesms.domain.repository.BlocklistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ChatUiState {
    object Loading : ChatUiState()
    data class Success(val messages: List<Message>, val isBlocked: Boolean) : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val getMessagesUseCase: GetMessagesUseCase,
    private val sendSmsUseCase: SendSmsUseCase,
    private val blockNumberUseCase: BlockNumberUseCase,
    private val scheduleMessageUseCase: ScheduleMessageUseCase,
    private val smsRepository: SmsRepository,
    private val blocklistRepository: BlocklistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState

    fun loadMessages(threadId: Long, address: String) {
        viewModelScope.launch {
            _uiState.value = ChatUiState.Loading
            smsRepository.setReadStatus(threadId, true)

            val messagesFlow = getMessagesUseCase(threadId)
            val isBlockedFlow = blocklistRepository.getBlockedNumbers().map { list ->
                list.any { it.phoneNumber == address }
            }

            messagesFlow.combine(isBlockedFlow) { messages, isBlocked ->
                ChatUiState.Success(messages, isBlocked)
            }
            .catch { e ->
                _uiState.value = ChatUiState.Error(e.message ?: "Unknown error")
            }
            .collect { state ->
                _uiState.value = state
            }
        }
    }

    fun sendMessage(address: String, body: String) {
        viewModelScope.launch {
            sendSmsUseCase(address, body)
        }
    }

    fun scheduleMessage(address: String, body: String, timeMs: Long) {
        viewModelScope.launch {
            scheduleMessageUseCase.schedule(address, body, timeMs)
        }
    }

    fun blockNumber(phoneNumber: String, reason: String? = null) {
        viewModelScope.launch {
            blockNumberUseCase.block(phoneNumber, reason)
        }
    }

    fun unblockNumber(phoneNumber: String) {
        viewModelScope.launch {
            blockNumberUseCase.unblock(phoneNumber)
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            smsRepository.deleteMessage(messageId)
        }
    }
}
