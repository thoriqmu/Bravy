package com.pkmk.bravy.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.pkmk.bravy.R
import com.pkmk.bravy.ui.view.auth.OnboardingFragment

class OnboardingAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = 3 // Jumlah halaman onboarding

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> OnboardingFragment.newInstance(
                "Welcome to Bravy.id",
                "Your journey to confident English speaking starts here. Let's overcome speaking anxiety together!",
                R.drawable.onboarding_1
            )
            1 -> OnboardingFragment.newInstance(
                "Practice With Ease",
                "Explore fun exercises and personalized videos to boost your speaking skills at your own pace.",
                R.drawable.onboarding_2
            )
            else -> OnboardingFragment.newInstance(
                "Let's Get Started",
                "Connect with other learners, share tips, and practice speaking in a supportive environment.",
                R.drawable.onboarding_3
            )
        }
    }
}