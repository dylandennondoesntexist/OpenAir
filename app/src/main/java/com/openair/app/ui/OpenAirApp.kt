package com.openair.app.ui

import android.content.ComponentName
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.openair.app.data.ClipRepository
import com.openair.app.data.MockClipRepository
import com.openair.app.data.OpenAirSupabase
import com.openair.app.data.SupabaseClipRepository
import com.openair.app.playback.PlaybackService
import kotlinx.coroutines.delay

private enum class AppTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Explore("Explore", Icons.Rounded.Explore),
    Create("Create", Icons.Rounded.Mic),
    Inbox("Inbox", Icons.Rounded.ChatBubble),
    Profile("Profile", Icons.Rounded.Person)
}

@Composable
fun OpenAirApp(
    repository: ClipRepository = remember {
        if (OpenAirSupabase.isConfigured) SupabaseClipRepository() else MockClipRepository()
    }
) {
    val state = rememberNowPlayingState(repository)
    var selectedTab by remember { mutableStateOf(AppTab.Home) }

    // Connect to the Media3 playback service; the controller becomes the
    // playback engine behind NowPlayingState once attached.
    val context = LocalContext.current
    DisposableEffect(state) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture.addListener(
            {
                if (!controllerFuture.isCancelled) {
                    state.attachPlayer(controllerFuture.get())
                }
            },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            state.detachPlayer()
            MediaController.releaseFuture(controllerFuture)
        }
    }

    // Mirrors player position into Compose state (or simulates in previews).
    LaunchedEffect(state) {
        while (true) {
            delay(250L)
            state.onPollTick(0.25f)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                AppTab.Home -> HomeScreen(state = state)
                AppTab.Explore -> ExploreScreen(
                    stations = repository.stations(),
                    onStationSelected = { station ->
                        state.selectStation(station)
                        selectedTab = AppTab.Home
                    }
                )
                AppTab.Create -> CreateScreen(repository = repository)
                AppTab.Inbox -> InboxScreen()
                AppTab.Profile -> ProfileScreen(state = state, repository = repository)
            }
        }
    }
}

@Preview
@Composable
private fun OpenAirPreview() {
    OpenAirTheme {
        OpenAirApp()
    }
}
