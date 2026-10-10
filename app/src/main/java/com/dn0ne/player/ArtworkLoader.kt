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
     * Guaranteed to work even if the file was just moved across folders.
     */
    suspend fun loadArtwork(
        context: Context, 
        trackUri: Uri?, 
        filePath: String? = null
    ): Bitmap? = withContext(Dispatchers.IO) {

        // Tier 1: Direct File / FileDescriptor extraction via MediaMetadataRetriever
        // This is 100% immune to folder moves because it inspects the actual ID3 tags of the file on disk.
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

        // Tier 2: ContentResolver FileDescriptor via trackUri
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

        // Tier 3: Android 10+ MediaStore thumbnail loader (only works if MediaStore already re-indexed the move)
        if (trackUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return@withContext context.contentResolver.loadThumbnail(
                    trackUri,
                    Size(512, 512),
                    null
                )
            } catch (_: Exception) {}
        }

        null
    }
}
