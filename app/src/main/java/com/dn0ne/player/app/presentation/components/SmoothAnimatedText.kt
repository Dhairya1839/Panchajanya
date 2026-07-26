package com.dn0ne.player.app.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    wordDelayMs: Long = 200L,
    fadeDurationMs: Int = 500
) {
    val words = remember(text) { text.split(" ") }
    var visibleWordsCount by remember { mutableStateOf(0) }

    LaunchedEffect(text) {
        for (i in 1..words.size) {
            delay(wordDelayMs)
            visibleWordsCount = i
        }
    }

    Row(modifier = modifier) {
        words.forEachIndexed { index, word ->
            val alpha by animateFloatAsState(
                targetValue = if (index < visibleWordsCount) 1f else 0f,
                animationSpec = tween(durationMillis = fadeDurationMs),
                label = "word_alpha"
            )

            Text(
                text = if (index == words.size - 1) word else "$word ",
                style = style,
                color = color,
                modifier = Modifier.alpha(alpha)
            )
        }
    }
}
