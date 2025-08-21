package com.pkmk.bravy.ui.view.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pkmk.bravy.R
import com.pkmk.bravy.databinding.ActivitySettingBinding
import com.pkmk.bravy.databinding.DialogTermsAndConditionsBinding

class SettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingBinding

    // Launcher untuk meminta izin notifikasi
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Izin diberikan, simpan preferensi
                saveNotificationPreference(true)
                Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show()
            } else {
                // Izin ditolak, kembalikan switch ke posisi off
                binding.switchMaterial.isChecked = false
                saveNotificationPreference(false)
                Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        syncNotificationSwitch()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.switchMaterial.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Jika switch dihidupkan, minta izin
                requestNotificationPermission()
            } else {
                // Jika switch dimatikan, simpan preferensi
                saveNotificationPreference(false)
                Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAboutUs.setOnClickListener {
            showInfoDialog(getString(R.string.about_us_title), getString(R.string.about_us_content))
        }

        binding.btnTermsConditions.setOnClickListener {
            showInfoDialog(getString(R.string.terms_and_conditions_title), getString(R.string.terms_and_conditions_full_text))
        }

        binding.btnPrivacyPolicy.setOnClickListener {
            showInfoDialog(getString(R.string.privacy_policy_title), getString(R.string.terms_and_conditions_full_text))
        }
    }

    private fun requestNotificationPermission() {
        // Hanya perlu minta izin untuk Android 13 (TIRAMISU) ke atas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Izin sudah diberikan
                    saveNotificationPreference(true)
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Pengguna pernah menolak, tunjukkan dialog penjelasan
                    showPermissionRationaleDialog()
                }
                else -> {
                    // Minta izin untuk pertama kalinya
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Untuk Android di bawah 13, notifikasi aktif secara default
            saveNotificationPreference(true)
            binding.switchMaterial.isChecked = true
        }
    }

    // Sinkronkan status switch dengan preferensi yang tersimpan
    private fun syncNotificationSwitch() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val notificationsEnabled = prefs.getBoolean("notifications_enabled", true) // Default true
        binding.switchMaterial.isChecked = notificationsEnabled
    }

    // Simpan preferensi notifikasi
    private fun saveNotificationPreference(isEnabled: Boolean) {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("notifications_enabled", isEnabled).apply()
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission")
            .setMessage("To receive updates and alerts, please enable notifications in the app settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                // Arahkan pengguna ke pengaturan aplikasi
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
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
        // Kita bisa gunakan kembali layout dialog T&C
        val dialogBinding = DialogTermsAndConditionsBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tvDialogTitle.text = title
        dialogBinding.tvTermsContent.text = content

        // Sembunyikan dan nonaktifkan tombol 'Agree' karena tidak relevan di sini
        dialogBinding.btnAgree.visibility = View.GONE

        dialogBinding.btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}