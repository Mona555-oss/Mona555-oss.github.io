package com.alfleyla.zeituna.data.models

import kotlinx.serialization.Serializable

@Serializable
data class PromoCode(
    val id: Long? = null,
    val subject: String? = "",
    val language: String = "",
    val video_url: String? = "",
    val thumbnail: String? = null, // New field for the thumbnail URL
    val questions: List<String>? = emptyList(),
    val options: List<String>? = emptyList(),
    val answers: List<String>? = emptyList(),
    val code: String? = "",
    val discount: Double = 0.0,
    val is_active: Boolean? = true,
    val created_at: String? = null
)
