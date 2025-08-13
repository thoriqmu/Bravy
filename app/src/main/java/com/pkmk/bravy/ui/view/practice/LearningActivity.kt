package com.pkmk.bravy.ui.view.practice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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
    private lateinit var sectionListAdapter: SectionListAdapter
    private lateinit var pagerAdapter: LearningPagerAdapter

    private var isRecording = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var countDownTimer: CountDownTimer? = null

    private var currentLevelId: String = "very_anxious" // Ganti dengan ID dari intent

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Izin diberikan, mulai merekam
                startRecording()
            } else {
                // Izin ditolak, beri tahu pengguna
                Toast.makeText(this, "Audio permission is required for this feature.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLearningBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentLevelId = intent.getStringExtra("LEVEL_ID") ?: "level_1"

        setupToolbar()
        setupRecyclerView()
        setupMicControls()
        setupObservers()

        viewModel.loadLevelData(currentLevelId)
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        sectionListAdapter = SectionListAdapter { section ->
            if (!section.isLocked) {
                // Pindah ke halaman ViewPager yang sesuai
                binding.viewPagerContent.currentItem = section.order - 1
            } else {
                Toast.makeText(this, "Complete the previous section first!", Toast.LENGTH_SHORT).show()
            }
        }
        binding.rvSectionList.adapter = sectionListAdapter
    }

    private fun setupViewPager(sections: List<com.pkmk.bravy.data.model.LearningSection>) {
        pagerAdapter = LearningPagerAdapter(this, sections)
        binding.viewPagerContent.adapter = pagerAdapter
        binding.viewPagerContent.isUserInputEnabled = false // Mencegah swipe manual
    }

    private fun setupMicControls() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val speechListener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                // Tidak perlu mengubah state di sini, biarkan onResults/onError yang menangani
            }

            override fun onError(error: Int) {
                if (isRecording) { // Hanya proses jika error terjadi saat sedang merekam
                    viewModel.postSpeechResult("no_speech")
                    isRecording = false
                    updateMicButtonUI()
                }
            }

            override fun onResults(results: Bundle?) {
                // PERBAIKAN: Gunakan konstanta yang benar yaitu RESULTS_RECOGNITION
                val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                if (data.isNullOrEmpty()) {
                    viewModel.postSpeechResult("no_speech")
                } else {
                    viewModel.postSpeechResult("fluent")
                }
                isRecording = false
                updateMicButtonUI()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer?.setRecognitionListener(speechListener)

        binding.btnMic.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                // BARU: Cek izin sebelum mulai merekam
                checkAndRequestAudioPermission()
            }
        }
    }

    // BARU: Fungsi untuk mengecek dan meminta izin
    private fun checkAndRequestAudioPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Izin sudah ada, langsung mulai merekam
                startRecording()
            }
            else -> {
                // Minta izin ke pengguna
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startRecording() {
        if (!isRecording) {
            isRecording = true
            updateMicButtonUI()

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            speechRecognizer?.startListening(intent)

            countDownTimer?.cancel()
            countDownTimer = object : CountDownTimer(20000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    binding.tvCountdown.text = String.format("00:%02d", millisUntilFinished / 1000)
                }
                override fun onFinish() {
                    if (isRecording) {
                        stopRecording()
                    }
                }
            }.start()
        }
    }

    private fun stopRecording() {
        if (isRecording) {
            countDownTimer?.cancel()
            speechRecognizer?.stopListening() // Ini akan trigger onResults atau onError
            // State `isRecording` akan diubah di dalam callback listener
        }
    }

    private fun updateMicButtonUI() {
        if (isRecording) {
            // Asumsi Anda punya drawable bernama ic_stop
            binding.btnMic.setIconResource(R.drawable.ic_stop)
        } else {
            binding.btnMic.setIconResource(R.drawable.ic_voice)
        }
    }

    private fun setupObservers() {
        viewModel.learningLevel.observe(this) { level ->
            level?.let {
                binding.tvMaterialTitle.text = it.title
                binding.tvLevelTitle.text = it.title
            }
        }

        viewModel.sections.observe(this) { sections ->
            sectionListAdapter.submitList(sections)
            if (!::pagerAdapter.isInitialized) {
                setupViewPager(sections)
            }
        }

        viewModel.showMicControls.observe(this) { isVisible ->
            binding.layoutMicControls.isVisible = isVisible
        }

        viewModel.error.observe(this) { errorMsg ->
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    fun onSectionCompleted(sectionId: String) {
        if (sectionId.isNotEmpty()) {
            viewModel.completeSection(currentLevelId, sectionId)
            // Otomatis pindah ke section selanjutnya jika ada
            viewModel.sections.value?.let { sections ->
                val currentSection = sections.firstOrNull { it.sectionId == sectionId }
                if (currentSection != null && currentSection.order < sections.size) {
                    binding.viewPagerContent.currentItem = currentSection.order
                } else {
                    // Level Selesai
                    Toast.makeText(this, "Congratulations! You've completed the level.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        speechRecognizer?.destroy()
    }
}