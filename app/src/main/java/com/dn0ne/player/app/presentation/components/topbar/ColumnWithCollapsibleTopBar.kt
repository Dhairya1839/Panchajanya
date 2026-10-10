package com.dn0ne.player.app.presentation.components.topbar

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ColumnWithCollapsibleTopBar(
    topBarContent: @Composable BoxScope.() -> Unit,
    collapseFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    minTopBarHeight: Dp = 64.dp,
    maxTopBarHeight: Dp = 160.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    contentHorizontalAlignment: Alignment.Horizontal = Alignment.Start,
    contentVerticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val minHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxHeightPx = with(density) { maxTopBarHeight.toPx() }
    val scrollRange = (maxHeightPx - minHeightPx).coerceAtLeast(1f)

    val fraction by remember {
        derivedStateOf {
            (1f - (scrollState.value / scrollRange)).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(fraction) {
        collapseFraction(fraction)
    }

    val currentTopBarHeight = minTopBarHeight + (maxTopBarHeight - minTopBarHeight) * fraction
    val layoutDirection = LocalLayoutDirection.current

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(currentTopBarHeight)
        ) {
            topBarContent()
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding()
                ),
            horizontalAlignment = contentHorizontalAlignment,
            verticalArrangement = contentVerticalArrangement
        ) {
            content()
        }
    }
}
