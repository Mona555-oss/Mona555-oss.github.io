package com.alfleyla.zeituna.ui.booking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfleyla.zeituna.booking.BookingViewModel
import com.alfleyla.zeituna.data.models.LessonService
import com.alfleyla.zeituna.theme.TurquoisePrimary
import com.alfleyla.zeituna.theme.TurquoiseDark
import com.alfleyla.zeituna.ui.dashboard.ZeitunaScrollbar
import org.jetbrains.compose.resources.painterResource
import silentspace.shared.generated.resources.Res
import silentspace.shared.generated.resources.sub_icon

@Composable
fun BookingScreen(
    viewModel: BookingViewModel,
    onBackToProfile: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToMaterials: () -> Unit,
    onServiceClick: (LessonService, Double) -> Unit
) {
    val services by viewModel.services.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var promoCodeInput by remember { mutableStateOf("") }
    val activePromo by viewModel.activePromo.collectAsState()
    val promoError by viewModel.promoError.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.fetchServices()
    }

    Scaffold(
        backgroundColor = MaterialTheme.colors.background,
        topBar = {
            TopAppBar(
                backgroundColor = MaterialTheme.colors.primary,
                contentPadding = PaddingValues(0.dp),
                elevation = 4.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    TextButton(
                        onClick = onLogout,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colors.onPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Logout", color = MaterialTheme.colors.onPrimary, fontSize = 12.sp)
                    }

                    Image(
                        painter = painterResource(Res.drawable.sub_icon),
                        contentDescription = null,
                        modifier = Modifier.width(80.dp).height(100.dp).align(Alignment.Center)
                    )

                    TextButton(
                        onClick = {
                            viewModel.clearPromo()
                            onBackToProfile()
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Text("Profile", color = MaterialTheme.colors.onPrimary, fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = MaterialTheme.colors.onPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background)
                    .verticalScroll(scrollState)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Choose your Course",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 16.dp),
                    textAlign = TextAlign.Center
                )

                // Promo Center Header
                Card(
                    elevation = 0.dp,
                    backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colors.primary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Don't have a discount yet?", fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
                        Text("Watch a short video to earn one!", fontSize = 12.sp, textAlign = TextAlign.Center, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onNavigateToMaterials,
                            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colors.onPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Earn a Discount", color = MaterialTheme.colors.onPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (services.isEmpty() && !isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No packages available.", color = Color.Gray)
                    }
                }

                services.forEach { service ->
                    val isMatched = activePromo != null && (activePromo!!.code == promoCodeInput.trim().uppercase())
                    val discount = if (isMatched && service.count > 1) activePromo!!.discount else 0.0
                    
                    ServiceItem(
                        service = service,
                        appliedDiscount = discount,
                        onClick = { onServiceClick(service, discount) }
                    )
                }
                
                // Promo Code Input Box
                Card(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = MaterialTheme.colors.surface,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = promoCodeInput,
                                onValueChange = { 
                                    promoCodeInput = it.uppercase()
                                    if (promoError != null) viewModel.clearPromo() 
                                },
                                label = { Text("Enter Promo Code") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                isError = promoError != null,
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    focusedBorderColor = MaterialTheme.colors.primary,
                                    textColor = MaterialTheme.colors.onSurface,
                                    cursorColor = MaterialTheme.colors.primary,
                                    unfocusedBorderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = { viewModel.validateCode(promoCodeInput) },
                                enabled = promoCodeInput.isNotBlank() && !isLoading,
                                colors = ButtonDefaults.buttonColors(backgroundColor = TurquoiseDark),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Apply", color = Color.White)
                            }
                        }
                        
                        if (promoError != null) {
                            Text(
                                text = promoError!!,
                                color = Color(0xFFCF6679),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                            )
                        } else if (activePromo != null) {
                            Text(
                                text = "🎉 Promo Applied: $${activePromo!!.discount} off packages!",
                                color = Color(0xFF81C784),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(64.dp))
            }

            // Interactive Scrollbar
            ZeitunaScrollbar(
                scrollState = scrollState,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
            )

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colors.primary)
            }
        }
    }
}

@Composable
expect fun VideoPlayer(url: String, modifier: Modifier)

@Composable
fun ServiceItem(
    service: LessonService,
    appliedDiscount: Double,
    onClick: () -> Unit
) {
    val finalPrice = (service.price - appliedDiscount).coerceAtLeast(0.0)

    Card(
        elevation = 4.dp,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = MaterialTheme.colors.surface,
        border = BorderStroke(1.dp, MaterialTheme.colors.primary.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = service.displayLabel, fontSize = 14.sp, color = MaterialTheme.colors.primary)
                Text(text = service.course_name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
            }

            Column(horizontalAlignment = Alignment.End) {
                if (appliedDiscount > 0) {
                    Text(
                        text = "$${service.price}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                    )
                }
                Text(
                    text = "$$finalPrice",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (appliedDiscount > 0) Color(0xFF81C784) else MaterialTheme.colors.primary
                )
            }
        }
    }
}
