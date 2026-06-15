package com.privatesms.ui.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.privatesms.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val currentTheme by viewModel.theme.collectAsStateWithLifecycle()
    val accentColorIndex by viewModel.accentColor.collectAsStateWithLifecycle()
    val fontSizeSetting by viewModel.fontSize.collectAsStateWithLifecycle()
    val screenshotPrevention by viewModel.screenshotPrevention.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val autoLockTimeout by viewModel.autoLockTimeout.collectAsStateWithLifecycle()
    val biometricEnabled by viewModel.biometricEnabled.collectAsStateWithLifecycle()
    val spamKeywords by viewModel.spamKeywords.collectAsStateWithLifecycle()

    var newKeywordInput by remember { mutableStateOf("") }

    // Backup import file picker
    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val success = viewModel.backupRestoreUseCase.importBackup(inputStream)
                        if (success) {
                            Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Failed to parse backup JSON file.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Error importing file.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            // 1. Default SMS App Check
            val isDefault = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
                    roleManager.isRoleHeld(RoleManager.ROLE_SMS)
                } else {
                    @Suppress("DEPRECATION")
                    Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
                }
            }
            
            if (!isDefault) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Not Set as Default SMS App",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "To send and receive encrypted messages offline, set Private SMS as your default handler.",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
                                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                                context.startActivity(intent)
                            } else {
                                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                                    putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        }) {
                            Text("Make Default App")
                        }
                    }
                }
            }

            // 2. Custom M3 Theme & Dynamic Accents
            Column {
                Text("App Customization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                
                // Theme picker
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system", "light", "dark").forEach { themeMode ->
                        FilterChip(
                            selected = currentTheme == themeMode,
                            onClick = { viewModel.updateTheme(themeMode) },
                            label = { Text(themeMode.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Select Accent Color Palette", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))

                // Presets accent colors row
                val presets = listOf(
                    BluePrimary to "Blue",
                    GreenPrimary to "Green",
                    PurplePrimary to "Purple",
                    OrangePrimary to "Orange",
                    RedPrimary to "Red",
                    TealPrimary to "Teal"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(presets.indices.toList()) { index ->
                        val color = presets[index].first
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (accentColorIndex == index) 3.dp else 0.dp,
                                    color = if (accentColorIndex == index) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.updateAccentColor(index) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("App Font Size", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("small", "medium", "large").forEach { size ->
                        FilterChip(
                            selected = fontSizeSetting == size,
                            onClick = { viewModel.updateFontSize(size) },
                            label = { Text(size.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }

            Divider()

            // 3. Security Settings
            Column {
                Text("Privacy & Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("App PIN Lock Screen")
                    Switch(
                        checked = appLockEnabled,
                        onCheckedChange = { viewModel.updateAppLockEnabled(it) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Biometric Authentication")
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { viewModel.updateBiometricEnabled(it) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Block Screenshots / Incognito Keyboard")
                    Switch(
                        checked = screenshotPrevention,
                        onCheckedChange = { viewModel.updateScreenshotPrevention(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.lockApp() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Lock Database Immediately")
                }
            }

            Divider()

            // 4. Keyword Spam Filter Config
            Column {
                Text("Keyword Filter (Auto-Move to Spam)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newKeywordInput,
                        onValueChange = { newKeywordInput = it },
                        label = { Text("Spam Keyword") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        if (newKeywordInput.isNotBlank()) {
                            viewModel.addSpamKeyword(newKeywordInput)
                            newKeywordInput = ""
                        }
                    }) {
                        Text("Add")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                // Keywords row
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    spamKeywords.forEach { keyword ->
                        AssistChip(
                            onClick = {},
                            label = { Text(keyword) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Delete",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { viewModel.removeSpamKeyword(keyword) }
                                )
                            }
                        )
                    }
                }
            }

            Divider()

            // 5. Backups & Restore
            Column {
                Text("Backup & Local Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "All backups are fully offline and stored locally inside your device's /Downloads/PrivateSMS folder.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = {
                        coroutineScope.launch {
                            val path = viewModel.backupRestoreUseCase.exportBackup()
                            if (path != null) {
                                Toast.makeText(context, "Backup saved to: $path", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Error exporting backup.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Text("Export Backup JSON")
                    }

                    Button(onClick = {
                        importBackupLauncher.launch("*/*")
                    }) {
                        Text("Import JSON File")
                    }
                }
            }

            Divider()

            // 6. About Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("SmsX App", fontWeight = FontWeight.Bold)
                Text("Version 1.0 (Fully Offline)")
                Text("AES-256 Room + SQLCipher Encryption", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable () -> Unit
) {
    // Simple custom layout wrap for FlowRow to run safely across compose versions
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        var rowWidth = 0
        var rowHeight = 0
        var totalHeight = 0
        val space = 8.dp.roundToPx()

        layout(constraints.maxWidth, constraints.maxHeight) {
            var x = 0
            var y = 0
            placeables.forEach { placeable ->
                if (x + placeable.width > constraints.maxWidth) {
                    x = 0
                    y += rowHeight + space
                    rowHeight = 0
                }
                placeable.placeRelative(x, y)
                x += placeable.width + space
                rowHeight = maxOf(rowHeight, placeable.height)
            }
        }
    }
}
