package com.dn0ne.player.jam

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

class JamManager(private val context: Context) {

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val serviceId = "com.dn0ne.player.jam.SERVICE_ID"
    private val strategy = Strategy.P2P_STAR

    private val _jamRole = MutableStateFlow(JamRole.NONE)
    val jamRole: StateFlow<JamRole> = _jamRole.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<String>>(emptyList())
    val connectedPeers: StateFlow<List<String>> = _connectedPeers.asStateFlow()

    private val _discoveredHosts = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val discoveredHosts: StateFlow<List<DiscoveredHost>> = _discoveredHosts.asStateFlow()

    // Callbacks for media player events
    var onCommandReceived: ((JamCommand) -> Unit)? = null
    var onAudioFileReceived: ((File) -> Unit)? = null

    private var incomingPayload: Payload? = null
    private var pendingFileName: String = "temp_jam_audio.mp3"

    // --- HOST METHODS ---

    fun startHosting(hostName: String) {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(strategy).build()

        connectionsClient.startAdvertising(
            hostName,
            serviceId,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            _jamRole.value = JamRole.HOST
            Toast.makeText(context, "Shankhanaad session started!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(context, "Failed to start: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- GUEST METHODS ---

    fun startDiscovering() {
        _discoveredHosts.value = emptyList()
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(strategy).build()

        connectionsClient.startDiscovery(
            serviceId,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            _jamRole.value = JamRole.GUEST
        }.addOnFailureListener {
            Toast.makeText(context, "Discovery failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun joinHost(endpointId: String, guestName: String) {
        connectionsClient.requestConnection(guestName, endpointId, connectionLifecycleCallback)
            .addOnFailureListener {
                Toast.makeText(context, "Connection failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- DATA & SYNC METHODS ---

    fun sendCommand(command: JamCommand) {
        val payload = Payload.fromBytes(command.toJson().toByteArray(StandardCharsets.UTF_8))
        val peers = _connectedPeers.value
        if (peers.isNotEmpty()) {
            connectionsClient.sendPayload(peers, payload)
        }
    }

    fun sendAudioFile(uri: Uri, fileName: String) {
        try {
            sendCommand(JamCommand(action = JamAction.FILE_INCOMING, fileName = fileName))

            val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
            pfd?.let {
                val filePayload = Payload.fromFile(it)
                val peers = _connectedPeers.value
                if (peers.isNotEmpty()) {
                    connectionsClient.sendPayload(peers, filePayload)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopJam() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        _jamRole.value = JamRole.NONE
        _connectedPeers.value = emptyList()
        _discoveredHosts.value = emptyList()
    }

    // --- NEARBY CALLBACKS ---

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val list = _discoveredHosts.value.toMutableList()
            if (list.none { it.endpointId == endpointId }) {
                list.add(DiscoveredHost(endpointId, info.endpointName))
                _discoveredHosts.value = list
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _discoveredHosts.value = _discoveredHosts.value.filter { it.endpointId != endpointId }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                val current = _connectedPeers.value.toMutableList()
                if (!current.contains(endpointId)) {
                    current.add(endpointId)
                    _connectedPeers.value = current
                }
                Toast.makeText(context, "Connected to Shankhanaad!", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onDisconnected(endpointId: String) {
            _connectedPeers.value = _connectedPeers.value.filter { it != endpointId }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    val jsonStr = String(bytes, StandardCharsets.UTF_8)
                    val cmd = JamCommand.fromJson(jsonStr)
                    if (cmd.action == JamAction.FILE_INCOMING) {
                        pendingFileName = cmd.fileName
                    } else {
                        onCommandReceived?.invoke(cmd)
                    }
                }
                Payload.Type.FILE -> {
                    incomingPayload = payload
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (incomingPayload != null && update.payloadId == incomingPayload?.id &&
                update.status == PayloadTransferUpdate.Status.SUCCESS
            ) {
                val javaFile = incomingPayload?.asFile()?.asJavaFile()
                if (javaFile != null) {
                    val targetFile = File(context.cacheDir, pendingFileName)
                    try {
                        FileInputStream(javaFile).use { input ->
                            FileOutputStream(targetFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        javaFile.delete()
                        onAudioFileReceived?.invoke(targetFile)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                incomingPayload = null
            }
        }
    }
}
