package com.alfleyla.zeituna.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
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
        backgroundColor = MaterialTheme.colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.1f))
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
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
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
    val statusColor = if (isUnscheduled) Color(0xFFE57373) else if (isCompleted) Color.Gray else MaterialTheme.colors.primary

    var showFullReason by remember { mutableStateOf(false) }
    val localTZ = remember { DateTimeUtils.safeTimeZone(null) }
    
    val localLessonDateTime = remember(event.date, event.start, event.teacher_timezone, localTZ) {
        DateTimeUtils.convertTeacherToLocal(event.date, event.start, event.teacher_timezone, localTZ)
    }

    val displayDate = localLessonDateTime?.date?.toString() ?: event.date
    val displayTime = localLessonDateTime?.time?.toString()?.take(5) ?: event.start.take(5)

    Card(
        elevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        backgroundColor = MaterialTheme.colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(cardAlpha),
        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = displayDate, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
                Text(text = displayTime, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary)
            }

            Divider(modifier = Modifier.width(1.dp).height(50.dp).padding(horizontal = 8.dp), color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f))

            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                event.client_name?.let { Text(text = it, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.primary) }
                Text(text = event.service_name ?: "Lesson", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onSurface)
                Text(text = "Status: ${event.status ?: "Confirmed"}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = statusColor)
            }

            if (showUnschedule && !isInactive) {
                TextButton(onClick = onUnscheduleClick) {
                    Text(text = "Unschedule", color = MaterialTheme.colors.primary, fontSize = 12.sp)
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
            modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title.uppercase(), modifier = Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colors.onBackground)
            Icon(imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colors.onBackground)
        }
        AnimatedVisibility(visible = isExpanded) {
            Column { content() }
        }
        Divider(color = MaterialTheme.colors.onSurface.copy(alpha = 0.1f))
    }
}

/**
 * A custom interactive mouse-based scrollbar for Web.
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
    
    BoxWithConstraints(modifier = modifier.fillMaxHeight().width(12.dp)) {
        val viewHeightPx = with(density) { maxHeight.toPx() }
        val contentHeightPx = viewHeightPx + maxScroll
        
        val thumbHeightPx = (viewHeightPx / contentHeightPx) * viewHeightPx
        val thumbHeightDp = with(density) { thumbHeightPx.coerceAtLeast(60f).toDp() }
        
        val trackHeightPx = viewHeightPx - with(density) { thumbHeightDp.toPx() }
        val currentScroll = scrollState.value.toFloat()
        val scrollPercent = if (maxScroll > 0) currentScroll / maxScroll else 0f
        val thumbOffsetDp = with(density) { (scrollPercent * trackHeightPx).toDp() }

        // Track (Click to jump)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.05f), CircleShape)
                .pointerInput(maxScroll) {
                    detectTapGestures { offset ->
                        val targetPercent = (offset.y / viewHeightPx).coerceIn(0f, 1f)
                        scope.launch { scrollState.scrollTo((targetPercent * maxScroll).toInt()) }
                    }
                }
        )

        // Handle (Drag to scroll)
        Box(
            modifier = Modifier
                .offset(y = thumbOffsetDp)
                .width(8.dp)
                .height(thumbHeightDp)
                .clip(CircleShape)
                .background(TurquoiseDark.copy(alpha = 0.7f))
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        val scrollDelta = (delta / trackHeightPx) * maxScroll
                        scope.launch { scrollState.scrollBy(scrollDelta) }
                    }
                )
                .align(Alignment.TopCenter)
        )
    }
}
