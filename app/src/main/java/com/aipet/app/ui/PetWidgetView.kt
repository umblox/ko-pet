package com.aipet.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PetWidgetView(emotion: PetEmotion) {
    Row(
        modifier = Modifier
            .size(180.dp, 100.dp)
            .background(Color(0xFF0D0D0D), shape = RoundedCornerShape(24.dp)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EyeComponent(emotion = emotion, isLeftEye = true)
        EyeComponent(emotion = emotion, isLeftEye = false)
    }
}

@Composable
fun EyeComponent(emotion: PetEmotion, isLeftEye: Boolean) {
    val neonColor = Color(0xFF00FFCC)
    
    val eyeShape = remember(emotion) {
        GenericShape { size, _ ->
            when (emotion) {
                PetEmotion.HAPPY -> {
                    moveTo(0f, size.height)
                    cubicTo(0f, 0f, size.width, 0f, size.width, size.height)
                    lineTo(size.width * 0.8f, size.height)
                    cubicTo(size.width * 0.8f, size.height * 0.3f, size.width * 0.2f, size.height * 0.3f, size.width * 0.2f, size.height)
                }
                PetEmotion.ANGRY -> {
                    if (isLeftEye) {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height * 0.5f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                    } else {
                        moveTo(0f, size.height * 0.5f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                    }
                }
                PetEmotion.SAD -> {
                    moveTo(0f, size.height * 0.3f)
                    lineTo(size.width, size.height * 0.8f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                }
                PetEmotion.BORED -> {
                    addRect(androidx.compose.ui.geometry.Rect(0f, size.height * 0.4f, size.width, size.height * 0.6f))
                }
                else -> addOval(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            }
        }
    }

    val heightModifier = when (emotion) {
        PetEmotion.IDLE -> Modifier.height(45.dp)
        PetEmotion.SURPRISED -> Modifier.height(65.dp).width(35.dp)
        PetEmotion.THINKING -> Modifier.height(20.dp)
        PetEmotion.SLEEPY -> Modifier.height(10.dp)
        PetEmotion.WINK -> if (isLeftEye) Modifier.height(5.dp) else Modifier.height(45.dp)
        else -> Modifier.height(40.dp)
    }

    Box(
        modifier = Modifier
            .width(30.dp)
            .then(heightModifier)
            .clip(eyeShape)
            .background(neonColor)
    )
}
