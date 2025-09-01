package com.pkmk.bravy.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.pkmk.bravy.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UserActivityViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) : ViewModel() {

    private val _points = MutableLiveData<Int>()
    val points: LiveData<Int> = _points

    private val _streak = MutableLiveData<Int>()
    val streak: LiveData<Int> = _streak

    private val _user = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private var userListener: ValueEventListener? = null
    private lateinit var userRef: DatabaseReference

    fun loadUserData() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            _error.value = "User not logged in"
            return
        }

        userRef = database.getReference("users").child(userId)

        // Hapus listener lama jika ada untuk menghindari duplikasi
        userListener?.let { userRef.removeEventListener(it) }

        userListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                _user.value = user
                // Asumsi 'points' dan 'streak' adalah bagian dari model User Anda
                // Jika tidak, Anda perlu menyesuaikan cara mengambilnya dari snapshot
                _points.value = snapshot.child("points").getValue(Int::class.java) ?: 0
                _streak.value = snapshot.child("streak").getValue(Int::class.java) ?: 0
            }

            override fun onCancelled(databaseError: DatabaseError) {
                _error.value = "Failed to load user data: ${databaseError.message}"
            }
        }
        userRef.addValueEventListener(userListener!!)
    }

    override fun onCleared() {
        super.onCleared()
        // Hapus listener saat ViewModel dihancurkan untuk mencegah memory leak
        userListener?.let {
            if (::userRef.isInitialized) { // Pastikan userRef sudah diinisialisasi
                userRef.removeEventListener(it)
            }
        }
    }
}