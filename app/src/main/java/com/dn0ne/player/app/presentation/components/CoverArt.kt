package com.dn0ne.player.app.presentation.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.dn0ne.player.ArtworkLoader

@Composable
fun CoverArt(
    uri: Uri?,
    modifier: Modifier = Modifier,
    onCoverArtLoaded: ((ImageBitmap?) -> Unit)? = null
) {
    val context = LocalContext.current
    var imageBitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(uri) {
        val loadedBitmap = if (uri != null) {
            ArtworkLoader.loadArtwork(context, uri)?.asImageBitmap()
        } else {
            null
        }
        imageBitmap = loadedBitmap
        onCoverArtLoaded?.invoke(loadedBitmap)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(color = MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        val currentBitmap = imageBitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxSize(0.4f)
            )
        }
    }
}
