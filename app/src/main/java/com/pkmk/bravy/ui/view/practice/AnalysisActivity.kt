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
import kotlin.math.ceil
import kotlin.math.floor

class AnalysisActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnalysisBinding
    private lateinit var cameraExecutor: ExecutorService
    private var anxietyClassifier: AnxietyClassifier? = null

    private var practiceMode: String? = null
    private var keySentence: String? = null // Untuk Level 1
    private var expectedAnswer: String? = null // Untuk Level 2
    private var options: List<String>? = null // Untuk Level 2

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

        // Ambil data dari Intent untuk menentukan mode
        practiceMode = intent.getStringExtra("PRACTICE_MODE")
        keySentence = intent.getStringExtra("KEY_SENTENCE")
        expectedAnswer = intent.getStringExtra("EXPECTED_ANSWER")
        options = intent.getStringArrayListExtra("OPTIONS")
        val promptText = intent.getStringExtra("PROMPT_TEXT")

        binding.tvInstruction.text = promptText ?: "Speak now!"

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
                if (!hasFinished) {
                    hasFinished = true
                    calculateScores(lastSpokenText)
                }
            }
            override fun onResults(results: Bundle?) {
                if (!hasFinished) {
                    hasFinished = true
                    val spokenText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                    calculateScores(spokenText)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                lastSpokenText = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    private fun calculateScores(spokenText: String?) {
        ProcessCameraProvider.getInstance(this).get().unbindAll()

        val averageConfidence = if (analysisFrameCount > 0) {
            (totalConfidenceScore.toFloat() / analysisFrameCount).coerceIn(1f, 5f).toInt()
        } else { 1 }

        val speechPoints = when (practiceMode) {
            "RECONSTRUCTION" -> calculateReconstructionScore(spokenText)
            else -> calculateShadowingScore(spokenText) // Default ke mode Level 1
        }

        Log.d("AnalysisActivity", "Final Scores -> Confidence: $averageConfidence, Speech: $speechPoints")
        finishWithResult(averageConfidence, speechPoints)
    }

    private fun calculateReconstructionScore(spokenText: String?): Int {
        if (spokenText.isNullOrBlank() || expectedAnswer.isNullOrBlank() || options.isNullOrEmpty()) {
            return 0
        }

        val normalizedSpoken = spokenText.lowercase(Locale.US)

        // 1. Skor Opsi Jawaban (0 atau 5 poin)
        val correctOption = options?.find { option ->
            expectedAnswer!!.lowercase(Locale.US).contains(option.lowercase(Locale.US))
        }
        val optionScore = if (correctOption != null && normalizedSpoken.contains(correctOption.lowercase(Locale.US))) {
            5
        } else {
            0
        }

        // 2. Skor Kemiripan Kalimat (0 sampai 5 poin)
        val similarityScore = calculateSentenceSimilarity(spokenText, expectedAnswer!!)

        Log.d("AnalysisActivity", "Reconstruction -> Option Score: $optionScore, Similarity Score: $similarityScore")
        return optionScore + similarityScore
    }

    private fun calculateShadowingScore(spokenText: String?): Int {
        if (spokenText.isNullOrBlank() || keySentence.isNullOrBlank()) {
            return 0
        }
        val similarity = calculateSentenceSimilarity(spokenText, keySentence!!)
        return (similarity * 2) // Skala 0-5 menjadi 0-10
    }

    private fun calculateSentenceSimilarity(spoken: String, reference: String): Int {
        val referenceWords = reference.lowercase(Locale.US).split("\\s+".toRegex()).toSet()
        val spokenWords = spoken.lowercase(Locale.US).split("\\s+".toRegex()).toSet()
        val matchedWords = referenceWords.intersect(spokenWords).count()
        val accuracy = if (referenceWords.isNotEmpty()) matchedWords.toFloat() / referenceWords.size else 0f

        // Konversi akurasi (0.0 - 1.0) ke skor (0 - 5)
        return (accuracy * 5).coerceIn(0f, 5f).toInt()
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