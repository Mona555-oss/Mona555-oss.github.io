package com.alfleyla.zeituna

import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alfleyla.zeituna.auth.AuthViewModel
import com.alfleyla.zeituna.booking.BookingViewModel
import com.alfleyla.zeituna.booking.CalendarBookingViewModel
import com.alfleyla.zeituna.data.SupabaseClientObj
import com.alfleyla.zeituna.data.models.LessonService
import com.alfleyla.zeituna.profile.ProfileViewModel
import com.alfleyla.zeituna.theme.ZeitunaTheme
import com.alfleyla.zeituna.ui.auth.LoginScreen
import com.alfleyla.zeituna.ui.auth.RegisterScreen
import com.alfleyla.zeituna.ui.dashboard.TeacherDashboardScreen
import com.alfleyla.zeituna.ui.dashboard.StudentDashboardScreen
import com.alfleyla.zeituna.ui.booking.BookingScreen
import com.alfleyla.zeituna.ui.booking.CalendarBookingScreen
import com.alfleyla.zeituna.ui.booking.ServiceDetailsScreen
import com.alfleyla.zeituna.ui.materials.MaterialsScreen
import com.alfleyla.zeituna.ui.profile.AccountScreen
import com.alfleyla.zeituna.utils.platformGetCurrentUrl
import com.alfleyla.zeituna.utils.platformGetSessionData
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

enum class Screen {
    Login, Register, Dashboard, Booking, ServiceDetails, CalendarBooking, Materials, Account
}

@Composable
fun App() {
    var currentScreen by remember { mutableStateOf(Screen.Login) }
    var selectedService by remember { mutableStateOf<LessonService?>(null) }
    var selectedBookingId by remember { mutableStateOf<String?>(null) }
    var appliedDiscount by remember { mutableStateOf(0.0) }
    
    val scope = rememberCoroutineScope()
    val sessionStatus = SupabaseClientObj.client.auth.sessionStatus.collectAsState(SessionStatus.NotAuthenticated(isSignOut = false))
    val authViewModel: AuthViewModel = viewModel { AuthViewModel() }
    val profileViewModel: ProfileViewModel = viewModel { ProfileViewModel() }

    ZeitunaTheme {
        // Restore state if returning from PayPal
        LaunchedEffect(Unit) {
            val url = platformGetCurrentUrl()
            if (url.contains("payment=success")) {
                val savedSId = platformGetSessionData("pending_sid")
                if (!savedSId.isNullOrEmpty()) {
                    try {
                        val service = SupabaseClientObj.client.postgrest["Services"]
                            .select { filter { eq("id", savedSId) } }
                            .decodeSingleOrNull<LessonService>()
                        
                        if (service != null) {
                            selectedService = service
                            selectedBookingId = null
                            currentScreen = Screen.CalendarBooking
                        }
                    } catch (e: Exception) { }
                }
            }
        }

        LaunchedEffect(sessionStatus.value) {
            if (sessionStatus.value is SessionStatus.Authenticated) {
                if (currentScreen == Screen.Login || currentScreen == Screen.Register) {
                    currentScreen = Screen.Dashboard
                }
            } else {
                if (currentScreen != Screen.Register) {
                    currentScreen = Screen.Login
                }
            }
        }

        when (currentScreen) {
            Screen.Login -> {
                LoginScreen(
                    viewModel = authViewModel,
                    onNavigateToRegister = { 
                        authViewModel.clearError()
                        currentScreen = Screen.Register 
                    }
                )
            }
            Screen.Register -> {
                RegisterScreen(
                    viewModel = authViewModel,
                    onNavigateToLogin = { 
                        authViewModel.clearError()
                        currentScreen = Screen.Login 
                    }
                )
            }
            Screen.Dashboard -> {
                val profile by profileViewModel.profile.collectAsState()
                
                if (profile?.role == "teacher") {
                    TeacherDashboardScreen(
                        viewModel = profileViewModel,
                        onLogout = {
                            profileViewModel.logout()
                            currentScreen = Screen.Login
                        },
                        onPackageClick = { },
                        onNavigateToMaterials = { currentScreen = Screen.Materials },
                        onNavigateToAccount = { currentScreen = Screen.Account }
                    )
                } else {
                    StudentDashboardScreen(
                        viewModel = profileViewModel,
                        onLogout = {
                            profileViewModel.logout()
                            currentScreen = Screen.Login
                        },
                        onBuyClick = {
                            selectedBookingId = null
                            appliedDiscount = 0.0
                            currentScreen = Screen.Booking
                        },
                        onPackageClick = { pkg ->
                            selectedService = pkg.service
                            selectedBookingId = pkg.id
                            appliedDiscount = 0.0
                            currentScreen = Screen.CalendarBooking
                        },
                        onNavigateToMaterials = { currentScreen = Screen.Materials },
                        onNavigateToAccount = { currentScreen = Screen.Account }
                    )
                }
            }
            Screen.Booking -> {
                val bookingViewModel: BookingViewModel = viewModel { BookingViewModel() }
                BookingScreen(
                    viewModel = bookingViewModel,
                    onBackToProfile = { currentScreen = Screen.Dashboard },
                    onLogout = {
                        scope.launch {
                            try { SupabaseClientObj.client.auth.signOut() } catch (e: Exception) {}
                            currentScreen = Screen.Login
                        }
                    },
                    onNavigateToMaterials = { currentScreen = Screen.Materials },
                    onServiceClick = { service, discount ->
                        selectedService = service
                        selectedBookingId = null
                        appliedDiscount = discount
                        currentScreen = Screen.ServiceDetails
                    }
                )
            }
            Screen.ServiceDetails -> {
                selectedService?.let { service ->
                    ServiceDetailsScreen(
                        service = service,
                        onBack = { currentScreen = Screen.Booking },
                        onConfirmBuy = { currentScreen = Screen.CalendarBooking }
                    )
                }
            }
            Screen.CalendarBooking -> {
                val calendarViewModel: CalendarBookingViewModel = viewModel { CalendarBookingViewModel() }
                selectedService?.let { service ->
                    CalendarBookingScreen(
                        viewModel = calendarViewModel,
                        service = service.copy(price = service.price - appliedDiscount),
                        existingBookingId = selectedBookingId,
                        onBack = { 
                            if (selectedBookingId != null) {
                                currentScreen = Screen.Dashboard
                            } else {
                                currentScreen = Screen.ServiceDetails
                            }
                        },
                        onSuccess = { 
                            currentScreen = Screen.Dashboard 
                        }
                    )
                }
            }
            Screen.Materials -> {
                MaterialsScreen(
                    viewModel = profileViewModel,
                    onBack = { currentScreen = Screen.Dashboard }
                )
            }
            Screen.Account -> {
                AccountScreen(
                    viewModel = profileViewModel,
                    onBack = { currentScreen = Screen.Dashboard },
                    onLogout = {
                        profileViewModel.logout()
                        currentScreen = Screen.Login
                    }
                )
            }
        }
    }
}
