package com.pkmk.bravy.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {
    private val _redeemResult = MutableLiveData<Result<String>>()
    val redeemResult: LiveData<Result<String>> get() = _redeemResult

    private val _registerResult = MutableLiveData<Result<Unit>>()
    val registerResult: LiveData<Result<Unit>> get() = _registerResult

    private val _loginResult = MutableLiveData<Result<Unit>>()
    val loginResult: LiveData<Result<Unit>> get() = _loginResult

    fun validateRedeemCode(code: String) {
        viewModelScope.launch {
            val result = repository.validateRedeemCode(code)
            _redeemResult.postValue(result.map { it.code })
        }
    }

    fun markRedeemCodeAsUsed(code: String) {
        viewModelScope.launch {
            repository.markRedeemCodeAsUsed(code)
        }
    }

    fun registerUser(name: String, email: String, password: String, redeemCode: String) {
        viewModelScope.launch {
            val createResult = repository.createUserWithEmail(email, password)
            createResult.onSuccess { uid ->
                val user = User(uid, name, email, redeemCode)
                val registerResult = repository.registerUser(user)
                _registerResult.postValue(registerResult)
                if (registerResult.isSuccess) {
                    markRedeemCodeAsUsed(redeemCode)
                }
            }.onFailure { exception ->
                _registerResult.postValue(Result.failure(exception))
            }
        }
    }

    fun loginUser(email: String, password: String) {
        viewModelScope.launch {
            val result = repository.loginUser(email, password)
            _loginResult.postValue(result.map { Unit })
        }
    }
}