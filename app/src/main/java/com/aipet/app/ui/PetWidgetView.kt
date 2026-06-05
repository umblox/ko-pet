package com.aipet.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PetWidgetView(petState: PetState) {
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    
    // Animasi kedipan mata konstan saat IDLE
    val blinkScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3000
                1f at 0
                1f at 2800
                0.1f at 2900
                1f at 3000
            },
            repeatMode = RepeatMode.Restart
        ), label = "eye_blink"
    )

    // Respons ukuran mata berdasarkan State
    val eyeHeight = when (petState) {
        PetState.IDLE -> 40.dp * blinkScale
        PetState.DETECTED -> 60.dp  // Mata membesar kaget/senang
        PetState.TALKING -> 45.dp   // Mata berbinar aktif
    }

    Row(
        modifier = Modifier
            .size(160.dp, 90.dp)
            .background(Color(0xFF121212), shape = CircleShape),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mata Kiri
        Box(
            modifier = Modifier
                .size(30.dp, eyeHeight)
                .background(Color(0xFF00FFCC), shape = CircleShape)
        )
        // Mata Kanan
        Box(
            modifier = Modifier
                .size(30.dp, eyeHeight)
                .background(Color(0xFF00FFCC), shape = CircleShape)
        )
    }
}

