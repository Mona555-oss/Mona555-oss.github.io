package com.alfleyla.zeituna.booking

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alfleyla.zeituna.data.SupabaseClientObj
import com.alfleyla.zeituna.data.models.LessonService
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class BookingViewModel : ViewModel() {

    private val _services = MutableLiveData<List<LessonService>>()
    val services: LiveData<List<LessonService>> = _services

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun fetchServices() {
        viewModelScope.launch {
            _isLoading.postValue(true)

            // DEBUG: Test the Bookitit API Key and list actual IDs

            try {
                val result = SupabaseClientObj.client.postgrest["Services"]
                    .select()
                    .decodeList<LessonService>()

                _services.postValue(result)
            } catch (e: Exception) {
                Log.e("SupabaseFetch", "Error: ${e.message}")
                _services.postValue(emptyList())
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
}
