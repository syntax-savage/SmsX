package com.privatesms.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.privatesms.ui.blocked.BlockedScreen
import com.privatesms.ui.chat.ChatScreen
import com.privatesms.ui.compose.ComposeScreen
import com.privatesms.ui.conversations.ConversationsScreen
import com.privatesms.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Conversations : Screen("conversations")
    object Chat : Screen("chat/{threadId}/{address}") {
        fun createRoute(threadId: Long, address: String) = "chat/$threadId/$address"
    }
    object Compose : Screen("compose")
    object Blocked : Screen("blocked")
    object Settings : Screen("settings")
    object Spam : Screen("spam")
    object PrivateSpace : Screen("private_space")
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    onBackPress: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Conversations.route
    ) {
        composable(Screen.Conversations.route) {
            ConversationsScreen(
                navController = navController,
                isPrivateSpace = false,
                isSpamFolder = false
            )
        }
        composable(Screen.Spam.route) {
            ConversationsScreen(
                navController = navController,
                isPrivateSpace = false,
                isSpamFolder = true
            )
        }
        composable(Screen.PrivateSpace.route) {
            ConversationsScreen(
                navController = navController,
                isPrivateSpace = true,
                isSpamFolder = false
            )
        }
        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("threadId") { type = NavType.LongType },
                navArgument("address") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val threadId = backStackEntry.arguments?.getLong("threadId") ?: -1L
            val address = backStackEntry.arguments?.getString("address") ?: ""
            ChatScreen(
                navController = navController,
                threadId = threadId,
                address = address
            )
        }
        composable(Screen.Compose.route) {
            ComposeScreen(navController = navController)
        }
        composable(Screen.Blocked.route) {
            BlockedScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
    }
}
