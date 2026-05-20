package com.example.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ProfileManager
import com.example.data.database.AppDatabase
import com.example.data.model.Contact
import com.example.data.model.Message
import com.example.data.repository.ChatRepository
import com.example.data.webrtc.RtcEvent
import com.example.data.webrtc.RtcState
import com.example.data.webrtc.WebRtcManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen {
    REGISTER,
    CONTACTS,
    CHAT,
    SETTINGS
}

data class ReplyContext(
    val msgId: String,
    val senderName: String,
    val text: String
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val profileManager = ProfileManager(application)
    private val database = AppDatabase.getDatabase(application)
    private val repository = ChatRepository(database.contactDao(), database.messageDao())
    val webRtcManager = WebRtcManager(application, profileManager, viewModelScope)

    // Routing
    private val _currentScreen = MutableStateFlow(AppScreen.REGISTER)
    val currentScreen: StateFlow<AppScreen> = _currentScreen

    // Database state flows
    val contactsList: StateFlow<List<Contact>> = repository.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentMessages = MutableStateFlow<List<Message>>(emptyList())
    val currentMessages: StateFlow<List<Message>> = _currentMessages

    // WebRTC connection state wrapped flows
    val rtcState: StateFlow<RtcState> = webRtcManager.connectionState
    val processText: StateFlow<String> = webRtcManager.processText

    // UI Interactive States
    private val _peerName = MutableStateFlow("")
    val peerName: StateFlow<String> = _peerName

    private val _peerHash = MutableStateFlow("")
    val peerHash: StateFlow<String> = _peerHash

    private val _incomingRequest = MutableStateFlow<Pair<String, String>?>(null) // Pair(hash, name)
    val incomingRequest: StateFlow<Pair<String, String>?> = _incomingRequest

    private val _latencyMs = MutableStateFlow<Long?>(null)
    val latencyMs: StateFlow<Long?> = _latencyMs

    private val _isPeerTyping = MutableStateFlow(false)
    val isPeerTyping: StateFlow<Boolean> = _isPeerTyping

    private val _replyTarget = MutableStateFlow<ReplyContext?>(null)
    val replyTarget: StateFlow<ReplyContext?> = _replyTarget

    private val _currentTheme = MutableStateFlow(profileManager.getTheme())
    val currentTheme: StateFlow<String> = _currentTheme

    private val _usernameInput = MutableStateFlow(profileManager.getName())
    val usernameInput: StateFlow<String> = _usernameInput

    private val _hideInfoToggle = MutableStateFlow(profileManager.getHideInfo())
    val hideInfoToggle: StateFlow<Boolean> = _hideInfoToggle

    init {
        // Detect if already registered
        if (profileManager.isRegistered()) {
            _currentScreen.value = AppScreen.CONTACTS
            webRtcManager.connectSignaling()
        } else {
            _currentScreen.value = AppScreen.REGISTER
        }

        // Collect WebRTC Events
        viewModelScope.launch {
            webRtcManager.eventFlow.collect { event ->
                handleRtcEvent(event)
            }
        }
    }

    private fun handleRtcEvent(event: RtcEvent) {
        when (event) {
            is RtcEvent.IncomingRequest -> {
                _incomingRequest.value = Pair(event.peerHash, event.peerName)
            }
            is RtcEvent.ConnectError -> {
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(getApplication(), event.error, Toast.LENGTH_LONG).show()
                }
            }
            is RtcEvent.MessageReceived -> {
                _isPeerTyping.value = false
                val activeHash = _peerHash.value
                if (activeHash.isNotEmpty()) {
                    val incomingMsg = Message(
                        id = event.id,
                        peerHash = activeHash,
                        fromMe = false,
                        fromName = event.fromName,
                        content = event.text,
                        timestamp = event.sentAt,
                        isSystem = false,
                        replyToId = event.replyToId,
                        replyToName = event.replyToName,
                        replyToContent = event.replyToContent
                    )
                    viewModelScope.launch {
                        repository.saveMessage(incomingMsg)
                        // Also auto add/refresh contact info
                        repository.saveContact(event.fromName, activeHash)
                    }
                }
            }
            is RtcEvent.TypingStateChanged -> {
                _isPeerTyping.value = event.isTyping
            }
            is RtcEvent.LatencyMeasured -> {
                _latencyMs.value = event.latencyMs
            }
            is RtcEvent.P2PDisconnected -> {
                _latencyMs.value = null
                _isPeerTyping.value = false
                
                // Add system message indicating disconnection
                val activeHash = _peerHash.value
                if (activeHash.isNotEmpty() && _currentScreen.value == AppScreen.CHAT) {
                    val sysMsg = Message(
                        id = "${System.currentTimeMillis()}-sys",
                        peerHash = activeHash,
                        fromMe = false,
                        fromName = "System",
                        content = "Koneksi terputus. Menunggu sambungan ulang...",
                        timestamp = System.currentTimeMillis(),
                        isSystem = true
                    )
                    viewModelScope.launch {
                        repository.saveMessage(sysMsg)
                    }
                }
            }
        }
    }

    // Handlers
    fun registerLocalAccount(username: String) {
        if (username.trim().isEmpty()) {
            Toast.makeText(getApplication(), "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }
        val generatedHash = generateUniqueHash()
        profileManager.register(username.trim(), generatedHash)
        _usernameInput.value = username.trim()
        _currentScreen.value = AppScreen.CONTACTS
        
        webRtcManager.connectSignaling()
    }

    private fun generateUniqueHash(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = java.security.SecureRandom()
        val builder = java.lang.StringBuilder()
        for (i in 0 until 8) {
            builder.append(chars[random.nextInt(chars.length)])
        }
        return builder.toString()
    }

    fun searchAndConnect(targetHash: String) {
        val cleanHash = targetHash.trim().uppercase().replace("[^A-Z0-9]".toRegex(), "")
        if (cleanHash.length < 6) {
            Toast.makeText(getApplication(), "Format hash tidak valid", Toast.LENGTH_SHORT).show()
            return
        }
        if (cleanHash == profileManager.getHash()) {
            Toast.makeText(getApplication(), "Tidak bisa menghubungi diri sendiri", Toast.LENGTH_SHORT).show()
            return
        }

        _peerHash.value = cleanHash
        _peerName.value = cleanHash // Temp until handshake
        
        _currentScreen.value = AppScreen.CHAT
        observeChatHistory(cleanHash)

        webRtcManager.lookupPeer(cleanHash)
    }

    fun startConnectingFromList(contact: Contact) {
        val cleanHash = contact.hash
        _peerHash.value = cleanHash
        _peerName.value = contact.name

        _currentScreen.value = AppScreen.CHAT
        observeChatHistory(cleanHash)

        webRtcManager.lookupPeer(cleanHash)
    }

    fun acceptCall() {
        val req = _incomingRequest.value ?: return
        _incomingRequest.value = null

        _peerHash.value = req.first
        _peerName.value = req.second

        _currentScreen.value = AppScreen.CHAT
        observeChatHistory(req.first)

        webRtcManager.acceptIncomingRequest()
    }

    fun rejectCall() {
        _incomingRequest.value = null
        webRtcManager.rejectIncomingRequest()
    }

    fun disconnectChatAndGoBack() {
        webRtcManager.closeP2P()
        _peerHash.value = ""
        _peerName.value = ""
        _latencyMs.value = null
        _isPeerTyping.value = false
        _replyTarget.value = null
        _currentScreen.value = AppScreen.CONTACTS

        // Reconnect signaling to be ready for next pairs
        webRtcManager.connectSignaling()
    }

    fun triggerOffline() {
        webRtcManager.disconnectSignaling()
    }

    fun triggerOnline() {
        webRtcManager.connectSignaling()
    }

    private var chatHistoryJob: kotlinx.coroutines.Job? = null
    
    private fun observeChatHistory(peerHash: String) {
        chatHistoryJob?.cancel()
        chatHistoryJob = viewModelScope.launch {
            repository.getMessagesForPeer(peerHash).collect { list ->
                _currentMessages.value = list
            }
        }
    }

    fun sendTextMessage(content: String) {
        if (content.trim().isEmpty()) return
        val activeHash = _peerHash.value
        if (activeHash.isEmpty()) return

        val reply = _replyTarget.value
        val msgId = webRtcManager.sendMessage(
            text = content,
            replyToId = reply?.msgId,
            replyToName = reply?.senderName,
            replyToContent = reply?.text
        )

        val newMsg = Message(
            id = msgId,
            peerHash = activeHash,
            fromMe = true,
            fromName = profileManager.getName(),
            content = content,
            timestamp = System.currentTimeMillis(),
            isSystem = false,
            replyToId = reply?.msgId,
            replyToName = reply?.senderName,
            replyToContent = reply?.text
        )

        viewModelScope.launch {
            repository.saveMessage(newMsg)
        }

        _replyTarget.value = null
        webRtcManager.sendTyping(false)
    }

    fun broadcastTypingState(isTyping: Boolean) {
        webRtcManager.sendTyping(isTyping)
    }

    fun setReplyMessage(message: Message) {
        _replyTarget.value = ReplyContext(
            msgId = message.id,
            senderName = if (message.fromMe) "Anda" else message.fromName,
            text = message.content.take(80)
        )
    }

    fun cancelReply() {
        _replyTarget.value = null
    }

    fun clearChatHistory() {
        val activeHash = _peerHash.value
        if (activeHash.isNotEmpty()) {
            viewModelScope.launch {
                repository.clearMessages(activeHash)
            }
        }
    }

    fun deleteContact(hash: String) {
        viewModelScope.launch {
            repository.deleteContact(hash)
        }
    }

    fun blockContact(hash: String) {
        viewModelScope.launch {
            repository.blockContact(hash)
        }
    }

    // Settings actions
    fun saveSettingsUsername(newName: String) {
        if (newName.trim().isEmpty()) {
            Toast.makeText(getApplication(), "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
            return
        }
        profileManager.updateName(newName.trim())
        _usernameInput.value = newName.trim()
        Toast.makeText(getApplication(), "Username disimpan", Toast.LENGTH_SHORT).show()
        
        // Refresh signaling if online
        webRtcManager.disconnectSignaling()
        webRtcManager.connectSignaling()
    }

    fun changeTheme(theme: String) {
        profileManager.setTheme(theme)
        _currentTheme.value = theme
    }

    fun toggleHideInfo(hide: Boolean) {
        profileManager.setHideInfo(hide)
        _hideInfoToggle.value = hide
    }

    fun regeneratePrivateHash() {
        val newHash = generateUniqueHash()
        profileManager.updateHash(newHash)
        Toast.makeText(getApplication(), "Hash baru berhasil dibuat", Toast.LENGTH_SHORT).show()
        
        // Re-authenticate signaling
        webRtcManager.disconnectSignaling()
        webRtcManager.connectSignaling()
    }

    fun resetWholeAccount() {
        profileManager.reset()
        viewModelScope.launch {
            database.clearAllTables()
        }
        webRtcManager.closeP2P()
        webRtcManager.disconnectSignaling()
        _usernameInput.value = ""
        _currentScreen.value = AppScreen.REGISTER
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun getMyProfileName() = profileManager.getName()
    fun getMyProfileHash() = profileManager.getHash()

    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
