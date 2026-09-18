package com.alfleyla.zeituna.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfleyla.zeituna.data.BookititApiService
import com.alfleyla.zeituna.data.SupabaseClientObj
import com.alfleyla.zeituna.data.models.*
import com.alfleyla.zeituna.utils.platformLog
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlin.random.Random

class CalendarBookingViewModel : ViewModel() {

    private val _slots = MutableStateFlow<List<BookititSlot>>(emptyList())
    val slots: StateFlow<List<BookititSlot>> = _slots

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _bookingStatus = MutableStateFlow<Result<Boolean>?>(null)
    val bookingStatus: StateFlow<Result<Boolean>?> = _bookingStatus

    private val _agendaConfig = MutableStateFlow<AgendaConfig?>(null)
    val agendaConfig: StateFlow<AgendaConfig?> = _agendaConfig
    
    private val _userProfile = MutableStateFlow<Profile?>(null)
    val userProfile: StateFlow<Profile?> = _userProfile

    private val _serviceDetails = MutableStateFlow<LessonService?>(null)
    val serviceDetails: StateFlow<LessonService?> = _serviceDetails

    private val _hasPreviousPurchases = MutableStateFlow(false)
    val hasPreviousPurchases: StateFlow<Boolean> = _hasPreviousPurchases

    init {
        fetchCurrentUserProfile()
    }

    private fun safeTimeZone(tz: String? = "Europe/Madrid"): TimeZone {
        return try {
            if (!tz.isNullOrBlank()) TimeZone.of(tz) else TimeZone.of("Europe/Madrid")
        } catch (t: Throwable) {
            TimeZone.UTC
        }
    }

    fun fetchCurrentUserProfile() {
        viewModelScope.launch {
            try {
                SupabaseClientObj.client.auth.awaitInitialization()
                val user = SupabaseClientObj.client.auth.currentUserOrNull()
                platformLog("CalendarVM", "Current user: ${user?.id}")
                
                if (user != null) {
                    val profile = fetchProfileSync(user.id)
                    _userProfile.value = profile

                    val result = SupabaseClientObj.client.postgrest["Bookings"].select {
                        filter { eq("user_id", user.id) }
                        limit(1)
                    }
                    val bookings = result.decodeList<Booking>()
                    _hasPreviousPurchases.value = bookings.isNotEmpty()
                    platformLog("CalendarVM", "Profile loaded. Agreed: ${profile?.agreed_to_purchase_terms}, Has Purchases: ${bookings.isNotEmpty()}")
                }
            } catch (e: Exception) {
                platformLog("CalendarVM", "Error fetching profile: ${e.message}", true)
            }
        }
    }

    fun fetchServiceDetails(supabaseId: String) {
        viewModelScope.launch {
            val service = fetchServiceSync(supabaseId)
            _serviceDetails.value = service
        }
    }

    fun updatePurchaseAgreement() {
        viewModelScope.launch {
            try {
                val user = SupabaseClientObj.client.auth.currentUserOrNull() ?: return@launch
                SupabaseClientObj.client.postgrest["profiles"].update(mapOf("agreed_to_purchase_terms" to true)) {
                    filter { eq("id", user.id) }
                }
                _userProfile.value = _userProfile.value?.copy(agreed_to_purchase_terms = true)
                platformLog("CalendarVM", "Agreement updated in database")
            } catch (e: Exception) {
                platformLog("CalendarVM", "Error updating agreement: ${e.message}", true)
            }
        }
    }

    fun fetchAgendaConfig(agendaId: String) {
        viewModelScope.launch {
            BookititApiService.getAgendaConfiguration(agendaId).onSuccess { config ->
                _agendaConfig.value = config
            }.onFailure { e ->
                platformLog("CalendarVM", "Failed to fetch agenda config: ${e.message}", true)
            }
        }
    }

