package com.pkmk.bravy.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.model.UserProgress
import com.pkmk.bravy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val auth: FirebaseAuth
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
                // Jika pembuatan Auth user berhasil, coba simpan ke database
                try {
                    val levelsResult = repository.getLearningLevels()
                    if (levelsResult.isFailure) {
                        throw levelsResult.exceptionOrNull() ?: Exception("Failed to get learning levels")
                    }

                    val levelsSnapshot = levelsResult.getOrThrow()
                    val initialProgress = mutableMapOf<String, UserProgress>()
                    for (levelSnap in levelsSnapshot.children) {
                        val levelId = levelSnap.key ?: continue
                        val sectionsMap = mutableMapOf<String, Boolean>()
                        levelSnap.child("sections").children.forEach { sectionSnap ->
                            val sectionId = sectionSnap.key ?: return@forEach
                            sectionsMap[sectionId] = false
                        }
                        initialProgress[levelId] = UserProgress(completed_sections = sectionsMap)
                    }

                    val user = User(
                        uid = uid,
                        name = name,
                        email = email,
                        redeemCode = redeemCode,
                        user_progress = initialProgress,
                        createdAt = System.currentTimeMillis()
                    )

                    // Simpan objek User ke database
                    val registerResult = repository.registerUser(user)
                    if (registerResult.isFailure) {
                        throw registerResult.exceptionOrNull() ?: Exception("Failed to save user to database")
                    }

                    // Jika semua berhasil, tandai kode redeem dan kirim sinyal sukses
                    markRedeemCodeAsUsed(redeemCode)
                    _registerResult.postValue(Result.success(Unit))

                } catch (dbException: Exception) {
                    // JIKA GAGAL, hapus user dari Auth untuk rollback
                    auth.currentUser?.delete()?.await()
                    _registerResult.postValue(Result.failure(dbException))
                }

            }.onFailure { authException ->
                // Jika pembuatan user di Auth gagal dari awal
                _registerResult.postValue(Result.failure(authException))
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

    fun saveSessionData(token: String, sessionId: String) {
        viewModelScope.launch {
            repository.saveSessionData(token, sessionId)
        }
    }
}