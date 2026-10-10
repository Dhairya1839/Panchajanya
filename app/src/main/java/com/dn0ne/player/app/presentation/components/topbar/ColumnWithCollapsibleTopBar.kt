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
import java.io.InputStream

object ArtworkLoader {

    suspend fun loadArtwork(
        context: Context,
        uri: Uri?,
        filePath: String? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (uri == null && filePath.isNullOrBlank()) return@withContext null

        // 1. Direct ContentResolver stream (works immediately if uri is an albumart URI or image file)
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) return@withContext bitmap
                }
            } catch (_: Exception) {}
        }

        // 2. Direct File Path ID3 extraction via MediaMetadataRetriever
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

        // 3. Audio File Descriptor extraction via MediaMetadataRetriever (if uri is audio media)
        if (uri != null) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
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

        // 4. Android 10+ MediaStore thumbnail loader
        if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return@withContext context.contentResolver.loadThumbnail(
                    uri,
                    Size(512, 512),
                    null
                )
            } catch (_: Exception) {}
        }

        null
    }
}
