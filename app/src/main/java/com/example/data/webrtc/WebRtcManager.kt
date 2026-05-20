package com.example.data.webrtc

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.ProfileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import org.webrtc.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

enum class RtcState {
    OFFLINE,
    CONNECTING_SIGNAL,
    SIGNAL_ONLINE,
    LOOKING_UP,
    PAIRING,
    P2P_CONNECTING,
    P2P_CONNECTED,
    DISCONNECTED
}

sealed class RtcEvent {
    data class IncomingRequest(val peerHash: String, val peerName: String) : RtcEvent()
    data class ConnectError(val error: String) : RtcEvent()
    data class MessageReceived(
        val id: String,
        val text: String,
        val fromName: String,
        val sentAt: Long,
        val replyToId: String?,
        val replyToName: String?,
        val replyToContent: String?
    ) : RtcEvent()
    data class TypingStateChanged(val isTyping: Boolean) : RtcEvent()
    data class LatencyMeasured(val latencyMs: Long) : RtcEvent()
    object P2PDisconnected : RtcEvent()
}

class WebRtcManager(
    private val context: Context,
    private val profileManager: ProfileManager,
    private val scope: CoroutineScope
) {
    private val TAG = "PeerX_WebRtc"
    private val WORKER_URL = "wss://nexlink.dako-sh.workers.dev"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val okHttpClient = OkHttpClient()
    private var webSocket: WebSocket? = null

    // WebRTC peer components
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    // Connection Context states
    private var targetPeerHash: String? = null
    private var targetPeerName: String? = null
    private var pendingOfferJson: JSONObject? = null
    private val pendingCandidates = mutableListOf<IceCandidate>()

    // Exposed States
    private val _connectionState = MutableStateFlow(RtcState.OFFLINE)
    val connectionState: StateFlow<RtcState> = _connectionState

    private val _eventFlow = MutableSharedFlow<RtcEvent>(extraBufferCapacity = 64)
    val eventFlow: SharedFlow<RtcEvent> = _eventFlow

    private val _processText = MutableStateFlow("Idle")
    val processText: StateFlow<String> = _processText

    init {
        initWebRtc()
    }

    private fun initWebRtc() {
        try {
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            val options = PeerConnectionFactory.Options()
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory()
            Log.d(TAG, "WebRTC Initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "WebRTC initialization failed", e)
        }
    }

    fun connectSignaling() {
        if (_connectionState.value == RtcState.P2P_CONNECTED) return
        if (webSocket != null) return

        updateState(RtcState.CONNECTING_SIGNAL)
        _processText.value = "Menghubungkan ke signaling..."

        val request = Request.Builder()
            .url(WORKER_URL)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                mainHandler.post {
                    _processText.value = "Signaling terhubung. Mendaftarkan..."
                    // Send registration message
                    val regJson = JSONObject().apply {
                        put("type", "register")
                        put("hash", profileManager.getHash())
                        put("name", profileManager.getName())
                    }
                    webSocket.send(regJson.toString())
                    updateState(RtcState.SIGNAL_ONLINE)
                    _processText.value = "Idle"
                    Log.d(TAG, "Signaling registered")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                mainHandler.post {
                    handleSignalMessage(text)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                mainHandler.post {
                    this@WebRtcManager.webSocket = null
                    if (_connectionState.value != RtcState.P2P_CONNECTED) {
                        updateState(RtcState.OFFLINE)
                        _processText.value = "Signaling terputus"
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                mainHandler.post {
                    this@WebRtcManager.webSocket = null
                    if (_connectionState.value != RtcState.P2P_CONNECTED) {
                        updateState(RtcState.OFFLINE)
                        _processText.value = "Signaling error: ${t.localizedMessage}"
                    }
                    Log.e(TAG, "Signaling failed", t)
                }
            }
        })
    }

    fun disconnectSignaling() {
        webSocket?.close(1000, "Clean closure")
        webSocket = null
    }

    fun lookupPeer(targetHash: String) {
        if (_connectionState.value != RtcState.SIGNAL_ONLINE) {
            emitError("Harap tunggu signaling terhubung")
            return
        }

        updateState(RtcState.LOOKING_UP)
        _processText.value = "Mencari peer $targetHash..."
        
        val lookupJson = JSONObject().apply {
            put("type", "lookup")
            put("hash", targetHash)
        }
        webSocket?.send(lookupJson.toString())
    }

    private fun handleSignalMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "lookup-result" -> {
                    val online = json.optBoolean("online")
                    val hash = json.optString("hash")
                    val name = json.optString("name", hash)
                    if (online) {
                        _processText.value = "Peer online. Memulai pairing..."
                        initiateOffer(hash, name)
                    } else {
                        updateState(RtcState.SIGNAL_ONLINE)
                        _processText.value = "Idle"
                        emitError("Pengguna $hash sedang offline")
                    }
                }
                "offer" -> {
                    val fromHash = json.optString("from")
                    val fromName = json.optString("name", fromHash)
                    pendingOfferJson = json
                    scope.launch {
                        _eventFlow.emit(RtcEvent.IncomingRequest(fromHash, fromName))
                    }
                }
                "answer" -> {
                    val sdpObj = json.optJSONObject("sdp") ?: return
                    val sdpText = sdpObj.optString("sdp")
                    val type = sdpObj.optString("type")
                    if (peerConnection != null && sdpText.isNotEmpty()) {
                        setRemoteDescription(SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdpText))
                    }
                }
                "ice-candidate" -> {
                    val candidateObj = json.optJSONObject("candidate") ?: return
                    val sdpMid = candidateObj.optString("sdpMid")
                    val sdpMLineIndex = candidateObj.optInt("sdpMLineIndex")
                    val sdpStr = candidateObj.optString("candidate")
                    val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdpStr)
                    if (peerConnection != null && peerConnection?.remoteDescription != null) {
                        peerConnection?.addIceCandidate(candidate)
                    } else {
                        pendingCandidates.add(candidate)
                    }
                }
                "peer-offline" -> {
                    _processText.value = "Peer offline dari signaling"
                    closeP2P()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling signalling message", e)
        }
    }

    private fun createPeerConnection(peerHash: String): PeerConnection? {
        targetPeerHash = peerHash
        pendingCandidates.clear()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                mainHandler.post {
                    Log.d(TAG, "ICE Connection change: $state")
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            updateState(RtcState.P2P_CONNECTED)
                            _processText.value = "Koneksi P2P Direct Berhasil"
                            // Complete disconnection from signaling server once connected!
                            disconnectSignaling()
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED,
                        PeerConnection.IceConnectionState.FAILED,
                        PeerConnection.IceConnectionState.CLOSED -> {
                            closeP2P()
                        }
                        else -> {}
                    }
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                mainHandler.post {
                    candidate?.let {
                        val iceJson = JSONObject().apply {
                            put("type", "ice-candidate")
                            put("to", targetPeerHash)
                            val candObj = JSONObject().apply {
                                put("sdpMid", it.sdpMid)
                                put("sdpMLineIndex", it.sdpMLineIndex)
                                put("candidate", it.sdp)
                            }
                            put("candidate", candObj)
                        }
                        webSocket?.send(iceJson.toString())
                    }
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {}

            override fun onRemoveStream(stream: MediaStream?) {}

            override fun onDataChannel(channel: DataChannel?) {
                mainHandler.post {
                    Log.d(TAG, "Incoming Data Channel received")
                    channel?.let {
                        this@WebRtcManager.dataChannel = it
                        setupDataChannel(it)
                    }
                }
            }

            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
        return peerConnection
    }

    private fun initiateOffer(peerHash: String, name: String) {
        targetPeerName = name
        updateState(RtcState.PAIRING)
        _processText.value = "Membuat RTC Connection..."

        val pc = createPeerConnection(peerHash) ?: return
        val dcInit = DataChannel.Init().apply {
            ordered = true
        }
        val dc = pc.createDataChannel("chat", dcInit)
        this.dataChannel = dc
        setupDataChannel(dc)

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                mainHandler.post {
                    desc?.let {
                        pc.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {
                                mainHandler.post {
                                    val localDesc = pc.localDescription ?: return@post
                                    val offerJson = JSONObject().apply {
                                        put("type", "offer")
                                        put("to", peerHash)
                                        put("name", profileManager.getName())
                                        val sdpObj = JSONObject().apply {
                                            put("type", localDesc.type.canonicalForm().lowercase())
                                            put("sdp", localDesc.description)
                                        }
                                        put("sdp", sdpObj)
                                    }
                                    webSocket?.send(offerJson.toString())
                                    updateState(RtcState.P2P_CONNECTING)
                                    _processText.value = "Menunggu jawaban peer..."
                                }
                            }
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(p0: String?) {}
                        }, it)
                    }
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(reason: String?) {
                mainHandler.post { emitError("Gagal membuat offer: $reason") }
            }
            override fun onSetFailure(reason: String?) {}
        }, MediaConstraints())
    }

    fun acceptIncomingRequest() {
        val offerJson = pendingOfferJson ?: return
        val fromHash = offerJson.optString("from")
        val fromName = offerJson.optString("name", fromHash)
        pendingOfferJson = null

        targetPeerName = fromName
        updateState(RtcState.PAIRING)
        _processText.value = "Menerima panggilan dan menyambung..."

        val pc = createPeerConnection(fromHash) ?: return
        val sdpObj = offerJson.optJSONObject("sdp") ?: return
        val sdpText = sdpObj.optString("sdp")
        val typeStr = sdpObj.optString("type")

        val remoteSdp = SessionDescription(SessionDescription.Type.fromCanonicalForm(typeStr), sdpText)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                mainHandler.post {
                    flushCandidates()
                    pc.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription?) {
                            mainHandler.post {
                                desc?.let {
                                    pc.setLocalDescription(object : SdpObserver {
                                        override fun onCreateSuccess(p0: SessionDescription?) {}
                                        override fun onSetSuccess() {
                                            mainHandler.post {
                                                val localDesc = pc.localDescription ?: return@post
                                                val answerJson = JSONObject().apply {
                                                    put("type", "answer")
                                                    put("to", fromHash)
                                                    put("name", profileManager.getName())
                                                    val sdpObjLocal = JSONObject().apply {
                                                        put("type", localDesc.type.canonicalForm().lowercase())
                                                        put("sdp", localDesc.description)
                                                    }
                                                    put("sdp", sdpObjLocal)
                                                }
                                                webSocket?.send(answerJson.toString())
                                                updateState(RtcState.P2P_CONNECTING)
                                                _processText.value = "Pairing... Mohon tunggu..."
                                            }
                                        }
                                        override fun onCreateFailure(p0: String?) {}
                                        override fun onSetFailure(p0: String?) {}
                                    }, it)
                                }
                            }
                        }
                        override fun onSetSuccess() {}
                        override fun onCreateFailure(reason: String?) {
                            mainHandler.post { emitError("Gagal menerima SDP: $reason") }
                        }
                        override fun onSetFailure(reason: String?) {}
                    }, MediaConstraints())
                }
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(reason: String?) {
                mainHandler.post { emitError("Gagal set remote SDP: $reason") }
            }
        }, remoteSdp)
    }

    fun rejectIncomingRequest() {
        pendingOfferJson = null
        _processText.value = "Idle"
        if (_connectionState.value == RtcState.PAIRING) {
            updateState(RtcState.SIGNAL_ONLINE)
        }
    }

    private fun setRemoteDescription(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                mainHandler.post { flushCandidates() }
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(reason: String?) {
                mainHandler.post { Log.e(TAG, "Failed to set remote desc: $reason") }
            }
        }, sdp)
    }

    private fun flushCandidates() {
        for (candidate in pendingCandidates) {
            peerConnection?.addIceCandidate(candidate)
        }
        pendingCandidates.clear()
    }

    private fun setupDataChannel(dc: DataChannel) {
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(amount: Long) {}

            override fun onStateChange() {
                mainHandler.post {
                    Log.d(TAG, "DataChannel State: ${dc.state()}")
                    if (dc.state() == DataChannel.State.OPEN) {
                        // send handshake
                        val handshake = JSONObject().apply {
                            put("type", "handshake")
                            put("name", profileManager.getName())
                        }
                        sendDataChannelPayload(handshake.toString())
                    }
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = buffer.data
                val bytes = ByteArray(data.remaining())
                data.get(bytes)
                val text = String(bytes, StandardCharsets.UTF_8)
                mainHandler.post {
                    handleDataChannelMessage(text)
                }
            }
        })
    }

    private fun handleDataChannelMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "handshake" -> {
                    val name = json.optString("name", "Peer")
                    targetPeerName = name
                    _processText.value = "Hubungan Aman Tersambung dengan $name"
                }
                "msg" -> {
                    val content = json.optString("content")
                    val senderName = json.optString("name", "Peer")
                    val time = json.optLong("sentAt", System.currentTimeMillis())
                    val msgId = json.optString("msgId")
                    
                    val replyTo = json.optJSONObject("replyTo")
                    val replyToId = replyTo?.optString("id")
                    val replyToName = replyTo?.optString("name")
                    val replyToContent = replyTo?.optString("content")

                    // Send Ack back
                    val ack = JSONObject().apply {
                        put("type", "msg-ack")
                        put("msgId", msgId)
                        put("sentAt", time)
                    }
                    sendDataChannelPayload(ack.toString())

                    scope.launch {
                        _eventFlow.emit(
                            RtcEvent.MessageReceived(
                                id = msgId,
                                text = content,
                                fromName = senderName,
                                sentAt = time,
                                replyToId = replyToId,
                                replyToName = replyToName,
                                replyToContent = replyToContent
                            )
                        )
                    }
                }
                "msg-ack" -> {
                    val sentAt = json.optLong("sentAt")
                    val delay = System.currentTimeMillis() - sentAt
                    scope.launch {
                        _eventFlow.emit(RtcEvent.LatencyMeasured(delay))
                    }
                }
                "typing" -> {
                    val isTyping = json.optBoolean("typing")
                    scope.launch {
                        _eventFlow.emit(RtcEvent.TypingStateChanged(isTyping))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "DataChannel parse message error", e)
        }
    }

    fun sendMessage(text: String, replyToId: String? = null, replyToName: String? = null, replyToContent: String? = null): String {
        val msgId = "${System.currentTimeMillis()}-${(1000..9999).random()}"
        val payload = JSONObject().apply {
            put("type", "msg")
            put("content", text)
            put("name", profileManager.getName())
            put("msgId", msgId)
            put("sentAt", System.currentTimeMillis())
            
            if (replyToId != null) {
                val rObj = JSONObject().apply {
                    put("id", replyToId)
                    put("name", replyToName)
                    put("content", replyToContent)
                }
                put("replyTo", rObj)
            }
        }

        sendDataChannelPayload(payload.toString())
        return msgId
    }

    fun sendTyping(isTyping: Boolean) {
        val payload = JSONObject().apply {
            put("type", "typing")
            put("typing", isTyping)
        }
        sendDataChannelPayload(payload.toString())
    }

    private fun sendDataChannelPayload(text: String) {
        val channel = dataChannel
        if (channel != null && channel.state() == DataChannel.State.OPEN) {
            val buffer = ByteBuffer.wrap(text.toByteArray(StandardCharsets.UTF_8))
            channel.send(DataChannel.Buffer(buffer, false))
        } else {
            Log.e(TAG, "Data channel not open. Cannot send: $text")
        }
    }

    fun closeP2P() {
        mainHandler.post {
            dataChannel?.close()
            dataChannel = null
            peerConnection?.close()
            peerConnection = null
            targetPeerHash = null
            targetPeerName = null
            pendingCandidates.clear()
            updateState(RtcState.OFFLINE)
            _processText.value = "Idle"
            scope.launch {
                _eventFlow.emit(RtcEvent.P2PDisconnected)
            }
        }
    }

    private fun updateState(state: RtcState) {
        _connectionState.value = state
    }

    private fun emitError(msg: String) {
        scope.launch {
            _eventFlow.emit(RtcEvent.ConnectError(msg))
        }
    }

    fun getTargetPeerHash(): String? = targetPeerHash
    fun getTargetPeerName(): String? = targetPeerName
}
