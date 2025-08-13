package com.pkmk.bravy.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.model.UserProgress
import com.pkmk.bravy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {
    private val _redeemResult = MutableLiveData<Result<String>>()
    val redeemResult: LiveData<Result<String>> get() = _redeemResult

    private val _registerResult = MutableLiveData<Result<Unit>>()
    val registerResult: LiveData<Result<Unit>> get() = _registerResult

    private val _loginResult = MutableLiveData<Result<Unit>>()
    val loginResult: LiveData<Result<Unit>> get() = _loginResult

    fun registerUser(name: String, email: String, password: String, redeemCode: String) {
        viewModelScope.launch {
            // 1. Buat user di Firebase Authentication
            val createResult = repository.createUserWithEmail(email, password)

            createResult.onSuccess { uid ->
                // 2. Jika sukses, ambil data semua level untuk inisialisasi
                val levelsResult = repository.getLearningLevels()

                levelsResult.onSuccess { levelsSnapshot ->
                    // 3. Bangun struktur user_progress awal
                    val initialProgress = mutableMapOf<String, UserProgress>()
                    // Iterasi setiap level (e.g., "level_1", "level_2")
                    for (levelSnap in levelsSnapshot.children) {
                        val levelId = levelSnap.key ?: continue
                        val sectionsMap = mutableMapOf<String, Boolean>()
                        // Iterasi setiap section di dalam level
                        levelSnap.child("sections").children.forEach { sectionSnap ->
                            val sectionId = sectionSnap.key ?: return@forEach
                            sectionsMap[sectionId] = false // Set semua ke false
                        }
                        initialProgress[levelId] = UserProgress(completed_sections = sectionsMap)
                    }

                    // 4. Buat objek User lengkap dengan progress awal
                    val user = User(
                        uid = uid,
                        name = name,
                        email = email,
                        redeemCode = redeemCode,
                        user_progress = initialProgress
                    )

                    // 5. Simpan objek User ke database
                    val registerResult = repository.registerUser(user)
                    _registerResult.postValue(registerResult)
                    if (registerResult.isSuccess) {
                        markRedeemCodeAsUsed(redeemCode)
                    }

                }.onFailure { exception ->
                    // Gagal mengambil data level, pendaftaran tidak bisa dilanjutkan
                    _registerResult.postValue(Result.failure(Exception("Failed to initialize user progress. ${exception.message}")))
                }

            }.onFailure { exception ->
                _registerResult.postValue(Result.failure(exception))
            }
        }
    }

    fun validateRedeemCode(code: String) {
        viewModelScope.launch {
            val result = repository.validateRedeemCode(code)
            _redeemResult.postValue(result.map { it.code })
        }
    }

    fun markRedeemCodeAsUsed(code: String) {
        viewModelScope.launch {
            repository.markRedeemCodeAsUsed(code)
        }
    }

    fun loginUser(email: String, password: String) {
        viewModelScope.launch {
            val result = repository.loginUser(email, password)
            _loginResult.postValue(result.map { })
        }
    }
}