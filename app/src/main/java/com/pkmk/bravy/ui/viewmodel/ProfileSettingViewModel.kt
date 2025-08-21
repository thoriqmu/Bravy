package com.pkmk.bravy.ui.viewmodel

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProfileSettingViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _userProfile = MutableLiveData<Result<User>>()
    val userProfile: LiveData<Result<User>> = _userProfile

    private val _updateStatus = MutableLiveData<Result<Unit>>()
    val updateStatus: LiveData<Result<Unit>> = _updateStatus

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private var currentUser: User? = null

    fun loadUserProfile() {
        val uid = auth.currentUser?.uid ?: return
        _isLoading.value = true
        viewModelScope.launch {
            val result = repository.getUser(uid)
            _userProfile.postValue(result)
            result.onSuccess {
                currentUser = it // Simpan user asli untuk perbandingan
            }
            _isLoading.value = false
        }
    }

    fun saveProfileChanges(newName: String, newBio: String, newImageUri: Uri?, isImageRemoved: Boolean) {
        val userToUpdate = currentUser ?: return
        _isLoading.value = true

        viewModelScope.launch {
            try {
                var finalImageName = userToUpdate.image

                if (newImageUri != null) {
                    // Jika ada gambar baru untuk diunggah
                    val imageName = "profile_${userToUpdate.uid}.jpg"
                    val storageRef = FirebaseStorage.getInstance().getReference("picture/$imageName")
                    storageRef.putFile(newImageUri).await()
                    finalImageName = imageName
                } else if (isImageRemoved) {
                    // Jika gambar dihapus
                    finalImageName = null
                }

                // Buat objek user yang sudah diperbarui
                val updatedUser = userToUpdate.copy(
                    name = newName,
                    bio = newBio,
                    image = finalImageName
                )

                // Simpan ke Realtime Database
                val result = repository.updateUser(updatedUser)
                _updateStatus.postValue(result)

            } catch (e: Exception) {
                _updateStatus.postValue(Result.failure(e))
            } finally {
                _isLoading.value = false
            }
        }
    }
}