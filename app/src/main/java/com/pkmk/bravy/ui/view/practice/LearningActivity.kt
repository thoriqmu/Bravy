package com.pkmk.bravy.ui.view.practice

import android.Manifest
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
import com.pkmk.bravy.data.model.LearningScene
import com.pkmk.bravy.databinding.ActivityLearningBinding
import com.pkmk.bravy.ui.adapter.LearningPagerAdapter
import com.pkmk.bravy.ui.adapter.SectionListAdapter
import com.pkmk.bravy.ui.view.practice.level1.PracticeLevel1Fragment
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
                Toast.makeText(this, "Izin mikrofon diperlukan", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLearningBinding.inflate(layoutInflater)
        setContentView(binding.root)

        levelId = intent.getStringExtra("LEVEL_ID")
        if (levelId == null) {
            finish()
            return
        }

        setupUI()
        observeViewModel()
        viewModel.resetScores()
        viewModel.loadLevelData(levelId!!)
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        sectionsAdapter = SectionListAdapter { _, position ->
            binding.viewPagerContent.currentItem = position
        }
        binding.rvSectionList.apply {
            adapter = sectionsAdapter
            layoutManager = LinearLayoutManager(this@LearningActivity)
        }

        pagerAdapter = LearningPagerAdapter(this)
        binding.viewPagerContent.adapter = pagerAdapter
        binding.viewPagerContent.isUserInputEnabled = false

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
                // Cara baru: Panggil fungsi di fragment aktif
                val currentFragment = supportFragmentManager.findFragmentByTag("f${binding.viewPagerContent.currentItem}")
                (currentFragment as? PracticeLevel1Fragment)?.launchAnalysis(scene)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.learningLevel.observe(this) { level ->
            level?.let {
                binding.tvLevelTitle.text = it.title
                binding.tvMaterialTitle.text = it.title
                binding.tvMaterialDescription.text = it.description
            }
        }

        viewModel.sections.observe(this) { sections ->
            sectionsAdapter.submitList(sections)
            pagerAdapter.setSections(sections)
        }

        viewModel.showMicControls.observe(this) { (isVisible, duration) ->
            binding.layoutMicControls.isVisible = isVisible
            if (!isVisible) {
                countDownTimer?.cancel()
                binding.tvCountdown.text = ""
            }
        }

        viewModel.showAnalysisButton.observe(this) { isVisible ->
            binding.layoutAnalysisControls.isVisible = isVisible
        }

        viewModel.finalPracticeScore.observe(this) { finalScore ->
            Toast.makeText(this, "Practice Complete! Your average score: $finalScore / 15", Toast.LENGTH_LONG).show()
        }

        viewModel.error.observe(this) { errorMsg ->
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    fun onSectionCompleted(sectionId: String) {
        viewModel.completeSection(levelId!!, sectionId)
        Toast.makeText(this, "Section Selesai!", Toast.LENGTH_SHORT).show()
        // Pindah ke section berikutnya secara otomatis
        val nextPosition = binding.viewPagerContent.currentItem + 1
        if (nextPosition < pagerAdapter.itemCount) {
            binding.viewPagerContent.currentItem = nextPosition
        }
    }

    fun showAnalysisButton(scene: LearningScene) {
        binding.btnStartAnalysis.setOnClickListener {
            val intent = Intent(this, AnalysisActivity::class.java).apply {
                putExtra("KEY_SENTENCE", scene.keySentence)
            }
            // Dapatkan fragment saat ini dan minta dia untuk meluncurkan activity
            val currentFragment = supportFragmentManager.findFragmentByTag("f${binding.viewPagerContent.currentItem}")
            (currentFragment as? PracticeLevel1Fragment)?.launchAnalysis(scene)
        }
    }

    private fun handleMicClick() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val duration = viewModel.showMicControls.value?.second ?: 20
            startCountdown(duration)
            startSpeechRecognition()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startCountdown(durationSeconds: Int) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(durationSeconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvCountdown.text = String.format(Locale.getDefault(), "00:%02d", millisUntilFinished / 1000)
            }
            override fun onFinish() {
                binding.tvCountdown.text = "00:00"
                speechRecognizer?.stopListening()
            }
        }.start()
    }

    private fun startSpeechRecognition() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { countDownTimer?.cancel() }
                override fun onError(error: Int) { viewModel.postSpeechResult("no_speech") }
                override fun onResults(results: Bundle?) {
                    val spokenText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    viewModel.postSpeechResult(if (spokenText.isNullOrEmpty()) "no_speech" else "fluent")
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }
        speechRecognizer?.startListening(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        countDownTimer?.cancel()
    }
}