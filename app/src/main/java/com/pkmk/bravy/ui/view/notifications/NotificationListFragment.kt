package com.pkmk.bravy.ui.view.notifications

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.pkmk.bravy.databinding.FragmentNotificationListBinding
import com.pkmk.bravy.ui.adapter.NotificationAdapter
import com.pkmk.bravy.ui.viewmodel.NotificationsViewModel

class NotificationListFragment : Fragment() {

    private var _binding: FragmentNotificationListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NotificationsViewModel by activityViewModels()
    private lateinit var notificationAdapter: NotificationAdapter
    private var filterType: String = "ALL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            filterType = it.getString(ARG_FILTER_TYPE) ?: "ALL"
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.shimmerViewContainer.visibility = View.VISIBLE
                binding.rvNotification.visibility = View.GONE
                binding.tvNoNotifications.visibility = View.GONE
                binding.shimmerViewContainer.startShimmer()
            } else {
                binding.shimmerViewContainer.stopShimmer()
                binding.shimmerViewContainer.visibility = View.GONE
            }
        }

        viewModel.notifications.observe(viewLifecycleOwner) { result ->
            result.onSuccess { allNotifications ->
                val filteredList = when (filterType) {
                    "PROGRESS" -> allNotifications.filter { it.type == "PROGRESS" }
                    "CHAT" -> allNotifications.filter { it.type == "NEW_COMMENT" || it.type == "NEW_POST" || it.type == "NEW_LIKE" || it.type == "CHAT_MESSAGE" }
                    else -> allNotifications
                }

                if (filteredList.isEmpty()) {
                    binding.rvNotification.visibility = View.GONE
                    binding.tvNoNotifications.visibility = View.VISIBLE
                    binding.tvNoNotifications.text = when (filterType) {
                        "PROGRESS" -> "No notifications for Progress"
                        "CHAT" -> "No notifications for Chat"
                        else -> "No notifications"
                    }
                } else {
                    binding.rvNotification.visibility = View.VISIBLE
                    binding.tvNoNotifications.visibility = View.GONE
                    notificationAdapter.submitList(filteredList)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        notificationAdapter = NotificationAdapter()
        binding.rvNotification.apply {
            adapter = notificationAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_FILTER_TYPE = "filter_type"
        fun newInstance(filterType: String) =
            NotificationListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FILTER_TYPE, filterType)
                }
            }
    }
}