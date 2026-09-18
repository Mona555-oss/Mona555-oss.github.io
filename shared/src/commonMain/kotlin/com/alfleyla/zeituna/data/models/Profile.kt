package com.alfleyla.zeituna.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val email: String?,
    val full_name: String? = null,
    val bookitit_client_id: String? = null,
    val role: String? = "user",
    val bookitit_agenda_id: String? = null,
    val time_zone: String? = null,
    val agreed_to_purchase_terms: Boolean = false,
    val agreed_to_legal: Boolean = false,
    val solved_quizzes: List<String> = emptyList(), // Tracks solved quizzes
    val used_promos: List<String> = emptyList(),    // Tracks spent promo codes (after payment)
    val introduction: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)
