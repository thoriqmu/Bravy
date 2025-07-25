package com.pkmk.bravy

import android.app.Application
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Disable offline persistence for debugging
        FirebaseDatabase.getInstance().setPersistenceEnabled(false)
    }
}