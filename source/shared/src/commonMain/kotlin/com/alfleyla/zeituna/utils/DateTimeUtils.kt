package com.alfleyla.zeituna.utils

import kotlinx.datetime.*

object DateTimeUtils {
    /**
     * Safely resolves a TimeZone with specific fallbacks for Wasm/JS environments.
     */
    fun safeTimeZone(tz: String?, preferMadrid: Boolean = false): TimeZone {
        // 1. Try provided timezone string
        if (!tz.isNullOrBlank()) {
            try { return TimeZone.of(tz) } catch (t: Throwable) {}
        }

        // 2. If we are looking for the teacher's zone (Madrid)
        if (preferMadrid) {
            // Spain/Madrid is UTC+2 (Summer, CEST) or UTC+1 (Winter, CET).
            // September is Summer.
            val zones = listOf("Europe/Madrid", "CEST", "CET", "UTC+02:00", "UTC+01:00", "+02:00", "+01:00", "Europe/Paris")
            for (z in zones) {
                try { 
                    val resolved = TimeZone.of(z)
                    // Verify it's not returning UTC if we asked for something specific
                    if (resolved.id != "UTC" || z == "UTC") return resolved
                } catch (t: Throwable) {}
            }
            return TimeZone.of("UTC+02:00") // Stronger fallback for Spain in Summer
        }

        // 3. Try platform specific ID first
        val platformId = platformGetTimeZoneId()
        if (platformId != "SYSTEM" && platformId != "UTC") {
            try { 
                val resolved = TimeZone.of(platformId)
                if (resolved.id != "UTC") return resolved
            } catch (t: Throwable) {}
        }

        // 4. Try System Default
        try {
            return TimeZone.currentSystemDefault()
        } catch (t: Throwable) {}

        return TimeZone.UTC
    }

    /**
     * Returns a descriptive ID for logging, using platform lookup if the TimeZone ID is generic.
     */
    fun getDisplayId(tz: TimeZone): String {
        val id = tz.id
        if (id == "SYSTEM" || id == "UTC" || id.startsWith("FixedOffset")) {
            val pId = platformGetTimeZoneId()
            if (pId != "SYSTEM" && pId != "UTC") return pId
        }
        return id
    }

    fun parseLocalTimeSafe(timeStr: String): LocalTime? {
        return try {
            val trimmed = timeStr.trim().take(5)
            val parts = trimmed.split(":")
            if (parts.size >= 2) {
                LocalTime(parts[0].toInt(), parts[1].toInt())
            } else {
                LocalTime(trimmed.toInt(), 0)
            }
        } catch (t: Throwable) { null }
    }

    /**
     * Converts a teacher's local time to the user's local time.
     */
    fun convertTeacherToLocal(
        dateStr: String, 
        timeStr: String, 
        teacherTZStr: String?, 
        localTZ: TimeZone
    ): LocalDateTime? {
        if (dateStr.isBlank() || timeStr.isBlank()) return null
        
        try {
            val date = LocalDate.parse(dateStr.trim())
            val time = parseLocalTimeSafe(timeStr) ?: return null
            val teacherTZ = safeTimeZone(teacherTZStr, preferMadrid = true)
            
            // If they are effectively the same offset at that instant, no shift needed
            val teacherDT = LocalDateTime(date, time)
            val instant = teacherDT.toInstant(teacherTZ)
            return instant.toLocalDateTime(localTZ)
        } catch (t: Throwable) {
            return null
        }
    }
}
