package com.alfleyla.zeituna.profile

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfleyla.zeituna.data.BookititApiService
import com.alfleyla.zeituna.data.SupabaseClientObj
import com.alfleyla.zeituna.data.models.Booking
import com.alfleyla.zeituna.data.models.BookititEvent
import com.alfleyla.zeituna.data.models.Profile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ProfileViewModel : ViewModel() {

    private val _profile = MutableLiveData<Profile?>()
    val profile: LiveData<Profile?> = _profile

    private val _activePackages = MutableLiveData<List<Booking>>()
    val activePackages: LiveData<List<Booking>> = _activePackages

    private val _upcomingLessons = MutableLiveData<List<BookititEvent>>()
    val upcomingLessons: LiveData<List<BookititEvent>> = _upcomingLessons

    private val _completedLessons = MutableLiveData<List<BookititEvent>>()
    val completedLessons: LiveData<List<BookititEvent>> = _completedLessons

    private val _unscheduledLessons = MutableLiveData<List<BookititEvent>>()
    val unscheduledLessons: LiveData<List<BookititEvent>> = _unscheduledLessons

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _unscheduleMessage = MutableLiveData<String?>()
    val unscheduleMessage: LiveData<String?> = _unscheduleMessage

    private val _deleteStatus = MutableLiveData<Result<Unit>?>()
    val deleteStatus: LiveData<Result<Unit>?> = _deleteStatus

    private val _showUnscheduleWarning = MutableLiveData<Boolean>()
    val showUnscheduleWarning: LiveData<Boolean> = _showUnscheduleWarning

    private val _showReasonInput = MutableLiveData<BookititEvent?>()
    val showReasonInput: LiveData<BookititEvent?> = _showReasonInput

    fun loadProfileData() {
        viewModelScope.launch {
            Log.d("ProfileVM", "loadProfileData started")
            _isLoading.postValue(true)
            val user = SupabaseClientObj.client.auth.currentUserOrNull() ?: run {
                Log.e("ProfileVM", "User not logged in")
                _isLoading.postValue(false)
                return@launch
            }
            val email = user.email ?: ""

            val profileData = fetchSupabaseProfile(user.id)
            _profile.postValue(profileData)

            if (profileData == null) {
                Log.e("ProfileVM", "Profile data is null for user ${user.id}")
                _isLoading.postValue(false)
                return@launch
            }

            Log.d("ProfileVM", "User Role: ${profileData.role}, Agenda ID: ${profileData.bookitit_agenda_id}")

            val isTeacher = profileData.role == "teacher"
            val agendaId = profileData.bookitit_agenda_id

            // 1. Fetch Supabase Bookings
            val allBookings = fetchUserBookings(profileData)
            Log.d("ProfileVM", "Total Supabase bookings fetched: ${allBookings.size}")
            
            // 2. Fetch Bookitit API Events
            val apiEvents = if (isTeacher && agendaId != null) {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val calStart = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
                val start = sdf.format(calStart.time)
                val calEnd = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 60) }
                val end = sdf.format(calEnd.time)
                Log.d("ProfileVM", "Fetching Teacher events from $start to $end for agenda $agendaId")
                val events = BookititApiService.getAgendaEvents(agendaId, start, end)
                Log.d("ProfileVM", "Teacher events fetched: ${events.size}")
                events
            } else {
                val clientId = profileData.bookitit_client_id ?: BookititApiService.findClientByEmail(email)
                Log.d("ProfileVM", "Fetching Student events for client: $clientId")
                val events = if (clientId != null) BookititApiService.getClientEvents(clientId) else emptyList()
                Log.d("ProfileVM", "Student events fetched: ${events.size}")
                events
            }

            // --- PROCESS PACKAGES ---
            val filteredPackages = allBookings.filter { it.parent_package_id == null && it.remaining_lessons > 0 }
            Log.d("ProfileVM", "Active packages: ${filteredPackages.size}")
            _activePackages.postValue(filteredPackages)

            // --- PROCESS LESSONS ---
            val mergedLessons = mutableListOf<BookititEvent>()

            // Add lessons tracked in Supabase
            val dbLessons = allBookings.filter { !it.start_date.isNullOrEmpty() || it.status == "unscheduled" }
            Log.d("ProfileVM", "Lessons from Supabase DB: ${dbLessons.size}")
            
            dbLessons.forEach { booking ->
                mergedLessons.add(BookititEvent(
                    bookitit_event_id = booking.bookitit_event_id,
                    date = booking.start_date ?: "",
                    start = booking.start_time ?: "",
                    end = booking.end_time ?: "",
                    service_name = booking.service?.course_name ?: "Lesson",
                    status = booking.status,
                    created_at = booking.created_at,
                    expires_at = booking.expires_at,
                    supabase_id = booking.id,
                    parent_package_id = booking.parent_package_id,
                    client_name = booking.student_name,
                    client_email = booking.email,
                    unschedule_reason = booking.unschedule_reason
                ))
            }

            // Add events from Bookitit API
            apiEvents.forEach { apiEv ->
                val isDuplicate = mergedLessons.any { 
                    (it.bookitit_event_id != null && it.bookitit_event_id == apiEv.bookitit_event_id) ||
                    (it.date == apiEv.date && it.start == apiEv.start)
                }
                if (!isDuplicate && apiEv.status?.lowercase() != "deleted") {
                    mergedLessons.add(apiEv)
                }
            }
            Log.d("ProfileVM", "Total merged lessons: ${mergedLessons.size}")

            val now = Calendar.getInstance()
            val upcoming = mutableListOf<BookititEvent>()
            val completed = mutableListOf<BookititEvent>()
            val unscheduled = mutableListOf<BookititEvent>()

            mergedLessons.forEach { event ->
                val status = event.status?.lowercase()
                
                if (status == "unscheduled") {
                    unscheduled.add(event)
                } else if (isPastLesson(event, now) || status == "completed") {
                    completed.add(event.copy(status = "completed"))
                    if (event.status == "scheduled") markAsCompletedInDb(event.supabase_id)
                } else {
                    upcoming.add(event)
                }
            }

            Log.d("ProfileVM", "Final Counts - Upcoming: ${upcoming.size}, Completed: ${completed.size}, Unscheduled: ${unscheduled.size}")

            _upcomingLessons.postValue(upcoming.sortedBy { it.date + it.start })
            _completedLessons.postValue(completed.sortedByDescending { it.date + it.start })
            _unscheduledLessons.postValue(unscheduled.sortedByDescending { it.created_at ?: it.date })
            _isLoading.postValue(false)
        }
    }

    private fun isPastLesson(event: BookititEvent, now: Calendar): Boolean {
        if (event.date.isEmpty() || event.start.isEmpty()) return false
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val timeStr = if (!event.end.isNullOrEmpty()) event.end else calculateEndTime(event.start, 30)
            val lessonEnd = format.parse("${event.date} $timeStr")
            lessonEnd?.before(now.time) ?: false
        } catch (e: Exception) { false }
    }

    private fun calculateEndTime(startTime: String, durationMinutes: Int): String {
        return try {
            val parts = startTime.split(":")
            val total = parts[0].toInt() * 60 + parts[1].toInt() + durationMinutes
            String.format(Locale.US, "%02d:%02d", (total / 60) % 24, total % 60)
        } catch (e: Exception) { startTime }
    }

    private fun markAsCompletedInDb(supabaseId: String?) {
        if (supabaseId == null) return
        viewModelScope.launch {
            try {
                SupabaseClientObj.client.postgrest["Bookings"].update(mapOf("status" to "completed")) { 
                    filter { eq("id", supabaseId) } 
                }
            } catch (e: Exception) {
                Log.e("ProfileVM", "Failed to mark completed: ${e.message}")
            }
        }
    }

    fun requestUnschedule(lesson: BookititEvent) {
        val role = _profile.value?.role
        if (role == "teacher") {
            _showReasonInput.postValue(lesson)
        } else {
            if (isWithin20Hours(lesson)) {
                _showUnscheduleWarning.postValue(true)
            } else {
                _showReasonInput.postValue(lesson)
            }
        }
    }

    private fun isWithin20Hours(lesson: BookititEvent): Boolean {
        if (lesson.date.isEmpty() || lesson.start.isEmpty()) return false
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val lessonStart = format.parse("${lesson.date} ${lesson.start}")
            val now = Calendar.getInstance().time
            val diff = (lessonStart?.time ?: 0) - now.time
            val hours = diff / (1000 * 60 * 60)
            hours <= 20
        } catch (e: Exception) { false }
    }

    fun performUnschedule(lesson: BookititEvent, reason: String? = null) {
        viewModelScope.launch {
            _isLoading.postValue(true)
            val isTeacher = _profile.value?.role == "teacher"
            val deletedBy = if (isTeacher) "company" else "client"
            
            val deleteResult = BookititApiService.deleteEvent(
                eventId = lesson.bookitit_event_id ?: "",
                sendNotification = true,
                deletedBy = deletedBy
            )
            
            if (deleteResult.isSuccess) {
                try {
                    markLessonAsUnscheduled(lesson, reason)
                    _unscheduleMessage.postValue("Lesson unscheduled successfully")
                } catch (e: Exception) {
                    Log.e("ProfileVM", "Unschedule status update failed: ${e.message}")
                }
                loadProfileData()
            } else {
                _isLoading.postValue(false)
            }
        }
    }

    private suspend fun markLessonAsUnscheduled(lesson: BookititEvent, reason: String?) {
        if (!lesson.parent_package_id.isNullOrEmpty()) {
            addLessonBackToPackage(lesson.parent_package_id!!)
            SupabaseClientObj.client.postgrest["Bookings"].update(
                mapOf("status" to "unscheduled", "bookitit_event_id" to null, "unschedule_reason" to reason)
            ) { filter { eq("id", lesson.supabase_id!!) } }
        } else if (lesson.supabase_id != null) {
            val currentBooking = SupabaseClientObj.client.postgrest["Bookings"]
                .select { filter { eq("id", lesson.supabase_id!!) } }.decodeSingle<Booking>()
            SupabaseClientObj.client.postgrest["Bookings"].update(
                mapOf("bookitit_event_id" to null, "remaining_lessons" to (currentBooking.remaining_lessons + 1), "status" to "unscheduled", "unschedule_reason" to reason)
            ) { filter { eq("id", lesson.supabase_id!!) } }
        }
    }

    private suspend fun addLessonBackToPackage(packageId: String) {
        try {
            val currentPkg = SupabaseClientObj.client.postgrest["Bookings"]
                .select { filter { eq("id", packageId) } }.decodeSingle<Booking>()
            SupabaseClientObj.client.postgrest["Bookings"].update(
                mapOf("remaining_lessons" to (currentPkg.remaining_lessons + 1))
            ) { filter { eq("id", packageId) } }
        } catch (e: Exception) { }
    }

    private suspend fun fetchSupabaseProfile(userId: String): Profile? {
        return try {
            SupabaseClientObj.client.postgrest["profiles"].select { filter { eq("id", userId) } }.decodeSingle<Profile>()
        } catch (e: Exception) { 
            Log.e("ProfileVM", "Error fetching profile: ${e.message}")
            null 
        }
    }

    private suspend fun fetchUserBookings(profile: Profile): List<Booking> {
        return try {
            SupabaseClientObj.client.postgrest["Bookings"].select(Columns.raw("*, service:Services(*)")) {
                filter {
                    if (profile.role == "teacher" && profile.bookitit_agenda_id != null) {
                        eq("bookitit_agenda_id", profile.bookitit_agenda_id!!)
                    } else {
                        eq("user_id", profile.id)
                    }
                }
            }.decodeList<Booking>()
        } catch (e: Exception) { 
            Log.e("ProfileVM", "Error fetching bookings: ${e.message}")
            emptyList() 
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _isLoading.postValue(true)
            try {
                val user = SupabaseClientObj.client.auth.currentUserOrNull()
                if (user == null) {
                    _deleteStatus.postValue(Result.failure(Exception("Not logged in")))
                    return@launch
                }

                val profileData = _profile.value ?: fetchSupabaseProfile(user.id)
                profileData?.bookitit_client_id?.let { clientId ->
                    BookititApiService.deleteClient(clientId)
                }

                SupabaseClientObj.client.postgrest["profiles"].delete {
                    filter { eq("id", user.id) }
                }

                SupabaseClientObj.client.auth.signOut()
                _deleteStatus.postValue(Result.success(Unit))
            } catch (e: Exception) {
                Log.e("ProfileVM", "Delete account failed: ${e.message}")
                _deleteStatus.postValue(Result.failure(e))
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            SupabaseClientObj.client.auth.signOut()
        }
    }

    fun clearDeleteStatus() { _deleteStatus.postValue(null) }
    fun clearUnscheduleMessage() { _unscheduleMessage.postValue(null) }
    fun clearUnscheduleWarning() { _showUnscheduleWarning.postValue(false) }
    fun clearReasonInput() { _showReasonInput.postValue(null) }
}
