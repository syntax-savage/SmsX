package com.privatesms.ui

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.fragment.app.FragmentActivity
import com.privatesms.data.settingsDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.privatesms.data.db.DatabaseManager
import com.privatesms.domain.repository.SmsRepository
import com.privatesms.ui.lock.AppLockScreen
import com.privatesms.ui.navigation.AppNavGraph
import com.privatesms.ui.navigation.Screen
import com.privatesms.ui.theme.PrivateSmsTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// Use shared settingsDataStore

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var databaseManager: DatabaseManager

    @Inject
    lateinit var smsRepository: SmsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val threadIdFromIntent = intent.getLongExtra("thread_id", -1L)

        setContent {
            val isUnlocked by databaseManager.isUnlocked.collectAsStateWithLifecycle()
            val context = LocalContext.current

            val screenshotPrevention by context.settingsDataStore.data
                .map { it[booleanPreferencesKey("screenshot_prevention")] ?: false }
                .collectAsStateWithLifecycle(initialValue = false)

            LaunchedEffect(screenshotPrevention) {
                if (screenshotPrevention) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            val themeIndex by context.settingsDataStore.data
                .map { it[intPreferencesKey("accent_color")] ?: 0 }
                .collectAsStateWithLifecycle(initialValue = 0)
            val fontStyle by context.settingsDataStore.data
                .map { it[stringPreferencesKey("font_size")] ?: "medium" }
                .collectAsStateWithLifecycle(initialValue = "medium")
            val themeMode by context.settingsDataStore.data
                .map { it[stringPreferencesKey("theme")] ?: "system" }
                .collectAsStateWithLifecycle(initialValue = "system")

            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            PrivateSmsTheme(
                themeIndex = themeIndex,
                darkTheme = darkTheme,
                fontSize = fontStyle
            ) {
                if (!isUnlocked) {
                    AppLockScreen(
                        databaseManager = databaseManager,
                        onUnlocked = {
                            lifecycleScope.launch {
                                try {
                                    smsRepository.reschedulePendingAlarms()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    )
                } else {
                    val navController = rememberNavController()

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavGraph(
                            navController = navController,
                            onBackPress = { finish() }
                        )
                    }

                    LaunchedEffect(isUnlocked) {
                        if (threadIdFromIntent != -1L) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val db = databaseManager.getDatabase()
                                val conv = db.conversationDao().getConversationByThreadId(threadIdFromIntent)
                                if (conv != null) {
                                    withContext(Dispatchers.Main) {
                                        navController.navigate(Screen.Chat.createRoute(conv.threadId, conv.address))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