    fun fetchAvailability(serviceId: String, date: String, agendaId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val config = _agendaConfig.value ?: BookititApiService.getAgendaConfiguration(agendaId).getOrNull()?.also {
                    _agendaConfig.value = it
                }
                
                val availableSlots = BookititApiService.getAvailability(serviceId, date, agendaId)
                platformLog("CalendarVM", "Fetched ${availableSlots.size} slots for $date")
                val filteredSlots = filterSlotsByConfig(availableSlots, date, config)
                _slots.value = filteredSlots
            } catch (e: Exception) {
                platformLog("CalendarVM", "Error fetching slots: ${e.message}", true)
                _slots.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun filterSlotsByConfig(slots: List<BookititSlot>, date: String, config: AgendaConfig?): List<BookititSlot> {
        if (config == null) return slots
        val earliestAllowedTimestamp = config.getMinBookingMillis()
        val tz = safeTimeZone()
        
        return slots.filter { slot ->
            try {
                val lessonDate = LocalDate.parse(date)
                val lessonTime = LocalTime.parse(slot.start.padStart(5, '0').take(5))
                val lessonDT = LocalDateTime(lessonDate, lessonTime)
                val slotTimestamp = lessonDT.toInstant(tz).toEpochMilliseconds()
                slotTimestamp >= earliestAllowedTimestamp
            } catch (e: Throwable) { true }
        }
    }

    fun validateSchedulingMargin(date: String, time: String): Result<Boolean> {
        return try {
            val tz = safeTimeZone()
            val lessonDT = LocalDateTime(LocalDate.parse(date), LocalTime.parse(time.padStart(5, '0').take(5)))
            val lessonInstant = lessonDT.toInstant(tz)
            val now = Clock.System.now()
            val diff = lessonInstant - now
            if (diff.inWholeHours < 20) {
                Result.failure(Exception("Lessons must be scheduled at least 20 hours in advance."))
            } else { Result.success(true) }
        } catch (e: Throwable) { 
            Result.success(true) 
        }
    }

    fun validateExpirationMargin(date: String, time: String, expiresAt: String?): Result<Boolean> {
        if (expiresAt == null) return Result.success(true)
        return try {
            val tz = safeTimeZone()
            val lessonDT = LocalDateTime(LocalDate.parse(date), LocalTime.parse(time.padStart(5, '0').take(5)))
            val lessonInstant = lessonDT.toInstant(tz)
            val expiryInstant = Instant.parse(expiresAt)
            if (lessonInstant > expiryInstant) {
                Result.failure(Exception("The lesson date is past the package expiration date."))
            } else { Result.success(true) }
        } catch (e: Exception) { Result.success(true) }
    }

    fun confirmBooking(bookititId: String, supabaseId: String, date: String, time: String, duration: Int, lessonCount: Int) {
        platformLog("CalendarVM", "Confirming booking: Date=$date, Time=$time, Service=$supabaseId")
        val marginCheck = validateSchedulingMargin(date, time)
        if (marginCheck.isFailure) {
            _bookingStatus.value = Result.failure(marginCheck.exceptionOrNull() ?: Exception("Notice period error"))
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                SupabaseClientObj.client.auth.awaitInitialization()
                val user = SupabaseClientObj.client.auth.currentUserOrNull() ?: throw Exception("Your session has expired. Please login again.")
                
                val service = fetchServiceSync(supabaseId)
                val profile = fetchProfileSync(user.id)
                val rootId = generateUUID()
                val agendaId = service?.bookitit_agenda_id ?: ""
                
                val rootBooking = Booking(
                    id = rootId, 
                    user_id = user.id, 
                    service_id = supabaseId,
                    status = "active", 
                    remaining_lessons = lessonCount, 
                    email = user.email ?: profile?.email, 
                    expires_at = calculateExpiry(service),
                    bookitit_agenda_id = agendaId, 
                    student_name = profile?.full_name
                )
                
                platformLog("CalendarVM", "Saving booking to database...")
                SupabaseClientObj.client.postgrest["Bookings"].insert(rootBooking)
                
                performScheduling(bookititId, rootId, date, time, duration, agendaId)
            } catch (e: Exception) {
                platformLog("CalendarVM", "Booking confirmation failed: ${e.message}", true)
                _bookingStatus.value = Result.failure(Exception("Database error: ${e.message}"))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun schedulePackageLesson(bookititId: String, bookingId: String, date: String, time: String, duration: Int) {
        val marginCheck = validateSchedulingMargin(date, time)
        if (marginCheck.isFailure) {
            _bookingStatus.value = Result.failure(marginCheck.exceptionOrNull() ?: Exception("Notice period error"))
            return
        }
        viewModelScope.launch { 
            _isLoading.value = true
            try {
                val rootPkg = SupabaseClientObj.client.postgrest["Bookings"].select(Columns.raw("*")) { filter { eq("id", bookingId) } }.decodeSingleOrNull<Booking>()
                if (rootPkg == null) {
                    _bookingStatus.value = Result.failure(Exception("The selected package could not be found."))
                    return@launch
                }
                val expirationCheck = validateExpirationMargin(date, time, rootPkg.expires_at)
                if (expirationCheck.isFailure) {
                    _bookingStatus.value = Result.failure(expirationCheck.exceptionOrNull() ?: Exception("Notice period error"))
                    return@launch
                }
                val agendaId = rootPkg.bookitit_agenda_id ?: ""
                performScheduling(bookititId, bookingId, date, time, duration, agendaId)
            } catch (e: Exception) {
                _bookingStatus.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun performScheduling(bookititId: String, bookingId: String, date: String, time: String, duration: Int, agendaId: String) {
        try {
            val user = SupabaseClientObj.client.auth.currentUserOrNull() ?: return
            val profile = fetchProfileSync(user.id)
            val uniqueLessonId = generateUUID()
            
            val request = BookititBookingRequest(
                service_id = bookititId,
                date = date,
                time = time,
                name = profile?.full_name ?: "User",
                email = user.email ?: profile?.email ?: "",
                duration = duration,
                clientId = profile?.bookitit_client_id
            )
            
            platformLog("CalendarVM", "Sending scheduling request to provider...")
            val result = BookititApiService.createBooking(request, agendaId)
            result.onSuccess { newEventId ->
                platformLog("CalendarVM", "Scheduling successful. Finalising records...")
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
                _bookingStatus.value = Result.success(true)
            }.onFailure { e -> 
                platformLog("CalendarVM", "Provider scheduling failed: ${e.message}", true)
                _bookingStatus.value = Result.failure(Exception("Scheduling failed: ${e.message}")) 
            }
        } catch (e: Exception) {
            platformLog("CalendarVM", "Internal processing error: ${e.message}", true)
            _bookingStatus.value = Result.failure(Exception("Internal error: ${e.message}"))
        }
    }

    private suspend fun fetchProfileSync(userId: String) = try { SupabaseClientObj.client.postgrest["profiles"].select { filter { eq("id", userId) } }.decodeSingle<Profile>() } catch (e: Exception) { null }
    private suspend fun fetchServiceSync(id: String) = try { SupabaseClientObj.client.postgrest["Services"].select { filter { eq("id", id) } }.decodeSingle<LessonService>() } catch (e: Exception) { null }

    private fun calculateEndTime(startTime: String, durationMinutes: Int): String {
        val parts = startTime.split(":")
        val total = parts[0].toInt() * 60 + parts[1].toInt() + durationMinutes
        val h = (total / 60) % 24
        val m = total % 60
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }

    private fun calculateExpiry(service: LessonService?): String? {
        return service?.validity_days?.let { days ->
            val now = Clock.System.now()
            val expiry = now.plus(days, DateTimeUnit.DAY, TimeZone.UTC)
            expiry.toString()
        }
    }

    private fun generateUUID(): String {
        val hexChars = "0123456789abcdef"
        fun randomHex(length: Int) = (1..length).map { hexChars[Random.nextInt(16)] }.joinToString("")
        return "${randomHex(8)}-${randomHex(4)}-4${randomHex(3)}-a${randomHex(3)}-${randomHex(12)}"
    }
    
    fun clearBookingStatus() {
        _bookingStatus.value = null
    }
}
