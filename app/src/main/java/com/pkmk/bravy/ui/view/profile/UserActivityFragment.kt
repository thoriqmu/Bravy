package com.pkmk.bravy.ui.view.profile

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pkmk.bravy.databinding.FragmentUserActivityBinding
import com.pkmk.bravy.ui.view.friend.FriendActivity

class UserActivityFragment : Fragment() {

    private var _binding: FragmentUserActivityBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // TODO: Tambahkan ViewModel untuk mengambil data Points dan Streak
        // Untuk sekarang, kita buat listenernya dulu

        binding.btnFriendList.setOnClickListener {
            startActivity(Intent(requireContext(), FriendActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}