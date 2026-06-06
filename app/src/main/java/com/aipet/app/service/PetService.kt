package com.aipet.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aipet.app.data.AppDatabase
import com.aipet.app.data.UserMemory
import com.aipet.app.ui.PetEmotion
import com.aipet.app.ui.PetViewModel
import com.aipet.app.ui.PetWidgetView
import com.aipet.app.utils.FaceAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.Executors

class PetService : LifecycleService(), TextToSpeech.OnInitListener, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var tts: TextToSpeech
    private lateinit var database: AppDatabase
    private val viewModel = PetViewModel()
    private var lastGreetingTime = 0L
    
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "pet_service_channel"

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(Bundle())
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        lifecycleScope.launch(Dispatchers.IO) {
            database = AppDatabase.getDatabase(applicationContext)
            withContext(Dispatchers.Main) {
                try {
                    initOverlayWindow()
                    initTTS()
                    startCameraAnalysis()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "AI Pet Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Pet Aktif")
            .setContentText("Pet sedang mengawasi meja kerjamu...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun initOverlayWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        // Inisialisasi basis View murni tanpa langsung memanggil setContent
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PetService)
            setViewTreeSavedStateRegistryOwner(this@PetService)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 200
        }

        // PERBAIKAN UTAMA: Tambahkan listener untuk mendeteksi kapan View benar-benar menempel di layar OS
        composeView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                // Eksekusi perwujudan UI Compose HANYA setelah jendela terikat fisik di layar
                composeView.setContent {
                    val state = viewModel.emotion.collectAsState()
                    PetWidgetView(emotion = state.value)
                }
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })

        // Masukkan View ke WindowManager terlebih dahulu agar proses pengikatan berjalan
        windowManager.addView(composeView, params)
    }

    private fun initTTS() {
        tts = TextToSpeech(applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("id", "ID")
        }
    }

    private fun startCameraAnalysis() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(applicationContext)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), FaceAnalyzer { faceDetected ->
                    if (faceDetected) {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastGreetingTime > 20000) {
                            lastGreetingTime = currentTime
                            val mockEmbedding = FloatArray(128) { 0.5f }
                            onFaceAnalyzed(true, mockEmbedding)
                        }
                    }
                })

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                cameraProvider.unbindAll()
                
                val useCaseGroup = UseCaseGroup.Builder()
                    .addUseCase(imageAnalysis)
                    .build()
                cameraProvider.bindToLifecycle(this, cameraSelector, useCaseGroup)
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(applicationContext))
    }

    private fun onFaceAnalyzed(isFaceDetected: Boolean, faceEmbeddingDetected: FloatArray?) {
        if (!isFaceDetected || faceEmbeddingDetected == null) return

        lifecycleScope.launch {
            viewModel.setEmotion(PetEmotion.THINKING)
            delay(1500)

            val savedOwner = withContext(Dispatchers.IO) {
                database.memoryDao().getOwner()
            }

            if (savedOwner != null) {
                val isMatch = compareEmbeddings(faceEmbeddingDetected, savedOwner.faceEmbedding)
                if (isMatch) {
                    viewModel.setEmotion(PetEmotion.HAPPY)
                    speakOut("Halo master ${savedOwner.name}! Senang melihatmu kembali bekerja.")
                    delay(3000)
                    viewModel.setEmotion(PetEmotion.IDLE)
                } else {
                    viewModel.setEmotion(PetEmotion.ANGRY)
                    speakOut("Hei, kamu siapa? Kamu bukan bos saya!")
                    delay(4000)
                    viewModel.setEmotion(PetEmotion.BORED)
                }
            } else {
                viewModel.setEmotion(PetEmotion.SURPRISED)
                speakOut("Wah, halo! Saya pet baru di sini. Kenalkan, nama kamu siapa?")
                delay(4000)
                viewModel.setEmotion(PetEmotion.LOADING)
                
                val mockEmbeddingString = faceEmbeddingDetected.joinToString(",")
                
                withContext(Dispatchers.IO) {
                    database.memoryDao().saveOwner(UserMemory(name = "Master Ikrom", faceEmbedding = mockEmbeddingString))
                }
                
                viewModel.setEmotion(PetEmotion.WINK)
                speakOut("Selesai! Mulai detik ini, kamu adalah Master Ikrom, pemilik sah saya!")
                delay(3000)
                viewModel.setEmotion(PetEmotion.IDLE)
            }
        }
    }

    private fun compareEmbeddings(current: FloatArray, savedString: String): Boolean {
        val saved = savedString.split(",").map { it.toFloat() }.toFloatArray()
        var distance = 0f
        for (i in current.indices) {
            val diff = current[i] - saved[i]
            distance += diff * diff
        }
        return Math.sqrt(distance.toDouble()) < 0.75
    }

    private fun speakOut(text: String) {
        if (::tts.isInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onDestroy() {
        if (::composeView.isInitialized) {
            try { windowManager.removeView(composeView) } catch (e: Exception) { e.printStackTrace() }
        }
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
