package com.alfleyla.zeituna.ui.materials

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alfleyla.zeituna.data.models.PromoCode
import com.alfleyla.zeituna.profile.ProfileViewModel
import com.alfleyla.zeituna.ui.booking.VideoPlayer
import com.alfleyla.zeituna.utils.platformLog
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import silentspace.shared.generated.resources.Cinema
import silentspace.shared.generated.resources.Res

@Composable
fun MaterialsScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit
) {
    val materials by viewModel.materials.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val profile by viewModel.profile.collectAsState()

    var selectedPromo by remember { mutableStateOf<PromoCode?>(null) }
    var promoToDelete by remember { mutableStateOf<PromoCode?>(null) }
    
    val isTeacher = profile?.role == "teacher"
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.fetchMaterials()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Learning Materials", color = Color.White) },
                backgroundColor = MaterialTheme.colors.primary,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                elevation = 0.dp
            )
        },
        backgroundColor = MaterialTheme.colors.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Cinema Background Image
            Image(
                painter = painterResource(Res.drawable.Cinema),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Earthy/Creamy overlay for background image to ensure readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background.copy(alpha = 0.85f))
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colors.primary)
                }
            } else if (materials.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No materials available yet.", color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                ) {
                    item { Spacer(Modifier.height(16.dp)) }
                    
                    materials.forEach { (language, list) ->
                        item {
                            Text(
                                text = language.uppercase(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colors.primary,
                                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp, start = 4.dp),
                                letterSpacing = 1.sp
                            )
                        }
                        items(list) { promo ->
                            val isSolved = profile?.solved_quizzes?.contains(promo.id.toString()) == true
                            MaterialCard(
                                promo = promo,
                                isUsed = isSolved,
                                isTeacher = isTeacher,
                                onClick = { selectedPromo = promo },
                                onDelete = { promoToDelete = promo }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }

            // Web Scrollbar
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState)
            )
        }

        selectedPromo?.let { promo ->
            val isSolved = profile?.solved_quizzes?.contains(promo.id.toString()) == true
            PromoQuizDialog(
                promo = promo,
                isAlreadyUsed = isSolved,
                onDismiss = { selectedPromo = null },
                onClaimed = {
                    promo.id?.let { viewModel.markQuizAsSolved(it) }
                }
            )
        }

        promoToDelete?.let { promo ->
            AlertDialog(
                onDismissRequest = { promoToDelete = null },
                backgroundColor = MaterialTheme.colors.surface,
                contentColor = MaterialTheme.colors.onSurface,
                title = { Text("Delete Material?") },
                text = { Text("Are you sure you want to permanently delete \"${promo.subject ?: ""}\"? This cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            promo.id?.let { viewModel.deleteMaterial(it) }
                            promoToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F))
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { promoToDelete = null }) {
                        Text("Cancel", color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                    }
                }
            )
        }
    }
}

@Composable
fun MaterialCard(
    promo: PromoCode, 
    isUsed: Boolean, 
    isTeacher: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = MaterialTheme.colors.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.1f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp) 
                    .background(if (isUsed) Color.Gray.copy(alpha = 0.2f) else MaterialTheme.colors.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (!promo.thumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = promo.thumbnail,
                        contentDescription = "Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (isUsed) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = if (isUsed) Color.Gray else MaterialTheme.colors.primary
                    )
                }
                
                if (!isUsed) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                        color = MaterialTheme.colors.primary,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "EARN $${promo.discount}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                if (isTeacher) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .size(24.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = promo.subject ?: "Untitled",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUsed) Color.Gray else MaterialTheme.colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (isUsed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Quiz solved", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun PromoQuizDialog(
    promo: PromoCode,
    isAlreadyUsed: Boolean,
    onDismiss: () -> Unit,
    onClaimed: () -> Unit
) {
    val validIndices = remember(promo.questions) {
        promo.questions?.mapIndexedNotNull { index, q ->
            if (q.isNotBlank()) index else null
        } ?: emptyList()
    }

    val questions = remember(validIndices, promo.questions) {
        validIndices.map { promo.questions!![it] }
    }
    val answers = remember(validIndices, promo.answers) {
        validIndices.map { promo.answers?.getOrNull(it) ?: "" }
    }
    val allChunkedOptions = remember(promo.options) {
        promo.options?.chunked(3) ?: emptyList()
    }
    val optionsForDisplayedQuestions = remember(validIndices, allChunkedOptions) {
        validIndices.map { allChunkedOptions.getOrNull(it) ?: emptyList() }
    }

    val selections = remember(questions) {
        mutableStateListOf<String?>().apply { repeat(questions.size) { add(null) } }
    }
    
    var isCorrect by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = 8.dp,
            backgroundColor = MaterialTheme.colors.surface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = promo.subject ?: "",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                VideoPlayer(url = promo.video_url ?: "", modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(modifier = Modifier.height(16.dp))

                if (isCorrect) {
                    Text(
                        text = "Correct! 🎉\n\nUse code: ${promo.code ?: ""}\nto get $${promo.discount} off!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else if (isAlreadyUsed) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Text(
                        text = "You have already solved the quiz for this video.\n\nCode: ${promo.code ?: ""}",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    questions.forEachIndexed { index, question ->
                        QuizOptionGroup(
                            question = question,
                            options = optionsForDisplayedQuestions.getOrNull(index) ?: emptyList(),
                            selectedOption = selections[index],
                            onOptionSelected = { 
                                showError = false
                                selections[index] = it 
                            }
                        )
                    }

                    if (showError) {
                        Text(
                            "Some answers are incorrect. Please check again.",
                            color = Color.Red,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val allCorrect = questions.indices.all { i ->
                                val userSelectionText = selections[i]
                                val currentQuestionOptions = optionsForDisplayedQuestions.getOrNull(i) ?: emptyList()
                                
                                val selectedIndexInOptions = currentQuestionOptions.indexOf(userSelectionText)
                                val selectedLetter = when (selectedIndexInOptions) {
                                    0 -> "A"
                                    1 -> "B"
                                    2 -> "C"
                                    else -> ""
                                }
                                
                                val correctLetter = answers.getOrNull(i)?.trim() ?: ""
                                selectedLetter.equals(correctLetter, ignoreCase = true)
                            }

                            if (allCorrect) {
                                isCorrect = true
                                onClaimed()
                            } else {
                                showError = true
                            }
                        },
                        enabled = selections.all { it != null } && questions.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Verify Answers", color = Color.White)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Close", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun QuizOptionGroup(
    question: String,
    options: List<String>,
    selectedOption: String?,
    onOptionSelected: (String) -> Unit
) {
    val validOptions = remember(options) { options.filter { it.isNotBlank() } }
    if (validOptions.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = question, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary, modifier = Modifier.padding(bottom = 8.dp))
        validOptions.forEach { option ->
            val isSelected = (option == selectedOption)
            Card(
                elevation = 0.dp,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colors.primary else Color.Gray.copy(alpha = 0.3f)),
                backgroundColor = if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.1f) else Color.Transparent,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onOptionSelected(option) }
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isSelected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = option, fontSize = 14.sp, color = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface)
                }
            }
        }
    }
}
