package com.pkmk.bravy.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
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
                // Profil diambil berdasarkan Bearer token, bukan uid Firebase.
                val result = authRepository.getProfileBackend()
                _userProfile.postValue(result)
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
        viewModelScope.launch {
            // Sesi ditentukan oleh Bearer token, jadi keluar dilakukan dengan
            // mencabut token di server, bukan lewat FirebaseAuth.
            val result = authRepository.logoutBackend()
            _logoutResult.postValue(result.isSuccess)
        }
    }

    fun updateUserProfile(name: String, bio: String) {
        viewModelScope.launch {
            if (_userProfile.value?.getOrNull() == null) {
                _updateProfileResult.postValue(Result.failure(Exception("User not found")))
                return@launch
            }

            // Backend mengembalikan dokumen profil terbaru, jadi LiveData diisi dari
            // respons alih-alih menyalin state lokal.
            val result = authRepository.updateProfileBackend(name, bio)
            _updateProfileResult.postValue(result.map { Unit })
            result.onSuccess { _userProfile.postValue(Result.success(it)) }
        }
    }

    fun uploadProfilePicture(imageFile: File) {
        viewModelScope.launch {
            // Endpoint menerima berkas mentah dan menyimpannya pada user pemilik
            // Bearer token, sehingga uid tidak lagi diperlukan sebagai argumen.
            val result = authRepository.uploadAvatarBackend(imageFile)

            // Memproses hasil dari repository
            result.onSuccess { newImageUrl ->
                // Backend sudah menyimpan avatarUrl pada dokumen user saat unggahan
                // berhasil, jadi tidak ada PATCH profil lanjutan yang dikirim di sini.
                updateUserImage(newImageUrl)
                _uploadPictureResult.postValue(Result.success(Unit))
            }.onFailure { exception ->
                _uploadPictureResult.postValue(Result.failure(exception))
            }
        }
    }

    private fun updateUserImage(newImageUrl: String) {
        val currentProfile = _userProfile.value?.getOrNull() ?: return
        _userProfile.postValue(Result.success(currentProfile.copy(image = newImageUrl)))
    }
}