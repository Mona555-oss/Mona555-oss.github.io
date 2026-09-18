package com.alfleyla.zeituna.data

import com.alfleyla.zeituna.data.models.*
import com.alfleyla.zeituna.utils.*
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

object BookititApiService {
    private const val HOST = "app.bookitit.com"
    private const val ROOT = "api/11"

    private const val PUBLIC_KEY = "10c907a8df9bc380e1cf9ba485948f5f7"
    private const val PRIVATE_KEY = "17b24296caf70b766778711a15df5ce3d"

    private var proxyUrl: String? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun setProxy(url: String?) {
        proxyUrl = url
        platformLog("BookititAPI", "Proxy set to: $url")
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 60000
            socketTimeoutMillis = 60000
        }
    }

    private fun HttpRequestBuilder.applyProxyHeaders() {
        if (proxyUrl != null) {
            header("X-Requested-With", "XMLHttpRequest")
            header("apikey", SupabaseClientObj.SUPABASE_KEY)
        }
    }

    private fun getBaseUrl(): String {
        val p = proxyUrl
        return if (p != null) {
            val clean = p.trimEnd('/')
            "$clean/"
        } else {
            "https://$HOST/$ROOT/"
        }
    }

    private fun makeBasicAuth(urlPart: String): String {
        val pathForSig = urlPart.trimStart('/')
        val hashBytes = hmacMd5(PRIVATE_KEY.trim().encodeToByteArray(), pathForSig.trim().encodeToByteArray())
        val signature = hashBytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        val credentials = "${PUBLIC_KEY.trim()}:$signature"
        return "Basic ${base64Encode(credentials.encodeToByteArray())}"
    }

    private fun timeToMinutes(time: String): Int {
        return try {
            val parts = time.split(":")
            parts[0].trim().toInt() * 60 + parts[1].trim().take(2).toInt()
        } catch (e: Exception) { 0 }
    }

    private fun safeParseJson(input: String): JsonElement? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        return try {
            json.parseToJsonElement(trimmed.replace("'", "\""))
        } catch (e: Exception) { null }
    }

    suspend fun getAvailability(serviceId: String, date: String, agendaId: String): List<BookititSlot> {
        val cleanServiceId = serviceId.trim().let { if (!it.startsWith("bkt")) "bkt$it" else it }
        val cleanAgendaId = agendaId.trim().let { if (!it.startsWith("bkt")) "bkt$it" else it }
        val urlPart = "getfreeslots/${PUBLIC_KEY.trim()}/$cleanServiceId/$cleanAgendaId/${date.trim()}"

        return try {
            val response = client.get("${getBaseUrl()}$urlPart") {
                header("Authorization", makeBasicAuth(urlPart))
                header("Accept", "application/json")
                applyProxyHeaders()
            }
            val body = response.bodyAsText()
            val jsonElement = safeParseJson(body)

            val hoursArray = when {
                jsonElement?.jsonObject?.containsKey("slots") == true -> {
                    jsonElement.jsonObject["slots"]?.jsonObject?.get("hours")?.jsonArray
                }
                jsonElement?.jsonObject?.containsKey("freeslots") == true -> {
                    val fs = jsonElement.jsonObject["freeslots"]
                    if (fs is JsonArray) fs else fs?.jsonObject?.get("hours")?.jsonArray
                }
                else -> null
            }

            hoursArray?.map {
                val time = it.jsonPrimitive.content.replace("*", "")
                BookititSlot(start = time, end = "")
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createBooking(request: BookititBookingRequest, agendaId: String): Result<String> {
        val cleanServiceId = request.service_id.trim().let { if (!it.startsWith("bkt")) "bkt$it" else it }
        val cleanAgendaId = agendaId.trim().let { if (!it.startsWith("bkt")) "bkt$it" else it }

        // urlPart used for signature calculation - must NOT include query parameters
        val urlPart = "addevent/${PUBLIC_KEY.trim()}"
        val startMins = timeToMinutes(request.time)
        val endMins = startMins + request.duration

        return try {
            // query param 'format=json' removed from URL to avoid signature mismatch.
            val response = client.post("${getBaseUrl()}$urlPart") {
                header("Authorization", makeBasicAuth(urlPart))
                header("Accept", "application/json")
                applyProxyHeaders()
                setBody(FormDataContent(Parameters.build {
                    append("p_sAgendaID", cleanAgendaId)
                    append("p_sServiceID", cleanServiceId)
                    
                    // Parameters names matched EXACTLY to the addevent documentation screenshot
                    append("p_dStartDate", request.date.trim())
                    append("p_dEndDate", request.date.trim())
                    append("p_iStartTime", startMins.toString())
                    append("p_iEndTime", endMins.toString())
                    
                    append("p_sComments", "App Booking")

                    if (!request.clientId.isNullOrBlank()) {
                        // Documentation says p_sClientID (Capital ID)
                        append("p_sClientID", request.clientId.trim())
                    } else {
                        append("p_sClientName", request.name.trim())
                        append("p_sClientEmail", request.email.trim())
                    }
                    
                    // Force JSON response via body parameter
                    append("format", "json")
                }))
            }
            val body = response.bodyAsText()
            platformLog("BookititAPI", "Add Event Response: $body")
            val jsonElement = safeParseJson(body)
            val eventNode = jsonElement?.jsonObject?.get("event")?.jsonObject
            val eventId = eventNode?.get("id")?.jsonPrimitive?.content

            if (eventId != null) {
                Result.success(eventId)
            } else {
                val errorMsg = jsonElement?.jsonObject?.get("error")?.let {
                    if (it is JsonObject) it["message"]?.jsonPrimitive?.content else it.jsonPrimitive.content
                } ?: "Booking failed: $body"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAgendaConfiguration(agendaId: String): Result<AgendaConfig> {
        val cleanAgendaId = agendaId.trim().let { if (!it.startsWith("bkt")) "bkt$it" else it }
        val urlPart = "getagendaconfiguration/${PUBLIC_KEY.trim()}/$cleanAgendaId"
        return try {
            val response = client.get("${getBaseUrl()}$urlPart") {
                header("Authorization", makeBasicAuth(urlPart))
                header("Accept", "application/json")
                applyProxyHeaders()
            }
            val body = response.bodyAsText()
            val jsonElement = safeParseJson(body)
            val configNode = jsonElement?.jsonObject?.get("configuration") ?: jsonElement?.jsonObject?.get("data")?.jsonObject?.get("configuration")
            if (configNode != null) Result.success(json.decodeFromJsonElement<AgendaConfig>(configNode))
            else Result.failure(Exception("Configuration not found"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun findClientByEmail(email: String): String? {
        val urlPart = "getclientbyvalidationfield/${PUBLIC_KEY.trim()}"
        return try {
            val response = client.post("${getBaseUrl()}$urlPart") {
                header("Authorization", makeBasicAuth(urlPart))
                header("Accept", "application/json")
                applyProxyHeaders()
                setBody(FormDataContent(Parameters.build {
                    append("p_sFieldType", "email")
                    append("p_sFieldvalue", email.trim().lowercase())
                    append("format", "json")
                }))
            }
            val body = response.bodyAsText()
            val jsonElement = safeParseJson(body)
            jsonElement?.jsonObject?.get("client")?.jsonObject?.get("id")?.jsonPrimitive?.content
        } catch (e: Exception) { null }
    }

    suspend fun getAgendaEvents(agendaId: String, start: String, end: String, timezone: String? = null): List<BookititEvent> {
        val cleanAgendaId = agendaId.trim().let { if (!it.startsWith("bkt")) "bkt$it" else it }
        val urlPart = "getagendaevents/${PUBLIC_KEY.trim()}/$cleanAgendaId/${start.trim()}/${end.trim()}"
        return try {
            val response = client.get("${getBaseUrl()}$urlPart") {
                header("Authorization", makeBasicAuth(urlPart))
                header("Accept", "application/json")
                applyProxyHeaders()
                if (timezone != null) parameter("timezone", timezone)
                parameter("format", "json")
            }
            val body = response.bodyAsText()
            val jsonElement = safeParseJson(body)
            val data = jsonElement?.jsonObject?.get("events")?.jsonArray
            data?.let { json.decodeFromJsonElement<List<BookititEvent>>(it) } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getClientEvents(clientId: String, timezone: String? = null): List<BookititEvent> {
        val cleanClientId = clientId.trim().let { if (!it.startsWith("bkt")) "bkt$it" else it }
        val urlPart = "getclientevents/${PUBLIC_KEY.trim()}/$cleanClientId"
        return try {
            val response = client.get("${getBaseUrl()}$urlPart") {
                header("Authorization", makeBasicAuth(urlPart))
                header("Accept", "application/json")
                applyProxyHeaders()
                if (timezone != null) parameter("timezone", timezone)
                parameter("format", "json")
            }
            val body = response.bodyAsText()
            val jsonElement = safeParseJson(body)
            val data = jsonElement?.jsonObject?.get("events")?.jsonArray
            data?.let { json.decodeFromJsonElement<List<BookititEvent>>(it) } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun deleteEvent(eventId: String, sendNotification: Boolean = true, deletedBy: String = "client"): Result<Unit> {
        val cleanEventId = eventId.trim().let { if (!it.startsWith("bkt")) "bkt$it" else it }
        val urlPart = "deleteevent/${PUBLIC_KEY.trim()}/$cleanEventId"
        return try {
            val response = client.post("${getBaseUrl()}$urlPart") {
                header("Authorization", makeBasicAuth(urlPart))
                header("Accept", "application/json")
                applyProxyHeaders()
                setBody(FormDataContent(Parameters.build {
                    append("p_bSendNotification", if (sendNotification) "1" else "0")
                    append("p_sDeletedBy", deletedBy)
                    append("format", "json")
                }))
            }
            if (response.status.isSuccess()) Result.success(Unit) else Result.failure(Exception("Delete failed"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteClient(clientId: String): Result<Unit> {
        val cleanClientId = clientId.trim().let { if (!it.startsWith("bkt")) "bkt$it" else it }
        val urlPart = "deleteclient/${PUBLIC_KEY.trim()}/$cleanClientId"
        return try {
            val response = client.post("${getBaseUrl()}$urlPart") {
                header("Authorization", makeBasicAuth(urlPart))
                header("Accept", "application/json")
                applyProxyHeaders()
                setBody(FormDataContent(Parameters.build {
                    append("format", "json")
                }))
            }
            if (response.status.isSuccess()) Result.success(Unit) else Result.failure(Exception("Delete failed"))
        } catch (e: Exception) { Result.failure(e) }
    }
}
