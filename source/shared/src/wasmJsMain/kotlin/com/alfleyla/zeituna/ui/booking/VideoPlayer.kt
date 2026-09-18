package com.alfleyla.zeituna.ui.booking

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlinx.browser.document
import org.w3c.dom.HTMLVideoElement

@Composable
actual fun VideoPlayer(url: String, modifier: Modifier) {
    val density = LocalDensity.current
    
    // Use a unique ID for the video element to avoid collisions
    val videoId = remember(url) { "video-player-${url.hashCode()}" }

    val videoElement = remember(url) {
        (document.createElement("video") as HTMLVideoElement).apply {
            id = videoId
            src = url
            controls = true
            style.position = "absolute"
            style.backgroundColor = "black"
            style.borderRadius = "8px"
            style.display = "none"
            style.zIndex = "100" // Ensure it stays on top of the canvas
        }
    }

    DisposableEffect(url) {
        document.body?.appendChild(videoElement)
        onDispose {
            videoElement.remove()
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val size = coordinates.size
                val position = coordinates.positionInWindow()
                
                with(density) {
                    videoElement.style.width = "${size.width.toDp().value}px"
                    videoElement.style.height = "${size.height.toDp().value}px"
                    videoElement.style.left = "${position.x.toDp().value}px"
                    videoElement.style.top = "${position.y.toDp().value}px"
                    videoElement.style.display = "block"
                }
            }
    )
}
