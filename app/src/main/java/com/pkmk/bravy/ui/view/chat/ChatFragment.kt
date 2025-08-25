package com.pkmk.bravy.ui.view.chat

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.CommunityPostDetails
import com.pkmk.bravy.databinding.FragmentChatBinding
import com.pkmk.bravy.ui.adapter.SuggestedFriendAdapter
import com.pkmk.bravy.ui.view.friend.FriendActivity
import com.pkmk.bravy.ui.viewmodel.ChatViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatViewModel by viewModels()
    private val storageRef = FirebaseStorage.getInstance().getReference("picture")
    private lateinit var suggestedFriendAdapter: SuggestedFriendAdapter
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSuggestedFriendsAdapter()
        setupListeners()
        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadUserProfile()
        viewModel.loadRecentChatUsers()
        viewModel.loadSuggestedFriends()
        viewModel.loadLatestCommunityPost()
    }

    private fun setupListeners() {
        binding.btnFriendList.setOnClickListener {
            startActivity(Intent(requireContext(), FriendActivity::class.java))
        }

        binding.btnPrivateChat.setOnClickListener {
            startActivity(Intent(requireContext(), PrivateChatActivity::class.java))
        }

        binding.btnCommunityChat.setOnClickListener {
            startActivity(Intent(requireContext(), CommunityChatActivity::class.java))
        }
    }

    private fun setupObservers() {
        viewModel.userProfile.observe(viewLifecycleOwner) { result ->
            result.onSuccess { user ->
                loadUserImage(user.image)
            }.onFailure { exception ->
                Toast.makeText(requireContext(), "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                loadUserImage(null)
            }
        }

        viewModel.suggestedFriends.observe(viewLifecycleOwner) { result ->
            result.onSuccess { users ->
                // TAMBAHKAN LOG INI untuk verifikasi
                Log.d("ChatFragment", "Successfully loaded ${users.size} suggested friends. Updating adapter.")
                if (users.isEmpty()) {
                    // Jika tidak ada rekomendasi, sembunyikan RecyclerView dan tampilkan Card
                    binding.rvSuggestedFriends.visibility = View.GONE
                    binding.cardFindFriends.visibility = View.VISIBLE
                } else {
                    // Jika ada rekomendasi, tampilkan RecyclerView dan sembunyikan Card
                    binding.rvSuggestedFriends.visibility = View.VISIBLE
                    binding.cardFindFriends.visibility = View.GONE
                    suggestedFriendAdapter.updateUsers(users)
                }
            }.onFailure {
                Toast.makeText(requireContext(), "Failed to load suggestions", Toast.LENGTH_SHORT).show()
                binding.rvSuggestedFriends.visibility = View.GONE
                binding.cardFindFriends.visibility = View.VISIBLE
            }
        }

        viewModel.friendActionStatus.observe(viewLifecycleOwner) { result ->
            result.onFailure {
                Toast.makeText(requireContext(), "Action failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.recentChats.observe(viewLifecycleOwner) { result ->
            result.onSuccess { recentChats ->
                if (recentChats.isNotEmpty()) {
                    // Ambil chat pertama dari daftar yang sudah diurutkan
                    val firstChat = recentChats.first()
                    val user = firstChat.user
                    val chatId = firstChat.chatId
                    val lastMessage = firstChat.lastMessage

                    binding.recentChatName.text = user.name

                    // Gunakan data pesan terakhir yang sudah ada, JANGAN observe lagi
                    binding.recentChatText.text = when (lastMessage?.type) {
                        "text" -> lastMessage.content
                        "image" -> "Image"
                        "audio" -> "Audio"
                        else -> "No messages yet"
                    }
                    binding.recentChatTime.text = formatTimestamp(lastMessage?.timestamp)

                    loadProfileImage(user.image)

                    binding.layoutRecentChat.setOnClickListener {
                        val intent = Intent(requireContext(), DetailPrivateChatActivity::class.java).apply {
                            // Kirim data yang dibutuhkan oleh DetailPrivateChatActivity
                            putExtra(DetailPrivateChatActivity.EXTRA_CHAT_ID, chatId)
                            putExtra(DetailPrivateChatActivity.EXTRA_OTHER_USER, user)
                        }
                        // Mulai Activity
                        startActivity(intent)
                    }

                } else {
                    // Handle jika tidak ada recent chat
                    binding.recentChatName.text = "No Recent Chat"
                    binding.recentChatText.text = "Start a new conversation"
                }
            }.onFailure { exception ->
                Toast.makeText(requireContext(), "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.latestCommunityPost.observe(viewLifecycleOwner) { result ->
            result.onSuccess { postDetails ->
                if (postDetails != null) {
                    binding.layoutCommunity.visibility = View.VISIBLE
                    bindLatestCommunityPost(postDetails) // Panggil fungsi bind terpisah
                } else {
                    binding.layoutCommunity.visibility = View.GONE
                }
            }.onFailure { exception ->
                binding.layoutCommunity.visibility = View.GONE
                Log.e("ChatFragment", "Error loading latest community post: ${exception.message}")
            }
        }
    }

    private fun bindLatestCommunityPost(postDetails: CommunityPostDetails) {
        val post = postDetails.post
        val author = postDetails.author

        // Isi data ke view
        binding.tvUserName.text = author.name
        binding.tvPostTitle.text = post.title
        binding.tvPostDescription.text = post.description
        binding.tvPostTime.text = formatTimestamp(post.timestamp)
        binding.likesCount.text = (post.likes?.size ?: 0).toString()
        binding.commentsCount.text = (post.comments?.size ?: 0).toString()

        loadCommunityAuthorProfileImage(author.image)

        if (post.imageUrl != null) {
            binding.ivCommunityChatAttachment.visibility = View.VISIBLE
            Glide.with(this).load(post.imageUrl).into(binding.ivCommunityChatAttachment)
        } else {
            binding.ivCommunityChatAttachment.visibility = View.GONE
        }

        // --- LOGIKA INTERAKTIF ---
        updateLikeButton(post.likes?.containsKey(currentUserId) == true)

        binding.btnLike.setOnClickListener {
            viewModel.toggleLikeOnLatestPost()
        }

        val openDetailIntent = { focusComment: Boolean ->
            val intent = Intent(requireContext(), DetailCommunityChatActivity::class.java).apply {
                putExtra(DetailCommunityChatActivity.EXTRA_POST_ID, post.postId)
                putExtra(DetailCommunityChatActivity.EXTRA_AUTHOR_ID, author.uid)
                putExtra(DetailCommunityChatActivity.EXTRA_FOCUS_COMMENT, focusComment)
            }
            startActivity(intent)
        }

        binding.layoutPostContent.setOnClickListener { openDetailIntent(false) }
        binding.btnComment.setOnClickListener { openDetailIntent(true) }
    }

    private fun updateLikeButton(isLiked: Boolean) {
        val context = requireContext()
        if (isLiked) {
            binding.btnLike.setTextColor(ContextCompat.getColor(context, R.color.primary))
            binding.btnLike.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary))
        } else {
            binding.btnLike.setTextColor(ContextCompat.getColor(context, R.color.onBackground))
            binding.btnLike.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.onBackground))
        }
    }

    private fun loadUserImage(imageName: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Coba muat gambar dari nama yang diberikan, jika null, gunakan default.jpg
                val finalImageName = imageName ?: "default.jpg"
                val imageUrl = storageRef.child(finalImageName).downloadUrl.await()

                Glide.with(this@ChatFragment)
                    .load(imageUrl)
                    .circleCrop() // Membuat gambar jadi lingkaran
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(binding.ivProfilePhoto)

            } catch (e: Exception) {
                // Jika semua gagal, muat gambar placeholder dari drawable
                Glide.with(this@ChatFragment)
                    .load(R.drawable.ic_profile)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(binding.ivProfilePhoto)
            }
        }
    }

    private fun loadProfileImage(imageUrl: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val finalImageName = imageUrl ?: "default.jpg"

                val storageRef = FirebaseStorage.getInstance().getReference("picture").child(finalImageName)
                val downloadUrl = storageRef.downloadUrl.await()
                Glide.with(this@ChatFragment)
                    .load(downloadUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(binding.ivRecentChat)
            } catch (e: Exception) {
                // Jika gagal mengambil dari storage, muat gambar default
                Log.e("ChatFragment", "Error loading profile image: ${e.message}")
                binding.ivRecentChat.setImageResource(R.drawable.ic_profile)
            }
        }
    }

    private fun loadCommunityAuthorProfileImage(imageName: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val finalImageName = imageName ?: "default.jpg"
                val downloadUrl = storageRef.child(finalImageName).downloadUrl.await()
                Glide.with(this@ChatFragment)
                    .load(downloadUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_profile)
                    .into(binding.ivUserProfile) // <-- Target ImageView yang benar
            } catch (e: Exception) {
                binding.ivUserProfile.setImageResource(R.drawable.ic_profile)
            }
        }
    }

    private fun setupSuggestedFriendsAdapter() {
        // PERUBAHAN: Berikan viewLifecycleOwner.lifecycleScope ke adapter
        suggestedFriendAdapter = SuggestedFriendAdapter(
            emptyList(),
            viewLifecycleOwner.lifecycleScope, // <-- Pinjamkan scope di sini
            onAddFriend = { user -> viewModel.sendFriendRequest(user.uid) },
            onCancelRequest = { user -> viewModel.cancelFriendRequest(user.uid) }
        )
        binding.rvSuggestedFriends.adapter = suggestedFriendAdapter
    }

    private fun formatTimestamp(timestamp: Long?): String {
        if (timestamp == null) return "Unknown"
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
            else -> "$seconds seconds ago"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}