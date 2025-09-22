package com.pkmk.bravy.ui.view.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.pkmk.bravy.R
import com.pkmk.bravy.databinding.ActivityDailyMissionBinding
import com.pkmk.bravy.databinding.DialogMissionResultBinding
import com.pkmk.bravy.ml.AnxietyClassifier // Menggunakan classifier yang sama seperti AnalysisActivity
import com.pkmk.bravy.ui.viewmodel.DailyMissionViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper
import com.pkmk.bravy.databinding.DialogInactivityPromptBinding

@AndroidEntryPoint
class DailyMissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDailyMissionBinding
    private val viewModel: DailyMissionViewModel by viewModels()
    private lateinit var cameraExecutor: ExecutorService
    private var anxietyClassifier: AnxietyClassifier? = null

    private var isAnalyzing = false
    private var totalConfidenceScore = 0
    private var analysisFrameCount = 0

    private var prepCountdown: CountDownTimer? = null
    private var missionCountdown: CountDownTimer? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var spokenText: String = ""

    private val silenceHandler = Handler(Looper.getMainLooper())
    private var inactivityDialog: Dialog? = null
    private val silenceRunnable = Runnable { showInactivityDialog() }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.CAMERA] == true && permissions[Manifest.permission.RECORD_AUDIO] == true) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera and Microphone permissions are required.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDailyMissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        lifecycleScope.launch(Dispatchers.IO) {
            // PERBAIKAN 1: Inisialisasi classifier di background thread
            anxietyClassifier = AnxietyClassifier(this@DailyMissionActivity)
        }

        requestMultiplePermissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    @SuppressLint("UnsafeOptInUsageError")
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
                                // PERBAIKAN 2: Pastikan klasifikasi berjalan di dalam coroutine
                                lifecycleScope.launch(Dispatchers.IO) {
                                    classifyFace(bitmap)
                                }
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
                Log.e("DailyMissionActivity", "Use case binding failed", e)
                Toast.makeText(this, "Failed to start camera.", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun classifyFace(bitmap: Bitmap) {
        anxietyClassifier?.let { classifier ->
            val confidenceScore = classifier.classify(bitmap)
            totalConfidenceScore += confidenceScore
            analysisFrameCount++
        }
    }

    private fun startPreparationCountdown() {
        binding.tvCountdown.visibility = View.VISIBLE
        binding.tvInstruction.text = "Position your face in the oval"
        prepCountdown = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (isFinishing) return
                binding.tvCountdown.text = "${millisUntilFinished / 1000 + 1}"
            }

            override fun onFinish() {
                if (isFinishing) return
                startMissionCountdown()
            }
        }.start()
    }

    private fun startMissionCountdown() {
        isAnalyzing = true // <-- Analisis dimulai
        startSpeechRecognition()
        resetSilenceTimer()

        val topic = intent.getStringExtra("TOPIC") ?: "Speak about your day!"
        binding.tvTopic.text = topic
        binding.tvTopic.visibility = View.VISIBLE
        binding.tvInstruction.text = "Start Speaking!"

        missionCountdown = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (isFinishing) return
                binding.tvCountdown.text = "${millisUntilFinished / 1000}"
            }

            override fun onFinish() {
                if (isFinishing) return
                stopSilenceTimer()
                isAnalyzing = false
                speechRecognizer?.stopListening()
                ProcessCameraProvider.getInstance(this@DailyMissionActivity).get().unbindAll()
                binding.tvCountdown.visibility = View.GONE
                binding.tvInstruction.text = "Analyzing your result..."
                calculateAndShowResult()
            }
        }.start()
    }

    private fun startSpeechRecognition() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        spokenText = matches[0]
                        Log.d("SpeechLog", "Final Text: $spokenText")
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        spokenText = matches[0]
                        Log.d("SpeechLog", "Partial Text: $spokenText")
                    }
                }
                override fun onError(error: Int) { Log.e("SpeechRecognizer", "Error: $error") }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    if (rmsdB > 2.5f) {
                        resetSilenceTimer()
                    }
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(intent)
        }
    }

    private fun resetSilenceTimer() {
        silenceHandler.removeCallbacks(silenceRunnable)
        silenceHandler.postDelayed(silenceRunnable, 7000) // 7 detik
    }

    private fun stopSilenceTimer() {
        silenceHandler.removeCallbacks(silenceRunnable)
    }

    private fun showInactivityDialog() {
        if (isFinishing || (inactivityDialog?.isShowing == true)) {
            return
        }

        missionCountdown?.cancel()
        isAnalyzing = false
        stopSilenceTimer()

        val dialogBinding = DialogInactivityPromptBinding.inflate(LayoutInflater.from(this))
        inactivityDialog = Dialog(this, R.style.Theme_Bravy).apply { // Gunakan constructor default, atau R.style.Theme_Bravy jika sudah di-setting transparan
            setContentView(dialogBinding.root)
            setCancelable(false)

            // ================== INI KUNCINYA ==================
            // Membuat window dialog menjadi transparan
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            // ====================================================
        }

        dialogBinding.btnExitSession.setOnClickListener {
            inactivityDialog?.dismiss()
            finish()
        }

        dialogBinding.btnRetrySession.setOnClickListener {
            inactivityDialog?.dismiss()

            val remainingTime = binding.tvCountdown.text.toString().toLongOrNull() ?: 0
            if (remainingTime > 0) {
                isAnalyzing = true
                resumeMissionCountdown(remainingTime * 1000)
            }
            resetSilenceTimer()
        }

        inactivityDialog?.show()
    }

    // Fungsi baru untuk melanjutkan countdown
    private fun resumeMissionCountdown(remainingMillis: Long) {
        missionCountdown = object : CountDownTimer(remainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (isFinishing) return
                binding.tvCountdown.text = "${millisUntilFinished / 1000}"
            }

            override fun onFinish() {
                if (isFinishing) return
                stopSilenceTimer()
                isAnalyzing = false
                speechRecognizer?.stopListening()
                ProcessCameraProvider.getInstance(this@DailyMissionActivity).get().unbindAll()
                binding.tvCountdown.visibility = View.GONE
                binding.tvInstruction.text = "Analyzing your result..."
                calculateAndShowResult()
            }
        }.start()
    }

    private fun calculateAndShowResult() {
        val averageConfidence = if (analysisFrameCount > 0) {
            // Skala 1-5 dari classifier Anda
            (totalConfidenceScore.toFloat() / analysisFrameCount)
        } else {
            1f // Skor minimum jika tidak ada frame
        }

        // Konversi skor 1-5 ke persentase 0-100
        val confidencePercent = ((averageConfidence - 1) / 4 * 100).toInt()

        val emotion = when {
            averageConfidence > 3.5 -> "Happy"    // Skor tinggi -> emosi positif
            averageConfidence > 2.0 -> "Neutral"  // Skor sedang -> netral
            else -> "Sad"                         // Skor rendah -> emosi negatif
        }

        val wordCount = if (spokenText.isBlank()) 0 else spokenText.split("\\s+".toRegex()).size
        Log.d("SpeechLog", "Total words counted: $wordCount")

        showResultDialog(emotion, confidencePercent, wordCount)
    }

    private fun showResultDialog(emotion: String, confidence: Int, wordCount: Int) {
        if (isFinishing) return // Tambahkan pengecekan ini untuk mencegah dialog muncul di activity yang sudah hancur

        val dialogBinding = DialogMissionResultBinding.inflate(LayoutInflater.from(this))
        val dialog = Dialog(this, R.style.Theme_Bravy)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.tvResultEmotionName.text = "You seem $emotion"
        dialogBinding.tvResultConfidence.text = "(Confidence: $confidence%)"

        val emotionDrawable = when (emotion) {
            "Happy" -> R.drawable.vector_happy
            "Neutral" -> R.drawable.vector_neutral
            else -> R.drawable.vector_sad
        }
        dialogBinding.ivResultEmotion.setImageResource(emotionDrawable)

        dialogBinding.btnCloseDialog.setOnClickListener {
            dialog.dismiss() // Tutup dialog dulu
            saveAndFinish(emotion, confidence, wordCount) // Panggil fungsi baru
        }

        dialog.setCancelable(false)
        dialog.show()
    }

    // PERBAIKAN 3: Fungsi baru untuk menyimpan hasil dan menutup activity dengan aman
    private fun saveAndFinish(emotion: String, confidence: Int, wordCount: Int) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            finish() // Jika tidak ada user, langsung tutup
            return
        }

        lifecycleScope.launch {
            try {
                viewModel.completeSpeakingMission(uid, emotion, confidence, wordCount)
                // Pindah ke UI thread untuk menampilkan Toast
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DailyMissionActivity, "Mission saved!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("DailyMissionActivity", "Failed to save mission", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DailyMissionActivity, "Failed to save mission.", Toast.LENGTH_SHORT).show()
                }
            } finally {
                // Pastikan finish() dipanggil di UI thread setelah semua selesai
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSilenceTimer() // Pastikan handler dihentikan
        inactivityDialog?.dismiss() // Hapus dialog jika masih ada
        prepCountdown?.cancel()
        missionCountdown?.cancel()
        if (::cameraExecutor.isInitialized && !cameraExecutor.isShutdown) {
            cameraExecutor.shutdown()
        }
        anxietyClassifier?.close()
        speechRecognizer?.destroy()
    }
}