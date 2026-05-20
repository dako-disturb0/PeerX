package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Contact
import com.example.data.model.Message
import com.example.data.webrtc.RtcState
import com.example.ui.AppScreen
import com.example.ui.ChatViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ChatViewModel = viewModel()
            val themeState by viewModel.currentTheme.collectAsState()
            
            val isDark = when (themeState) {
                "light" -> false
                "dark" -> true
                else -> true
            }

            MyApplicationTheme(darkTheme = isDark, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppNavigation(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppNavigation(viewModel: ChatViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val incomingRequest by viewModel.incomingRequest.collectAsState()

    // Back handlers
    BackHandler(enabled = currentScreen != AppScreen.CONTACTS && currentScreen != AppScreen.REGISTER) {
        when (currentScreen) {
            AppScreen.CHAT -> viewModel.disconnectChatAndGoBack()
            AppScreen.SETTINGS -> viewModel.navigateTo(AppScreen.CONTACTS)
            else -> {}
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.REGISTER -> RegisterScreen(viewModel)
                AppScreen.CONTACTS -> ContactsScreen(viewModel)
                AppScreen.CHAT -> ChatScreen(viewModel)
                AppScreen.SETTINGS -> SettingsScreen(viewModel)
            }

            // Incoming connection modal
            incomingRequest?.let { req ->
                IncomingCallDialog(
                    peerHash = req.first,
                    peerName = req.second,
                    onAccept = { viewModel.acceptCall() },
                    onReject = { viewModel.rejectCall() }
                )
            }
        }
    }
}

@Composable
fun RegisterScreen(viewModel: ChatViewModel) {
    var nameInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(64.dp)
                        .padding(bottom = 8.dp)
                )

                Text(
                    text = "PeerX",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "P2P Messenger aman berbasis WebRTC tanpa server penyimpanan pesan.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= 32) nameInput = it },
                    label = { Text("NAMA ANDA") },
                    placeholder = { Text("Masukkan nama panggilan...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        viewModel.registerLocalAccount(nameInput)
                    })
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.registerLocalAccount(nameInput)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buat Akun Lokal", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(viewModel: ChatViewModel) {
    var targetHash by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val contactsList by viewModel.contactsList.collectAsState()
    val rtcState by viewModel.rtcState.collectAsState()
    val processText by viewModel.processText.collectAsState()

    var showQrDialog by remember { mutableStateOf(false) }
    var showScanDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Custom Top Bar with Status Header
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "PeerX P2P",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (rtcState == RtcState.SIGNAL_ONLINE) "Signal Online" else "Offline / Menghubungkan...",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (rtcState == RtcState.SIGNAL_ONLINE) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }
            },
            actions = {
                IconButton(onClick = { viewModel.navigateTo(AppScreen.SETTINGS) }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Pengaturan",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // Sub bar / process tracker
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (rtcState == RtcState.SIGNAL_ONLINE) Color(0xFF4CAF50) else Color(
                                    0xFFF44336
                                )
                            )
                    )
                    Text(
                        text = if (rtcState == RtcState.SIGNAL_ONLINE) "Server Online" else "Menghubungkan...",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = "${contactsList.size} kontak",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // My Own Identity Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = viewModel.getMyProfileName().take(1).uppercase(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            Column {
                                Text(
                                    text = viewModel.getMyProfileName(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Username Lokal Anda",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "KODE HASH P2P",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = viewModel.getMyProfileHash(),
                                    fontSize = 18.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(viewModel.getMyProfileHash()))
                                    Toast.makeText(context, "Kode Hash dicopy!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "Copy Hash Code",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Connection field (Pair via Hash)
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Sambung dengan Teman Baru",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = targetHash,
                            onValueChange = {
                                val clean = it.uppercase().replace("[^A-Z0-9]".toRegex(), "")
                                if (clean.length <= 8) targetHash = clean
                            },
                            placeholder = { Text("KODE HASH TEMAN") },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(onAny = {
                                focusManager.clearFocus()
                                viewModel.searchAndConnect(targetHash)
                            })
                        )

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.searchAndConnect(targetHash)
                            },
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Hubungkan")
                        }
                    }

                    // Bottom Row Quick Access
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { showQrDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("QR Code Saya", fontSize = 12.sp)
                        }

                        FilledTonalButton(
                            onClick = { showScanDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan QR Teman", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Connection processing banner or progress text
            if (processText != "Idle") {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = processText,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Contact list section
            item {
                Text(
                    text = "Riwayat Kontak P2P",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (contactsList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada kontak terdaftar.\nMintalah kode hash teman Anda untuk memulai sambungan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(contactsList) { contact ->
                    ContactRow(
                        contact = contact,
                        onConnect = { viewModel.startConnectingFromList(contact) },
                        onBlock = { viewModel.blockContact(contact.hash) },
                        onDelete = { viewModel.deleteContact(contact.hash) }
                    )
                }
            }
        }
    }

    // QR Show Dialog
    if (showQrDialog) {
        Dialog(onDismissRequest = { showQrDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Kode QR P2P Anda",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Draw a gorgeous pseudo-QR matrix utilizing custom Canvas! This is clean and matches high-tech look.
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val hash = viewModel.getMyProfileHash()
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val seed = hash.hashCode()
                            val random = java.util.Random(seed.toLong())
                            val gridSize = 16
                            val cellSize = size.width / gridSize
                            
                            // Draw 3 corner positioning blocks like native QR
                            val markerPositions = listOf(
                                Offset(0f, 0f),
                                Offset((gridSize - 5) * cellSize, 0f),
                                Offset(0f, (gridSize - 5) * cellSize)
                            )
                            
                            // Fill background
                            drawRect(color = Color.White)
                            
                            // Draw the markers
                            for (pos in markerPositions) {
                                drawRect(
                                    color = Color.Black,
                                    topLeft = pos,
                                    size = Size(5 * cellSize, 5 * cellSize)
                                )
                                drawRect(
                                    color = Color.White,
                                    topLeft = Offset(pos.x + cellSize, pos.y + cellSize),
                                    size = Size(3 * cellSize, 3 * cellSize)
                                )
                                drawRect(
                                    color = Color.Black,
                                    topLeft = Offset(pos.x + 2 * cellSize, pos.y + 2 * cellSize),
                                    size = Size(cellSize, cellSize)
                                )
                            }
                            
                            // Let's seed random dark dots across the remaining grid spaces
                            for (x in 0 until gridSize) {
                                for (y in 0 until gridSize) {
                                    // Skip marker boundaries
                                    if ((x < 5 && y < 5) || (x >= gridSize - 5 && y < 5) || (x < 5 && y >= gridSize - 5)) {
                                        continue
                                    }
                                    if (random.nextBoolean()) {
                                        drawRect(
                                            color = Color.Black,
                                            topLeft = Offset(x * cellSize, y * cellSize),
                                            size = Size(cellSize, cellSize)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = viewModel.getMyProfileHash(),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Minta teman Anda menginput kode hash di atas untuk terhubung langsung.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { showQrDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tutup")
                    }
                }
            }
        }
    }

    // Manual Simulate Scan Dialog (since actual camera parsing needs live WebRTC + ZXing stream, let's provide a sleek quick-add helper!)
    if (showScanDialog) {
        var scanInput by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showScanDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Input Hash Teman",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Kamera tidak tersedia di workspace ini. Sila masukkan kode Hash teman Anda di bawah untuk mensimulasikan pemindaian instan.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = scanInput,
                        onValueChange = { scanInput = it.uppercase().replace("[^A-Z0-9]".toRegex(), "") },
                        placeholder = { Text("MISAL: F8K2A9B") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showScanDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Batal")
                        }
                        Button(
                            onClick = {
                                showScanDialog = false
                                if (scanInput.isNotEmpty()) {
                                    targetHash = scanInput
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Gunakan")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactRow(
    contact: Contact,
    onConnect: () -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConnect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.name.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 18.sp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = contact.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (contact.isBlocked) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "[DIBLOKIR]",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Text(
                        text = contact.hash,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onConnect) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Sambung",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onBlock) {
                    Icon(
                        imageVector = if (contact.isBlocked) Icons.Default.Check else Icons.Outlined.Lock,
                        contentDescription = if (contact.isBlocked) "Buka Blokir" else "Blokir",
                        tint = if (contact.isBlocked) Color.Gray else MaterialTheme.colorScheme.error
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus Kontak",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val activeMessages by viewModel.currentMessages.collectAsState()
    val rtcState by viewModel.rtcState.collectAsState()
    val processText by viewModel.processText.collectAsState()

    val peerHash by viewModel.peerHash.collectAsState()
    val peerName by viewModel.peerName.collectAsState()
    val isTyping by viewModel.isPeerTyping.collectAsState()
    val replyTarget by viewModel.replyTarget.collectAsState()
    val latencyMs by viewModel.latencyMs.collectAsState()

    var messageInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Scroll automatically when messages size changes
    LaunchedEffect(activeMessages.size) {
        if (activeMessages.isNotEmpty()) {
            listState.animateScrollToItem(activeMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Chat Header Top Bar
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { viewModel.disconnectChatAndGoBack() }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali")
                }
            },
            title = {
                Column {
                    Text(
                        text = peerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "P2P Hash: $peerHash",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            actions = {
                IconButton(onClick = { viewModel.clearChatHistory() }) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Bersihkan Chat")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // WebRTC Connection Info strip
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (rtcState) {
                                    RtcState.P2P_CONNECTED -> Color(0xFF4CAF50)
                                    RtcState.P2P_CONNECTING, RtcState.PAIRING -> Color(0xFFFF9800)
                                    else -> Color(0xFFF44336)
                                }
                            )
                    )
                    Text(
                        text = when (rtcState) {
                            RtcState.P2P_CONNECTED -> "P2P Secure"
                            RtcState.P2P_CONNECTING -> "Menghubungkan..."
                            RtcState.PAIRING -> "Pairing..."
                            RtcState.LOOKING_UP -> "Memanggil Peer..."
                            else -> "Koneksi Terputus"
                        },
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (latencyMs != null) "$latencyMs ms" else "-- ms",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = processText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Messages Box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(activeMessages) { message ->
                    MessageBubble(
                        message = message,
                        onLongClick = { viewModel.setReplyMessage(message) },
                        viewModel = viewModel
                    )
                }
            }

            // Typing Indicator
            if (isTyping) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 10.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$peerName sedang mengetik",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Reply context banner
        replyTarget?.let { reply ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Membalas ${reply.senderName}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = reply.text,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = { viewModel.cancelReply() }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Batal", modifier = Modifier.size(18.dp))
                }
            }
        }

        // Input bottom app bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = {
                        messageInput = it
                        viewModel.broadcastTypingState(it.isNotEmpty())
                    },
                    placeholder = { Text("Ketik pesan P2P...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    enabled = rtcState == RtcState.P2P_CONNECTED
                )

                IconButton(
                    onClick = {
                        if (messageInput.trim().isNotEmpty()) {
                            viewModel.sendTextMessage(messageInput)
                            messageInput = ""
                        }
                    },
                    enabled = rtcState == RtcState.P2P_CONNECTED && messageInput.trim().isNotEmpty(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (rtcState == RtcState.P2P_CONNECTED && messageInput.trim().isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Kirim",
                        tint = if (rtcState == RtcState.P2P_CONNECTED && messageInput.trim().isNotEmpty()) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    onLongClick: () -> Unit,
    viewModel: ChatViewModel
) {
    if (message.isSystem) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = message.content,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val bubbleColor = if (message.fromMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (message.fromMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val alignment = if (message.fromMe) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            // Context header for sender (only on incoming message)
            if (!message.fromMe) {
                Text(
                    text = message.fromName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }

            Surface(
                color = bubbleColor,
                shape = if (message.fromMe) {
                    RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                } else {
                    RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                },
                modifier = Modifier.combinedClickable(
                    onLongClick = onLongClick,
                    onClick = {}
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Replied references box inside bubble
                    if (message.replyToId != null) {
                        Surface(
                            color = if (message.fromMe) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.3f)
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Text(
                                    text = message.replyToName ?: "Peer",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (message.fromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = message.replyToContent ?: "",
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (message.fromMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Text(
                        text = message.content,
                        fontSize = 15.sp,
                        color = textColor
                    )
                }
            }

            Text(
                text = viewModel.formatTime(message.timestamp),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ChatViewModel) {
    val currentTheme by viewModel.currentTheme.collectAsState()
    val usernameInput by viewModel.usernameInput.collectAsState()
    val hideInfoToggle by viewModel.hideInfoToggle.collectAsState()
    val context = LocalContext.current

    var editUsername by remember { mutableStateOf(usernameInput) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { viewModel.navigateTo(AppScreen.CONTACTS) }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Kembali")
                }
            },
            title = { Text("Pengaturan", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Profil Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Profil Anda",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { if (it.length <= 32) editUsername = it },
                        label = { Text("USERNAME") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = { viewModel.saveSettingsUsername(editUsername) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simpan Username")
                    }
                }
            }

            // Connection Hash Code Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Kode Hash P2P",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewModel.getMyProfileHash(),
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.regeneratePrivateHash() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Hubungkan Ulang & Generate Baru")
                    }
                }
            }

            // Theme Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Tampilan Tema",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.changeTheme("dark") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = if (currentTheme == "dark") {
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            } else ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Text("Gelap")
                        }

                        FilledTonalButton(
                            onClick = { viewModel.changeTheme("light") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = if (currentTheme == "light") {
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            } else ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Text("Terang")
                        }
                    }
                }
            }

            // Account settings Delete
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Keluar & Reset Akun",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.error
                    )

                    Button(
                        onClick = { viewModel.resetWholeAccount() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Seluruh Akun Lokal")
                    }
                }
            }
        }
    }
}

@Composable
fun IncomingCallDialog(
    peerHash: String,
    peerName: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Dialog(onDismissRequest = onReject) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )

                Text(
                    text = "Permintaan Koneksi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = peerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = peerHash,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Ingin terhubung aman dengan Anda menggunakan WebRTC.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text("Tolak")
                    }

                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Terima")
                    }
                }
            }
        }
    }
}
