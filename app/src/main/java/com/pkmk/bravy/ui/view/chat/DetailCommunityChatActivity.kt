package com.pkmk.bravy.ui.view.chat

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.CommunityPostDetails
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.databinding.ActivityDetailCommunityChatBinding
import com.pkmk.bravy.databinding.ItemCommunityChatPostDetailsBinding
import com.pkmk.bravy.ui.adapter.CommentAdapter
import com.pkmk.bravy.ui.viewmodel.DetailCommunityChatViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class DetailCommunityChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailCommunityChatBinding
    private lateinit var postDetailsBinding: ItemCommunityChatPostDetailsBinding
    private val viewModel: DetailCommunityChatViewModel by viewModels()
    private lateinit var commentAdapter: CommentAdapter
    private var selectedMediaUri: Uri? = null
    private var selectedMediaType: String? = "image"

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedMediaUri = it
            binding.layoutMediaPreview.visibility = View.VISIBLE
            Glide.with(this).load(it).into(binding.ivMediaPreview)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailCommunityChatBinding.inflate(layoutInflater)
        postDetailsBinding = ItemCommunityChatPostDetailsBinding.bind(binding.layoutCommunityChat.root)
        setContentView(binding.root)

        // --- UBAH CARA MENGAMBIL DATA DARI INTENT ---
        val postId = intent.getStringExtra(EXTRA_POST_ID)
        val authorId = intent.getStringExtra(EXTRA_AUTHOR_ID)
        val focusComment = intent.getBooleanExtra(EXTRA_FOCUS_COMMENT, false)

        if (postId == null || authorId == null) {
            Toast.makeText(this, "Error: Post data is missing.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRecyclerView()
        setupListeners(postId)
        setupObservers()

        viewModel.loadAndListenToPost(postId)
        viewModel.loadAuthorDetails(authorId) // Minta ViewModel memuat data author

        if (focusComment) {
            focusCommentInput()
        }
    }

    private fun setupObservers() {
        // --- TAMBAHKAN OBSERVER BARU INI ---
        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                binding.shimmerViewContainer.visibility = View.VISIBLE
                binding.contentLayout.visibility = View.GONE
                binding.shimmerViewContainer.startShimmer()
            } else {
                binding.shimmerViewContainer.stopShimmer()
                binding.shimmerViewContainer.visibility = View.GONE
                binding.contentLayout.visibility = View.VISIBLE
            }
        }

        viewModel.post.observe(this) { result ->
            result.onSuccess { post ->
                // Update UI yang bisa berubah (seperti like/comment count)
                postDetailsBinding.tvPostTitle.text = post.title
                postDetailsBinding.tvPostDescription.text = post.description
                postDetailsBinding.tvPostTime.text = formatTimestamp(post.timestamp)
                postDetailsBinding.likesCount.text = (post.likes?.size ?: 0).toString()
                postDetailsBinding.commentsCount.text = (post.comments?.size ?: 0).toString()

                if (post.imageUrl != null) {
                    postDetailsBinding.ivCommunityChatAttachment.visibility = View.VISIBLE
                    Glide.with(this).load(post.imageUrl).into(postDetailsBinding.ivCommunityChatAttachment)
                } else {
                    postDetailsBinding.ivCommunityChatAttachment.visibility = View.GONE
                }
            }
        }

        viewModel.authorDetails.observe(this) { result ->
            result.onSuccess { author ->
                // --- UPDATE TOOLBAR TITLE ---
                val postTitle = viewModel.post.value?.getOrNull()?.title ?: ""
                binding.materialTextView8.text = "${author.name} - $postTitle"

                // Update UI author di dalam layout post
                postDetailsBinding.tvUserName.text = author.name
                loadProfileImage(author.image)
            }
        }

        // --- TAMBAHKAN OBSERVER BARU UNTUK KOMENTAR ---
        viewModel.commentDetails.observe(this) { result ->
            result.onSuccess { comments ->
                commentAdapter.submitList(comments)
            }.onFailure {
                Toast.makeText(this, "Failed to load comments", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.commentPostStatus.observe(this) { result ->
            result.onSuccess {
                binding.commentInput.text?.clear()
                hideKeyboard()
                Toast.makeText(this, "Comment posted", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, "Failed to post comment", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.commentPostStatus.observe(this) { result ->
            result.onSuccess {
                binding.commentInput.text?.clear()
                hideKeyboard()
                // Reset media preview
                selectedMediaUri = null
                selectedMediaType = null
                binding.layoutMediaPreview.visibility = View.GONE
                Toast.makeText(this, "Comment posted", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, "Failed to post comment", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupListeners(postId: String) {
        binding.btnSend.setOnClickListener {
            val commentText = binding.commentInput.text.toString().trim()
            viewModel.postComment(postId, commentText, selectedMediaUri, selectedMediaType)
        }

        binding.btnAttachment.setOnClickListener {
            pickMediaLauncher.launch("image/*")
        }

        binding.btnRemoveMedia.setOnClickListener {
            selectedMediaUri = null
            binding.layoutMediaPreview.visibility = View.GONE
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        commentAdapter = CommentAdapter()
        binding.recyclerView.adapter = commentAdapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this) // Perbaikan dari log sebelumnya
    }

    private fun focusCommentInput() {
        binding.commentInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.commentInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val view = this.currentFocus
        view?.let {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    private fun loadProfileImage(imageName: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = if (imageName.isNullOrEmpty()) {
                    FirebaseStorage.getInstance().getReference("picture/default.jpg").downloadUrl.await()
                } else {
                    FirebaseStorage.getInstance().getReference("picture/$imageName").downloadUrl.await()
                }
                withContext(Dispatchers.Main) {
                    Glide.with(this@DetailCommunityChatActivity)
                        .load(url)
                        .circleCrop()
                        .placeholder(R.drawable.ic_profile)
                        .into(postDetailsBinding.ivUserProfile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    postDetailsBinding.ivUserProfile.setImageResource(R.drawable.ic_profile)
                }
            }
        }
    }

    private fun formatTimestamp(timestamp: Long?): String {
        if (timestamp == null) return ""
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - timestamp
        val seconds = elapsedTime / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days > 0 -> "$days days ago"
            hours > 0 -> "$hours hours ago"
            minutes > 0 -> "$minutes minutes ago"
            else -> "Just now"
        }
    }

    companion object {
        const val EXTRA_POST_ID = "extra_post_id"
        const val EXTRA_AUTHOR_ID = "extra_author_id"
        const val EXTRA_FOCUS_COMMENT = "extra_focus_comment"
    }
}