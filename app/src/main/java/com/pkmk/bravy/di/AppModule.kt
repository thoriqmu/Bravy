package com.pkmk.bravy.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.data.repository.AuthRepository
import com.pkmk.bravy.data.repository.AuthRepositoryImpl
import com.pkmk.bravy.data.source.FirebaseDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase {
        return FirebaseDatabase.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage { // Ditambahkan: Firebase Storage
        return FirebaseStorage.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseDataSource(
        auth: FirebaseAuth,
        database: FirebaseDatabase,
        storage: FirebaseStorage // Ditambahkan: Firebase Storage
    ): FirebaseDataSource {
        return FirebaseDataSource(auth, database, storage)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(dataSource: FirebaseDataSource): AuthRepository {
        return AuthRepositoryImpl(dataSource)
    }
}