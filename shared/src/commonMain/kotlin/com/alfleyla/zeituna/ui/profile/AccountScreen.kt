package com.alfleyla.zeituna.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfleyla.zeituna.profile.ProfileViewModel
import com.alfleyla.zeituna.theme.TurquoisePrimary
import com.alfleyla.zeituna.theme.TurquoiseDark

@Composable
fun AccountScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val profile by viewModel.profile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val deleteStatus by viewModel.deleteStatus.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRequestReceivedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deleteStatus) {
        deleteStatus?.let {
            if (it.isSuccess) {
                showRequestReceivedDialog = true
            }
            viewModel.clearDeleteStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Account", color = Color.White) },
                backgroundColor = TurquoisePrimary,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = Color.White,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        AccountInfoField("Full Name", profile?.full_name ?: "-")
                        Spacer(modifier = Modifier.height(16.dp))
                        AccountInfoField("Email Address", profile?.email ?: "-")
                        Spacer(modifier = Modifier.height(16.dp))
                        AccountInfoField("Member Since", formatMemberDate(profile?.created_at))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isLoading
                ) {
                    Text("Delete Account", color = Color.White, fontSize = 16.sp)
                }
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = TurquoisePrimary)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account") },
            text = { Text("Are you sure you want to delete your account? This action cannot be undone and all your booking history will be lost.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccount()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRequestReceivedDialog) {
        AlertDialog(
            onDismissRequest = { /* Cannot dismiss */ },
            title = { Text("Request Received") },
            text = { Text("Your account deletion request has been received. Your account will be permanently deleted within 3 business days.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRequestReceivedDialog = false
                        onLogout()
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun AccountInfoField(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TurquoiseDark
        )
        Text(
            text = value,
            fontSize = 18.sp,
            color = Color.Black,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun formatMemberDate(createdAt: String?): String {
    if (createdAt.isNullOrEmpty()) return "-"
    // Very simple parsing for now, matching the logic from the Android side's fallback
    return createdAt.take(10)
}
