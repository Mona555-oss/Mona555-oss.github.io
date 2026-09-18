package com.alfleyla.zeituna.data

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseClientObj {
    const val SUPABASE_URL = "https://rdtxtuuqlqvluwuneyta.supabase.co"
    const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJkdHh0dXVxbHF2bHV3dW5leXRhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTQzOTQ5NzUsImV4cCI6MjA2OTk3MDk3NX0.KoPNxc3f47et5IVSGVdeZdqwBRWBx0pyMNw_SktKQPM"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Auth) {
            autoLoadFromStorage = true
            alwaysAutoRefresh = true
        }
        install(Postgrest)
        install(Storage) // Required for video uploads
    }
}
