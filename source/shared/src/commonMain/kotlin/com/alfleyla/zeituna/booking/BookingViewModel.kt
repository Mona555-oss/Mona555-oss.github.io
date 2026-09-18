package com.alfleyla.zeituna.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfleyla.zeituna.data.SupabaseClientObj
import com.alfleyla.zeituna.data.models.LessonService
import com.alfleyla.zeituna.data.models.PromoCode
import com.alfleyla.zeituna.data.models.Profile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.alfleyla.zeituna.utils.platformLog

class BookingViewModel : ViewModel() {
    private val _services = MutableStateFlow<List<LessonService>>(emptyList())
    val services: StateFlow<List<LessonService>> = _services

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _activePromo = MutableStateFlow<PromoCode?>(null)
    val activePromo: StateFlow<PromoCode?> = _activePromo

    private val _promoError = MutableStateFlow<String?>(null)
    val promoError: StateFlow<String?> = _promoError

    fun fetchServices() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = SupabaseClientObj.client.postgrest["Services"]
                    .select()
                    .decodeList<LessonService>()
                _services.value = result
            } catch (e: Exception) {
                platformLog("BookingVM", "Error fetching services: ${e.message}", isError = true)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Validates a manually entered promo code.
     */
    fun validateCode(inputCode: String) {
        viewModelScope.launch {
            _promoError.value = null
            try {
                // 1. Fetch promo from DB
                val promo = SupabaseClientObj.client.postgrest["promo_codes"]
                    .select {
                        filter {
                            eq("code", inputCode.trim().uppercase())
                            eq("is_active", true)
                        }
                        limit(1)
                    }.decodeSingleOrNull<PromoCode>()

                if (promo == null) {
                    _promoError.value = "Invalid or expired promo code."
                    _activePromo.value = null
                    return@launch
                }

                // 2. Check if user has already used this specific promo
                val user = SupabaseClientObj.client.auth.currentUserOrNull()
                if (user != null) {
                    val profile = SupabaseClientObj.client.postgrest["profiles"]
                        .select { filter { eq("id", user.id) } }
                        .decodeSingleOrNull<Profile>()
                    
                    if (profile?.used_promos?.contains(promo.id.toString()) == true) {
                        _promoError.value = "You have already used this promo code."
                        _activePromo.value = null
                        return@launch
                    }
                }

                _activePromo.value = promo
                platformLog("BookingVM", "Promo applied: ${promo.discount} off")
            } catch (e: Exception) {
                _promoError.value = "Error validating code."
            }
        }
    }

    fun clearPromo() {
        _activePromo.value = null
        _promoError.value = null
    }
}
