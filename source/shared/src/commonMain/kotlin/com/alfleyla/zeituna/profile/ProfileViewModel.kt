package com.alfleyla.zeituna.profile

import com.alfleyla.zeituna.data.BookititApiService
import com.alfleyla.zeituna.data.SupabaseClientObj
import com.alfleyla.zeituna.data.models.*
import com.alfleyla.zeituna.utils.platformLog
import com.alfleyla.zeituna.utils.DateTimeUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.serialization.json.jsonPrimitive

class ProfileViewModel : ViewModel() {
    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile

    private val _activePackages = MutableStateFlow<List<Booking>>(emptyList())
    val activePackages: StateFlow<List<Booking>> = _activePackages

    private val _upcomingLessons = MutableStateFlow<List<BookititEvent>>(emptyList())
    val upcomingLessons: StateFlow<List<BookititEvent>> = _upcomingLessons

    private val _completedLessons = MutableStateFlow<List<BookititEvent>>(emptyList())
    val completedLessons: StateFlow<List<BookititEvent>> = _completedLessons

    private val _unscheduledLessons = MutableStateFlow<List<BookititEvent>>(emptyList())
    val unscheduledLessons: StateFlow<List<BookititEvent>> = _unscheduledLessons

    private val _materials = MutableStateFlow<Map<String, List<PromoCode>>>(emptyMap())
    val materials: StateFlow<Map<String, List<PromoCode>>> = _materials

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _unscheduleMessage = MutableStateFlow<String?>(null)
    val unscheduleMessage: StateFlow<String?> = _unscheduleMessage

    private val _deleteStatus = MutableStateFlow<Result<Unit>?>(null)
    val deleteStatus: StateFlow<Result<Unit>?> = _deleteStatus

    private val _showUnscheduleWarning = MutableStateFlow(false)
    val showUnscheduleWarning: StateFlow<Boolean> = _showUnscheduleWarning

    private val _showReasonInput = MutableStateFlow<BookititEvent?>(null)
    val showReasonInput: StateFlow<BookititEvent?> = _showReasonInput

    init {
        viewModelScope.launch {
            try {
                SupabaseClientObj.client.auth.awaitInitialization()
                SupabaseClientObj.client.auth.sessionStatus.collect { status ->
                    if (status is SessionStatus.Authenticated) {
                        loadProfileData()
                        fetchMaterials()
                    } else if (status is SessionStatus.NotAuthenticated) {
                        _profile.value = null
                        clearData()
                    }
                }
            } catch (e: Exception) {
                platformLog("ProfileVM", "Init error: ${e.message}", true)
            }
        }
    }

    private fun clearData() {
        _activePackages.value = emptyList()
        _upcomingLessons.value = emptyList()
        _completedLessons.value = emptyList()
        _unscheduledLessons.value = emptyList()
        _materials.value = emptyMap()
    }

    private fun getTeacherTimeZone(event: BookititEvent? = null): TimeZone {
        val userProfile = _profile.value
        val tzId = if (userProfile?.role == "teacher") userProfile.time_zone else event?.teacher_timezone
        return DateTimeUtils.safeTimeZone(tzId, preferMadrid = true)
    }

    fun loadProfileData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val user = SupabaseClientObj.client.auth.currentUserOrNull()
                if (user == null) {
                    _isLoading.value = false
                    return@launch
                }

                val dbProfile = fetchSupabaseProfile(user.id)
                val finalProfile = if (dbProfile == null) {
                    val nameFromMeta = user.userMetadata?.get("full_name")?.jsonPrimitive?.content
                    Profile(
                        id = user.id, 
                        email = user.email, 
                        full_name = nameFromMeta ?: user.email?.substringBefore("@") ?: "User",
                        role = "user" 
                    )
                } else {
                    dbProfile.copy(email = dbProfile.email ?: user.email)
                }

                _profile.value = finalProfile
                
                val isTeacher = finalProfile.role == "teacher"
                val sourceTZ = getTeacherTimeZone()
                val sourceTZStr = DateTimeUtils.getDisplayId(sourceTZ)
                
