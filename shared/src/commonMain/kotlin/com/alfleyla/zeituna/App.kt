package com.alfleyla.zeituna

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.jetbrains.compose.resources.painterResource
import silentspace.shared.generated.resources.Res
import silentspace.shared.generated.resources.sub_icon

enum class Screen {
    Landing, Login, Register, Dashboard, Booking, ServiceDetails, CalendarBooking, Materials, Account
}

@Composable
fun App() {
    var currentScreen by remember { mutableStateOf(Screen.Landing) }
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
            }
        }

        when (currentScreen) {
            Screen.Landing -> {
                LandingScreen(
                    onLoginClick = { currentScreen = Screen.Login },
                    onNavigateToPrivacy = { /* Add link logic if needed */ },
                    onNavigateToTerms = { /* Add link logic if needed */ }
                )
            }
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
                            currentScreen = Screen.Landing
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
                            currentScreen = Screen.Landing
                        },
                        onBuyClick = {
                            selectedBookingId = null
                            appliedDiscount = 0.0
                            currentScreen = Screen.Booking
                        },
                        onPackageClick = { pkg ->
                            if (pkg.service != null) {
                                selectedService = pkg.service
                                selectedBookingId = pkg.id
                                appliedDiscount = 0.0
                                currentScreen = Screen.CalendarBooking
                            }
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
                            currentScreen = Screen.Landing
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
                        currentScreen = Screen.Landing
                    }
                )
            }
        }
    }
}

@Composable
fun LandingScreen(
    onLoginClick: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToTerms: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Zeituna",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colors.primaryVariant
            )
            
            Row {
                TextButton(onClick = onNavigateToPrivacy) {
                    Text("Privacy", color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f))
                }
                TextButton(onClick = onNavigateToTerms) {
                    Text("Terms", color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f))
                }
            }
        }

        // Hero Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = MaterialTheme.colors.surface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Text(
                    "Welcome to Zeituna",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }

            Text(
                text = "Language lessons,\nmade simple.",
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 56.sp,
                color = MaterialTheme.colors.primaryVariant
            )

            Text(
                text = "Zeituna makes it easy to book and manage language lessons in a simple and organized way.",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp, bottom = 40.dp).fillMaxWidth(0.8f),
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onLoginClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primaryVariant),
                    modifier = Modifier.height(56.dp).padding(end = 8.dp)
                ) {
                    Text("Get Started / Login", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                }

                OutlinedButton(
                    onClick = { /* Link to Play Store */ },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colors.primaryVariant),
                    modifier = Modifier.height(56.dp).padding(start = 8.dp)
                ) {
                    Text("Get it on Google Play", color = MaterialTheme.colors.primaryVariant, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
