package com.pkmk.bravy.ui.view.practice.level2

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.LearningScene
import com.pkmk.bravy.data.model.LearningSection
import com.pkmk.bravy.databinding.FragmentLearningSectionBinding
import com.pkmk.bravy.ui.view.practice.AnalysisActivity
import com.pkmk.bravy.ui.view.practice.LearningActivity
import com.pkmk.bravy.ui.viewmodel.LearningViewModel
import com.pkmk.bravy.util.VideoCache
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PracticeLevel2Fragment : Fragment() {

    private var _binding: FragmentLearningSectionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LearningViewModel by activityViewModels()
    private var exoPlayer: ExoPlayer? = null
    private var section: LearningSection? = null
    private var currentSceneIndex = 0
    private var feedbackPlayer: MediaPlayer? = null
    private var wasPlayingWhenPaused = false // <-- PERBAIKAN: Tambahkan variabel ini

    private val analysisLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val confidenceScore = result.data?.getIntExtra("CONFIDENCE_POINTS", 0) ?: 0
            val speechScore = result.data?.getIntExtra("SPEECH_POINTS", 0) ?: 0

            // Ganti nama fungsi ini di ViewModel nanti
            viewModel.addPracticeScore(confidenceScore, speechScore)

            // Jawaban benar jika skor speech > 5 (karena 5 dari opsi + >0 dari kemiripan)
            handleAnalysisResult(speechScore > 5)
        } else {
            handleAnalysisResult(false)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLearningSectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        section = arguments?.getParcelable(ARG_SECTION)
        setupPlayer()
        if (savedInstanceState == null) {
            executeCurrentScene()
        }
    }

    private fun executeCurrentScene() {
        if (!isAdded || _binding == null) return
        hideAllUI()
        val scenes = section?.scenes ?: return
        if (currentSceneIndex >= scenes.size) {
            viewModel.calculateFinalScore()
            (activity as? LearningActivity)?.onSectionCompleted(section?.sectionId ?: "")
            return
        }

        val currentScene = scenes[currentSceneIndex]
        when (currentScene.sceneType) {
            "PLAY_VIDEO" -> playVideoFromUrl(currentScene.videoUrl) { goToNextScene() }
            "RECONSTRUCTION_PRACTICE" -> playQuestionAndShowButton(currentScene)
            else -> goToNextScene()
        }
    }

    private fun playQuestionAndShowButton(scene: LearningScene) {
        playVideoFromUrl(scene.questionVideoUrl) {
            (activity as? LearningActivity)?.showAnalysisButton(scene, this::launchAnalysis)
        }
    }

    private fun launchAnalysis(scene: LearningScene) {
        val intent = Intent(requireContext(), AnalysisActivity::class.java).apply {
            putExtra("PRACTICE_MODE", "RECONSTRUCTION")
            putExtra("EXPECTED_ANSWER", scene.expectedAnswer)
            putStringArrayListExtra("OPTIONS", ArrayList(scene.options ?: listOf()))
            putExtra("PROMPT_TEXT", scene.prompt)
        }
        analysisLauncher.launch(intent)
    }

    private fun handleAnalysisResult(isCorrect: Boolean) {
        val currentScene = section?.scenes?.getOrNull(currentSceneIndex) ?: return
        val feedbackUrl = if (isCorrect) {
            playSound(R.raw.correct_answer)
            currentScene.feedbackCorrectVideoUrl
        } else {
            playSound(R.raw.incorrect_answer)
            currentScene.feedbackIncorrectVideoUrl
        }

        playVideoFromUrl(feedbackUrl) {
            if (isCorrect) {
                goToNextScene()
            } else {
                executeCurrentScene()
            }
        }
    }

    private fun playSound(soundResId: Int) {
        feedbackPlayer?.release()
        feedbackPlayer = MediaPlayer.create(requireContext(), soundResId).apply {
            setOnCompletionListener { it.release() }
            start()
        }
    }

    @OptIn(UnstableApi::class)
    private fun playVideoFromUrl(storageUrl: String?, onComplete: () -> Unit) {
        if (storageUrl.isNullOrEmpty() || !isAdded) {
            onComplete()
            return
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!isAdded) return
                binding.progressBar.isVisible = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) {
                    exoPlayer?.removeListener(this)
                    onComplete()
                }
            }
        }
        exoPlayer?.addListener(listener)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val videoUri = FirebaseStorage.getInstance().getReferenceFromUrl(storageUrl).downloadUrl.await()
                val cacheDataSourceFactory = CacheDataSource.Factory()
                    .setCache(VideoCache.getInstance(requireContext()))
                    .setUpstreamDataSourceFactory(DefaultDataSource.Factory(requireContext()))
                val mediaSource = ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(videoUri))

                exoPlayer?.setMediaSource(mediaSource)
                exoPlayer?.prepare()
                exoPlayer?.playWhenReady = true
            } catch (e: Exception) {
                if (isAdded) {
                    Toast.makeText(context, "Failed to load video: ${e.message}", Toast.LENGTH_SHORT).show()
                    exoPlayer?.removeListener(listener)
                    onComplete()
                }
            }
        }
    }

    private fun goToNextScene() {
        currentSceneIndex++
        executeCurrentScene()
    }

    private fun setupPlayer() {
        exoPlayer = ExoPlayer.Builder(requireContext()).build().also {
            binding.playerView.player = it
            binding.playerView.useController = true
        }
    }

    private fun hideAllUI() {
        if (_binding == null) return
        // Panggil fungsi di Activity untuk menyembunyikan tombol
        (activity as? LearningActivity)?.hideAnalysisButton()
        binding.playerView.isVisible = true
        binding.progressBar.isVisible = false
        exoPlayer?.stop()
    }

    override fun onPause() {
        super.onPause()
        wasPlayingWhenPaused = exoPlayer?.isPlaying ?: false
        exoPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        if (wasPlayingWhenPaused) {
            exoPlayer?.play()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        exoPlayer?.release()
        exoPlayer = null
        feedbackPlayer?.release()
        _binding = null
    }

    companion object {
        private const val ARG_SECTION = "arg_section"
        // PERBAIKAN: Pastikan ini mengembalikan PracticeLevel2Fragment
        fun newInstance(section: LearningSection) = PracticeLevel2Fragment().apply {
            arguments = Bundle().apply { putParcelable(ARG_SECTION, section) }
        }
    }
}