                val allBookings = fetchUserBookings(finalProfile)
                
                val apiEvents = try {
                    if (isTeacher && finalProfile.bookitit_agenda_id != null) {
                        val today = Clock.System.now().toLocalDateTime(sourceTZ).date
                        val start = today.minus(30, DateTimeUnit.DAY).toString()
                        val end = today.plus(60, DateTimeUnit.DAY).toString()
                        BookititApiService.getAgendaEvents(finalProfile.bookitit_agenda_id, start, end, sourceTZStr)
                    } else {
                        val clientId = finalProfile.bookitit_client_id ?: BookititApiService.findClientByEmail(user.email ?: "")
                        if (clientId != null) BookititApiService.getClientEvents(clientId, sourceTZStr) else emptyList()
                    }
                } catch (e: Exception) { emptyList() }

                _activePackages.value = allBookings.filter { it.parent_package_id == null && it.remaining_lessons > 0 }

                val mergedLessons = mutableListOf<BookititEvent>()
                allBookings.filter { !it.start_date.isNullOrEmpty() || it.status == "unscheduled" }.forEach { booking ->
                    mergedLessons.add(BookititEvent(
                        bookitit_event_id = booking.bookitit_event_id,
                        date = booking.start_date ?: "",
                        start = booking.start_time ?: "",
                        end = booking.end_time ?: "",
                        service_name = booking.service?.course_name ?: "Lesson",
                        status = booking.status,
                        created_at = booking.created_at,
                        supabase_id = booking.id,
                        parent_package_id = booking.parent_package_id,
                        client_name = booking.student_name,
                        unschedule_reason = booking.unschedule_reason,
                        teacher_timezone = if (isTeacher) sourceTZStr else (booking.service?.teacher ?: "Europe/Madrid")
                    ))
                }

                apiEvents.forEach { apiEv ->
                    if (mergedLessons.none { it.bookitit_event_id == apiEv.bookitit_event_id } && apiEv.status?.lowercase() != "deleted") {
                        mergedLessons.add(apiEv)
                    }
                }

                val now = Clock.System.now()
                val upcoming = mutableListOf<BookititEvent>()
                val completed = mutableListOf<BookititEvent>()
                val unscheduled = mutableListOf<BookititEvent>()

                mergedLessons.forEach { event ->
                    if (event.status?.lowercase() == "unscheduled") {
                        unscheduled.add(event)
                    } else if (isPastLesson(event, now, getTeacherTimeZone(event)) || event.status == "completed") {
                        completed.add(event.copy(status = "completed"))
                    } else {
                        upcoming.add(event)
                    }
                }

