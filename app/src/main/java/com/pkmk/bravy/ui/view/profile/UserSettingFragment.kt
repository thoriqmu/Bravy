package com.pkmk.bravy.ui.view.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.google.firebase.auth.FirebaseAuth
import com.pkmk.bravy.R
import com.pkmk.bravy.databinding.DialogTermsAndConditionsBinding
import com.pkmk.bravy.databinding.FragmentUserSettingBinding
import com.pkmk.bravy.ui.view.auth.LoginActivity
import com.pkmk.bravy.ui.view.auth.OnboardingActivity
import com.pkmk.bravy.ui.viewmodel.ProfileViewModel

class UserSettingFragment : Fragment() {

    private var _binding: FragmentUserSettingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by activityViewModels()
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    // --- PERBAIKAN 2: Pindahkan inisialisasi ke onCreate ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    saveNotificationPreference(true)
                    Toast.makeText(requireContext(), "Notifications enabled", Toast.LENGTH_SHORT).show()
                } else {
                    binding.switchMaterial.isChecked = false
                    saveNotificationPreference(false)
                    Toast.makeText(requireContext(), "Notifications disabled", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        syncNotificationSwitch()
        setupListeners()
        setupObservers()
    }

    private fun setupListeners() {
        binding.btnProfileSetting.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileSettingActivity::class.java))
        }

        binding.btnAboutUs.setOnClickListener {
            showInfoDialog(getString(R.string.about_us_title), getString(R.string.about_us_content))
        }

        binding.btnTermsConditions.setOnClickListener {
            showInfoDialog(getString(R.string.terms_and_conditions_title), getString(R.string.terms_and_conditions_content))
        }

        binding.btnPrivacyPolicy.setOnClickListener {
            showInfoDialog(getString(R.string.privacy_policy_title), getString(R.string.privacy_policy_content))
        }

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        binding.switchMaterial.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestNotificationPermission()
            } else {
                saveNotificationPreference(false)
                Toast.makeText(requireContext(), "Notifications disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupObservers() {
        viewModel.logoutResult.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(requireContext(), "Logging out...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Logout failed. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    // --- PERBAIKAN 4: Gunakan requireContext() ---
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    saveNotificationPreference(true)
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    showPermissionRationaleDialog()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            saveNotificationPreference(true)
            binding.switchMaterial.isChecked = true
        }
    }

    private fun syncNotificationSwitch() {
        // --- PERBAIKAN 5: Gunakan requireActivity() untuk SharedPreferences ---
        val prefs = requireActivity().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
        binding.switchMaterial.isChecked = notificationsEnabled
    }

    private fun saveNotificationPreference(isEnabled: Boolean) {
        val prefs = requireActivity().getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("notifications_enabled", isEnabled).apply()
    }

    private fun showPermissionRationaleDialog() {
        // --- PERBAIKAN 6: Gunakan requireContext() untuk Dialog dan packageName ---
        AlertDialog.Builder(requireContext())
            .setTitle("Notification Permission")
            .setMessage("To receive updates and alerts, please enable notifications in the app settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", requireContext().packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                binding.switchMaterial.isChecked = false
                dialog.dismiss()
            }
            .show()
    }

    private fun showInfoDialog(title: String, content: String) {
        val dialogBinding = DialogTermsAndConditionsBinding.inflate(LayoutInflater.from(requireContext()))
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tvDialogTitle.text = title
        dialogBinding.tvTermsContent.text = content
        dialogBinding.btnAgree.visibility = View.GONE

        dialogBinding.btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Log Out")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Yes") { _, _ ->
                viewModel.logout()
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}