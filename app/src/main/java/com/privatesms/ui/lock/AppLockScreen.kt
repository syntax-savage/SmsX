package com.privatesms.ui.lock

import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.fragment.app.FragmentActivity
import com.privatesms.data.settingsDataStore
import com.privatesms.data.db.CryptoUtils
import com.privatesms.data.db.DatabaseManager
import com.privatesms.data.db.KeyStoreHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Use shared settingsDataStore

@Composable
fun AppLockScreen(
    databaseManager: DatabaseManager,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var isSetupMode by remember { mutableStateOf(false) }
    var setupStep by remember { mutableStateOf(1) } // 1 = enter new, 2 = confirm new
    
    var titleText by remember { mutableStateOf("Enter PIN") }
    var pinInput by remember { mutableStateOf("") }
    var tempPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    
    val maxPinLength = 6
    
    val PREF_PIN_HASH = stringPreferencesKey("pin_hash")
    val PREF_PIN_SALT = stringPreferencesKey("pin_salt")
    val PREF_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    val PREF_ENCRYPTED_PIN = stringPreferencesKey("encrypted_pin")
    
    // Check if PIN is set at startup
    LaunchedEffect(Unit) {
        val prefs = context.settingsDataStore.data.first()
        val hasPin = prefs[PREF_PIN_HASH] != null
        val biometricEnabled = prefs[PREF_BIOMETRIC_ENABLED] ?: false
        
        if (!hasPin) {
            isSetupMode = true
            titleText = "Create a Secure PIN\n(4 to 6 digits)"
        } else {
            titleText = "Enter PIN to Unlock"
            if (biometricEnabled) {
                showBiometricPrompt(context as FragmentActivity, databaseManager, onUnlocked) { err ->
                    errorMessage = err
                }
            }
        }
    }
    
    val onKeyPress: (String) -> Unit = { digit ->
        errorMessage = ""
        if (pinInput.length < maxPinLength) {
            pinInput += digit
            
            // Check if user completed typing PIN in unlock mode
            if (!isSetupMode && pinInput.length >= 4) {
                // We don't auto-submit unless length matches the set PIN length.
                // In unlock mode, we can submit when they press check mark or when length matches the saved one.
                // We'll let them type and press submit, or auto-unlock if it matches stored key credentials.
            }
        }
    }
    
    val onDeletePress: () -> Unit = {
        if (pinInput.isNotEmpty()) {
            pinInput = pinInput.dropLast(1)
        }
    }
    
    val onSubmitPress: () -> Unit = {
        if (pinInput.length < 4) {
            errorMessage = "PIN must be at least 4 digits"
        } else {
            coroutineScope.launch {
                val prefs = context.settingsDataStore.data.first()
                if (isSetupMode) {
                    if (setupStep == 1) {
                        tempPin = pinInput
                        pinInput = ""
                        setupStep = 2
                        titleText = "Confirm your PIN"
                    } else {
                        if (pinInput == tempPin) {
                            // Save PIN
                            val salt = CryptoUtils.generateSalt()
                            val hash = CryptoUtils.hashPin(pinInput, salt)
                            val encPin = KeyStoreHelper.encrypt(pinInput)
                            
                            context.settingsDataStore.edit {
                                it[PREF_PIN_HASH] = hash
                                it[PREF_PIN_SALT] = salt
                                it[PREF_ENCRYPTED_PIN] = encPin
                            }
                            
                            // Initialize & rekey DB
                            val success = databaseManager.setupInitialPin(pinInput)
                            if (success) {
                                onUnlocked()
                            } else {
                                errorMessage = "Database encryption error"
                            }
                        } else {
                            errorMessage = "PINs do not match. Try again."
                            pinInput = ""
                            setupStep = 1
                            titleText = "Create a Secure PIN\n(4 to 6 digits)"
                        }
                    }
                } else {
                    // Unlock mode
                    val savedHash = prefs[PREF_PIN_HASH]
                    val savedSalt = prefs[PREF_PIN_SALT]
                    if (savedHash != null && savedSalt != null) {
                        val inputHash = CryptoUtils.hashPin(pinInput, savedSalt)
                        if (inputHash == savedHash) {
                            // Correct PIN
                            val unlocked = databaseManager.unlock(pinInput)
                            if (unlocked) {
                                onUnlocked()
                            } else {
                                errorMessage = "Failed to unlock database"
                            }
                        } else {
                            errorMessage = "Incorrect PIN"
                            pinInput = ""
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "SmsX",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = titleText,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            // Dot Indicators
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                for (i in 0 until maxPinLength) {
                    val isSelected = i < pinInput.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                            )
                    )
                }
            }
            
            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            // Custom Numeric Keyboard
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val buttonModifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9")
                )
                
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { digit ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = buttonModifier.clickable { onKeyPress(digit) }
                            ) {
                                Text(
                                    text = digit,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                // Bottom row: Biometric/Cancel, 0, Backspace/Submit
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Biometric Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                if (!isSetupMode) MaterialTheme.colorScheme.secondaryContainer 
                                else Color.Transparent
                            )
                            .clickable(enabled = !isSetupMode) {
                                if (!isSetupMode) {
                                    showBiometricPrompt(
                                        context as FragmentActivity,
                                        databaseManager,
                                        onUnlocked
                                    ) { err ->
                                        errorMessage = err
                                    }
                                }
                            }
                    ) {
                        if (!isSetupMode) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric Lock",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    
                    // "0" Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = buttonModifier.clickable { onKeyPress("0") }
                    ) {
                        Text(
                            text = "0",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Backspace/Delete Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = buttonModifier.clickable { onDeletePress() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onSubmitPress,
                    modifier = Modifier.width(180.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(text = if (isSetupMode && setupStep == 1) "Next" else if (isSetupMode) "Confirm" else "Unlock")
                }
            }
        }
    }
}

private fun showBiometricPrompt(
    activity: FragmentActivity,
    databaseManager: DatabaseManager,
    onUnlocked: () -> Unit,
    onError: (String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            // Error code 10 = user canceled (e.g. back press), don't show toast
            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                onError(errString.toString())
            }
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            // Biometric succeeded, unlock database using Keystore-saved PIN
            val coroutineScope = CoroutineScope(Dispatchers.IO)
            coroutineScope.launch {
                val PREF_ENCRYPTED_PIN = stringPreferencesKey("encrypted_pin")
                val prefs = activity.settingsDataStore.data.first()
                val encPin = prefs[PREF_ENCRYPTED_PIN]
                if (encPin != null) {
                    try {
                        val pin = KeyStoreHelper.decrypt(encPin)
                        val unlocked = databaseManager.unlock(pin)
                        if (unlocked) {
                            activity.runOnUiThread {
                                onUnlocked()
                            }
                        } else {
                            activity.runOnUiThread {
                                onError("Failed to unlock database via biometrics")
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        activity.runOnUiThread {
                            onError("Encryption key error")
                        }
                    }
                } else {
                    activity.runOnUiThread {
                        onError("Biometric credentials not configured")
                    }
                }
            }
        }

        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            onError("Biometric verification failed")
        }
    }

    val biometricPrompt = BiometricPrompt(activity, executor, callback)
    
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Biometric Unlock")
        .setSubtitle("Authenticate to access SmsX")
        .setNegativeButtonText("Use PIN")
        .build()

    biometricPrompt.authenticate(promptInfo)
}
