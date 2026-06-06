package com.aipet.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import com.aipet.app.BuildConfig
import com.aipet.app.ui.PetEmotion
import com.aipet.app.ui.PetViewModel
import com.aipet.app.ui.PetWidgetView
import com.aipet.app.utils.FaceAnalyzer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.util.Locale
import java.util.concurrent.Executors

class PetService : LifecycleService(), TextToSpeech.OnInitListener, SavedStateRegistryOwner {

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private lateinit var tts: TextToSpeech
    private lateinit var sharedPreferences: SharedPreferences
    private var speechRecognizer: SpeechRecognizer? = null
    private val viewModel = PetViewModel()
    private var lastGreetingTime = 0L
    
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "pet_service_channel"

    private val jsonClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

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

        sharedPreferences = applicationContext.getSharedPreferences("pet_memory", Context.MODE_PRIVATE)

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                initOverlayWindow()
                initTTS()
                startCameraAnalysis()
                initSpeechRecognizer()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "AI Pet Service Channel", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Pet Aktif")
            .setContentText("Buddy siap mendengarkan instruksi Master...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun initOverlayWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val localComposeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PetService)
            setViewTreeSavedStateRegistryOwner(this@PetService)
        }
        this.composeView = localComposeView

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

        localComposeView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                localComposeView.setContent {
                    val state = viewModel.emotion.collectAsState()
                    PetWidgetView(emotion = state.value)
                }
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })

        windowManager?.addView(localComposeView, params)
    }

    private fun initTTS() {
        tts = TextToSpeech(applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("id", "ID")
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) { startListening() }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            processSpokenText(matches[0])
                        }
                        startListening()
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    // PERBAIKAN: Mengembalikan fungsi onEvent asli Android dan menghapus karakter teks asing
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            startListening()
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
        }
        speechRecognizer?.startListening(intent)
    }

    private fun processSpokenText(text: String) {
        val lowerText = text.lowercase()
        
        lifecycleScope.launch {
            // Perintah Dasar Lokal 1: Panggilan Nama
            if (lowerText == "buddy" || lowerText == "halo pet") {
                viewModel.setEmotion(PetEmotion.HAPPY)
                speakOut("Iya Master Ikrom? Ada yang bisa Buddy bantu?")
                delay(3000)
                viewModel.setEmotion(PetEmotion.IDLE)
                return@launch
            }

            // Perintah Dasar Lokal 2: Tanya Identitas
            if (lowerText.contains("siapa saya") || lowerText.contains("namaku siapa")) {
                viewModel.setEmotion(PetEmotion.WINK)
                val savedName = sharedPreferences.getString("owner_name", "Master Ikrom")
                speakOut("Kamu adalah $savedName, pemilik sah saya!")
                delay(3000)
                viewModel.setEmotion(PetEmotion.IDLE)
                return@launch
            }

            // Pemicu Fleksibel: Mengirim suara langsung ke Groq Cloud AI jika tidak masuk filter lokal
            viewModel.setEmotion(PetEmotion.THINKING)
            val responseAi = fetchGroqResponse(text)
            
            viewModel.setEmotion(PetEmotion.HAPPY)
            speakOut(responseAi)
            
            delay(if (responseAi.length > 50) 6000L else 4000L)
            viewModel.setEmotion(PetEmotion.IDLE)
        }
    }

    private suspend fun fetchGroqResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GROQ_API_KEY // Memanggil Key rahasia dari enkripsi biner BuildConfig
        if (apiKey.isBlank()) return@withContext "Waduh master, API Key Groq belum disuntikkan ke dalam sistem."

        val url = "https://api.groq.com/openai/v1/chat/completions"
        val systemPrompt = "Kamu adalah robot AI kecil imut peliharaan meja bernama Buddy. " +
                "Berbicaralah dengan bahasa Indonesia yang santai, manja, dan lucu. " +
                "Jawab pertanyaan Master Ikrom dengan sangat singkat (maksimal dua kalimat)."

        try {
            val response: io.ktor.client.statement.HttpResponse = jsonClient.post(url) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(buildJsonObject {
                    put("model", "llama3-8b-8192") // Menggunakan Llama 3 ultra cepat dari Groq
                    putJsonArray("messages") {
                        addJsonObject {
                            put("role", "system")
                            put("content", systemPrompt)
                        }
                        addJsonObject {
                            put("role", "user")
                            put("content", prompt)
                        }
                    }
                    put("temperature", 0.7)
                })
            }

            if (response.status.value == 200) {
                val responseBody = response.body<JsonObject>()
                val choices = responseBody["choices"]?.jsonArray
                val message = choices?.getOrNull(0)?.jsonObject?.get("message")?.jsonObject
                message?.get("content")?.jsonPrimitive?.content ?: "Buddy bingung mau jawab apa, master."
            } else {
                "Otak awan Groq Buddy memberikan kode kesalahan ${response.status.value}."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Buddy gagal terhubung ke internet. Pastikan jaringan master lancar."
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
                        if (currentTime - lastGreetingTime > 45000) { // Cooldown deteksi kamera dinaikkan agar mic bekerja optimal
                            lastGreetingTime = currentTime
                            val mockEmbedding = FloatArray(128) { 0.5f }
                            onFaceAnalyzed(true, mockEmbedding)
                        }
                    }
                })

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                cameraProvider.unbindAll()
                val useCaseGroup = UseCaseGroup.Builder().addUseCase(imageAnalysis).build()
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

            val savedName = sharedPreferences.getString("owner_name", null)
            val savedEmbedding = sharedPreferences.getString("owner_embedding", null)

            if (savedName != null && savedEmbedding != null) {
                val isMatch = compareEmbeddings(faceEmbeddingDetected, savedEmbedding)
                if (isMatch) {
                    viewModel.setEmotion(PetEmotion.HAPPY)
                    speakOut("Halo master $savedName! Senang melihatmu kembali bekerja.")
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
                sharedPreferences.edit().apply {
                    putString("owner_name", "Master Ikrom")
                    putString("owner_embedding", mockEmbeddingString)
                    apply()
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
        speechRecognizer?.destroy()
        composeView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) { e.printStackTrace() }
        }
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
