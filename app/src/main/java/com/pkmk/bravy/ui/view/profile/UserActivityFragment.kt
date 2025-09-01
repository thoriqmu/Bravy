package com.pkmk.bravy.ui.view.profile

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.pkmk.bravy.databinding.FragmentUserActivityBinding
import com.pkmk.bravy.ui.view.friend.FriendActivity
import com.pkmk.bravy.ui.viewmodel.UserActivityViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UserActivityFragment : Fragment() {

    private var _binding: FragmentUserActivityBinding? = null
    private val binding get() = _binding!!

    // Inisialisasi ViewModel
    private val viewModel: UserActivityViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        viewModel.loadUserData() // Panggil fungsi untuk memuat data

        binding.btnFriendList.setOnClickListener {
            startActivity(Intent(requireContext(), FriendActivity::class.java))
        }
    }

    private fun setupObservers() {
        viewModel.points.observe(viewLifecycleOwner) {
            points -> binding.tvPoints.text = points.toString()
        }

        viewModel.streak.observe(viewLifecycleOwner) {
            streak -> binding.tvStreak.text = streak.toString()
        }

        viewModel.user.observe(viewLifecycleOwner) {
            user ->
            // Anda bisa menggunakan data user lainnya di sini jika perlu
            // Misalnya, binding.tvUserName.text = user?.name
        }

        viewModel.error.observe(viewLifecycleOwner) {
            errorMessage ->
            if (errorMessage.isNotEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}