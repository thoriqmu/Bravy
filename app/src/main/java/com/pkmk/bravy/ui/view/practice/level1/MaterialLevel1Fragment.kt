package com.pkmk.bravy.ui.view.practice.level1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import com.pkmk.bravy.data.model.LearningScene
import com.pkmk.bravy.data.model.LearningSection
import com.pkmk.bravy.databinding.FragmentLearningSectionBinding
import com.pkmk.bravy.ui.view.practice.LearningActivity
import com.pkmk.bravy.ui.viewmodel.LearningViewModel
import com.pkmk.bravy.util.VideoCache
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MaterialLevel1Fragment : Fragment() {

    private var _binding: FragmentLearningSectionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LearningViewModel by activityViewModels()
    private var exoPlayer: ExoPlayer? = null
    private var section: LearningSection? = null
    private var currentSceneIndex = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLearningSectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        section = arguments?.getParcelable(ARG_SECTION)
        setupPlayer()

        viewModel.speechResult.observe(viewLifecycleOwner) { resultType ->
            val currentScene = section?.scenes?.getOrNull(currentSceneIndex)
            if (isResumed && currentScene?.sceneType == "SPEECH_PRACTICE") {
                playResponseVideo(currentScene, resultType)
            }
        }

        if (savedInstanceState == null) {
            executeCurrentScene()
        }
    }

    private fun setupPlayer() {
        exoPlayer = ExoPlayer.Builder(requireContext()).build().also {
            binding.playerView.player = it
            binding.playerView.useController = true
        }
    }

    private fun executeCurrentScene() {
        if (!isAdded || _binding == null) return

        hideAllUI()

        if (currentSceneIndex >= (section?.scenes?.size ?: 0)) {
            (activity as? LearningActivity)?.onSectionCompleted(section?.sectionId ?: "")
            return
        }

        val currentScene = section!!.scenes[currentSceneIndex]
        when (currentScene.sceneType) {
            "PLAY_VIDEO" -> handlePlayVideoScene(currentScene)
            "SPEECH_PRACTICE" -> handleSpeechPracticeScene(currentScene)
            else -> goToNextScene()
        }
    }

    private fun handlePlayVideoScene(scene: LearningScene) {
        binding.playerView.isVisible = true
        playVideoFromUrl(scene.videoUrl) { goToNextScene() }
    }

    private fun handleSpeechPracticeScene(scene: LearningScene) {
        // Tampilkan video terakhir yang diputar, tapi jangan auto-play
        binding.playerView.isVisible = true
        exoPlayer?.seekTo(0)
        exoPlayer?.playWhenReady = false

        viewModel.setMicControlsVisibility(true, scene.duration ?: 20)
    }

    private fun playResponseVideo(scene: LearningScene?, responseType: String) {
        viewModel.setMicControlsVisibility(false)
        val responseUrl = scene?.responses?.get(responseType)

        if (responseUrl.isNullOrEmpty()) {
            goToNextScene()
            return
        }

        binding.playerView.isVisible = true
        playVideoFromUrl(responseUrl) {
            goToNextScene()
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
                viewModel.setLastPlayedVideoUri(videoUri) // Simpan URI video terakhir
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

    private fun hideAllUI() {
        if (_binding == null) return
        viewModel.setMicControlsVisibility(false)
        // Jangan sembunyikan player view jika sudah ada video
        if (exoPlayer?.currentMediaItem == null) {
            binding.playerView.isVisible = false
        }
        binding.progressBar.isVisible = false
        exoPlayer?.stop()
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        if (exoPlayer?.isPlaying == false && exoPlayer?.playbackState != Player.STATE_IDLE) {
            exoPlayer?.play()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        exoPlayer?.release()
        exoPlayer = null
        _binding = null
    }

    companion object {
        private const val ARG_SECTION = "arg_section"
        fun newInstance(section: LearningSection) = MaterialLevel1Fragment().apply {
            arguments = Bundle().apply { putParcelable(ARG_SECTION, section) }
        }
    }
}