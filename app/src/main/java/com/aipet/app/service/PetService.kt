package com.aipet.app.service

import android.speech.tts.TextToSpeech
import androidx.lifecycle.lifecycleScope
import com.aipet.app.data.UserMemory
import com.aipet.app.ui.PetEmotion
import com.aipet.app.ui.PetViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Logika pemrosesan pengenalan pada PetService yang di-update
class PetServiceUpdated : PetService() {

    private val petViewModel = PetViewModel()
    private var isOwnerRecognized = false
    private var ownerName: String? = null

    // Pemicu skenario interaksi proaktif berbasis memori wajah
    fun onFaceAnalyzed(isFaceDetected: Boolean, faceEmbeddingDetected: FloatArray?) {
        if (!isFaceDetected || faceEmbeddingDetected == null) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastGreetingTime < 20000) return // Cooldown 20 detik
        lastGreetingTime = currentTime

        lifecycleScope.launch {
            petViewModel.setEmotion(PetEmotion.THINKING) // Mimik mendeteksi/mengingat
            delay(1500)

            val savedOwner = database.memoryDao().getOwner()

            if (savedOwner != null) {
                // Bandingkan wajah terdeteksi dengan database (Euclidean Distance simulative)
                val isMatch = compareEmbeddings(faceEmbeddingDetected, savedOwner.faceEmbedding)
                
                if (isMatch) {
                    // Skenario 1: Mengenali Pemilik
                    petViewModel.setEmotion(PetEmotion.HAPPY)
                    ownerName = savedOwner.name
                    speakOut("Halo master $ownerName! Senang melihatmu kembali bekerja. Semangat ya!")
                    delay(3000)
                    petViewModel.setEmotion(PetEmotion.IDLE)
                } else {
                    // Skenario 2: Melihat orang tidak dikenal saat pemilik sudah terdaftar
                    petViewModel.setEmotion(PetEmotion.ANGRY)
                    speakOut("Hei, kamu siapa? Kamu bukan bos saya! Tolong jangan sembarangan menyentuh meja ini.")
                    delay(4000)
                    petViewModel.setEmotion(PetEmotion.BORED)
                }
            } else {
                // Skenario 3: Database kosong / Pet pertama kali dinyalakan (Belum kenal siapa-siapa)
                petViewModel.setEmotion(PetEmotion.SURPRISED)
                speakOut("Wah, halo! Saya pet baru di sini tapi saya belum tahu siapa kamu. Bolehkah kamu memperkenalkan dirimu?")
                
                // Simulasikan mendengarkan input suara nama (Mocking registration)
                delay(4000)
                petViewModel.setEmotion(PetEmotion.LOADING)
                
                // Mendaftarkan otomatis wajah saat itu sebagai pemilik baru "User Master"
                val mockEmbeddingString = faceEmbeddingDetected.joinToString(",")
                database.memoryDao().saveOwner(UserMemory(name = "Master Ikrom", faceEmbedding = mockEmbeddingString))
                
                petViewModel.setEmotion(PetEmotion.WINK)
                speakOut("Selesai! Sekarang saya memprogram memori saya. Mulai detik ini, kamu adalah Master Ikrom, pemilik sah saya!")
                delay(3000)
                petViewModel.setEmotion(PetEmotion.IDLE)
            }
        }
    }

    private fun compareEmbeddings(current: FloatArray, savedString: String): Boolean {
        // Logika perbandingan vektor jarak kedekatan matematika wajah
        val saved = savedString.split(",").map { it.toFloat() }.toFloatArray()
        var distance = 0f
        for (i in current.indices) {
            val diff = current[i] - saved[i]
            distance += diff * diff
        }
        val threshold = 0.75f // Standar akurasi kecocokan struktur biometrik wajah
        return Math.sqrt(distance.toDouble()) < threshold
    }

    private fun speakOut(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
}
