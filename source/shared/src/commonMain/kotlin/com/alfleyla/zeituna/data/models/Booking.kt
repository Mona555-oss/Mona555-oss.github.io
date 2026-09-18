package com.alfleyla.zeituna.data.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Booking(
    val id: String? = null,
    val created_at: String? = null,
    val user_id: String,
    val service_id: String,
    val status: String,
    val remaining_lessons: Int,
    val email: String? = null,
    val payment_id: String? = null,
    // Use JsonElement to avoid crashes if Supabase returns number or string
    val payment_amount: JsonElement? = null,

    val bookitit_agenda_id: String? = null,
    val student_name: String? = null,

    val start_date: String? = null,
    val end_date: String? = null,
    val start_time: String? = null,
    val end_time: String? = null,
    val bookitit_event_id: String? = null,
    val parent_package_id: String? = null,
    val expires_at: String? = null,
    val service: LessonService? = null,
    val unschedule_reason: String? = null
)
