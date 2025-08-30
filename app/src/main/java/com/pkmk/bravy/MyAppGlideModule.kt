package com.pkmk.bravy

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.firebase.ui.storage.images.FirebaseImageLoader
import com.google.firebase.storage.StorageReference
import java.io.InputStream

// Anotasi ini penting untuk proses kompilasi Glide
@GlideModule
class MyAppGlideModule : AppGlideModule() {
    /**
     * Metode ini akan mendaftarkan komponen kita ke Glide.
     * Secara spesifik, kita memberitahu Glide cara menangani objek StorageReference
     * dengan menggunakan FirebaseImageLoader.
     */
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // Daftarkan FirebaseImageLoader untuk menangani objek StorageReference
        registry.append(
            StorageReference::class.java,
            InputStream::class.java,
            FirebaseImageLoader.Factory()
        )
    }
}