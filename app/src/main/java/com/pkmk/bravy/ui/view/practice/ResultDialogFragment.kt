package com.pkmk.bravy.ui.view.practice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.pkmk.bravy.databinding.DialogPracticeResultBinding

class ResultDialogFragment : DialogFragment() {

    private var _binding: DialogPracticeResultBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPracticeResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val confidence = arguments?.getInt(ARG_CONFIDENCE) ?: 0
        val speech = arguments?.getInt(ARG_SPEECH) ?: 0
        val total = arguments?.getInt(ARG_TOTAL) ?: 0
        val recommendation = arguments?.getString(ARG_RECOMMENDATION) ?: ""
        val levelTitle = arguments?.getString(ARG_LEVEL_TITLE) ?: "Practice"

        binding.tvCongratulations.text = "Results for $levelTitle"
        binding.tvConfidenceValue.text = "$confidence / 5"
        binding.tvCognitiveValue.text = "$speech / 10"
        binding.tvTotalValue.text = "$total / 15"
        binding.tvSuggestion1.text = recommendation

        binding.ratingConfidence.rating = confidence.toFloat()
        binding.progressCognitive.progress = speech * 10
        binding.tvCognitivePercentage.text = "$speech%"

        binding.fabClose.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    companion object {
        const val TAG = "ResultDialog"
        private const val ARG_CONFIDENCE = "confidence_score"
        private const val ARG_SPEECH = "speech_score"
        private const val ARG_TOTAL = "total_score"
        private const val ARG_RECOMMENDATION = "recommendation"
        private const val ARG_LEVEL_TITLE = "level_title"

        fun newInstance(
            confidenceScore: Int,
            speechScore: Int,
            totalScore: Int,
            recommendation: String,
            levelTitle: String
        ): ResultDialogFragment {
            val args = Bundle().apply {
                putInt(ARG_CONFIDENCE, confidenceScore)
                putInt(ARG_SPEECH, speechScore)
                putInt(ARG_TOTAL, totalScore)
                putString(ARG_RECOMMENDATION, recommendation)
                putString(ARG_LEVEL_TITLE, levelTitle)
            }
            return ResultDialogFragment().apply {
                arguments = args
            }
        }
    }
}