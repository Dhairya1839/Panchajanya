package com.dnone.player

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LocalDeviceTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val contentUri: Uri
)

class NativeVoiceService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null
    private var isListening = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        initForegroundNotification()
        mainHandler.postDelayed({
            setupSpeechRecognizer()
            startListening()
        }, 500)
    }

    private fun initForegroundNotification() {
        val channelId = "panchajanya_voice_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Panchajanya Offline Voice",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Keeps voice listener active for Hey Panch commands"
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Panchajanya Voice Active")
            .setContentText("Listening for 'Hey Panch play [song]'")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("NativeVoiceService", "Speech recognition unavailable on this device")
            stopSelf()
            return
        }

        destroyRecognizer()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onPartialResults(partialResults: Bundle?) {
                    evaluateSpokenInput(partialResults)
                }

                override fun onResults(results: Bundle?) {
                    evaluateSpokenInput(results)
                    restartListeningWithDelay()
                }

                override fun onError(error: Int) {
                    Log.d("NativeVoiceService", "SpeechRecognizer error: $error")
                    // Auto-restart on timeout, no match, or silence
                    restartListeningWithDelay()
                }

                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("NativeVoiceService", "Mic ready for speech")
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
    }

    private fun evaluateSpokenInput(bundle: Bundle?) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()?.lowercase()?.trim() ?: return

        Log.d("NativeVoiceService", "Heard text: $text")

        // Matches: "hey panch play [song]", "panch play [song]", or "play [song]"
        val triggerRegex = Regex("^(hey\\s+panch|panch|play)\\s*(.*)")
        val match = triggerRegex.find(text)

        if (match != null) {
            val extractedQuery = match.groupValues[2]
            val targetSong = extractedQuery.replace("^(play|music|song)\\s*".toRegex(), "").trim()

            if (targetSong.isNotEmpty()) {
                pauseListening()
                findAndPlayDownloadedSong(targetSong)
            }
        }
    }

    private fun findAndPlayDownloadedSong(songQuery: String) {
        serviceScope.launch(Dispatchers.IO) {
            val matchedTrack = queryDeviceMediaStore(songQuery)

            withContext(Dispatchers.Main) {
                if (matchedTrack != null) {
                    Toast.makeText(
                        applicationContext,
                        "Playing: ${matchedTrack.title}",
                        Toast.LENGTH_SHORT
                    ).show()

                    playDeviceAudio(matchedTrack.contentUri)
                } else {
                    Toast.makeText(
                        applicationContext,
                        "No local audio found for '$songQuery'",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                restartListeningWithDelay()
            }
        }
    }

    private fun queryDeviceMediaStore(query: String): LocalDeviceTrack? {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )

        val selection = "(${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.ARTIST} LIKE ?) AND ${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val selectionArgs = arrayOf("%$query%", "%$query%")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

            if (cursor.moveToFirst()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol)
                val artist = cursor.getString(artistCol)
                val trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                return LocalDeviceTrack(id, title, artist, trackUri)
            }
        }
        return null
    }

    private fun playDeviceAudio(uri: Uri) {
        val playIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(playIntent)
    }

    private fun startListening() {
        if (!isListening && speechRecognizer != null && recognizerIntent != null) {
            isListening = true
            mainHandler.post {
                try {
                    speechRecognizer?.startListening(recognizerIntent)
                } catch (e: Exception) {
                    Log.e("NativeVoiceService", "Start listening error: ${e.message}")
                    isListening = false
                }
            }
        }
    }

    private fun restartListeningWithDelay() {
        isListening = false
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.e("NativeVoiceService", "Cancel error: ${e.message}")
            }
        }
        mainHandler.postDelayed({
            startListening()
        }, 350)
    }

    private fun pauseListening() {
        isListening = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.e("NativeVoiceService", "Pause error: ${e.message}")
            }
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("NativeVoiceService", "Destroy error: ${e.message}")
        }
        speechRecognizer = null
    }

    override fun onDestroy() {
        isListening = false
        mainHandler.removeCallbacksAndMessages(null)
        destroyRecognizer()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 2002

        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (NativeVoiceService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }
}
