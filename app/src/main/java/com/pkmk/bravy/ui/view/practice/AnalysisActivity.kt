package com.pkmk.bravy.ui.view.practice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.pkmk.bravy.databinding.ActivityAnalysisBinding
import com.pkmk.bravy.ml.AnxietyClassifier
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AnalysisActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalysisBinding
    private lateinit var cameraExecutor: ExecutorService
    private var anxietyClassifier: AnxietyClassifier? = null
    private var keySentence: String? = null

    private var totalConfidenceScore = 0
    private var analysisFrameCount = 0
    private var speechRecognizer: SpeechRecognizer? = null

    private var isAnalyzing = false
    private var hasFinished = false
    private var lastSpokenText: String? = null

    private var analysisTimer: CountDownTimer? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnalysisBinding.inflate(layoutInflater)
        setContentView(binding.root)

        keySentence = intent.getStringExtra("KEY_SENTENCE")
        if (keySentence == null) {
            finishWithResult(0, 0)
            return
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        anxietyClassifier = AnxietyClassifier(this)

        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (isAnalyzing) {
                            val bitmap = imageProxy.toBitmap()
                            if (bitmap != null) {
                                classifyFace(bitmap)
                            }
                        }
                        imageProxy.close()
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                startPreparationCountdown()
            } catch (e: Exception) {
                Log.e("AnalysisActivity", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun classifyFace(bitmap: Bitmap) {
        val confidenceScore = anxietyClassifier?.classify(bitmap) ?: 0
        totalConfidenceScore += confidenceScore
        analysisFrameCount++
    }

    private fun startPreparationCountdown() {
        binding.tvInstruction.text = "Get ready... Position your face in the oval."
        object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (isFinishing) return
                binding.tvCountdown.text = (millisUntilFinished / 1000 + 1).toString()
            }
            override fun onFinish() {
                if (isFinishing) return
                startAnalysisPhase()
            }
        }.start()
    }

    private fun startAnalysisPhase() {
        isAnalyzing = true
        startSpeechRecognition()

        binding.tvInstruction.text = "Speak now!"
        analysisTimer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (isFinishing) return
                binding.tvCountdown.text = (millisUntilFinished / 1000 + 1).toString()
            }
            override fun onFinish() {
                if (isFinishing) return
                binding.tvInstruction.text = "Processing..."
                Handler(Looper.getMainLooper()).postDelayed({
                    if (speechRecognizer != null) {
                        speechRecognizer?.stopListening()
                    }
                }, 1200)
            }
        }.start()
    }

    private fun startSpeechRecognition() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak the sentence now")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isAnalyzing = false
                binding.tvInstruction.text = "Processing..."
            }

            override fun onError(error: Int) {
                Log.e("AnalysisActivity", "SpeechRecognizer Error: $error")
                if (!hasFinished) {
                    hasFinished = true
                    calculateScores(lastSpokenText) // Gunakan hasil parsial terakhir jika ada error
                }
            }

            override fun onResults(results: Bundle?) {
                if (!hasFinished) {
                    hasFinished = true
                    // --- PERBAIKAN KUNCI DI SINI ---
                    val spokenText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                    Log.d("AnalysisActivity", "Final Speech Result: $spokenText")
                    calculateScores(spokenText)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // --- PERBAIKAN KUNCI DI SINI ---
                lastSpokenText = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                Log.d("AnalysisActivity", "Partial Speech Result: $lastSpokenText")
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun calculateScores(spokenText: String?) {
        // Hentikan proses kamera untuk menghemat daya
        ProcessCameraProvider.getInstance(this).get().unbindAll()

        // 1. Hitung skor rata-rata dari analisis wajah (rentang 1-5)
        val averageConfidence = if (analysisFrameCount > 0) {
            Math.round(totalConfidenceScore.toFloat() / analysisFrameCount)
        } else {
            1 // Skor minimum jika tidak ada frame yang teranalisis
        }

        // 2. Hitung skor ucapan (rentang 0, 5, 10)
        val speechPoints = if (spokenText.isNullOrBlank()) {
            0
        } else {
            // --- LOGIKA PERBANDINGAN YANG DIPERBAIKI ---

            // Bersihkan dan pecah kalimat referensi dari database menjadi kata-kata unik
            val referenceWords = keySentence!!
                .lowercase(Locale.ROOT)
                .replace(Regex("[^a-z\\s]"), "") // Hanya sisakan huruf dan spasi
                .split("\\s+".toRegex()) // Pecah berdasarkan satu atau lebih spasi
                .filter { it.isNotBlank() } // Hapus elemen kosong
                .toSet() // Jadikan Set untuk perbandingan unik

            // Bersihkan dan pecah kalimat yang diucapkan pengguna
            val spokenWords = spokenText
                .lowercase(Locale.ROOT)
                .replace(Regex("[^a-z\\s]"), "")
                .split("\\s+".toRegex())
                .filter { it.isNotBlank() }
                .toSet()

            // Hitung berapa banyak kata dari referensi yang ada di ucapan pengguna
            val matchedWords = referenceWords.intersect(spokenWords).count()

            // Hitung persentase akurasi
            val accuracy = if (referenceWords.isNotEmpty()) {
                matchedWords.toFloat() / referenceWords.size.toFloat()
            } else {
                0f // Hindari pembagian dengan nol jika kalimat kunci kosong
            }

            Log.d("AnalysisActivity", "Speech Accuracy: $accuracy ($matchedWords / ${referenceWords.size})")
            Log.d("AnalysisActivity", "Reference: ${referenceWords.joinToString(" ")}")
            Log.d("AnalysisActivity", "Spoken: ${spokenWords.joinToString(" ")}")

            // Tentukan skor berdasarkan persentase
            when {
                accuracy >= 0.9 -> 10 // Sangat tepat
                accuracy >= 0.5 -> 5  // Cukup tepat
                else -> 0             // Tidak tepat
            }
        }

        Log.d("AnalysisActivity", "Final Scores -> Confidence: $averageConfidence, Speech: $speechPoints")
        finishWithResult(averageConfidence, speechPoints)
    }

    private fun finishWithResult(confidencePoints: Int, speechPoints: Int) {
        val resultIntent = Intent().apply {
            putExtra("CONFIDENCE_POINTS", confidencePoints)
            putExtra("SPEECH_POINTS", speechPoints)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisTimer?.cancel()
        cameraExecutor.shutdown()
        speechRecognizer?.destroy()
        anxietyClassifier?.close()
    }
}