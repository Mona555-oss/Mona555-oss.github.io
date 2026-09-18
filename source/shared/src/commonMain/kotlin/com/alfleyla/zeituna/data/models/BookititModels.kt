package com.alfleyla.zeituna.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class BookititSlot(
    val start: String,
    val end: String
)

@Serializable
data class BookititDayAvailability(
    val date: String,
    val slots: List<BookititSlot>
)

@Serializable
data class BookititAvailabilityResponse(
    val data: List<BookititDayAvailability>? = null,
    val error: String? = null
)

@Serializable
data class BookititBookingRequest(
    val service_id: String,
    val date: String,
    val time: String,
    val name: String,
    val email: String,
    val duration: Int = 30,
    val clientId: String? = null
)

@Serializable
data class BookititEvent(
    @SerialName("id")
    val bookitit_event_id: String? = null,
    val date: String,
    val start: String,
    val end: String? = null,
    val service_id: String? = null,
    val service_name: String? = null,
    val agenda_id: String? = null,
    val status: String? = null,
    val created_at: String? = null,
    val expires_at: String? = null,
    val supabase_id: String? = null,
    val parent_package_id: String? = null,
    val duration: JsonElement? = null,
    val unschedule_reason: String? = null,
    val client_name: String? = null,
    val client_email: String? = null,
    val teacher_timezone: String? = null // Removed hardcoded default
)

@Serializable
data class BookititEventsResponse(
    val data: List<BookititEvent>? = null,
    val error: String? = null
)
