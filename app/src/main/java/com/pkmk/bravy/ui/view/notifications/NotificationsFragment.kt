package com.pkmk.bravy.ui.view.notifications

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
// --- UBAH IMPORT INI ---
import androidx.fragment.app.activityViewModels
import com.google.android.material.tabs.TabLayoutMediator
import com.pkmk.bravy.databinding.FragmentNotificationsBinding
import com.pkmk.bravy.ui.adapter.NotificationPagerAdapter
import com.pkmk.bravy.ui.viewmodel.NotificationsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NotificationsViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewPagerNotification.adapter = NotificationPagerAdapter(this)

        TabLayoutMediator(binding.tabLayoutNotification, binding.viewPagerNotification) { tab, position ->
            tab.text = when(position) {
                0 -> "All"
                1 -> "Progress"
                2 -> "Chat"
                else -> null
            }
        }.attach()

        viewModel.loadNotifications()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}