package com.example.soillab.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.soillab.R
import kotlinx.coroutines.delay

@Composable
fun BootScreen() {
    var currentTextResId by remember { mutableStateOf<Int?>(null) }
    val bootSequenceIds = listOf(
        R.string.boot_os,
        R.string.boot_neural,
        R.string.boot_sensors,
        R.string.boot_models,
        R.string.boot_ready
    )

    LaunchedEffect(Unit) {
        for (resId in bootSequenceIds) {
            currentTextResId = resId
            delay(500) // Slightly faster boot sequence
        }
    }

    val bootText = currentTextResId?.let { stringResource(it) } ?: ""
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val backgroundColor = MaterialTheme.colorScheme.background

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "boot_transition")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
            label = "boot_rotation"
        )

        Canvas(modifier = Modifier.size(150.dp)) {
            drawArc(
                color = primaryColor,
                startAngle = rotation,
                sweepAngle = 120f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx())
            )
            drawArc(
                color = primaryColor.copy(alpha = 0.5f),
                startAngle = rotation + 180f,
                sweepAngle = 120f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = bootText,
            color = onSurfaceVariantColor,
            fontSize = 16.sp,
            letterSpacing = 2.sp
        )
    }
}

