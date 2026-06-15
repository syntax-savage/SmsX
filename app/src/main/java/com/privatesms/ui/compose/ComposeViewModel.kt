package com.privatesms.ui.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privatesms.data.contacts.ContactsRepository
import com.privatesms.domain.model.Contact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ComposeUiState {
    object Loading : ComposeUiState()
    data class Success(val contacts: List<Contact>) : ComposeUiState()
    data class Error(val message: String) : ComposeUiState()
}

@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val contactsRepository: ContactsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ComposeUiState>(ComposeUiState.Loading)
    val uiState: StateFlow<ComposeUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    init {
        loadContacts()
    }

    private fun loadContacts() {
        viewModelScope.launch {
            _uiState.value = ComposeUiState.Loading
            try {
                val allContacts = contactsRepository.getAllContacts()
                _searchQuery.collect { query ->
                    val filtered = if (query.isBlank()) {
                        allContacts
                    } else {
                        allContacts.filter {
                            it.name.contains(query, ignoreCase = true) || it.phoneNumber.contains(query)
                        }
                    }
                    _uiState.value = ComposeUiState.Success(filtered)
                }
            } catch (e: Exception) {
                _uiState.value = ComposeUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
