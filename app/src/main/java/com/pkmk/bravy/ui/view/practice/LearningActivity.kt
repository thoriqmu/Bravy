package com.pkmk.bravy.ui.view.practice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.pkmk.bravy.R
import com.pkmk.bravy.databinding.ActivityLearningBinding
import com.pkmk.bravy.ui.adapter.LearningPagerAdapter
import com.pkmk.bravy.ui.adapter.SectionListAdapter
import com.pkmk.bravy.ui.viewmodel.LearningViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class LearningActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLearningBinding
    private val viewModel: LearningViewModel by viewModels()

    private lateinit var sectionsAdapter: SectionListAdapter
    private lateinit var pagerAdapter: LearningPagerAdapter

    private var levelId: String? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var countDownTimer: CountDownTimer? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startSpeechRecognition()
            } else {
                Toast.makeText(this, "Izin mikrofon diperlukan untuk latihan berbicara", Toast.LENGTH_SHORT).show()
            }
        }

    private val analysisResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.hideAnalysisButton() // Sembunyikan tombol setelah kembali
        if (result.resultCode == Activity.RESULT_OK) {
            val confidencePoints = result.data?.getIntExtra("CONFIDENCE_POINTS", 0) ?: 0
            val speechPoints = result.data?.getIntExtra("SPEECH_POINTS", 0) ?: 0

            // Kirim hasil skor ke ViewModel untuk diproses
            viewModel.postAnalysisResult(confidencePoints, speechPoints)
        } else {
            Toast.makeText(this, "Analysis cancelled.", Toast.LENGTH_SHORT).show()
            viewModel.postAnalysisResult(0, 0) // Kirim skor 0 jika dibatalkan
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLearningBinding.inflate(layoutInflater)
        setContentView(binding.root)

        levelId = intent.getStringExtra("LEVEL_ID")
        if (levelId == null) {
            Toast.makeText(this, "Level tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        observeViewModel()
        viewModel.resetScores() // <-- TAMBAHKAN INI
        viewModel.loadLevelData(levelId!!)
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        // Setup untuk daftar section di bawah
        sectionsAdapter = SectionListAdapter { section, position ->
            if (!section.isLocked) {
                binding.viewPagerContent.currentItem = position
            } else {
                Toast.makeText(this, "Selesaikan section sebelumnya terlebih dahulu", Toast.LENGTH_SHORT).show()
            }
        }
        binding.rvSectionList.apply {
            adapter = sectionsAdapter
            layoutManager = LinearLayoutManager(this@LearningActivity, LinearLayoutManager.VERTICAL, false)
        }

        // Setup untuk ViewPager yang menampilkan fragment video/interaksi
        pagerAdapter = LearningPagerAdapter(this)
        binding.viewPagerContent.adapter = pagerAdapter
        binding.viewPagerContent.isUserInputEnabled = false // Agar navigasi hanya melalui klik

        binding.viewPagerContent.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                sectionsAdapter.setCurrentSection(position)
            }
        })

        binding.btnMic.setOnClickListener { handleMicClick() }

        binding.btnStartAnalysis.setOnClickListener {
            val scene = viewModel.getCurrentShadowingScene()
            if (scene != null) {
                val intent = Intent(this, AnalysisActivity::class.java).apply {
                    putExtra("KEY_SENTENCE", scene.keySentence)
                }
                analysisResultLauncher.launch(intent)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.learningLevel.observe(this) { level ->
            level?.let {
                binding.tvLevelTitle.text = it.title
                binding.tvMaterialTitle.text = it.title
                binding.tvAboutMaterial.text = it.description
            }
        }

        viewModel.sections.observe(this) { sections ->
            sectionsAdapter.submitList(sections)
            pagerAdapter.setSections(sections)
        }

        viewModel.showMicControls.observe(this) { (isVisible, duration) ->
            binding.layoutMicControls.isVisible = isVisible
            if (!isVisible) {
                // Jika UI disembunyikan, selalu batalkan timer
                countDownTimer?.cancel()
                binding.tvCountdown.text = "" // Reset teks countdown
            }
        }

        viewModel.finalPracticeScore.observe(this) { finalScore ->
            // Tampilkan dialog atau pesan dengan skor akhir
            Toast.makeText(this, "Practice Complete! Your average score: $finalScore / 15", Toast.LENGTH_LONG).show()
        }

        viewModel.showAnalysisButton.observe(this) { isVisible ->
            binding.layoutAnalysisControls.isVisible = isVisible
        }

        viewModel.error.observe(this) { errorMsg ->
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    fun onSectionCompleted(sectionId: String) {
        viewModel.completeSection(levelId!!, sectionId)
        Toast.makeText(this, "Section Selesai! Poin ditambahkan.", Toast.LENGTH_SHORT).show()
    }

    private fun handleMicClick() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // Dapatkan durasi dari ViewModel
            val duration = viewModel.showMicControls.value?.second ?: 20
            startCountdown(duration) // Mulai countdown di sini
            startSpeechRecognition()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startCountdown(durationSeconds: Int) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(durationSeconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                binding.tvCountdown.text = String.format(Locale.getDefault(), "00:%02d", secondsRemaining)
            }

            override fun onFinish() {
                binding.tvCountdown.text = "00:00"
                speechRecognizer?.stopListening()
            }
        }.start()
    }

    private fun startSpeechRecognition() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    countDownTimer?.cancel()
                }

                override fun onError(error: Int) {
                    // Jika ada error (termasuk tidak ada suara), kirim hasil 'no_speech'
                    viewModel.postSpeechResult("no_speech")
                }

                override fun onResults(results: Bundle?) {
                    val spokenText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (spokenText.isNullOrEmpty()) {
                        viewModel.postSpeechResult("no_speech")
                    } else {
                        // Untuk saat ini, kita anggap semua hasil adalah "fluent"
                        viewModel.postSpeechResult("fluent")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US") // Set ke Bahasa Inggris
        }
        speechRecognizer?.startListening(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        countDownTimer?.cancel()
    }
}