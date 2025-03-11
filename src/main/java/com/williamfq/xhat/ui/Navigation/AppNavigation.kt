package com.williamfq.xhat.ui.Navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.williamfq.domain.location.LocationTracker
import com.williamfq.xhat.ui.main.MainScreen
import com.williamfq.xhat.ui.screens.auth.PhoneNumberScreen
import com.williamfq.xhat.ui.screens.auth.ProfileSetupScreen
import com.williamfq.xhat.ui.screens.auth.VerificationCodeScreen
import com.williamfq.xhat.ui.screens.login.LoginScreen
import com.williamfq.xhat.ui.screens.register.RegisterScreen
import com.williamfq.xhat.ui.screens.profile.ProfileScreen
import com.williamfq.xhat.ui.screens.settings.SettingsScreen
import com.williamfq.xhat.panic.RealTimeLocationScreen
import com.williamfq.xhat.ui.screens.chat.ChatScreen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.lang.IllegalArgumentException

sealed class NavigationEvent {
    data class NavigateToChat(val chatId: String) : NavigationEvent()
    data class ActivatePanicMode(val chatId: String, val chatType: ChatType) : NavigationEvent()
    object NavigateBack : NavigationEvent()
}

class NavigationState {
    private val _navigationEvents = MutableSharedFlow<NavigationEvent>()
    val navigationEvents = _navigationEvents.asSharedFlow()

    suspend fun emit(event: NavigationEvent) {
        _navigationEvents.emit(event)
    }
}

@Composable
fun rememberNavigationState() = remember { NavigationState() }

class PermissionHandler(
    private val onPermissionGranted: () -> Unit,
    private val onPermissionDenied: () -> Unit
) {
    fun checkPermission(permission: Boolean) {
        if (permission) onPermissionGranted() else onPermissionDenied()
    }
}

@Composable
fun NavigationConfig(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider {
        content()
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String = Screen.PhoneNumber.route,
    onRequestPermissions: () -> Unit,
    permissionsGranted: Boolean,
    navigationState: NavigationState = rememberNavigationState(),
    locationTracker: LocationTracker
) {
    var navigationError by remember { mutableStateOf<String?>(null) }

    val permissionHandler = remember {
        PermissionHandler(
            onPermissionGranted = { Timber.d("Permisos concedidos") },
            onPermissionDenied = { Timber.d("Permisos denegados") }
        )
    }

    LaunchedEffect(navigationState) {
        navigationState.navigationEvents.collect { event ->
            when (event) {
                is NavigationEvent.NavigateToChat ->
                    navController.navigate(Screen.Chat.createRoute(event.chatId))
                is NavigationEvent.ActivatePanicMode ->
                    navController.navigate(Screen.PanicLocation.createRoute(event.chatId, event.chatType))
                is NavigationEvent.NavigateBack ->
                    navController.popBackStack()
            }
        }
    }

    LaunchedEffect(navigationError) {
        navigationError?.let {
            navigationError = null
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.PhoneNumber.route) {
            PhoneNumberScreen(navController = navController)
        }
        composable(
            route = Screen.VerificationCode.route,
            arguments = listOf(
                navArgument("phoneNumber") { type = NavType.StringType },
                navArgument("verificationId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val phoneNumber = backStackEntry.arguments?.getString("phoneNumber") ?: ""
            val verificationId = backStackEntry.arguments?.getString("verificationId") ?: ""
            if (phoneNumber.isNotEmpty() && verificationId.isNotEmpty()) {
                VerificationCodeScreen(
                    navController = navController,
                    phoneNumber = phoneNumber,
                    verificationId = verificationId
                )
            }
        }
        composable(Screen.ProfileSetup.route) {
            ProfileSetupScreen(navController = navController)
        }
        composable(Screen.Login.route) {
            LoginScreen(navController = navController)
        }
        composable(Screen.Register.route) {
            RegisterScreen(navController = navController)
        }
        composable(Screen.Main.route) {
            MainScreen(navController = navController)
        }
        composable(Screen.Profile.route) {
            ProfileScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        navigation(
            startDestination = Screen.ChatList.route,
            route = "chat_section"
        ) {
            composable(route = Screen.ChatList.route) {
                ChatScreen(
                    navController = navController,
                    chatId = null,
                    isDetailView = false
                )
            }
            composable(
                route = Screen.Chat.route,
                arguments = listOf(navArgument("chatId") { type = NavType.StringType })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId")
                ChatScreen(
                    navController = navController,
                    chatId = chatId,
                    isDetailView = false
                )
            }
            composable(
                route = Screen.ChatDetail.route,
                arguments = listOf(navArgument("chatId") { type = NavType.StringType })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId")
                ChatScreen(
                    navController = navController,
                    chatId = chatId,
                    isDetailView = true
                )
            }
        }
        composable(
            route = Screen.PanicLocation.route,
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("chatType") {
                    type = NavType.StringType
                    nullable = false
                }
            ),
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { NavigationConstants.TRANSITION_OFFSET },
                    animationSpec = tween(NavigationConstants.ANIMATION_DURATION)
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -NavigationConstants.TRANSITION_OFFSET },
                    animationSpec = tween(NavigationConstants.ANIMATION_DURATION)
                )
            }
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
            val chatType = backStackEntry.arguments?.getString("chatType")?.let {
                try {
                    ChatType.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    null
                }
            } ?: return@composable

            RealTimeLocationScreen(
                locationTracker = locationTracker,
                chatId = chatId,
                chatType = chatType,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
