package com.dn0ne.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ArtworkLoader {

    suspend fun loadArtwork(
        context: Context,
        trackUri: Uri?,
        filePath: String? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (trackUri == null && filePath.isNullOrBlank()) return@withContext null

        // 1. Direct ID3 extraction via track Uri (100% immune to moves and shared album bugs)
        if (trackUri != null) {
            try {
                context.contentResolver.openFileDescriptor(trackUri, "r")?.use { pfd ->
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(pfd.fileDescriptor)
                    val artBytes = retriever.embeddedPicture
                    retriever.release()
                    if (artBytes != null) {
                        return@withContext BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Direct ID3 extraction via file path (if provided)
        if (!filePath.isNullOrBlank()) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(filePath)
                val artBytes = retriever.embeddedPicture
                retriever.release()
                if (artBytes != null) {
                    return@withContext BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback: ContentResolver direct stream (for dedicated cover art image URIs)
        if (trackUri != null) {
            try {
                context.contentResolver.openInputStream(trackUri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) return@withContext bitmap
                }
            } catch (_: Exception) {}
        }

        null
    }
}
