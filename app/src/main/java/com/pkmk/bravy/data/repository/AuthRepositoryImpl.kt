package com.pkmk.bravy.data.repository

import android.util.Log
import com.pkmk.bravy.data.model.RedeemCode
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.source.FirebaseDataSource
import java.io.File
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val dataSource: FirebaseDataSource
) : AuthRepository {
    private val TAG = "AuthRepositoryImpl"

    override suspend fun validateRedeemCode(code: String): Result<RedeemCode> {
        return try {
            val redeemCode = dataSource.validateRedeemCode(code)
            Log.d(TAG, "Validating redeem code $code: $redeemCode")
            when {
                redeemCode == null -> {
                    Log.e(TAG, "Invalid redeem code: $code")
                    Result.failure(Exception("Invalid redeem code"))
                }
                redeemCode.isUsed -> {
                    Log.e(TAG, "Redeem code $code has been used")
                    Result.failure(Exception("Redeem code has been used"))
                }
                else -> {
                    Log.d(TAG, "Redeem code $code is valid")
                    Result.success(redeemCode)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating redeem code $code: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun markRedeemCodeAsUsed(code: String): Result<Unit> {
        return try {
            dataSource.markRedeemCodeAsUsed(code)
            Log.d(TAG, "Successfully marked redeem code $code as used")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking redeem code $code as used: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun registerUser(user: User): Result<Unit> {
        return try {
            dataSource.registerUser(user)
            Log.d(TAG, "Successfully registered user ${user.uid}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering user ${user.uid}: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun createUserWithEmail(email: String, password: String): Result<String> {
        return try {
            val uid = dataSource.createUserWithEmail(email, password)
            if (uid != null) {
                Log.d(TAG, "Successfully created user with email $email, uid: $uid")
                Result.success(uid)
            } else {
                Log.e(TAG, "Failed to create user with email $email: UID is null")
                Result.failure(Exception("Failed to create user"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating user with email $email: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun loginUser(email: String, password: String): Result<String> {
        return try {
            val uid = dataSource.loginUser(email, password)
            if (uid != null) {
                Log.d(TAG, "Successfully logged in user with email $email, uid: $uid")
                Result.success(uid)
            } else {
                Log.e(TAG, "Failed to login user with email $email: UID is null")
                Result.failure(Exception("Login failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging in user with email $email: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getUser(uid: String): Result<User> {
        return try {
            val user = dataSource.getUser(uid)
            if (user != null) {
                Log.d(TAG, "Successfully fetched user $uid")
                Result.success(user)
            } else {
                Log.e(TAG, "User $uid not found")
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user $uid: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun updateUser(user: User): Result<Unit> {
        return try {
            dataSource.updateUser(user)
            Log.d(TAG, "Successfully updated user ${user.uid}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user ${user.uid}: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun uploadProfilePicture(uid: String, imageFile: File): Result<String> {
        return try {
            val imageName = "profile_$uid.jpg"
            val downloadUrl = dataSource.uploadProfilePicture(imageFile, imageName)
            Log.d(TAG, "Successfully uploaded profile picture for $uid: $downloadUrl")
            Result.success(imageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading profile picture for $uid: ${e.message}")
            Result.failure(e)
        }
    }
}