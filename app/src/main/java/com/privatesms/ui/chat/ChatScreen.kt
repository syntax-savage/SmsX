package com.privatesms.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.LocalTextStyle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.privatesms.data.model.MessageEntity
import com.privatesms.domain.model.Message
import com.privatesms.ui.navigation.Screen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    threadId: Long,
    address: String,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    
    // Bottom Sheet states for Emoji & Scheduling
    var showEmojiDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showMessageMenuDialog by remember { mutableStateOf<Message?>(null) }

    // Date/Time picker states for scheduling
    var scheduleDate by remember { mutableStateOf(Calendar.getInstance()) }
    var scheduleYear by remember { mutableStateOf(scheduleDate.get(Calendar.YEAR)) }
    var scheduleMonth by remember { mutableStateOf(scheduleDate.get(Calendar.MONTH)) }
    var scheduleDay by remember { mutableStateOf(scheduleDate.get(Calendar.DAY_OF_MONTH)) }
    var scheduleHour by remember { mutableStateOf(scheduleDate.get(Calendar.HOUR_OF_DAY)) }
    var scheduleMinute by remember { mutableStateOf(scheduleDate.get(Calendar.MINUTE)) }
    
    // Attachment picker state
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri = uri
    }

    LaunchedEffect(threadId, address) {
        viewModel.loadMessages(threadId, address)
    }

    val counterString = remember(messageText) {
        val len = messageText.length
        val parts = if (len <= 160) 1 else (len + 152) / 153
        "$len / $parts"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            val initials = address.take(2).uppercase()
                            Text(initials, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(address, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                            Text("Offline encrypted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    var isBlocked = false
                    if (uiState is ChatUiState.Success) {
                        isBlocked = (uiState as ChatUiState.Success).isBlocked
                    }
                    
                    IconButton(onClick = {
                        if (isBlocked) {
                            viewModel.unblockNumber(address)
                            Toast.makeText(context, "$address unblocked", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.blockNumber(address, "Blocked from chat screen toolbar")
                            Toast.makeText(context, "$address blocked", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            imageVector = if (isBlocked) Icons.Default.Block else Icons.Default.CheckCircle,
                            contentDescription = "Block / Unblock",
                            tint = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is ChatUiState.Loading -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ChatUiState.Error -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
                is ChatUiState.Success -> {
                    // Scroll to bottom on load
                    LaunchedEffect(state.messages.size) {
                        if (state.messages.isNotEmpty()) {
                            listState.animateScrollToItem(state.messages.size - 1)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                onLongClick = { showMessageMenuDialog = message }
                            )
                        }
                    }
                }
            }

            // Image attachment preview if selected
            if (selectedImageUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Attachment",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Attachment ready", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { selectedImageUri = null }) {
                        Icon(Icons.Default.Delete, "Remove Image")
                    }
                }
            }

            // Modern, Professional Input Panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Pill Container for attachments + text field
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Icon(
                            imageVector = Icons.Default.AttachFile, 
                            contentDescription = "Attach image",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    IconButton(onClick = { showEmojiDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.SentimentSatisfiedAlt, 
                            contentDescription = "Emojis",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    IconButton(onClick = { showScheduleDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Schedule, 
                            contentDescription = "Schedule",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    BasicTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        textStyle = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        ),
                        decorationBox = { innerTextField ->
                            Box {
                                if (messageText.isEmpty()) {
                                    Text(
                                        text = "Encrypted message", 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontSize = 16.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    
                    if (messageText.isNotEmpty()) {
                        Text(
                            text = counterString,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Circular Send Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (messageText.isNotBlank()) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .clickable(enabled = messageText.isNotBlank()) {
                            viewModel.sendMessage(address, messageText)
                            messageText = ""
                            selectedImageUri = null
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (messageText.isNotBlank()) MaterialTheme.colorScheme.onPrimary 
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    // Emoji Dialog
    if (showEmojiDialog) {
        Dialog(onDismissRequest = { showEmojiDialog = false }) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Emoji", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                    val emojis = listOf("😀", "😂", "🥰", "👍", "🙏", "🔥", "🎉", "❤️", "🤔", "👏", "💩", "👀")
                    Row(
                        modifier = Modifier.width(280.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        emojis.forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 28.sp,
                                modifier = Modifier
                                    .clickable {
                                        messageText += emoji
                                        showEmojiDialog = false
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Date/Time Schedule Dialog
    if (showScheduleDialog) {
        Dialog(onDismissRequest = { showScheduleDialog = false }) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Schedule SMS", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))

                    Text("Wait - Pick execution delay in minutes:")
                    var delayMinutes by remember { mutableStateOf(5) }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (delayMinutes > 1) delayMinutes-- }) {
                            Icon(Icons.Default.Remove, "Less")
                        }
                        Text("$delayMinutes minutes", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                        IconButton(onClick = { delayMinutes++ }) {
                            Icon(Icons.Default.Add, "More")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showScheduleDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(onClick = {
                            val futureCal = Calendar.getInstance().apply {
                                add(Calendar.MINUTE, delayMinutes)
                            }
                            viewModel.scheduleMessage(address, messageText, futureCal.timeInMillis)
                            messageText = ""
                            showScheduleDialog = false
                            Toast.makeText(context, "Scheduled in $delayMinutes minutes", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Schedule")
                        }
                    }
                }
            }
        }
    }

    // Message Detail Context Menu Dialog
    showMessageMenuDialog?.let { message ->
        Dialog(onDismissRequest = { showMessageMenuDialog = null }) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .width(260.dp)
                ) {
                    Text("Message Options", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                    
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("SMS text", message.body)
                            clipboard.setPrimaryClip(clip)
                            showMessageMenuDialog = null
                            Toast.makeText(context, "Text copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy Text", textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    }
                    
                    TextButton(
                        onClick = {
                            viewModel.deleteMessage(message.id)
                            showMessageMenuDialog = null
                            Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete Message", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    }

                    TextButton(
                        onClick = {
                            // Forward - Navigate to compose with prefilled text
                            showMessageMenuDialog = null
                            navController.navigate(Screen.Compose.route + "?forward_body=${message.body}")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Forward message", textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    onLongClick: () -> Unit
) {
    val isSent = message.type == MessageEntity.TYPE_SENT
    val bubbleColor = if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (isSent) Alignment.CenterHorizontally else Alignment.CenterHorizontally

    val timeString = remember(message.date) {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        sdf.format(Date(message.date))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        contentAlignment = if (isSent) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(horizontalAlignment = if (isSent) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isSent) 16.dp else 4.dp,
                            bottomEnd = if (isSent) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (isSent) {
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    )
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongClick
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    if (message.attachmentPath != null) {
                        AsyncImage(
                            model = message.attachmentPath,
                            contentDescription = "Attached Image",
                            modifier = Modifier
                                .width(220.dp)
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .padding(bottom = 6.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = message.body, 
                            color = textColor, 
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = timeString,
                                fontSize = 10.sp,
                                color = textColor.copy(alpha = 0.7f)
                            )
                            if (isSent) {
                                Spacer(modifier = Modifier.width(3.dp))
                                val statusIcon = when (message.status) {
                                    MessageEntity.STATUS_DELIVERED -> "✓✓"
                                    MessageEntity.STATUS_SENT -> "✓"
                                    MessageEntity.STATUS_FAILED -> "!"
                                    else -> "✓"
                                }
                                Text(
                                    text = statusIcon,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (message.status == MessageEntity.STATUS_DELIVERED) Color(0xFF00C853) 
                                            else textColor.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
