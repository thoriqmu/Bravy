package com.pkmk.bravy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import com.pkmk.bravy.databinding.ActivityMainBinding
import com.pkmk.bravy.ui.view.auth.LoginActivity
import com.pkmk.bravy.ui.view.auth.OnboardingActivity
import com.pkmk.bravy.ui.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    // --- UBAH 'lateinit' menjadi nullable ---
    private var sessionListener: ValueEventListener? = null
    private var sessionRef: DatabaseReference? = null

    private val viewModel: AuthViewModel by viewModels()

    @Inject
    lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (auth.currentUser == null) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        navView.setupWithNavController(navController)

        // Panggil listenForSessionChanges hanya jika user ada
        auth.currentUser?.let {
            listenForSessionChanges(it.uid)
        }
    }

    private fun listenForSessionChanges(uid: String) {
        sessionRef = FirebaseDatabase.getInstance().getReference("users").child(uid).child("session")

        sessionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Tambahkan pengecekan: jika listener sudah dilepas, jangan lakukan apa-apa
                if (sessionListener == null) return

                if (snapshot.exists()) {
                    val remoteSessionId = snapshot.child("sessionId").getValue(String::class.java)
                    val sharedPref = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
                    val localSessionId = sharedPref.getString("SESSION_ID", null)

                    if (localSessionId != null && remoteSessionId != localSessionId) {
                        forceLogout()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Error ini wajar terjadi saat logout, jadi kita bisa log jika user masih ada
                if (auth.currentUser != null) {
                    Log.w("SessionListener", "Listener was cancelled unexpectedly", error.toException())
                }
            }
        }
        sessionRef?.addValueEventListener(sessionListener!!)
    }

    private fun forceLogout() {
        // --- Urutan yang lebih aman ---
        // 1. Hapus listener terlebih dahulu
        sessionListener?.let {
            sessionRef?.removeEventListener(it)
        }
        // 2. Null-kan referensi agar tidak ada lagi pemanggilan onDataChange
        sessionListener = null
        sessionRef = null

        // 3. Logout dari Firebase
        auth.signOut()

        // 4. Hapus data lokal
        val sharedPref = getSharedPreferences("AppSession", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            clear()
            apply()
        }

        // 5. Tampilkan pesan dan kembali ke Login
        Toast.makeText(this, "Your account is logged in from another device.", Toast.LENGTH_LONG).show()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Hapus listener jika masih ada saat activity dihancurkan
        sessionListener?.let {
            sessionRef?.removeEventListener(it)
        }
    }
}