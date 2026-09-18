package com.alfleyla.zeituna.data.models

import kotlinx.serialization.Serializable

@Serializable
data class LessonService(
    val id: String? = null,
    val bookitit_service_id: String? = null,
    val bookitit_agenda_id: String? = null, // New field to link service to a teacher
    val course_name: String,
    val teacher: String? = null, // Added teacher field
    val duration: Int,
    val count: Int,
    val price: Double,
    val bookitit_url: String? = null,
    val validity_days: Int? = null,
    val description: String? = null
) {
    val displayLabel: String
        get() = if (count == 1) "Individual Lesson (${duration}m)" else "$count Lessons (${duration}m)"

    val pricePerLesson: Double
        get() = if (count > 0) price / count else price
}
