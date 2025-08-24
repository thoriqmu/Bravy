package com.pkmk.bravy.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.data.model.Comment
import com.pkmk.bravy.data.model.CommentDetails
import com.pkmk.bravy.data.model.CommunityPost
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DetailCommunityChatViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _post = MutableLiveData<Result<CommunityPost>>()
    val post: LiveData<Result<CommunityPost>> = _post

    private val _commentDetails = MutableLiveData<Result<List<CommentDetails>>>()
    val commentDetails: LiveData<Result<List<CommentDetails>>> = _commentDetails

    private val _commentPostStatus = MutableLiveData<Result<Unit>>()
    val commentPostStatus: LiveData<Result<Unit>> = _commentPostStatus

    private val _authorDetails = MutableLiveData<Result<User>>()
    val authorDetails: LiveData<Result<User>> = _authorDetails

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private var postListener: ValueEventListener? = null

    fun loadAuthorDetails(authorId: String) {
        viewModelScope.launch {
            val result = repository.getUser(authorId)
            _authorDetails.postValue(result)
        }
    }

    fun loadAndListenToPost(postId: String) {
        val postRef = database.getReference("community_chats").child(postId)
        postListener?.let { postRef.removeEventListener(it) }

        postListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val postData = snapshot.getValue(CommunityPost::class.java)
                if (postData != null) {
                    _post.postValue(Result.success(postData))
                    fetchCommentUserDetails(postData.comments ?: emptyMap())
                } else {
                    _post.postValue(Result.failure(Exception("Post not found.")))
                }
            }
            override fun onCancelled(error: DatabaseError) {
                _post.postValue(Result.failure(error.toException()))
            }
        }
        postRef.addValueEventListener(postListener!!)
    }

    // --- TAMBAHKAN FUNGSI BARU UNTUK MENGGABUNGKAN DATA ---
    private fun fetchCommentUserDetails(comments: Map<String, Comment>) {
        viewModelScope.launch {
            try {
                val detailsList = mutableListOf<CommentDetails>()
                // Ambil semua author UID unik untuk mengurangi panggilan database
                val authorUids = comments.values.map { it.authorUid }.toSet()
                val authorsMap = mutableMapOf<String, User>()

                // Ambil data semua author dalam beberapa panggilan
                for (uid in authorUids) {
                    repository.getUser(uid).onSuccess { user ->
                        authorsMap[uid] = user
                    }
                }

                // Gabungkan comment dengan author-nya
                for (comment in comments.values) {
                    authorsMap[comment.authorUid]?.let { author ->
                        detailsList.add(CommentDetails(comment, author))
                    }
                }

                // Urutkan berdasarkan waktu dan post ke LiveData
                val sortedList = detailsList.sortedBy { it.comment.timestamp }
                _commentDetails.postValue(Result.success(sortedList))

            } catch (e: Exception) {
                _commentDetails.postValue(Result.failure(e))
            }
        }
    }

    fun postComment(postId: String, commentText: String) {
        val currentUid = auth.currentUser?.uid
        if (currentUid == null || commentText.isBlank()) {
            return
        }

        viewModelScope.launch {
            val comment = Comment(
                commentId = UUID.randomUUID().toString(),
                authorUid = currentUid,
                commentText = commentText,
                timestamp = System.currentTimeMillis()
            )
            val result = repository.postComment(postId, comment)
            _commentPostStatus.postValue(result)
        }
    }

    fun postComment(postId: String, commentText: String, mediaUri: Uri?, mediaType: String?) {
        val currentUid = auth.currentUser?.uid
        if (currentUid == null || (commentText.isBlank() && mediaUri == null)) {
            _commentPostStatus.postValue(Result.failure(Exception("Comment cannot be empty.")))
            return
        }
        _isLoading.value = true

        viewModelScope.launch {
            try {
                var finalMediaUrl: String? = null
                // 1. Jika ada media, upload dulu ke Storage
                if (mediaUri != null && mediaType != null) {
                    val filePath = "comment_media/${UUID.randomUUID()}"
                    val storageRef = FirebaseStorage.getInstance().getReference(filePath)
                    finalMediaUrl = storageRef.putFile(mediaUri).await()
                        .storage.downloadUrl.await().toString()
                }

                // 2. Buat objek Comment
                val comment = Comment(
                    commentId = UUID.randomUUID().toString(),
                    authorUid = currentUid,
                    commentText = commentText,
                    mediaUrl = finalMediaUrl,
                    mediaType = mediaType,
                    timestamp = System.currentTimeMillis()
                )

                // 3. Simpan ke database
                val result = repository.postComment(postId, comment)
                _commentPostStatus.postValue(result)
            } catch (e: Exception) {
                _commentPostStatus.postValue(Result.failure(e))
            } finally {
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Pastikan untuk menghapus listener saat ViewModel dihancurkan
        postListener?.let {
            database.getReference("community_chats").removeEventListener(it)
        }
    }
}