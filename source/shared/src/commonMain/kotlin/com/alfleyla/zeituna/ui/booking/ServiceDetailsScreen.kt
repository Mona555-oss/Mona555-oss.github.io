package com.alfleyla.zeituna.ui.booking

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfleyla.zeituna.data.models.LessonService
import com.alfleyla.zeituna.theme.TurquoiseDark
import com.alfleyla.zeituna.theme.TurquoisePrimary
import org.jetbrains.compose.resources.painterResource
import silentspace.shared.generated.resources.*
import silentspace.shared.generated.resources.Res
import silentspace.shared.generated.resources.bg
import silentspace.shared.generated.resources.conversation
import silentspace.shared.generated.resources.style

@Composable
fun ServiceDetailsScreen(
    service: LessonService,
    onBack: () -> Unit,
    onConfirmBuy: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFFFBF0))) {
        val isStyle = service.course_name.contains("MSA", ignoreCase = true)
        val bgResource = if (isStyle) Res.drawable.style else Res.drawable.bg
        
        Image(
            painter = painterResource(bgResource),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                // Only add padding if it's NOT the style.png background
                .then(if (isStyle) Modifier else Modifier.padding(48.dp))
                .alpha(0.2f),
            // Reverted to Crop for style.png, Fit for others
            contentScale = if (isStyle) ContentScale.Crop else ContentScale.Fit
        )

        Scaffold(
            backgroundColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Course Details", color = Color.White) },
                    backgroundColor = TurquoisePrimary,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
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
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = service.displayLabel,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TurquoiseDark,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = service.course_name,
                    fontSize = 18.sp,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Text(
                    text = "Expiration Policy",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = "Expires in: ${service.validity_days ?: 30} days after purchase",
                    fontSize = 16.sp,
                    color = TurquoisePrimary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Text(
                    text = "All lessons included in this course must be scheduled and completed before the expiration date. Any unused lessons remaining after this date will become inactive and can no longer be scheduled.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Text(
                    text = "Description",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = service.description ?: "No description available.",
                    fontSize = 16.sp,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                Button(
                    onClick = onConfirmBuy,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = TurquoisePrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Book", color = Color.White, fontSize = 18.sp)
                }

                Spacer(modifier = Modifier.height(50.dp))

                if (service.course_name.contains("conversation", ignoreCase = true)) {
                    Image(
                        painter = painterResource(Res.drawable.conversation),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
