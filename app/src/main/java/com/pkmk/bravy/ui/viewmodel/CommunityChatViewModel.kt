package com.pkmk.bravy.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.data.model.CommunityPost
import com.pkmk.bravy.data.model.CommunityPostDetails
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CommunityChatViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _allPosts = MutableLiveData<Result<List<CommunityPostDetails>>>()
    val allPosts: LiveData<Result<List<CommunityPostDetails>>> = _allPosts

    private val _friendPosts = MutableLiveData<Result<List<CommunityPostDetails>>>()
    val friendPosts: LiveData<Result<List<CommunityPostDetails>>> = _friendPosts

    private val _postCreationStatus = MutableLiveData<Result<Unit>>()
    val postCreationStatus: LiveData<Result<Unit>> = _postCreationStatus

    private val _currentUser = MutableLiveData<Result<User>>()
    val currentUser: LiveData<Result<User>> = _currentUser

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadCurrentUser() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _currentUser.postValue(Result.failure(Exception("User not logged in")))
            return
        }
        viewModelScope.launch {
            _currentUser.postValue(authRepository.getUser(uid))
        }
    }


    // --- TAMBAHKAN FUNGSI BARU ---
    fun createPost(title: String, description: String, imageUri: Uri?) {
        _isLoading.value = true
        val currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            _postCreationStatus.postValue(Result.failure(Exception("User not logged in.")))
            _isLoading.value = false
            return
        }

        viewModelScope.launch {
            try {
                var imageUrl: String? = null
                // 1. Jika ada gambar, upload dulu ke Firebase Storage
                if (imageUri != null) {
                    val storageRef = FirebaseStorage.getInstance()
                        .getReference("community_images/${UUID.randomUUID()}")
                    imageUrl = storageRef.putFile(imageUri).await()
                        .storage.downloadUrl.await().toString()
                }

                // 2. Buat objek CommunityPost
                val postId = UUID.randomUUID().toString()
                val post = CommunityPost(
                    postId = postId,
                    authorUid = currentUid,
                    title = title, // Kita akan gunakan baris pertama deskripsi sebagai judul
                    description = description,
                    imageUrl = imageUrl,
                    timestamp = System.currentTimeMillis()
                )

                // 3. Simpan post ke database
                val result = authRepository.createCommunityPost(post)
                _postCreationStatus.postValue(result)

            } catch (e: Exception) {
                _postCreationStatus.postValue(Result.failure(e))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadCommunityPosts() {
        _isLoading.value = true
        viewModelScope.launch {
            val currentUid = auth.currentUser?.uid ?: return@launch

            // Ambil semua post terlebih dahulu
            val allPostsResult = authRepository.getAllCommunityPostsWithDetails()
            _allPosts.postValue(allPostsResult)

            // Setelah mendapatkan semua post, filter untuk teman
            allPostsResult.onSuccess { allPostsList ->
                val friendsResult = authRepository.getFriendsData(currentUid)
                friendsResult.onSuccess { friendInfoList ->
                    val friendUids = friendInfoList
                        .filter { it.status == "friend" }
                        .map { it.user.uid }
                        .toSet()

                    val filteredPosts = allPostsList.filter { it.author.uid in friendUids }
                    _friendPosts.postValue(Result.success(filteredPosts))

                }.onFailure {
                    _friendPosts.postValue(Result.failure(it))
                }
            }.onFailure {
                // Jika gagal mengambil semua post, maka tab teman juga gagal
                _friendPosts.postValue(Result.failure(it))
            }
            _isLoading.postValue(false)
        }
    }

    fun toggleLikeOnPost(postId: String) {
        val currentUid = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            // Panggil repository untuk mengubah data di Firebase
            val result = authRepository.toggleLikeOnPost(postId, currentUid)

            // Jika berhasil, perbarui LiveData secara manual untuk umpan balik instan di UI
            if (result.isSuccess) {
                updatePostInLiveData(postId, currentUid)
            }
        }
    }

    private fun updatePostInLiveData(postId: String, currentUid: String) {
        val currentAllPosts = _allPosts.value?.getOrNull()?.toMutableList() ?: return

        // Cari post yang di-like di dalam daftar
        val postIndex = currentAllPosts.indexOfFirst { it.post.postId == postId }
        if (postIndex != -1) {
            val oldDetails = currentAllPosts[postIndex]
            val oldPost = oldDetails.post
            val newLikes = oldPost.likes.toMutableMap()

            // Toggle like status
            if (newLikes.containsKey(currentUid)) {
                newLikes.remove(currentUid) // Unlike
            } else {
                newLikes[currentUid] = true // Like
            }

            // Buat objek post baru dengan data like yang sudah diperbarui
            val newPost = oldPost.copy(likes = newLikes)
            val newDetails = oldDetails.copy(post = newPost)

            // Ganti item lama dengan yang baru di dalam daftar
            currentAllPosts[postIndex] = newDetails

            // Post daftar yang sudah diperbarui ke LiveData
            _allPosts.postValue(Result.success(currentAllPosts))

            // Lakukan hal yang sama untuk friendPosts
            // (Ini memastikan kedua tab disinkronkan)
            val currentFriendPosts = _friendPosts.value?.getOrNull()?.toMutableList() ?: return
            val friendPostIndex = currentFriendPosts.indexOfFirst { it.post.postId == postId }
            if(friendPostIndex != -1){
                currentFriendPosts[friendPostIndex] = newDetails
                _friendPosts.postValue(Result.success(currentFriendPosts))
            }
        }
    }
}