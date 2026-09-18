package com.alfleyla.zeituna.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfleyla.zeituna.data.models.BookititEvent
import com.alfleyla.zeituna.theme.TurquoisePrimary

@Composable
fun UnscheduleReasonDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    val isEnabled = reason.length >= 5

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Unschedule Reason",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Please provide a reason (min 5 chars)") },
                    placeholder = { Text("e.g., Conflict with other activity") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = TurquoisePrimary,
                        focusedLabelColor = TurquoisePrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason) },
                enabled = isEnabled,
                colors = ButtonDefaults.buttonColors(backgroundColor = TurquoisePrimary),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Confirm Unschedule", color = Color.White)
            }
        }
    )
}

@Composable
fun UnscheduleWarningDialog(
    message: String,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        buttons = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(backgroundColor = TurquoisePrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("I understand", color = Color.White, fontSize = 16.sp)
            }
        },
        title = {
            Text(
                text = "Unschedule Policy",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 16.sp,
                color = Color.DarkGray,
                lineHeight = 22.sp
            )
        },
        modifier = Modifier.padding(16.dp)
    )
}
