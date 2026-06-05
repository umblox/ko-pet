package com.aipet.app.ui

import androidx.lifecycle.ViewModel
import kotlinx.flow.MutableStateFlow
import kotlinx.flow.StateFlow

enum class PetState { IDLE, DETECTED, TALKING }

class PetViewModel : ViewModel() {
    private val _petState = MutableStateFlow(PetState.IDLE)
    val petState: StateFlow<PetState> = _petState

    fun updateState(newState: PetState) {
        _petState.value = newState
    }
}

