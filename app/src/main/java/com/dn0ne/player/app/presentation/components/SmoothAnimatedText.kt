package com.dn0ne.player.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

@Composable
fun SmoothAnimatedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
    wordDelayMs: Long = 250L,     // Time gap between words starting to appear
    fadeDurationMs: Int = 600      // Speed of the fade-in for each word
) {
    val words = remember(text) { text.split(" ") }
    
    // Create an alpha controller for each word
    val alphas = remember(text) {
        words.map { Animatable(0f) }
    }

    LaunchedEffect(text) {
        alphas.forEachIndexed { index, animatable ->
            delay(index * wordDelayMs)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = fadeDurationMs)
            )
        }
    }

    Row(modifier = modifier) {
        words.forEachIndexed { index, word ->
            Text(
                text = if (index == words.size - 1) word else "$word ",
                style = style,
                color = color,
                modifier = Modifier.alpha(alphas[index].value)
            )
        }
    }
}
