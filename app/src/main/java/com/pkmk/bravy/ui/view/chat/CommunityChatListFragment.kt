package com.pkmk.bravy.ui.view.chat

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.pkmk.bravy.databinding.FragmentCommunityChatListBinding
import com.pkmk.bravy.ui.adapter.CommunityChatAdapter
import com.pkmk.bravy.ui.viewmodel.CommunityChatViewModel

class CommunityChatListFragment : Fragment() {

    private var _binding: FragmentCommunityChatListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CommunityChatViewModel by activityViewModels()
    private lateinit var postAdapter: CommunityChatAdapter
    private var postType: String = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            postType = it.getString(ARG_POST_TYPE, "all")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommunityChatListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.shimmerViewContainer.visibility = View.VISIBLE
                binding.rvCommunityChat.visibility = View.GONE
                binding.shimmerViewContainer.startShimmer()
            } else {
                binding.shimmerViewContainer.stopShimmer()
                binding.shimmerViewContainer.visibility = View.GONE
                binding.rvCommunityChat.visibility = View.VISIBLE
            }
        }

        // Observer untuk data post
        if (postType == "all") {
            viewModel.allPosts.observe(viewLifecycleOwner) { result ->
                result.onSuccess { posts ->
                    postAdapter.submitList(posts)
                }
            }
        } else {
            viewModel.friendPosts.observe(viewLifecycleOwner) { result ->
                result.onSuccess { posts ->
                    postAdapter.submitList(posts)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        // --- PERBARUI CARA MEMBUAT ADAPTER ---
        postAdapter = CommunityChatAdapter(viewModel) { postDetails ->
            // TODO: Handle klik untuk membuka detail post
        }
        binding.rvCommunityChat.apply {
            adapter = postAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_POST_TYPE = "post_type"
        @JvmStatic
        fun newInstance(postType: String) =
            CommunityChatListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_POST_TYPE, postType)
                }
            }
    }
}