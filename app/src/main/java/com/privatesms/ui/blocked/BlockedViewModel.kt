package com.privatesms.ui.blocked

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privatesms.data.model.BlockedNumberEntity
import com.privatesms.domain.repository.BlocklistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BlockedUiState {
    object Loading : BlockedUiState()
    data class Success(val blockedNumbers: List<BlockedNumberEntity>) : BlockedUiState()
    data class Error(val message: String) : BlockedUiState()
}

@HiltViewModel
class BlockedViewModel @Inject constructor(
    private val blocklistRepository: BlocklistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BlockedUiState>(BlockedUiState.Loading)
    val uiState: StateFlow<BlockedUiState> = _uiState

    init {
        loadBlockedNumbers()
    }

    private fun loadBlockedNumbers() {
        viewModelScope.launch {
            blocklistRepository.getBlockedNumbers()
                .catch { e ->
                    _uiState.value = BlockedUiState.Error(e.message ?: "Unknown error")
                }
                .collect { list ->
                    _uiState.value = BlockedUiState.Success(list)
                }
        }
    }

    fun blockNumber(phoneNumber: String, reason: String? = null) {
        viewModelScope.launch {
            blocklistRepository.blockNumber(phoneNumber, reason)
        }
    }

    fun unblockNumber(phoneNumber: String) {
        viewModelScope.launch {
            blocklistRepository.unblockNumber(phoneNumber)
        }
    }

    fun unblockNumberById(id: Long) {
        viewModelScope.launch {
            blocklistRepository.unblockNumberById(id)
        }
    }
}
