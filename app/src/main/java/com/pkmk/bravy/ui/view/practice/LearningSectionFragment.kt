package com.pkmk.bravy.ui.view.practice

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.data.model.LearningScene
import com.pkmk.bravy.data.model.LearningSection
import com.pkmk.bravy.databinding.FragmentLearningSectionBinding
import com.pkmk.bravy.ui.viewmodel.LearningViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class LearningSectionFragment : Fragment() {

    private var _binding: FragmentLearningSectionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LearningViewModel by activityViewModels()

    private var section: LearningSection? = null
    private var currentSceneIndex = 0
    private var countDownTimer: CountDownTimer? = null

    private val speechResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        countDownTimer?.cancel()
        val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val responseType = when {
            result.resultCode != Activity.RESULT_OK -> "no_speech"
            spokenText.isNullOrEmpty() -> "no_speech"
            // TODO: Tambahkan logika deteksi "nervous" jika memungkinkan.
            // Untuk saat ini, semua ucapan dianggap "fluent".
            else -> "fluent"
        }
        val currentScene = section?.scenes?.get(currentSceneIndex)
        playResponseVideo(currentScene, responseType)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLearningSectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        section = arguments?.getParcelable(ARG_SECTION)

        // Observer untuk hasil speech dari Activity
        viewModel.speechResult.observe(viewLifecycleOwner) { resultType ->
            // Pastikan observer ini hanya bereaksi jika scene saat ini adalah speech practice
            if (section?.scenes?.getOrNull(currentSceneIndex)?.sceneType == "SPEECH_PRACTICE") {
                val currentScene = section!!.scenes[currentSceneIndex]
                playResponseVideo(currentScene, resultType)
            }
        }

        executeCurrentScene()
    }

    private fun executeCurrentScene() {
        // Sembunyikan semua UI sebelum menampilkan yang baru
        hideAllUI()

        // Cek jika semua scene sudah selesai
        if (currentSceneIndex >= (section?.scenes?.size ?: 0)) {
            (activity as? LearningActivity)?.onSectionCompleted(section?.sectionId ?: "")
            return
        }

        val currentScene = section!!.scenes[currentSceneIndex]
        when (currentScene.sceneType) {
            "PLAY_VIDEO" -> handlePlayVideoScene(currentScene)
            "SPEECH_PRACTICE" -> handleSpeechPracticeScene(currentScene)
            "SHOW_INSTRUCTION" -> handleInstructionScene(currentScene)
            else -> {
                // Jika tipe scene tidak dikenali, lewati saja
                Toast.makeText(context, "Unknown scene type: ${currentScene.sceneType}", Toast.LENGTH_SHORT).show()
                goToNextScene()
            }
        }
    }

    private fun handlePlayVideoScene(scene: LearningScene) {
        binding.videoView.isVisible = true
        playVideoFromUrl(scene.videoUrl) {
            // Callback: dipanggil setelah video selesai
            goToNextScene()
        }
    }

    private fun handleSpeechPracticeScene(scene: LearningScene) {
        // Fragment hanya memberi tahu Activity untuk menampilkan UI mic
        viewModel.setMicControlsVisibility(true)
        // Logika selanjutnya (menunggu hasil dari observer) sudah ditangani di atas
    }

    private fun handleInstructionScene(scene: LearningScene) {
        binding.layoutInstruction.isVisible = true
        binding.tvInstructionText.text = scene.text ?: "Instruction"
        binding.btnInstructionNext.setOnClickListener {
            goToNextScene()
        }
    }

    private fun playVideoFromUrl(storageUrl: String?, onComplete: () -> Unit) {
        if (storageUrl.isNullOrEmpty()) {
            Toast.makeText(context, "Video URL is missing", Toast.LENGTH_SHORT).show()
            onComplete() // Langsung lanjut jika URL tidak ada
            return
        }

        binding.progressBar.isVisible = true
        lifecycleScope.launch {
            try {
                val uri = FirebaseStorage.getInstance().getReferenceFromUrl(storageUrl).downloadUrl.await()
                binding.videoView.setVideoURI(uri)
                binding.videoView.setOnPreparedListener { mp ->
                    binding.progressBar.isVisible = false
                    mp.start()
                    mp.setOnCompletionListener { onComplete() }
                }
            } catch (e: Exception) {
                binding.progressBar.isVisible = false
                Toast.makeText(context, "Failed to load video: ${e.message}", Toast.LENGTH_SHORT).show()
                onComplete() // Lanjut meskipun video gagal load
            }
        }
    }

    private fun playResponseVideo(scene: LearningScene?, responseType: String) {
        val responseUrl = scene?.responses?.get(responseType)
        viewModel.setMicControlsVisibility(false)
        if (responseUrl.isNullOrEmpty()) {
            // Jika tidak ada video respons, langsung lanjut ke scene berikutnya
            goToNextScene()
            return
        }
        // Jika ADA video respons, mainkan video tersebut
        binding.videoView.isVisible = true
        playVideoFromUrl(responseUrl) {
            // Setelah video respons selesai, baru lanjut ke scene berikutnya
            goToNextScene()
        }
    }

    private fun startSpeechToText() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            Toast.makeText(context, "Speech recognition is not available", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
        }
        speechResultLauncher.launch(intent)
    }

    private fun goToNextScene() {
        // Pastikan kontrol mic disembunyikan saat pindah scene
        viewModel.setMicControlsVisibility(false)
        currentSceneIndex++
        executeCurrentScene()
    }

    private fun hideAllUI() {
        binding.videoView.isVisible = false
        binding.layoutInstruction.isVisible = false
        binding.progressBar.isVisible = false
        // Hentikan pemutaran video jika ada
        if (binding.videoView.isPlaying) {
            binding.videoView.stopPlayback()
        }
        // Batalkan timer jika sedang berjalan
        countDownTimer?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SECTION = "arg_section"
        fun newInstance(section: LearningSection) = LearningSectionFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_SECTION, section)
            }
        }
    }
}