                _upcomingLessons.value = upcoming.sortedBy { it.date + it.start }
                _completedLessons.value = completed.sortedByDescending { it.date + it.start }
                _unscheduledLessons.value = unscheduled.sortedByDescending { it.created_at ?: it.date }
                
            } catch (t: Throwable) {
                platformLog("ProfileVM", "Data load error: ${t.message}", isError = true)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchMaterials() {
        viewModelScope.launch {
            try {
                val result = SupabaseClientObj.client.postgrest["promo_codes"]
                    .select { filter { eq("is_active", true) } }
                    .decodeList<PromoCode>()
                
                _materials.value = result.groupBy { it.language.trim().ifEmpty { "Other" } }
            } catch (e: Exception) {
                platformLog("ProfileVM", "Materials error: ${e.message}", true)
            }
        }
    }

    fun claimPromo(promoId: Long) {
        viewModelScope.launch {
            try {
                val currentProfile = _profile.value ?: return@launch
                val updatedList = currentProfile.used_promos.toMutableList()
                val idStr = promoId.toString()
                
                if (!updatedList.contains(idStr)) {
                    updatedList.add(idStr)
                    SupabaseClientObj.client.postgrest["profiles"].update(mapOf("used_promos" to updatedList)) { 
                        filter { eq("id", currentProfile.id) } 
                    }
                    _profile.value = currentProfile.copy(used_promos = updatedList)
                }
            } catch (e: Exception) { platformLog("ProfileVM", "Claim error: ${e.message}", true) }
        }
    }

    /**
     * Marks a quiz as solved when correct answers are submitted.
     */
    fun markQuizAsSolved(promoId: Long) {
        viewModelScope.launch {
            try {
                val currentProfile = _profile.value ?: return@launch
                val updatedList = currentProfile.solved_quizzes.toMutableList()
                val idStr = promoId.toString()
                
                if (!updatedList.contains(idStr)) {
                    updatedList.add(idStr)
                    SupabaseClientObj.client.postgrest["profiles"].update(mapOf("solved_quizzes" to updatedList)) { 
                        filter { eq("id", currentProfile.id) } 
                    }
                    _profile.value = currentProfile.copy(solved_quizzes = updatedList)
                }
            } catch (e: Exception) { platformLog("ProfileVM", "Solve error: ${e.message}", true) }
        }
    }

    fun deleteMaterial(promoId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                SupabaseClientObj.client.postgrest["promo_codes"].delete {
                    filter { eq("id", promoId) }
                }
                fetchMaterials()
            } catch (e: Exception) {
                platformLog("ProfileVM", "Delete error: ${e.message}", true)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createVideoQuiz(
        subject: String, 
        language: String, 
        questions: List<String>, 
        options: List<String>, 
        answers: List<String>, 
        code: String, 
        discount: Double, 
        videoData: ByteArray, 
        videoName: String,
        thumbnailData: ByteArray? = null,
        thumbnailName: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val timestamp = Clock.System.now().toEpochMilliseconds()
                val cleanLang = language.replace(" ", "_")
                val cleanSub = subject.replace(" ", "_")
                
                // Upload Video
                val videoBucket = SupabaseClientObj.client.storage["quiz-videos"]
                val videoPath = "$cleanLang/${cleanSub}_${timestamp}_$videoName"
                videoBucket.upload(videoPath, videoData)
                val videoUrl = videoBucket.publicUrl(videoPath)

                // Upload Thumbnail if provided
                var thumbnailUrl: String? = null
                if (thumbnailData != null && thumbnailName != null) {
                    val thumbBucket = SupabaseClientObj.client.storage["quiz-thumbnails"]
                    val thumbPath = "$cleanLang/${cleanSub}_${timestamp}_$thumbnailName"
                    thumbBucket.upload(thumbPath, thumbnailData)
                    thumbnailUrl = thumbBucket.publicUrl(thumbPath)
                }

                val promo = PromoCode(
                    subject = subject, 
                    language = language, 
                    video_url = videoUrl, 
                    thumbnail = thumbnailUrl,
                    questions = questions, 
                    options = options, 
                    answers = answers, 
                    code = code, 
                    discount = discount
                )
                SupabaseClientObj.client.postgrest["promo_codes"].insert(promo)
                
                _unscheduleMessage.value = "Quiz created successfully!"
                fetchMaterials()
            } catch (e: Exception) { 
                _unscheduleMessage.value = "Failed: ${e.message}" 
            } finally { _isLoading.value = false }
        }
    }

    private fun isPastLesson(event: BookititEvent, now: Instant, teacherTZ: TimeZone): Boolean {
        if (event.date.isEmpty() || event.start.isEmpty()) return false
        return try {
            val date = LocalDate.parse(event.date)
            val time = DateTimeUtils.parseLocalTimeSafe(event.start) ?: return false
            LocalDateTime(date, time).toInstant(teacherTZ) < now
        } catch (e: Throwable) { false }
    }

    fun requestUnschedule(lesson: BookititEvent) {
        if (_profile.value?.role == "teacher" || !isWithin20Hours(lesson)) _showReasonInput.value = lesson
        else _showUnscheduleWarning.value = true
    }

    private fun isWithin20Hours(lesson: BookititEvent): Boolean {
        return try {
            val lessonStart = LocalDateTime(LocalDate.parse(lesson.date), DateTimeUtils.parseLocalTimeSafe(lesson.start)!!).toInstant(getTeacherTimeZone(lesson))
            (lessonStart - Clock.System.now()).inWholeHours <= 20
        } catch (e: Throwable) { false }
    }

    fun performUnschedule(lesson: BookititEvent, reason: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val deleteResult = BookititApiService.deleteEvent(lesson.bookitit_event_id ?: "", true, if (_profile.value?.role == "teacher") "admin" else "client")
                if (deleteResult.isSuccess) {
                    markLessonAsUnscheduled(lesson, reason)
                    _unscheduleMessage.value = "Unscheduled successfully"
                    loadProfileData()
                } else {
                    _unscheduleMessage.value = "Failed: ${deleteResult.exceptionOrNull()?.message}"
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _unscheduleMessage.value = "Error: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    private suspend fun markLessonAsUnscheduled(lesson: BookititEvent, reason: String?) {
        val update = mapOf<String, Any?>("status" to "unscheduled", "bookitit_event_id" to null, "unschedule_reason" to reason)
        if (!lesson.parent_package_id.isNullOrEmpty()) {
            addLessonBackToPackage(lesson.parent_package_id!!)
            SupabaseClientObj.client.postgrest["Bookings"].update(update) { filter { eq("id", lesson.supabase_id!!) } }
        } else if (lesson.supabase_id != null) {
            val booking = SupabaseClientObj.client.postgrest["Bookings"].select { filter { eq("id", lesson.supabase_id!!) } }.decodeSingleOrNull<Booking>()
            booking?.let {
                val upd = update.toMutableMap()
                upd["remaining_lessons"] = it.remaining_lessons + 1
                SupabaseClientObj.client.postgrest["Bookings"].update(upd) { filter { eq("id", lesson.supabase_id!!) } }
            }
        }
    }

    private suspend fun addLessonBackToPackage(packageId: String) {
        val currentPkg = SupabaseClientObj.client.postgrest["Bookings"].select { filter { eq("id", packageId) } }.decodeSingleOrNull<Booking>()
        currentPkg?.let { SupabaseClientObj.client.postgrest["Bookings"].update(mapOf("remaining_lessons" to (it.remaining_lessons + 1))) { filter { eq("id", packageId) } } }
    }

    private suspend fun fetchSupabaseProfile(userId: String): Profile? = try { 
        SupabaseClientObj.client.postgrest["profiles"].select { filter { eq("id", userId) } }.decodeSingleOrNull<Profile>() 
    } catch (e: Exception) { null }

    private suspend fun fetchUserBookings(profile: Profile): List<Booking> = try {
        SupabaseClientObj.client.postgrest["Bookings"].select(Columns.raw("*, service:Services(*)")) {
            filter { 
                if (profile.role == "teacher") {
                    eq("bookitit_agenda_id", profile.bookitit_agenda_id ?: "")
                } else {
                    eq("user_id", profile.id)
                }
            }
        }.decodeList<Booking>()
    } catch (e: Exception) { emptyList() }

    fun deleteAccount() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val user = SupabaseClientObj.client.auth.currentUserOrNull()
                if (user == null) {
                    _deleteStatus.value = Result.failure(Exception("Not logged in"))
                    return@launch
                }
                val profileData = _profile.value ?: fetchSupabaseProfile(user.id)
                profileData?.bookitit_client_id?.let { clientId ->
                    try { BookititApiService.deleteClient(clientId) } catch (e: Exception) {}
                }
                SupabaseClientObj.client.postgrest["profiles"].delete {
                    filter { eq("id", user.id) }
                }
                SupabaseClientObj.client.auth.signOut()
                _deleteStatus.value = Result.success(Unit)
            } catch (e: Exception) {
                platformLog("ProfileVM", "Delete account failed: ${e.message}", true)
                _deleteStatus.value = Result.failure(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() { viewModelScope.launch { SupabaseClientObj.client.auth.signOut() } }
    fun clearDeleteStatus() { _deleteStatus.value = null }
    fun clearUnscheduleMessage() { _unscheduleMessage.value = null }
    fun clearUnscheduleWarning() { _showUnscheduleWarning.value = false }
    fun clearReasonInput() { _showReasonInput.value = null }
}
