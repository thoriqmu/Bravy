package com.pkmk.bravy.ui.view.friend

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pkmk.bravy.databinding.FragmentFriendListBinding
import com.pkmk.bravy.ui.adapter.FriendAdapter
import com.pkmk.bravy.ui.viewmodel.FriendViewModel

class FriendListFragment : Fragment() {

    private var _binding: FragmentFriendListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FriendViewModel by activityViewModels()
    private lateinit var friendAdapter: FriendAdapter
    private var statusType: String = "friend"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            statusType = it.getString(ARG_STATUS_TYPE, "friend")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFriendListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()

        // Observer untuk loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.shimmerViewContainer.visibility = View.VISIBLE
                binding.rvFriends.visibility = View.GONE
                binding.shimmerViewContainer.startShimmer()
            } else {
                binding.shimmerViewContainer.stopShimmer()
                binding.shimmerViewContainer.visibility = View.GONE
                binding.rvFriends.visibility = View.VISIBLE
            }
        }

        // Observer untuk data
        when (statusType) {
            "friend" -> viewModel.friendList.observe(viewLifecycleOwner) { friendAdapter.submitList(it) }
            "received" -> viewModel.receivedList.observe(viewLifecycleOwner) { friendAdapter.submitList(it) }
            "sent" -> viewModel.sentList.observe(viewLifecycleOwner) { friendAdapter.submitList(it) }
        }
    }

    private fun setupRecyclerView() {
        // PERBAIKAN: Berikan viewLifecycleOwner.lifecycleScope ke adapter
        friendAdapter = FriendAdapter(viewLifecycleOwner.lifecycleScope) { friendInfo, action ->
            showConfirmationDialog(friendInfo.user.name, action) {
                when (action) {
                    "accept" -> viewModel.acceptRequest(friendInfo.user.uid)
                    "remove" -> viewModel.removeOrRejectFriendship(friendInfo.user.uid, false)
                    "cancel" -> viewModel.removeOrRejectFriendship(friendInfo.user.uid, true)
                    "reject" -> viewModel.removeOrRejectFriendship(friendInfo.user.uid, true)
                }
            }
        }
        binding.rvFriends.apply {
            adapter = friendAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun showConfirmationDialog(userName: String, action: String, onConfirm: () -> Unit) {
        val (title, message) = when (action) {
            "accept" -> "Accept Request" to "Are you sure you want to be friends with $userName?"
            "remove" -> "Remove Friend" to "Are you sure you want to remove $userName from your friend list?"
            "cancel" -> "Cancel Request" to "Are you sure you want to cancel your friend request to $userName?"
            "reject" -> "Reject Request" to "Are you sure you want to reject the friend request from $userName?"
            else -> "" to ""
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Yes") { _, _ -> onConfirm() }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_STATUS_TYPE = "status_type"
        @JvmStatic
        fun newInstance(statusType: String) =
            FriendListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_STATUS_TYPE, statusType)
                }
            }
    }
}