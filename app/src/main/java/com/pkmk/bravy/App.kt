package com.pkmk.bravy

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)

//        Log.d("BravyApp", "Application.onCreate() called") // Log untuk memastikan kelas ini berjalan
//
//        FirebaseApp.initializeApp(this)
//        val firebaseAppCheck = FirebaseAppCheck.getInstance()
//
//        Log.d("BravyApp", "Forcing installation of DebugAppCheckProviderFactory")
//
//        firebaseAppCheck.installAppCheckProviderFactory(
//            DebugAppCheckProviderFactory.getInstance()
//        )
    }
}