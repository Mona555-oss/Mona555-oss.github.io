package com.alfleyla.zeituna.data.models

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
data class AgendaConfig(
    val minAdvanceMakeEvent: Int,    // in minutes
    val maxAdvanceSeeAgenda: Int,    // currently used as hours for min_start_date margin per user request
    val minAdvanceCancelEvent: Int,  // in hours
    val confirmEvents: String = "no",
    val userValidate: String = "no",
    val keySendMethod: String = "email",
    val widgetIntervalSize: String,
    val shiftNumber: Int? = null,
    val shiftStep: Int? = null,
    val calculatedMinEventDate: String? = null, // YYYY-MM-DD
    val calculatedMaxEventDate: String? = null, // YYYY-MM-DD
    val minStartDate: Long? = null             // Timestamp (ms)
) {
    /**
     * Returns the exact minimum booking timestamp in milliseconds.
     * Uses minStartDate if present, otherwise calculates based on maxAdvanceSeeAgenda (as hours).
     */
    fun getMinBookingMillis(): Long {
        if (minStartDate != null && minStartDate > 0) return minStartDate

        val now = Clock.System.now().toEpochMilliseconds()
        // User specifically mentioned minimum_advance_see_agenda (maxAdvanceSeeAgenda) should be in hours.
        val marginMillis = maxAdvanceSeeAgenda.toLong() * 60 * 60 * 1000
        val minByMargin = now + marginMillis

        val minByDate = calculatedMinEventDate?.let { dateStr ->
            try {
                LocalDate.parse(dateStr).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            } catch (e: Exception) { null }
        } ?: 0L

        return max(minByMargin, minByDate)
    }

    /**
     * Returns the maximum booking timestamp in milliseconds based on API's calculatedMaxEventDate.
     * Defaults to 1 year from now if the field is missing.
     */
    fun getMaxBookingMillis(): Long {
        val maxByDate = calculatedMaxEventDate?.let { dateStr ->
            try {
                // Add almost 24h to ensure the last day is fully selectable
                val startOfDay = LocalDate.parse(dateStr).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                startOfDay + (24 * 60 * 60 * 1000L - 1000L)
            } catch (e: Exception) { null }
        }

        return maxByDate ?: (Clock.System.now().toEpochMilliseconds() + 365L * 24 * 60 * 60 * 1000L)
    }
}
