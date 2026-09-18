package com.alfleyla.zeituna.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alfleyla.zeituna.data.models.Booking
import com.alfleyla.zeituna.data.models.BookititEvent
import com.alfleyla.zeituna.theme.TurquoisePrimary
import com.alfleyla.zeituna.theme.TurquoiseDark
import com.alfleyla.zeituna.utils.DateTimeUtils
import kotlinx.coroutines.launch
import kotlinx.datetime.*

@Composable
fun PackageItem(
    booking: Booking,
    onClick: () -> Unit
) {
    Card(
        elevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = booking.service?.course_name ?: "Lesson Pack",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
                Text(
                    text = "Status: ${booking.status}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.primary
                )
                Text(
                    text = "bought: ${booking.created_at?.take(10) ?: "-"}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "expires: ${booking.expires_at?.take(10) ?: "-"}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${booking.remaining_lessons}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
                Text(
                    text = "Left",
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.primary
                )
            }
        }
    }
}

@Composable
fun LessonItem(
    event: BookititEvent,
    showUnschedule: Boolean = false,
    onUnscheduleClick: () -> Unit = {}
) {
    val status = event.status?.lowercase() ?: ""
    val isUnscheduled = status == "unscheduled"
    val isCompleted = status == "completed"
    val isInactive = isUnscheduled || isCompleted

    val cardAlpha = if (isInactive) 0.6f else 1.0f
    val borderColor = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
    val statusColor = if (isUnscheduled) Color(0xFFE57373) else if (isCompleted) Color.Gray else MaterialTheme.colors.primary

    var showFullReason by remember { mutableStateOf(false) }

    val localTZ = remember { DateTimeUtils.safeTimeZone(null) }
    
    val localLessonDateTime = remember(event.date, event.start, event.teacher_timezone, localTZ) {
        DateTimeUtils.convertTeacherToLocal(
            dateStr = event.date,
            timeStr = event.start,
            teacherTZStr = event.teacher_timezone,
            localTZ = localTZ
        )
    }

    val displayDate = localLessonDateTime?.date?.toString() ?: event.date
    val displayTime = localLessonDateTime?.time?.toString()?.take(5) ?: event.start.take(5)

    val scheduledAtLocal = remember(event.created_at, localTZ) {
        try {
            event.created_at?.let {
                val instant = Instant.parse(it.replace(" ", "T").let { s -> 
                    if (!s.contains("Z") && !s.contains("+")) s + "Z" else s 
                })
                val local = instant.toLocalDateTime(localTZ)
                "${local.date} ${local.time.toString().take(5)}"
            } ?: "-"
        } catch (t: Throwable) {
            event.created_at?.replace("T", " ")?.take(16) ?: "-" 
        }
    }

    Card(
        elevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(cardAlpha),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isUnscheduled) {
                    Text(
                        text = "previously scheduled time:",
                        fontSize = 9.sp,
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Text(
                    text = displayDate,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
                Text(
                    text = displayTime,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isInactive) Color.Gray else MaterialTheme.colors.primary
                )
            }

            Divider(
                modifier = Modifier
                    .width(1.dp)
                    .height(60.dp)
                    .padding(horizontal = 8.dp),
                color = borderColor
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                event.client_name?.let { name ->
                    if (name.isNotEmpty()) {
                        Text(
                            text = name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colors.primary
                        )
                    }
                }
                Text(
                    text = event.service_name ?: "Lesson",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary
                )
                Text(
                    text = "Status: ${event.status ?: "Confirmed"}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )

                Text(
                    text = "scheduled: $scheduledAtLocal",
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )

                event.unschedule_reason?.let { reason ->
                    if (reason.isNotEmpty()) {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            Text(
                                text = "Reason: ${if (showFullReason || reason.length <= 50) reason else reason.take(50) + "..."}",
                                fontSize = 11.sp,
                                color = Color(0xFFE57373)
                            )
                            if (reason.length > 50 && !showFullReason) {
                                Text(
                                    text = "more",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colors.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .clickable { showFullReason = true }
                                )
                            }
                        }
                    }
                }
            }

            if (showUnschedule && !isInactive) {
                TextButton(
                    onClick = onUnscheduleClick,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "Unschedule",
                        color = MaterialTheme.colors.primary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CollapsibleSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                modifier = Modifier.weight(1f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.onSurface
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colors.onSurface
            )
        }
        AnimatedVisibility(visible = isExpanded) {
            Column {
                content()
            }
        }
        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
    }
}

/**
 * A custom interactive mouse-based scrollbar for Web and Android.
 */
@Composable
fun ZeitunaScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    val maxScroll = scrollState.maxValue.toFloat()
    if (maxScroll <= 0) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val scrollValue = scrollState.value.toFloat()
    
    BoxWithConstraints(modifier = modifier.fillMaxHeight().width(14.dp)) {
        val viewHeightPx = with(density) { maxHeight.toPx() }
        val contentHeightPx = viewHeightPx + maxScroll
        
        // Handle size (thumb) proportional to viewport
        val thumbHeightPx = (viewHeightPx / contentHeightPx) * viewHeightPx
        val thumbHeightDp = with(density) { thumbHeightPx.coerceAtLeast(80f).toDp() }
        
        // Handle position
        val scrollPercent = scrollValue / maxScroll
        val trackHeightPx = viewHeightPx - with(density) { thumbHeightDp.toPx() }
        val thumbOffsetDp = with(density) { (scrollPercent * trackHeightPx).toDp() }

        // The Track (Jump-to-click)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(maxScroll) {
                    detectTapGestures { offset ->
                        val targetPercent = (offset.y / viewHeightPx).coerceIn(0f, 1f)
                        scope.launch { scrollState.scrollTo((targetPercent * maxScroll).toInt()) }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(Color.Black.copy(alpha = 0.05f), CircleShape)
                    .align(Alignment.Center)
            )
        }

        // The Handle (Drag-to-scroll)
        Box(
            modifier = Modifier
                .offset(y = thumbOffsetDp)
                .width(10.dp)
                .height(thumbHeightDp)
                .clip(CircleShape)
                .background(TurquoiseDark.copy(alpha = 0.8f))
                .pointerInput(maxScroll, trackHeightPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val scrollDelta = (dragAmount.y / trackHeightPx) * maxScroll
                        scope.launch { scrollState.scrollBy(scrollDelta) }
                    }
                }
                .align(Alignment.TopCenter)
        )
    }
}
