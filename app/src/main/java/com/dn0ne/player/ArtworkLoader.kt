package com.dn0ne.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ArtworkLoader {

    /**
     * Loads artwork from content Uri or raw file path.
     */
    suspend fun loadArtwork(
        context: Context, 
        trackUri: Uri?, 
        filePath: String? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (trackUri == null && filePath.isNullOrBlank()) return@withContext null

        // 1. Direct ContentResolver stream (works for album-art URIs & custom image URIs)
        if (trackUri != null) {
            try {
                context.contentResolver.openInputStream(trackUri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) return@withContext bitmap
                }
            } catch (_: Exception) {}
        }

        // 2. Android 10+ MediaStore thumbnail loader
        if (trackUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val thumb = context.contentResolver.loadThumbnail(
                    trackUri,
                    Size(512, 512),
                    null
                )
                if (thumb != null) return@withContext thumb
            } catch (_: Exception) {}
        }

        // 3. Direct File / FileDescriptor ID3 extraction via MediaMetadataRetriever (for audio files)
        if (!filePath.isNullOrBlank()) {
            val file = File(filePath)
            if (file.exists() && file.canRead()) {
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
        }

        // 4. FileDescriptor extraction for track audio URI
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

        null
    }
}
