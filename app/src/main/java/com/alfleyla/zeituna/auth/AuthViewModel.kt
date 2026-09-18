package com.alfleyla.zeituna.auth

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfleyla.zeituna.data.SupabaseClientObj
import com.alfleyla.zeituna.data.models.Profile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AuthViewModel : ViewModel() {

    private val _registrationStatus = MutableLiveData<Result<Boolean>>()
    val registrationStatus: LiveData<Result<Boolean>> = _registrationStatus

    private val _resetPasswordStatus = MutableLiveData<Result<Boolean>?>()
    val resetPasswordStatus: LiveData<Result<Boolean>?> = _resetPasswordStatus

    private val _legalUpdateStatus = MutableLiveData<Result<Boolean>>()
    val legalUpdateStatus: LiveData<Result<Boolean>> = _legalUpdateStatus

    fun registerStudent(emailInput: String, passwordInput: String, fullNameInput: String) {
        viewModelScope.launch {
            try {
                SupabaseClientObj.client.auth.signOut()
                val response = SupabaseClientObj.client.auth.signUpWith(Email) {
                    email = emailInput
                    password = passwordInput
                    data = buildJsonObject { put("full_name", fullNameInput) }
                }
                if (response != null) {
                    try {
                        val newProfile = Profile(
                            id = response.id,
                            email = emailInput,
                            full_name = fullNameInput,
                            agreed_to_legal = true // User agreed on Register Screen
                        )
                        SupabaseClientObj.client.postgrest["profiles"].insert(newProfile)
                        Log.d("AuthFlow", "Profile inserted during registration.")
                    } catch (e: Exception) {
                        Log.w("AuthFlow", "Profile insertion delayed until email confirmation: ${e.message}")
                    }
                }
                _registrationStatus.postValue(Result.success(true))
            } catch (e: Exception) {
                Log.e("AuthFlow", "Registration error: ${e.message}")
                _registrationStatus.postValue(Result.failure(e))
            }
        }
    }

    fun loginUser(emailInput: String, passwordInput: String, onResult: (Profile?) -> Unit) {
        viewModelScope.launch {
            try {
                SupabaseClientObj.client.auth.signInWith(Email) {
                    email = emailInput
                    password = passwordInput
                }
                fetchAndSyncProfile(onResult)
            } catch (e: Exception) {
                Log.e("AuthFlow", "Login error: ${e.message}")
                onResult(null)
            }
        }
    }

    fun loginWithGoogle(idTokenInput: String, onResult: (Profile?) -> Unit) {
        viewModelScope.launch {
            try {
                SupabaseClientObj.client.auth.signInWith(IDToken) {
                    idToken = idTokenInput
                    provider = Google
                }
                fetchAndSyncProfile(onResult)
            } catch (e: Exception) {
                Log.e("AuthFlow", "Google Login error: ${e.message}")
                onResult(null)
            }
        }
    }

    fun updateLegalAgreement(userId: String) {
        viewModelScope.launch {
            try {
                SupabaseClientObj.client.postgrest["profiles"].update(
                    mapOf("agreed_to_legal" to true)
                ) { filter { eq("id", userId) } }
                _legalUpdateStatus.postValue(Result.success(true))
            } catch (e: Exception) {
                Log.e("AuthFlow", "Legal update error: ${e.message}")
                _legalUpdateStatus.postValue(Result.failure(e))
            }
        }
    }

    fun sendResetPasswordEmail(emailInput: String, redirectUrlInput: String? = null) {
        viewModelScope.launch {
            try {
                SupabaseClientObj.client.auth.resetPasswordForEmail(
                    email = emailInput,
                    redirectUrl = redirectUrlInput
                )
                _resetPasswordStatus.postValue(Result.success(true))
            } catch (e: Exception) {
                Log.e("AuthFlow", "Reset password error: ${e.message}")
                _resetPasswordStatus.postValue(Result.failure(e))
            }
        }
    }

    fun clearResetStatus() { _resetPasswordStatus.postValue(null) }

    private suspend fun fetchAndSyncProfile(onResult: (Profile?) -> Unit) {
        val currentUser = SupabaseClientObj.client.auth.currentUserOrNull()
        if (currentUser != null) {
            var profile = try {
                SupabaseClientObj.client.postgrest["profiles"]
                    .select { filter { eq("id", currentUser.id) } }
                    .decodeSingleOrNull<Profile>()
            } catch (e: Exception) {
                Log.e("AuthFlow", "Error fetching profile: ${e.message}")
                null
            }
            
            if (profile == null) {
                // Step 2 logic: Determine if user had a chance to agree yet
                val provider = currentUser.appMetadata?.get("provider")?.jsonPrimitive?.contentOrNull
                val isEmailProvider = provider == "email"

                val fullName = currentUser.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull 
                    ?: currentUser.userMetadata?.get("name")?.jsonPrimitive?.contentOrNull
                    ?: "New User"
                
                val newProfile = Profile(
                    id = currentUser.id,
                    email = currentUser.email,
                    full_name = fullName,
                    agreed_to_legal = isEmailProvider // true if from Register screen, false if Google
                )
                try {
                    SupabaseClientObj.client.postgrest["profiles"].insert(newProfile)
                    profile = newProfile
                    Log.d("AuthFlow", "Profile created successfully. Agreed: $isEmailProvider")
                } catch (e: Exception) {
                    Log.e("AuthFlow", "Failed to create missing profile: ${e.message}")
                }
            }
            onResult(profile)
        } else {
            onResult(null)
        }
    }
}
