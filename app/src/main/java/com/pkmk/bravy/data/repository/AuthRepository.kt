package com.pkmk.bravy.data.repository

import com.google.firebase.database.DataSnapshot
import com.pkmk.bravy.data.model.RedeemCode
import com.pkmk.bravy.data.model.User
import java.io.File

interface AuthRepository {
    suspend fun validateRedeemCode(code: String): Result<RedeemCode>
    suspend fun markRedeemCodeAsUsed(code: String): Result<Unit>
    suspend fun registerUser(user: User): Result<Unit>
    suspend fun createUserWithEmail(email: String, password: String): Result<String>
    suspend fun loginUser(email: String, password: String): Result<String>
    suspend fun getUser(uid: String): Result<User>
    suspend fun updateUser(user: User): Result<Unit>
    suspend fun uploadProfilePicture(uid: String, imageFile: File): Result<String>
    suspend fun getLearningLevels(): Result<DataSnapshot>
}