package com.dn0ne.player.core.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.media.MediaScannerConnection
import android.provider.MediaStore
import com.dn0ne.player.R
import com.dn0ne.player.app.presentation.components.snackbar.SnackbarAction
import com.dn0ne.player.app.presentation.components.snackbar.SnackbarController
import com.dn0ne.player.app.presentation.components.snackbar.SnackbarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicScanner(
    private val context: Context,
    private val settings: Settings
) {
    suspend fun refreshMedia(showMessages: Boolean = true, onComplete: () -> Unit = {}) {
        withContext(Dispatchers.IO) {
            try {
                val foundPaths = mutableListOf<String>()
                val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(MediaStore.Audio.Media.DATA)
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

                context.contentResolver.query(
                    audioUri,
                    projection,
                    selection,
                    null,
                    null
                )?.use { cursor ->
                    val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    while (cursor.moveToNext()) {
                        val path = cursor.getString(pathColumn)
                        if (!path.isNullOrEmpty()) {
                            foundPaths.add(path)
                        }
                    }
                }

                if (foundPaths.isEmpty()) {
                    if (showMessages) {
                        SnackbarController.sendEvent(
                            event = SnackbarEvent(
                                message = R.string.nothing_to_refresh
                            )
                        )
                    }
                } else {
                    MediaScannerConnection.scanFile(
                        context,
                        foundPaths.toTypedArray(),
                        arrayOf("audio/*"),
                        null
                    )

                    if (showMessages) {
                        SnackbarController.sendEvent(
                            event = SnackbarEvent(
                                message = R.string.refreshed_successfully
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                if (showMessages) {
                    SnackbarController.sendEvent(
                        SnackbarEvent(
                            message = R.string.failed_to_refresh,
                            action = SnackbarAction(
                                name = R.string.copy_error,
                                action = {
                                    val clipboardManager = context.getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = ClipData.newPlainText(null, e.message + "\n" + e.stackTrace.joinToString("\n"))
                                    clipboardManager?.setPrimaryClip(clip)
                                }
                            )
                        )
                    )
                }
            }
            onComplete()
        }
    }

    suspend fun scanFolder(path: String, onComplete: () -> Unit = {}) {
        withContext(Dispatchers.IO) {
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(path),
                    arrayOf("audio/*"),
                    null
                )

                SnackbarController.sendEvent(
                    event = SnackbarEvent(
                        message = R.string.scanned_successfully
                    )
                )
            } catch (e: Exception) {
                SnackbarController.sendEvent(
                    SnackbarEvent(
                        message = R.string.failed_to_scan,
                        action = SnackbarAction(
                            name = R.string.copy_error,
                            action = {
                                val clipboardManager = context.getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText(null, e.message + "\n" + e.stackTrace.joinToString("\n"))
                                clipboardManager?.setPrimaryClip(clip)
                            }
                        )
                    )
                )
            }
            onComplete()
        }
    }
}
