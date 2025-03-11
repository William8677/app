package com.williamfq.xhat.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import com.williamfq.xhat.ui.Navigation.Screen
import com.williamfq.xhat.ui.call.screens.CallScreen
import com.williamfq.xhat.ui.channels.ChannelScreen
import com.williamfq.xhat.ui.communities.screens.CommunitiesScreen
import com.williamfq.xhat.ui.screens.main.components.EmptyScreenPlaceholder
import com.williamfq.xhat.ui.screens.main.viewmodel.MainViewModel
import com.williamfq.xhat.ui.screens.chat.ChatScreen
import com.williamfq.xhat.ui.stories.StoriesScreen
import kotlinx.coroutines.launch

enum class MainScreens(val title: String, val icon: ImageVector, val route: String) {
    CHATS("Chats", Icons.AutoMirrored.Filled.Chat, Screen.Chats.route),
    STORIES("Historias", Icons.Filled.PlayCircle, Screen.Stories.route),
    CHANNELS("Canales", Icons.Filled.Campaign, Screen.Channels.route),
    COMMUNITIES("Comunidades", Icons.Filled.Groups, Screen.Communities.route),
    CHAT_ROOMS("Salas", Icons.Filled.Forum, "chatrooms"),
    CALLS("Llamadas", Icons.Filled.Call, Screen.Calls.route);

    companion object {
        fun fromRoute(route: String?): MainScreens =
            entries.find { it.route == route } ?: CHATS
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPagerApi::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    viewModel: MainViewModel = hiltViewModel()
) {
    // Estado para controlar la angina actual del pager
    val pagerState = rememberPagerState(initialPage = 0)
    val coroutineScope = rememberCoroutineScope()

    // Actualiza el estado en el ViewModel cada vez que la página cambie
    LaunchedEffect(pagerState.currentPage) {
        viewModel.updateCurrentScreen(pagerState.currentPage)
    }

    Scaffold(
        topBar = {
            // TopAppBar combinando acciones de menú, perfil y ajustes
            TopAppBar(
                title = { Text(MainScreens.entries[pagerState.currentPage].title) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onMenuClick() }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menú")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Profile.route) }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Perfil")
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                MainScreens.entries.forEachIndexed { index, screen ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) }
                    )
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            count = MainScreens.entries.size,
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { page ->
            when (MainScreens.entries[page]) {
                MainScreens.CHATS -> ChatScreen(navController)
                MainScreens.STORIES -> StoriesScreen(navController)

                    MainScreens.CHANNELS -> ChannelScreen(
                channelId = "default",  // Ajusta el id según corresponda
                onNavigateUp = { navController.navigateUp() }
            )
                MainScreens.COMMUNITIES -> CommunitiesScreen(
                    onNavigateToCreateCommunity = { navController.navigate("community_create") },
                    onNavigateToCommunityDetail = { communityId ->
                        navController.navigate("community_detail/$communityId")
                    }
                )
                MainScreens.CHAT_ROOMS -> EmptyScreenPlaceholder(
                    icon = Icons.Filled.Forum,
                    text = "Salas"
                )
                MainScreens.CALLS -> CallScreen(
                    onNavigateBack = { navController.navigateUp() }
                )
            }
        }
    }
}
