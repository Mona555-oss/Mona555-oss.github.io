package com.alfleyla.zeituna.ui.booking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfleyla.zeituna.booking.CalendarBookingViewModel
import com.alfleyla.zeituna.data.models.BookititSlot
import com.alfleyla.zeituna.data.models.LessonService
import com.alfleyla.zeituna.theme.TurquoisePrimary
import com.alfleyla.zeituna.theme.TurquoiseDark
import com.alfleyla.zeituna.utils.*
import io.ktor.http.*
import kotlinx.datetime.*
import org.jetbrains.compose.resources.painterResource
import silentspace.shared.generated.resources.Res
import silentspace.shared.generated.resources.bg
import silentspace.shared.generated.resources.style
import kotlin.math.roundToInt

@Composable
fun CalendarBookingScreen(
    viewModel: CalendarBookingViewModel,
    service: LessonService,
    existingBookingId: String? = null,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val slots by viewModel.slots.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val bookingStatus by viewModel.bookingStatus.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    val freshServiceDetails by viewModel.serviceDetails.collectAsState()
    
    val localTZ = remember { DateTimeUtils.safeTimeZone(null) }
    val isPrepaid = existingBookingId != null

    val safeNowDate = remember(localTZ) {
        try {
            Clock.System.now().toLocalDateTime(localTZ).date
        } catch (t: Throwable) {
            Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        }
    }
    
    var selectedDate by remember { mutableStateOf(safeNowDate) }
    var selectedTime by remember { mutableStateOf<String?>(null) }
    var agreedToTerms by remember { mutableStateOf(false) }
    var showMarginDialog by remember { mutableStateOf(false) }

    val showTermsSection = remember(isPrepaid, profile) {
        !isPrepaid && profile?.agreed_to_purchase_terms != true
    }

    LaunchedEffect(Unit) {
        val currentUrl = platformGetCurrentUrl()
        if (currentUrl.contains("payment=success")) {
            val savedDate = platformGetSessionData("pending_date")
            val savedTime = platformGetSessionData("pending_time")
            val savedBId = platformGetSessionData("pending_bid")
            val savedSId = platformGetSessionData("pending_sid")
            
            if (!savedDate.isNullOrBlank() && !savedTime.isNullOrBlank()) {
                platformPutSessionData("pending_date", "")
                platformPutSessionData("pending_time", "")
                platformClearUrlParams()
                
                selectedDate = LocalDate.parse(savedDate)
                selectedTime = savedTime
                
                viewModel.confirmBooking(
                    bookititId = savedBId ?: service.bookitit_service_id ?: "",
                    supabaseId = savedSId ?: service.id ?: "",
                    date = savedDate,
                    time = savedTime,
                    duration = service.duration,
                    lessonCount = service.count
                )
            }
        }
    }

    LaunchedEffect(selectedDate) {
        service.bookitit_service_id?.let { sId ->
            service.bookitit_agenda_id?.let { aId ->
                viewModel.fetchAvailability(sId, selectedDate.toString(), aId)
            }
        }
    }

    if (bookingStatus?.isSuccess == true) {
        BookingSuccessScreen(onGoToProfile = {
            viewModel.clearBookingStatus()
            onSuccess()
        })
        return
    }

    if (showMarginDialog) {
        AlertDialog(
            onDismissRequest = { showMarginDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                    Spacer(Modifier.width(8.dp))
                    Text("Scheduling Rule")
                }
            },
            text = {
                Column {
                    Text("Lessons have to be booked in 20 hours or more in advance. Please choose a later time slot.")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Read our terms of service to know more.",
                        color = TurquoisePrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            platformOpenUrl("https://mona555-oss.github.io/terms_of_service.html")
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showMarginDialog = false }) {
                    Text("OK", color = TurquoisePrimary)
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFFFBF0))) {
        val isStyle = service.course_name.contains("MSA", ignoreCase = true)
        val bgResource = if (isStyle) Res.drawable.style else Res.drawable.bg
        
        Image(
            painter = painterResource(bgResource),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(if (isStyle) Modifier else Modifier.padding(40.dp))
                .alpha(0.12f),
            contentScale = if (isStyle) ContentScale.Crop else ContentScale.Fit
        )

        Scaffold(
            backgroundColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(if (isPrepaid) "Schedule Lesson" else "Select Date and Time", color = Color.White) },
                    backgroundColor = TurquoisePrimary,
                    navigationIcon = {
                        IconButton(onClick = {
                            viewModel.clearBookingStatus()
                            onBack()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    elevation = 4.dp
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.Transparent)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = "Choose a Date",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TurquoiseDark,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                DateSelectionCard(
                    selectedDate = selectedDate,
                    onDateSelected = { 
                        selectedDate = it
                        selectedTime = null
                    },
                    localTZ = localTZ
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Available Slots (${localTZ.id})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TurquoiseDark,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TurquoisePrimary)
                    }
                } else if (slots.isEmpty()) {
                    Text(
                        text = "No available slots for this date.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        color = TurquoiseDark
                    )
                } else {
                    TimeSlotGrid(
                        slots = slots,
                        selectedDate = selectedDate,
                        selectedTime = selectedTime,
                        onTimeSelected = { selectedTime = it },
                        localTZ = localTZ
                    )
                }

                if (showTermsSection) {
                    Spacer(modifier = Modifier.height(24.dp))
                    TermsSection(
                        agreed = agreedToTerms,
                        onAgreedChange = { agreedToTerms = it }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        val bId = service.bookitit_service_id
                        val sId = service.id
                        val time = selectedTime
                        
                        if (bId != null && sId != null && time != null) {
                            val marginCheck = viewModel.validateSchedulingMargin(selectedDate.toString(), time)
                            if (marginCheck.isFailure) {
                                showMarginDialog = true
                                return@Button
                            }

                            if (isPrepaid) {
                                viewModel.schedulePackageLesson(
                                    bookititId = bId,
                                    bookingId = existingBookingId!!,
                                    date = selectedDate.toString(),
                                    time = time,
                                    duration = service.duration
                                )
                            } else {
                                if (showTermsSection && agreedToTerms) {
                                    viewModel.updatePurchaseAgreement()
                                }
                                
                                platformPutSessionData("pending_date", selectedDate.toString())
                                platformPutSessionData("pending_time", time)
                                platformPutSessionData("pending_bid", bId)
                                platformPutSessionData("pending_sid", sId)
                                
                                val businessEmail = "mona@silentframes.net"
                                val currentPrice = freshServiceDetails?.price ?: service.price
                                val currentName = freshServiceDetails?.course_name ?: service.course_name
                                
                                var shortName = currentName.filter { it.isLetterOrDigit() || it.isWhitespace() }.take(20).trim()
                                if (shortName.isEmpty()) shortName = "Lesson"
                                
                                val totalCents = (currentPrice * 100.0).roundToInt()
                                val formattedAmount = "${totalCents / 100}.${(totalCents % 100).toString().padStart(2, '0')}"
                                
                                val baseUrl = platformGetCurrentUrl().substringBefore("?")
                                val returnUrl = if (baseUrl.endsWith("/")) "${baseUrl}?payment=success" else "$baseUrl/?payment=success"
                                
                                val paypalUrl = "https://www.paypal.com/cgi-bin/webscr?" +
                                        "cmd=_xclick" +
                                        "&business=${businessEmail.encodeURLParameter()}" +
                                        "&item_name=${shortName.encodeURLParameter()}" +
                                        "&amount=${formattedAmount.encodeURLParameter()}" +
                                        "&currency_code=USD" +
                                        "&no_shipping=1" +
                                        "&return=${returnUrl.encodeURLParameter()}" +
                                        "&rm=1"
                                
                                platformOpenUrl(paypalUrl)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = selectedTime != null && (!showTermsSection || agreedToTerms) && !isLoading,
                    colors = ButtonDefaults.buttonColors(backgroundColor = TurquoisePrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isPrepaid) "Confirm Schedule" else "Pay with PayPal", color = Color.White, fontSize = 18.sp)
                }

                if (bookingStatus?.isFailure == true) {
                    val errorMsg = bookingStatus?.exceptionOrNull()?.message ?: "Booking failed"
                    Text(
                        text = errorMsg,
                        color = Color.Red,
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

@Composable
fun DateSelectionCard(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    localTZ: TimeZone
) {
    Card(
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = Color.White.copy(alpha = 0.9f),
        modifier = Modifier.fillMaxWidth()
    ) {
        val today = remember(localTZ) {
            try {
                Clock.System.now().toLocalDateTime(localTZ).date
            } catch (t: Throwable) {
                Clock.System.now().toLocalDateTime(TimeZone.UTC).date
            }
        }
        val dates = (0..20).map { today.plus(it, DateTimeUnit.DAY) }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.height(180.dp).padding(8.dp),
            userScrollEnabled = false
        ) {
            items(dates) { date ->
                val isSelected = date == selectedDate
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .padding(2.dp)
                        .background(
                            color = if (isSelected) TurquoisePrimary else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onDateSelected(date) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Color.Black
                        )
                        Text(
                            text = date.month.name.take(3),
                            fontSize = 9.sp,
                            color = if (isSelected) Color.White else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimeSlotGrid(
    slots: List<BookititSlot>,
    selectedDate: LocalDate,
    selectedTime: String?,
    onTimeSelected: (String) -> Unit,
    localTZ: TimeZone
) {
    val teacherTZ = remember { DateTimeUtils.safeTimeZone("Europe/Madrid", preferMadrid = true) }

    Column {
        val chunkedSlots = slots.chunked(3)
        chunkedSlots.forEach { rowSlots ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowSlots.forEach { slot ->
                    val rawTime = slot.start.take(5)
                    
                    val displayTime = remember(rawTime, selectedDate, localTZ, teacherTZ) {
                        try {
                            val time = DateTimeUtils.parseLocalTimeSafe(rawTime) ?: return@remember rawTime
                            val teacherDT = LocalDateTime(selectedDate, time)
                            teacherDT.toInstant(teacherTZ).toLocalDateTime(localTZ).time.toString().take(5)
                        } catch (t: Throwable) { rawTime }
                    }

                    val isSelected = rawTime == selectedTime

                    Card(
                        elevation = 1.dp,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, TurquoisePrimary),
                        backgroundColor = if (isSelected) TurquoisePrimary else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                            .clickable { onTimeSelected(rawTime) }
                    ) {
                        Text(
                            text = displayTime,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center,
                            color = if (isSelected) Color.White else TurquoiseDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
                repeat(3 - rowSlots.size) {
                    Spacer(modifier = Modifier.weight(1f).padding(4.dp))
                }
            }
        }
    }
}

@Composable
fun TermsSection(agreed: Boolean, onAgreedChange: (Boolean) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "I understand that lesson courses and individual lesson purchases are non-refundable. I may unschedule or reschedule my lessons at no additional cost up to 20 hours before the scheduled start time. Lessons that are not unscheduled within this period may be considered completed. Read the full terms of service for more details.",
            fontSize = 12.sp,
            color = Color.Black,
            lineHeight = 16.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Checkbox(
                checked = agreed,
                onCheckedChange = onAgreedChange,
                colors = CheckboxDefaults.colors(checkedColor = TurquoisePrimary)
            )
            Text(
                text = "I have read and agree to the purchase terms.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    }
}

@Composable
fun BookingSuccessScreen(onGoToProfile: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFBF0))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = TurquoisePrimary
        )
        Text(
            text = "Booking Confirmed!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TurquoiseDark,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            text = "Your lesson has been successfully scheduled and added to your profile.",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
            color = Color.Black
        )
        Button(
            onClick = onGoToProfile,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = TurquoisePrimary),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Go to My Profile", color = Color.White)
        }
    }
}
