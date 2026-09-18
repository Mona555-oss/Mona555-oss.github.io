package com.alfleyla.zeituna.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfleyla.zeituna.data.SupabaseClientObj
import com.alfleyla.zeituna.data.models.Profile
import com.alfleyla.zeituna.utils.platformLog
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AuthViewModel : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _registrationSuccess = MutableStateFlow<Boolean?>(null)
    val registrationSuccess: StateFlow<Boolean?> = _registrationSuccess.asStateFlow()

    private val _legalUpdateSuccess = MutableStateFlow<Boolean?>(null)
    val legalUpdateSuccess: StateFlow<Boolean?> = _legalUpdateSuccess.asStateFlow()

    fun login(emailInput: String, passwordInput: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                SupabaseClientObj.client.auth.awaitInitialization()

                SupabaseClientObj.client.auth.signInWith(Email) {
                    email = emailInput
                    password = passwordInput
                }
                
                fetchAndSyncProfile()
                platformLog("AuthVM", "Login successful for $emailInput")
            } catch (e: Exception) {
                platformLog("AuthVM", "Login error: ${e.message}", isError = true)
                _error.value = e.message ?: "Login failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loginWithGoogle() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // On Web, this triggers a redirect to Supabase hosted UI
                SupabaseClientObj.client.auth.signInWith(Google)
            } catch (e: Exception) {
                _error.value = e.message ?: "Google login failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(emailInput: String, passwordInput: String, fullNameInput: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _registrationSuccess.value = null
            try {
                SupabaseClientObj.client.auth.awaitInitialization()

                try { SupabaseClientObj.client.auth.signOut() } catch (e: Exception) {}
                
                val user = SupabaseClientObj.client.auth.signUpWith(Email) {
                    email = emailInput
                    password = passwordInput
                    data = buildJsonObject { put("full_name", fullNameInput) }
                }

                if (user != null) {
                    try {
                        val newProfile = Profile(
                            id = user.id,
                            email = emailInput,
                            full_name = fullNameInput,
                            agreed_to_legal = true
                        )
                        SupabaseClientObj.client.postgrest["profiles"].insert(newProfile)
                        platformLog("AuthVM", "Profile created for $emailInput")
                    } catch (e: Exception) {
                        platformLog("AuthVM", "Profile insertion delayed until confirmation: ${e.message}")
                    }
                }
                _registrationSuccess.value = true
            } catch (e: Exception) {
                platformLog("AuthVM", "Registration error: ${e.message}", isError = true)
                _error.value = e.message ?: "Registration failed"
                _registrationSuccess.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateLegalAgreement(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                SupabaseClientObj.client.postgrest["profiles"].update(
                    mapOf("agreed_to_legal" to true)
                ) { filter { eq("id", userId) } }
                _legalUpdateSuccess.value = true
            } catch (e: Exception) {
                _error.value = e.message
                _legalUpdateSuccess.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendResetPasswordEmail(emailInput: String, redirectUrlInput: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                SupabaseClientObj.client.auth.resetPasswordForEmail(
                    email = emailInput,
                    redirectUrl = redirectUrlInput
                )
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchAndSyncProfile() {
        val currentUser = SupabaseClientObj.client.auth.currentUserOrNull() ?: return
        
        val profileExists = try {
            SupabaseClientObj.client.postgrest["profiles"]
                .select { filter { eq("id", currentUser.id) } }
                .decodeSingleOrNull<Profile>() != null
        } catch (e: Exception) {
            false
        }

        if (!profileExists) {
            val provider = currentUser.appMetadata?.get("provider")?.jsonPrimitive?.contentOrNull
            val isEmailProvider = provider == "email"

            val fullName = currentUser.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
                ?: currentUser.userMetadata?.get("name")?.jsonPrimitive?.contentOrNull
                ?: "New User"

            val newProfile = Profile(
                id = currentUser.id,
                email = currentUser.email,
                full_name = fullName,
                agreed_to_legal = isEmailProvider
            )
            try {
                SupabaseClientObj.client.postgrest["profiles"].insert(newProfile)
                platformLog("AuthVM", "Sync: Missing profile created for ${currentUser.email}")
            } catch (e: Exception) {
                platformLog("AuthVM", "Sync: Profile creation failed: ${e.message}", isError = true)
            }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
}
