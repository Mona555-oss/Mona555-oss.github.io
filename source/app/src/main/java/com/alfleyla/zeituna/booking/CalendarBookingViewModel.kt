package com.alfleyla.zeituna.booking

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfleyla.zeituna.data.BookititApiService
import com.alfleyla.zeituna.data.SupabaseClientObj
import com.alfleyla.zeituna.data.models.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CalendarBookingViewModel : ViewModel() {

    private val _slots = MutableLiveData<List<BookititSlot>>()
    val slots: LiveData<List<BookititSlot>> = _slots

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _bookingStatus = MutableLiveData<Result<Boolean>>()
    val bookingStatus: LiveData<Result<Boolean>> = _bookingStatus

    private val _agendaConfig = MutableLiveData<AgendaConfig>()
    val agendaConfig: LiveData<AgendaConfig> = _agendaConfig
    
    private val _userProfile = MutableLiveData<Profile?>()
    val userProfile: LiveData<Profile?> = _userProfile

    private val _serviceDetails = MutableLiveData<LessonService?>()
    val serviceDetails: LiveData<LessonService?> = _serviceDetails

    private val _hasPreviousPurchases = MutableLiveData<Boolean>(false)
    val hasPreviousPurchases: LiveData<Boolean> = _hasPreviousPurchases

    init {
        fetchCurrentUserProfile()
    }

    private fun fetchCurrentUserProfile() {
        viewModelScope.launch {
            try {
                SupabaseClientObj.client.auth.awaitInitialization()
                val user = SupabaseClientObj.client.auth.currentUserOrNull()
                if (user != null) {
                    val profile = fetchProfileSync(user.id)
                    _userProfile.postValue(profile)

                    val result = SupabaseClientObj.client.postgrest["Bookings"].select {
                        filter { eq("user_id", user.id) }
                        limit(1)
                    }
                    
                    val bookings = try {
                        result.decodeList<Booking>()
                    } catch (e: Exception) {
                        Log.e("CalendarVM", "Decoding check failed: ${e.message}")
                        emptyList<Booking>()
                    }
                    
                    val hasPurchases = bookings.isNotEmpty()
                    _hasPreviousPurchases.postValue(hasPurchases)
                }
            } catch (e: Exception) {
                Log.e("CalendarVM", "Error fetching profile: ${e.message}")
            }
        }
    }

    fun fetchServiceDetails(supabaseId: String) {
        viewModelScope.launch {
            val service = fetchServiceSync(supabaseId)
            _serviceDetails.postValue(service)
        }
    }

    fun updatePurchaseAgreement() {
        viewModelScope.launch {
            try {
                val userId = _userProfile.value?.id ?: return@launch
                SupabaseClientObj.client.postgrest["profiles"].update(mapOf("agreed_to_purchase_terms" to true)) {
                    filter { eq("id", userId) }
                }
                val updatedProfile = _userProfile.value?.copy(agreed_to_purchase_terms = true)
                _userProfile.postValue(updatedProfile)
            } catch (e: Exception) {
                Log.e("CalendarVM", "Error updating agreement: ${e.message}")
            }
        }
    }

    fun fetchAgendaConfig(agendaId: String) {
        viewModelScope.launch {
            BookititApiService.getAgendaConfiguration(agendaId).onSuccess { config ->
                _agendaConfig.postValue(config)
            }.onFailure { e ->
                Log.e("CalendarVM", "Failed to fetch agenda config: ${e.message}")
            }
        }
    }

    fun fetchAvailability(serviceId: String, date: String, agendaId: String) {
        viewModelScope.launch {
            _isLoading.postValue(true)
            val config = _agendaConfig.value ?: BookititApiService.getAgendaConfiguration(agendaId).getOrNull()?.also {
                _agendaConfig.postValue(it)
            }
            val availableSlots = BookititApiService.getAvailability(serviceId, date, agendaId)
            val filteredSlots = filterSlotsByConfig(availableSlots, date, config)
            _slots.postValue(filteredSlots)
            _isLoading.postValue(false)
        }
    }

    private fun filterSlotsByConfig(slots: List<BookititSlot>, date: String, config: AgendaConfig?): List<BookititSlot> {
        if (config == null) return slots
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val earliestAllowedTimestamp = config.getMinBookingMillis()
        return slots.filter { slot ->
            try {
                val slotTimestamp = sdf.parse("$date ${slot.start}")?.time ?: 0L
                slotTimestamp >= earliestAllowedTimestamp
            } catch (e: Exception) { true }
        }
    }

    fun validateSchedulingMargin(date: String, time: String): Result<Boolean> {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return try {
            val lessonTime = sdf.parse("$date $time")?.time ?: return Result.failure(Exception("Invalid time."))
            val diffMillis = lessonTime - System.currentTimeMillis()
            if (diffMillis < 20 * 60 * 60 * 1000L) {
                Result.failure(Exception("The lesson has to be scheduled with a minimum of 20 hours in advance."))
            } else { Result.success(true) }
        } catch (e: Exception) { Result.failure(e) }
    }

    fun validateExpirationMargin(date: String, time: String, expiresAt: String?): Result<Boolean> {
        if (expiresAt == null) return Result.success(true)
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val lessonTime = sdf.parse("$date $time")?.time ?: return Result.success(true)
            val normalized = expiresAt.replace(" ", "T").substringBefore("+").substringBefore("Z")
            val expiryTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { 
                timeZone = TimeZone.getTimeZone("UTC") 
            }.parse(normalized)?.time ?: return Result.success(true)
            if (lessonTime > expiryTime) {
                Result.failure(Exception("lessons have to be scheduled before the expiration date of the package"))
            } else { Result.success(true) }
        } catch (e: Exception) { Result.success(true) }
    }

    fun confirmBooking(bookititId: String, supabaseId: String, date: String, time: String, duration: Int, lessonCount: Int) {
        val marginCheck = validateSchedulingMargin(date, time)
        if (marginCheck.isFailure) {
            _bookingStatus.postValue(Result.failure(marginCheck.exceptionOrNull()!!))
            return
        }
        viewModelScope.launch {
            _isLoading.postValue(true)
            SupabaseClientObj.client.auth.awaitInitialization()
            val user = SupabaseClientObj.client.auth.currentUserOrNull() ?: run {
                _bookingStatus.postValue(Result.failure(Exception("Session expired.")))
                _isLoading.postValue(false)
                return@launch
            }
            val service = fetchServiceSync(supabaseId)
            val profile = fetchProfileSync(user.id)
            val rootId = UUID.randomUUID().toString()
            val agendaId = service?.bookitit_agenda_id ?: ""
            val rootBooking = Booking(
                id = rootId, user_id = user.id, service_id = supabaseId,
                status = "active", remaining_lessons = lessonCount, 
                email = user.email ?: profile?.email, expires_at = calculateExpiry(service),
                bookitit_agenda_id = agendaId, student_name = profile?.full_name
            )
            try {
                SupabaseClientObj.client.postgrest["Bookings"].insert(rootBooking)
                performScheduling(bookititId, rootId, date, time, duration, agendaId)
            } catch (e: Exception) {
                _bookingStatus.postValue(Result.failure(e))
                _isLoading.postValue(false)
            }
        }
    }

    fun schedulePackageLesson(bookititId: String, bookingId: String, date: String, time: String, duration: Int) {
        val marginCheck = validateSchedulingMargin(date, time)
        if (marginCheck.isFailure) {
            _bookingStatus.postValue(Result.failure(marginCheck.exceptionOrNull()!!))
            return
        }
        viewModelScope.launch { 
            _isLoading.postValue(true)
            try {
                val rootPkg = SupabaseClientObj.client.postgrest["Bookings"].select(Columns.raw("*")) { filter { eq("id", bookingId) } }.decodeSingleOrNull<Booking>()
                if (rootPkg == null) {
                    _bookingStatus.postValue(Result.failure(Exception("Package not found")))
                    return@launch
                }
                val expirationCheck = validateExpirationMargin(date, time, rootPkg.expires_at)
                if (expirationCheck.isFailure) {
                    _bookingStatus.postValue(Result.failure(expirationCheck.exceptionOrNull()!!))
                    return@launch
                }
                val agendaId = rootPkg.bookitit_agenda_id ?: ""
                performScheduling(bookititId, bookingId, date, time, duration, agendaId)
            } catch (e: Exception) {
                _bookingStatus.postValue(Result.failure(e))
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    private suspend fun performScheduling(bookititId: String, bookingId: String, date: String, time: String, duration: Int, agendaId: String) {
        val user = SupabaseClientObj.client.auth.currentUserOrNull() ?: return
        val profile = fetchProfileSync(user.id)
        val uniqueLessonId = UUID.randomUUID().toString()
        val result = BookititApiService.createBooking(prepareRequest(bookititId, date, time, duration, user.email, profile), agendaId)
        result.onSuccess { newEventId ->
            try {
                val rootPkg = SupabaseClientObj.client.postgrest["Bookings"].select(Columns.raw("*")) { filter { eq("id", bookingId) } }.decodeSingle<Booking>()
                SupabaseClientObj.client.postgrest["Bookings"].update(mapOf("remaining_lessons" to rootPkg.remaining_lessons - 1)) { filter { eq("id", bookingId) } }
                val lessonRow = Booking(
                    id = uniqueLessonId, user_id = rootPkg.user_id, service_id = rootPkg.service_id,
                    status = "scheduled", remaining_lessons = 0, email = rootPkg.email,
                    start_date = date, start_time = time, end_time = calculateEndTime(time, duration),
                    bookitit_event_id = newEventId, parent_package_id = bookingId, expires_at = rootPkg.expires_at,
                    bookitit_agenda_id = agendaId, student_name = profile?.full_name
                )
                SupabaseClientObj.client.postgrest["Bookings"].insert(lessonRow)
                _bookingStatus.postValue(Result.success(true))
            } catch (e: Exception) { _bookingStatus.postValue(Result.failure(Exception("Supabase error"))) }
        }.onFailure { e -> _bookingStatus.postValue(Result.failure(e)) }
    }

    private suspend fun fetchProfileSync(userId: String) = try { SupabaseClientObj.client.postgrest["profiles"].select { filter { eq("id", userId) } }.decodeSingle<Profile>() } catch (e: Exception) { null }
    private suspend fun fetchServiceSync(id: String) = try { SupabaseClientObj.client.postgrest["Services"].select { filter { eq("id", id) } }.decodeSingle<LessonService>() } catch (e: Exception) { null }
    
    private fun prepareRequest(bId: String, date: String, time: String, duration: Int, email: String?, profile: Profile?) = 
        BookititBookingRequest(bId, date, time, profile?.full_name ?: "User", email ?: profile?.email ?: "", duration, profile?.bookitit_client_id)

    private fun calculateEndTime(startTime: String, durationMinutes: Int): String {
        val parts = startTime.split(":")
        val total = parts[0].toInt() * 60 + parts[1].toInt() + durationMinutes
        return String.format(Locale.US, "%02d:%02d", (total / 60) % 24, total % 60)
    }
    private fun calculateExpiry(service: LessonService?): String? {
        return service?.validity_days?.let { days ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, days)
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(cal.time)
        }
    }
}
