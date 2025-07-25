package com.pkmk.bravy.ui.view.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.transition.Fade
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.databinding.FragmentHomeBinding
import com.pkmk.bravy.ui.viewmodel.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private val storageRef = FirebaseStorage.getInstance().getReference("picture")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loadUserProfile()

        // Observe user data
        viewModel.userProfile.observe(viewLifecycleOwner) { result ->
            result.onSuccess { user ->
                binding.tvUserName.text = "Hi,${user.name.split(" ").firstOrNull() ?: "User"}!"

                // Use viewLifecycleOwner.lifecycleScope for coroutines tied to the view's lifecycle
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val imageName = user.image ?: "default.jpg"
                        val imageRef = storageRef.child(imageName)
                        val downloadUrl = imageRef.downloadUrl.await()

                        // Ensure fragment is still added before using Glide
                        if (!isAdded) return@launch

                        Glide.with(this@HomeFragment)
                            .load(downloadUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(binding.ivProfilePhoto)
                    } catch (e: Exception) {
                        if (!isAdded) return@launch // Check again in case of error before next Glide call
                        Toast.makeText(requireContext(), "Failed to load profile picture: ${e.message}", Toast.LENGTH_SHORT).show()
                        // Muat default.jpg jika gagal
                        try {
                            val defaultUrl = storageRef.child("default.jpg").downloadUrl.await()
                            if (!isAdded) return@launch

                            Glide.with(this@HomeFragment)
                                .load(defaultUrl)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(binding.ivProfilePhoto)
                        } catch (innerE: Exception) {
                            if (!isAdded) return@launch

                            Glide.with(this@HomeFragment)
                                .load(R.drawable.ic_profile) // Load placeholder directly
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(binding.ivProfilePhoto)
                        }
                    }
                }
            }.onFailure { exception ->
                // Ensure fragment is still added before UI updates from LiveData observer
                if (!isAdded) return@observe

                Toast.makeText(requireContext(), "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                binding.tvUserName.text = "Hello,\nUser!"

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val defaultUrl = storageRef.child("default.jpg").downloadUrl.await()
                        if (!isAdded) return@launch

                        Glide.with(this@HomeFragment)
                            .load(defaultUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(binding.ivProfilePhoto)
                    } catch (e: Exception) {
                        if (!isAdded) return@launch

                        Glide.with(this@HomeFragment)
                            .load(R.drawable.ic_profile) // Load placeholder directly
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(binding.ivProfilePhoto)
                    }
                }
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}