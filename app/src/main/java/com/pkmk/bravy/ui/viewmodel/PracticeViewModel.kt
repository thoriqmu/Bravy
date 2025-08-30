package com.pkmk.bravy.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.data.model.LearningLevel
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class PracticeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth,
    private val firebaseStorage: FirebaseStorage
) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _userProfile = MutableLiveData<Result<User>>()
    val userProfile: LiveData<Result<User>> get() = _userProfile

    private val _learningLevels = MutableLiveData<Result<List<LearningLevel>>>()
    val learningLevels: LiveData<Result<List<LearningLevel>>> get() = _learningLevels

    fun loadData() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val currentUser = firebaseAuth.currentUser
                if (currentUser != null) {
                    loadUserProfile()
                    loadLearningLevels()
                }
            } catch (e: Exception) {
                _userProfile.postValue(Result.failure(e))
            } finally {
                delay(5000)
                _isLoading.value = false
            }
        }

    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            val currentUser = firebaseAuth.currentUser
            if (currentUser != null) {
                val result = authRepository.getUser(currentUser.uid)
                _userProfile.postValue(result)
            } else {
                _userProfile.postValue(Result.failure(Exception("No user logged in")))
            }
        }
    }

    private fun loadLearningLevels() {
        viewModelScope.launch {
            try {
                val userResult = authRepository.getUser(firebaseAuth.currentUser?.uid ?: "")
                val userPoints = userResult.getOrNull()?.points ?: 0

                val levelsResult = authRepository.getLearningLevels()
                levelsResult.onSuccess { snapshot ->
                    val levels = snapshot.children.mapNotNull {
                        it.getValue(LearningLevel::class.java)
                    }.map { level ->
                        level.isLocked = userPoints < level.minPoints
                        level
                    }

                    // Get thumbnail URLs
                    levels.forEach { level ->
                        if (level.thumbnail.isNotBlank()) {
                            val thumbnailUrl = getThumbnailUrl(level.thumbnail)
                            level.thumbnailUrl = thumbnailUrl
                        }
                    }

                    _learningLevels.postValue(Result.success(levels))
                }.onFailure {
                    _learningLevels.postValue(Result.failure(it))
                }
            } catch (e: Exception) {
                _learningLevels.postValue(Result.failure(e))
            }
        }
    }

    private suspend fun getThumbnailUrl(thumbnailName: String): String? {
        return try {
            // -- PERBAIKAN DI SINI --
            // Mengambil nama folder dari nama file (misal: "level_1.png" -> "level_1")
            val folderName = thumbnailName.substringBeforeLast('.')
            val path = "practice/$folderName/$thumbnailName"
            firebaseStorage.reference.child(path).downloadUrl.await().toString()
        } catch (e: Exception) {
            null
        }
    }
}