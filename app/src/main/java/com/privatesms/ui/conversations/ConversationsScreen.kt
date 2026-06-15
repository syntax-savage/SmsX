package com.privatesms.ui.conversations

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.hilt.navigation.compose.hiltViewModel
import com.privatesms.data.settingsDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.privatesms.data.db.CryptoUtils
import com.privatesms.domain.model.Conversation
import com.privatesms.ui.navigation.Screen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Use shared settingsDataStore

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationsScreen(
    navController: NavController,
    isPrivateSpace: Boolean,
    isSpamFolder: Boolean,
    viewModel: ConversationsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val syncProgress by viewModel.smsSync.syncProgress.collectAsStateWithLifecycle()
    val isSyncing by viewModel.smsSync.isSyncing.collectAsStateWithLifecycle()

    var selectedThreads by remember { mutableStateOf(setOf<Long>()) }
    var isMultiSelectMode by remember { mutableStateOf(false) }

    // Dialog state for Private Space PIN validation
    var showPrivateSpacePinDialog by remember { mutableStateOf(false) }
    var privateSpacePinInput by remember { mutableStateOf("") }
    var privateSpaceError by remember { mutableStateOf("") }
    var isPrivateSpaceSetup by remember { mutableStateOf(false) }

    val PREF_PRIVATE_PIN_HASH = stringPreferencesKey("private_pin_hash")

    LaunchedEffect(isPrivateSpace, isSpamFolder) {
        viewModel.loadConversations(
            archived = false,
            isPrivate = isPrivateSpace,
            isSpam = isSpamFolder
        )
    }

    // Permission launcher for SMS and Contacts
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.READ_SMS] == true
        if (smsGranted) {
            viewModel.triggerSync()
        } else {
            Toast.makeText(context, "Permissions are required to sync messages.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        // Automatically request permissions and sync on first launch
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "🔐 SmsX",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Divider()
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Message, "Inbox") },
                    label = { Text("Inbox") },
                    selected = !isPrivateSpace && !isSpamFolder,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        if (isPrivateSpace || isSpamFolder) {
                            navController.navigate(Screen.Conversations.route) {
                                popUpTo(Screen.Conversations.route) { inclusive = true }
                            }
                        }
                    },
                    modifier = Modifier.padding(8.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Lock, "Private Space") },
                    label = { Text("Private Space") },
                    selected = isPrivateSpace,
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            // Validate PIN
                            val prefs = context.settingsDataStore.data.first()
                            val hashExists = prefs[PREF_PRIVATE_PIN_HASH] != null
                            isPrivateSpaceSetup = !hashExists
                            showPrivateSpacePinDialog = true
                        }
                    },
                    modifier = Modifier.padding(8.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Report, "Spam Folder") },
                    label = { Text("Spam & Blocked") },
                    selected = isSpamFolder,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        navController.navigate(Screen.Spam.route)
                    },
                    modifier = Modifier.padding(8.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Block, "Blocked List") },
                    label = { Text("Manage Blocklist") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        navController.navigate(Screen.Blocked.route)
                    },
                    modifier = Modifier.padding(8.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, "Settings") },
                    label = { Text("Settings") },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        navController.navigate(Screen.Settings.route)
                    },
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = {
                        if (isMultiSelectMode) {
                            Text("${selectedThreads.size} selected")
                        } else {
                            Text(
                                if (isPrivateSpace) "🔐 Private Space" 
                                else if (isSpamFolder) "🚫 Spam & Keywords Filter" 
                                else "Messages"
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isMultiSelectMode) {
                                isMultiSelectMode = false
                                selectedThreads = emptySet()
                            } else {
                                coroutineScope.launch { drawerState.open() }
                            }
                        }) {
                            Icon(
                                imageVector = if (isMultiSelectMode) Icons.Default.Close else Icons.Default.Menu,
                                contentDescription = "Menu"
                            )
                        }
                    },
                    actions = {
                        if (isMultiSelectMode) {
                            IconButton(onClick = {
                                viewModel.bulkDelete(selectedThreads.toList())
                                isMultiSelectMode = false
                                selectedThreads = emptySet()
                            }) {
                                Icon(Icons.Default.Delete, "Bulk Delete")
                            }
                            IconButton(onClick = {
                                viewModel.bulkArchive(selectedThreads.toList(), !isSpamFolder)
                                isMultiSelectMode = false
                                selectedThreads = emptySet()
                            }) {
                                Icon(
                                    imageVector = if (isSpamFolder) Icons.Default.Unarchive else Icons.Default.Archive,
                                    contentDescription = "Bulk Archive"
                                )
                            }
                        } else {
                            // Search Action
                            var showSearch by remember { mutableStateOf(false) }
                            if (showSearch) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.updateSearchQuery(it) },
                                    placeholder = { Text("Search messages...") },
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            viewModel.updateSearchQuery("")
                                            showSearch = false
                                        }) {
                                            Icon(Icons.Default.Close, "Close Search")
                                        }
                                    },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    modifier = Modifier.width(200.dp)
                                )
                            } else {
                                IconButton(onClick = { showSearch = true }) {
                                    Icon(Icons.Default.Search, "Search")
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            floatingActionButton = {
                if (!isSpamFolder) {
                    FloatingActionButton(
                        onClick = { navController.navigate(Screen.Compose.route) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Edit, "New Message")
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Historical Sync Progress Banner
                if (isSyncing) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Importing historical SMS & MMS...",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = syncProgress,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                when (val state = uiState) {
                    is ConversationsUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is ConversationsUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    is ConversationsUiState.Success -> {
                        val conversations = state.conversations
                        if (conversations.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    if (isSpamFolder) "No spam messages." 
                                    else if (isPrivateSpace) "Private Space is empty." 
                                    else "No conversations."
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(conversations, key = { it.threadId }) { conversation ->
                                    val isSelected = selectedThreads.contains(conversation.threadId)
                                    ConversationItem(
                                        conversation = conversation,
                                        isSelected = isSelected,
                                        isMultiSelectActive = isMultiSelectMode,
                                        onClick = {
                                            if (isMultiSelectMode) {
                                                selectedThreads = if (isSelected) {
                                                    selectedThreads - conversation.threadId
                                                } else {
                                                    selectedThreads + conversation.threadId
                                                }
                                                if (selectedThreads.isEmpty()) {
                                                    isMultiSelectMode = false
                                                }
                                            } else {
                                                navController.navigate(
                                                    Screen.Chat.createRoute(conversation.threadId, conversation.address)
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            isMultiSelectMode = true
                                            selectedThreads = selectedThreads + conversation.threadId
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog for Private Space Pin Gate
    if (showPrivateSpacePinDialog) {
        Dialog(onDismissRequest = { showPrivateSpacePinDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isPrivateSpaceSetup) "Set Up Private PIN" else "Unlock Private Space",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    TextField(
                        value = privateSpacePinInput,
                        onValueChange = { privateSpacePinInput = it.take(6) },
                        placeholder = { Text("4 to 6 digit PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (privateSpaceError.isNotEmpty()) {
                        Text(
                            text = privateSpaceError,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showPrivateSpacePinDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(onClick = {
                            if (privateSpacePinInput.length < 4) {
                                privateSpaceError = "PIN must be at least 4 digits"
                            } else {
                                coroutineScope.launch {
                                    val salt = CryptoUtils.generateSalt()
                                    val newHash = CryptoUtils.hashPin(privateSpacePinInput, salt)
                                    
                                    if (isPrivateSpaceSetup) {
                                        // Save PIN
                                         context.settingsDataStore.edit {
                                            it[PREF_PRIVATE_PIN_HASH] = "$newHash:$salt"
                                        }
                                        showPrivateSpacePinDialog = false
                                        navController.navigate(Screen.PrivateSpace.route)
                                    } else {
                                         val prefs = context.settingsDataStore.data.first()
                                        val composite = prefs[PREF_PRIVATE_PIN_HASH] ?: ""
                                        val parts = composite.split(":")
                                        if (parts.size == 2) {
                                            val savedHash = parts[0]
                                            val savedSalt = parts[1]
                                            val inputHash = CryptoUtils.hashPin(privateSpacePinInput, savedSalt)
                                            if (inputHash == savedHash) {
                                                showPrivateSpacePinDialog = false
                                                navController.navigate(Screen.PrivateSpace.route)
                                            } else {
                                                privateSpaceError = "Incorrect PIN"
                                            }
                                        }
                                    }
                                }
                            }
                        }) {
                            Text("Submit")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    conversation: Conversation,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateString = remember(conversation.date) {
        val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        sdf.format(Date(conversation.date))
    }

    val initials = remember(conversation.contactName, conversation.address) {
        val name = conversation.contactName ?: conversation.address
        if (name.isNotEmpty()) {
            name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")
        } else {
            "?"
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) 
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection Check or Contact Avatar
            if (isMultiSelectActive && isSelected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            } else {
                // Display Avatar with initials fallback
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                ) {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.contactName ?: conversation.address,
                        fontWeight = if (!conversation.read) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.snippet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(
                            alpha = if (!conversation.read) 0.9f else 0.6f
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (!conversation.read && conversation.messageCount > 0) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = conversation.messageCount.toString(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
