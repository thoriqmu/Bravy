package com.pkmk.bravy.ui.view.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.pkmk.bravy.databinding.FragmentOnboardingBinding

class OnboardingFragment : Fragment() {

    // Deklarasi binding object
    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate layout menggunakan binding
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        val view = binding.root

        arguments?.let {
            binding.tvTitleOnboarding.text = it.getString(ARG_TITLE)
            binding.tvDescriptionOnboarding.text = it.getString(ARG_DESC)
            binding.ivOnboarding.setImageResource(it.getInt(ARG_IMAGE_RES_ID))
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Hindari memory leak dengan membersihkan binding
        _binding = null
    }

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_DESC = "arg_desc"
        private const val ARG_IMAGE_RES_ID = "arg_image"

        fun newInstance(title: String, description: String, imageResId: Int): OnboardingFragment {
            val fragment = OnboardingFragment()
            val args = Bundle().apply {
                putString(ARG_TITLE, title)
                putString(ARG_DESC, description)
                putInt(ARG_IMAGE_RES_ID, imageResId)
            }
            fragment.arguments = args
            return fragment
        }
    }
}