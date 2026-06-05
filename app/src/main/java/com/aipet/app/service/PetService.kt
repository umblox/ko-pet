package com.aipet.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aipet.app.service.PetService
import com.aipet.app.ui.PetState
import com.aipet.app.ui.PetViewModel
import com.aipet.app.ui.PetWidgetView
import com.aipet.app.utils.FaceAnalyzer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.Executors

class PetService : LifecycleService(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var tts: TextToSpeech
    private val viewModel = PetViewModel()
    private var lastGreetingTime = 0L

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        initOverlayWindow()
        initTTS()
        startCameraAnalysis()
    }

    private fun startForegroundService() {
        val channelId = "pet_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "AI Pet Running", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AI Pet Aktif")
            .setContentText("Pet sedang mengawasi meja kerjamu...")
            .build()
        startForeground(1, notification)
    }

    private fun initOverlayWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        composeView = ComposeView(this).apply {
            setContent {
                val state = viewModel.petState.collectAsState()
                PetWidgetView(petState = state.value)
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 200
        }
        windowManager.addView(composeView, params)
    }

    private fun initTTS() {
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("id", "ID")
        }
    }

    private fun startCameraAnalysis() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), FaceAnalyzer { faceDetected ->
                if (faceDetected) {
                    val currentTime = System.currentTimeMillis()
                    // Cegah spam sapaan (beri jeda minimal 15 detik antar sapaan)
                    if (currentTime - lastGreetingTime > 15000) {
                        lastGreetingTime = currentTime
                        triggerProactiveGreeting()
                    }
                }
            })

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun triggerProactiveGreeting() {
        lifecycleScope.launch {
            viewModel.updateState(PetState.DETECTED)
            delay(500)
            viewModel.updateState(PetState.TALKING)
            tts.speak("Halo Bos! Senang melihatmu kembali bekerja.", TextToSpeech.QUEUE_FLUSH, null, null)
            
            while (tts.isSpeaking) { delay(100) }
            viewModel.updateState(PetState.IDLE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::composeView.isInitialized) windowManager.removeView(composeView)
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
    }
}
