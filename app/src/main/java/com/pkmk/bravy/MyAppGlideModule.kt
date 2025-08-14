package com.pkmk.bravy

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.firebase.ui.storage.images.FirebaseImageLoader
import com.google.firebase.storage.StorageReference
import java.io.InputStream

// Anotasi ini tetap penting
@GlideModule
class MyAppGlideModule : AppGlideModule() {
    // Override metode ini untuk mendaftarkan komponen secara manual
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // Daftarkan FirebaseImageLoader untuk menangani objek StorageReference
        // dan mengubahnya menjadi InputStream yang bisa dibaca Glide.
        registry.append(
            StorageReference::class.java,
            InputStream::class.java,
            FirebaseImageLoader.Factory()
        )
    }
}