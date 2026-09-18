package com.alfleyla.zeituna.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alfleyla.zeituna.profile.ProfileViewModel
import com.alfleyla.zeituna.theme.TurquoisePrimary
import com.alfleyla.zeituna.theme.TurquoiseDark
import com.alfleyla.zeituna.utils.platformPickFile

@Composable
fun TeacherDashboardScreen(
    viewModel: ProfileViewModel,
    onLogout: () -> Unit,
    onPackageClick: (com.alfleyla.zeituna.data.models.Booking) -> Unit,
    onNavigateToMaterials: () -> Unit,
    onNavigateToAccount: () -> Unit
) {
    val profile by viewModel.profile.collectAsState()
    val activePackages by viewModel.activePackages.collectAsState()
    val upcomingLessons by viewModel.upcomingLessons.collectAsState()
    val completedLessons by viewModel.completedLessons.collectAsState()
    val unscheduledLessons by viewModel.unscheduledLessons.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val showReasonInput by viewModel.showReasonInput.collectAsState()
    val showUnscheduleWarning by viewModel.showUnscheduleWarning.collectAsState()
    val unscheduleMessage by viewModel.unscheduleMessage.collectAsState()

    var showCreateQuiz by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.loadProfileData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Teacher Dashboard", color = Color.White) },
                backgroundColor = TurquoisePrimary,
                navigationIcon = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(onClick = {
                                showMenu = false
                                onNavigateToMaterials()
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = TurquoisePrimary)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Materials")
                                }
                            }
                            DropdownMenuItem(onClick = {
                                showMenu = false
                                onNavigateToAccount()
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = TurquoisePrimary)
                                    Spacer(Modifier.width(12.dp))
                                    Text("My Account")
                                }
                            }
                        }
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = Color.White)
                        @Suppress("Deprecation")
                        Spacer(Modifier.width(4.dp))
                        Text("Logout", color = Color.White, fontSize = 12.sp)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState)
            ) {
                Card(
                    elevation = 2.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Welcome, ${profile?.full_name ?: "Teacher"}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TurquoiseDark
                        )
                        Text(
                            text = profile?.email ?: "",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Button(
                    onClick = { showCreateQuiz = true },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = TurquoisePrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Create Video Quiz / Promo", color = Color.White)
                }

                CollapsibleSection(title = "booked courses") {
                    if (activePackages.isEmpty()) {
                        Text("No active courses", modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        activePackages.forEach { pkg ->
                            PackageItem(pkg, onClick = { onPackageClick(pkg) })
                        }
                    }
                }

                CollapsibleSection(title = "Upcoming Lessons", initiallyExpanded = true) {
                    if (upcomingLessons.isEmpty()) {
                        Text("No upcoming lessons", modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        upcomingLessons.forEach { lesson ->
                            LessonItem(
                                event = lesson,
                                showUnschedule = true,
                                onUnscheduleClick = { viewModel.requestUnschedule(lesson) }
                            )
                        }
                    }
                }

                CollapsibleSection(title = "Completed Lessons") {
                    if (completedLessons.isEmpty()) {
                        Text("No completed lessons", modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        completedLessons.forEach { lesson ->
                            LessonItem(event = lesson)
                        }
                    }
                }

                CollapsibleSection(title = "Unscheduled Lessons") {
                    if (unscheduledLessons.isEmpty()) {
                        Text("No unscheduled lessons", modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        unscheduledLessons.forEach { lesson ->
                            LessonItem(event = lesson)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(100.dp))
            }

            // Visible Scrollbar
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState)
            )

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            if (showCreateQuiz) {
                CreateQuizDialog(
                    onDismiss = { showCreateQuiz = false },
                    onSave = { subject, language, questions, options, answers, code, discount, videoData, videoName, thumbnailData, thumbnailName ->
                        viewModel.createVideoQuiz(
                            subject, language, questions, options, answers, code, discount, 
                            videoData, videoName, thumbnailData, thumbnailName
                        )
                        showCreateQuiz = false
                    }
                )
            }
            
            showReasonInput?.let { lesson ->
                UnscheduleReasonDialog(
                    onDismiss = { viewModel.clearReasonInput() },
                    onConfirm = { reason ->
                        viewModel.performUnschedule(lesson, reason)
                        viewModel.clearReasonInput()
                    }
                )
            }
            
            if (showUnscheduleWarning) {
                UnscheduleWarningDialog(
                    message = "Lessons canceled with less than 20 hours notice cannot be rescheduled and will be marked as completed.",
                    onConfirm = { viewModel.clearUnscheduleWarning() }
                )
            }
            
            unscheduleMessage?.let { msg ->
                AlertDialog(
                    onDismissRequest = { viewModel.clearUnscheduleMessage() },
                    title = { Text("Information") },
                    text = { Text(msg) },
                    confirmButton = {
                        Button(onClick = { viewModel.clearUnscheduleMessage() }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CreateQuizDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, List<String>, List<String>, List<String>, String, Double, ByteArray, String, ByteArray?, String?) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("") }

    var q1 by remember { mutableStateOf("") }
    var q1o1 by remember { mutableStateOf("") }
    var q1o2 by remember { mutableStateOf("") }
    var q1o3 by remember { mutableStateOf("") }
    var a1 by remember { mutableStateOf("") }

    var q2 by remember { mutableStateOf("") }
    var q2o1 by remember { mutableStateOf("") }
    var q2o2 by remember { mutableStateOf("") }
    var q2o3 by remember { mutableStateOf("") }
    var a2 by remember { mutableStateOf("") }

    var q3 by remember { mutableStateOf("") }
    var q3o1 by remember { mutableStateOf("") }
    var q3o2 by remember { mutableStateOf("") }
    var q3o3 by remember { mutableStateOf("") }
    var a3 by remember { mutableStateOf("") }

    var code by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("") }
    
    var selectedVideoName by remember { mutableStateOf("Select video from computer") }
    var videoBytes by remember { mutableStateOf<ByteArray?>(null) }

    var selectedThumbnailName by remember { mutableStateOf<String?>(null) }
    var thumbnailBytes by remember { mutableStateOf<ByteArray?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f).widthIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    "Create New Video Quiz",
                    modifier = Modifier.padding(20.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TurquoiseDark
                )
                
                Divider()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Text("Basic Information", fontWeight = FontWeight.Bold, color = TurquoiseDark)
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject (e.g. MSA Lesson 1)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = language, onValueChange = { language = it }, label = { Text("Language (e.g. Arabic)") }, modifier = Modifier.fillMaxWidth())
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Question Blocks
                    QuizQuestionInput("Question 1", q1, q1o1, q1o2, q1o3, a1, 
                        onQ = { q1 = it }, onO1 = { q1o1 = it }, onO2 = { q1o2 = it }, onO3 = { q1o3 = it }, onA = { a1 = it })

                    QuizQuestionInput("Question 2", q2, q2o1, q2o2, q2o3, a2, 
                        onQ = { q2 = it }, onO1 = { q2o1 = it }, onO2 = { q2o2 = it }, onO3 = { q2o3 = it }, onA = { a2 = it })

                    QuizQuestionInput("Question 3", q3, q3o1, q3o2, q3o3, a3, 
                        onQ = { q3 = it }, onO1 = { q3o1 = it }, onO2 = { q3o2 = it }, onO3 = { q3o3 = it }, onA = { a3 = it })
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Promo Details", fontWeight = FontWeight.Bold, color = TurquoiseDark)
                    OutlinedTextField(value = code, onValueChange = { code = it.uppercase() }, label = { Text("Secret Promo Code") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = discount, onValueChange = { discount = it }, label = { Text("Discount Amount ($)") }, modifier = Modifier.fillMaxWidth())
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Video Picker
                    Button(
                        onClick = { 
                            platformPickFile(listOf("mp4", "mov", "avi")) { bytes, name ->
                                if (bytes != null && name != null) {
                                    videoBytes = bytes
                                    selectedVideoName = name
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = if (videoBytes != null) Color(0xFF4CAF50) else Color.LightGray),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(selectedVideoName, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Thumbnail Picker
                    Button(
                        onClick = { 
                            platformPickFile(listOf("jpg", "jpeg", "png", "webp")) { bytes, name ->
                                if (bytes != null && name != null) {
                                    thumbnailBytes = bytes
                                    selectedThumbnailName = name
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = if (thumbnailBytes != null) TurquoisePrimary else Color.LightGray),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(selectedThumbnailName ?: "Select thumbnail (Image)", color = Color.White)
                    }
                    
                    if (videoBytes != null) {
                        Text("Video ready: ${(videoBytes!!.size / 1024 / 1024)} MB", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
                
                Divider()
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        enabled = subject.isNotBlank() && language.isNotBlank() && code.isNotBlank() && videoBytes != null,
                        onClick = {
                            val questions = listOf(q1, q2, q3)
                            val options = listOf(q1o1, q1o2, q1o3, q2o1, q2o2, q2o3, q3o1, q3o2, q3o3)
                            val answers = listOf(a1, a2, a3)
                            onSave(subject, language, questions, options, answers, code, discount.toDoubleOrNull() ?: 0.0, videoBytes!!, selectedVideoName, thumbnailBytes, selectedThumbnailName)
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = TurquoisePrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(48.dp).width(160.dp)
                    ) { Text("Upload & Save", color = Color.White) }
                }
            }
        }
    }
}

@Composable
fun QuizQuestionInput(
    title: String,
    q: String, o1: String, o2: String, o3: String, a: String,
    onQ: (String) -> Unit, onO1: (String) -> Unit, onO2: (String) -> Unit, onO3: (String) -> Unit, onA: (String) -> Unit
) {
    Card(
        elevation = 0.dp,
        backgroundColor = TurquoisePrimary.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = TurquoiseDark)
            OutlinedTextField(value = q, onValueChange = onQ, label = { Text("Question") }, modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = o1, onValueChange = onO1, label = { Text("Option A") }, modifier = Modifier.weight(1f).padding(end = 4.dp))
                OutlinedTextField(value = o2, onValueChange = onO2, label = { Text("Option B") }, modifier = Modifier.weight(1f).padding(horizontal = 2.dp))
                OutlinedTextField(value = o3, onValueChange = onO3, label = { Text("Option C") }, modifier = Modifier.weight(1f).padding(start = 4.dp))
            }
            OutlinedTextField(value = a, onValueChange = onA, label = { Text("Correct Answer (A, B, or C)") }, modifier = Modifier.fillMaxWidth())
        }
    }
}
