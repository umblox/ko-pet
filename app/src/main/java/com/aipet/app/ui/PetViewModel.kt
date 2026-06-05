package com.aipet.app.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class PetEmotion {
    IDLE, HAPPY, SAD, ANGRY, SURPRISED, THINKING, SLEEPY, WINK, BORED, LOADING
}

class PetViewModel : ViewModel() {
    private val _emotion = MutableStateFlow(PetEmotion.IDLE)
    val emotion: StateFlow<PetEmotion> = _emotion

    fun setEmotion(newEmotion: PetEmotion) {
        _emotion.value = newEmotion
    }
}
