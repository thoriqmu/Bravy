package com.pkmk.bravy.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pkmk.bravy.data.model.CommunityPostDetails
import com.pkmk.bravy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
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

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

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
}