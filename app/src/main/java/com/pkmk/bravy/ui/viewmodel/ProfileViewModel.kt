package com.pkmk.bravy.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _userProfile = MutableLiveData<Result<User>>()
    val userProfile: LiveData<Result<User>> get() = _userProfile

    private val _logoutResult = MutableLiveData<Boolean>()
    val logoutResult: LiveData<Boolean> get() = _logoutResult

    private val _updateProfileResult = MutableLiveData<Result<Unit>>()
    val updateProfileResult: LiveData<Result<Unit>> get() = _updateProfileResult

    private val _uploadPictureResult = MutableLiveData<Result<Unit>>()
    val uploadPictureResult: LiveData<Result<Unit>> get() = _uploadPictureResult

    fun loadUserProfile() {
        _isLoading.value = true // Mulai loading
        viewModelScope.launch {
            try {
                val currentUser = firebaseAuth.currentUser
                if (currentUser != null) {
                    val result = authRepository.getUser(currentUser.uid)
                    _userProfile.postValue(result)
                } else {
                    _userProfile.postValue(Result.failure(Exception("No user logged in")))
                }
            } catch (e: Exception) {
                _userProfile.postValue(Result.failure(e))
            } finally {
                // Tambahkan delay 2 detik
                kotlinx.coroutines.delay(2000)
                _isLoading.postValue(false) // Selesaikan loading
            }
        }
    }

    fun logout() {
        try {
            firebaseAuth.signOut()
            _logoutResult.postValue(true)
        } catch (e: Exception) {
            _logoutResult.postValue(false)
        }
    }

    fun updateUserProfile(name: String, bio: String) {
        viewModelScope.launch {
            val currentUser = firebaseAuth.currentUser
            val currentProfile = _userProfile.value?.getOrNull()

            if (currentUser != null && currentProfile != null) {
                val updatedUser = currentProfile.copy(
                    name = name,
                    bio = bio
                )
                val result = authRepository.updateUser(updatedUser)
                _updateProfileResult.postValue(result)
                if (result.isSuccess) {
                    _userProfile.postValue(Result.success(updatedUser))
                }
            } else {
                _updateProfileResult.postValue(Result.failure(Exception("User not found")))
            }
        }
    }

    fun uploadProfilePicture(imageFile: File) {
        viewModelScope.launch {
            val currentUser = firebaseAuth.currentUser
            if (currentUser == null) {
                _uploadPictureResult.postValue(Result.failure(Exception("No user logged in")))
                return@launch
            }

            // Memanggil fungsi suspend dari repository
            val result = authRepository.uploadProfilePicture(currentUser.uid, imageFile)

            // Memproses hasil dari repository
            result.onSuccess { newImageUrl ->
                // Jika upload gambar sukses, update data user dengan URL gambar baru
                updateUserImage(newImageUrl)
                _uploadPictureResult.postValue(Result.success(Unit))
            }.onFailure { exception ->
                _uploadPictureResult.postValue(Result.failure(exception))
            }
        }
    }

    private fun updateUserImage(newImageUrl: String) {
        viewModelScope.launch {
            val currentProfile = _userProfile.value?.getOrNull()
            if (currentProfile != null) {
                val updatedUser = currentProfile.copy(image = newImageUrl)
                val result = authRepository.updateUser(updatedUser)
                if (result.isSuccess) {
                    _userProfile.postValue(Result.success(updatedUser))
                }
                // Anda bisa menambahkan penanganan error jika update user gagal
            }
        }
    }
}