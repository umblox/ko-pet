package com.aipet.app.ui

import androidx.lifecycle.ViewModel
import kotlinx.flow.MutableStateFlow
import kotlinx.flow.StateFlow

enum class PetEmotion {
    IDLE,       // Standby berkedip normal
    HAPPY,      // Tersenyum lebar (Mata melengkung ke atas)
    SAD,        // Sedih (Mata sayu melengkung ke bawah)
    ANGRY,      // Marah (Mata miring tajam ke dalam)
    SURPRISED,   // Kaget/Bingung melihat orang tidak dikenal (Mata bulat besar)
    THINKING,   // Berpikir/Proses mengenali (Mata menyipit)
    SLEEPY,     // Mengantuk (Mata setengah tertutup)
    WINK,       // Berkedip sebelah (Genit/Bercanda)
    BORED,      // Bosan dicuekin (Mata berbentuk garis horizontal datar)
    LOADING     // Sedang memproses database
}

class PetViewModel : ViewModel() {
    private val _emotion = MutableStateFlow(PetEmotion.IDLE)
    val emotion: StateFlow<PetEmotion> = _emotion

    fun setEmotion(newEmotion: PetEmotion) {
        _emotion.value = newEmotion
    }
}
