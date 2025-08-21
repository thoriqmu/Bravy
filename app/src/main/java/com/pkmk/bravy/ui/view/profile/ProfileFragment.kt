package com.pkmk.bravy.ui.view.profile

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.databinding.FragmentProfileBinding
import com.pkmk.bravy.ui.adapter.ProfilePagerAdapter
import com.pkmk.bravy.ui.viewmodel.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPagerAndTabs()
        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        // Muat ulang data setiap kali fragment ini ditampilkan
        viewModel.loadUserProfile()
    }

    private fun setupObservers() {
        viewModel.userProfile.observe(viewLifecycleOwner) { result ->
            result.onSuccess { user ->
                binding.tvUserName.text = user.name
                binding.tvUserEmail.text = user.email
                loadProfileImage(user.image)
            }.onFailure {
                Toast.makeText(requireContext(), "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupViewPagerAndTabs() {
        binding.viewPagerProfile.adapter = ProfilePagerAdapter(this)
        TabLayoutMediator(binding.tabLayoutProfile, binding.viewPagerProfile) { tab, position ->
            tab.text = when (position) {
                0 -> "Activity"
                1 -> "Setting"
                else -> null
            }
        }.attach()
    }

    private fun loadProfileImage(imageName: String?) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val url = if (imageName.isNullOrEmpty()) {
                    FirebaseStorage.getInstance().getReference("picture/default.jpg").downloadUrl.await()
                } else {
                    FirebaseStorage.getInstance().getReference("picture/$imageName").downloadUrl.await()
                }
                Glide.with(this@ProfileFragment)
                    .load(url)
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .into(binding.ivProfilePhoto)
            } catch (e: Exception) {
                binding.ivProfilePhoto.setImageResource(R.drawable.ic_profile)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}