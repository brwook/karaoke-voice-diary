package com.konodiary.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.konodiary.app.ui.components.MiniPlayerBar
import com.konodiary.app.ui.screens.HomeScreen
import com.konodiary.app.ui.screens.RecordingDetailScreen
import com.konodiary.app.ui.screens.SongDetailScreen
import com.konodiary.app.ui.screens.SongsScreen

private sealed class Tab(val route: String, val label: String) {
    data object Home : Tab("home", "녹음")
    data object Songs : Tab("songs", "노래")
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val container = rememberContainer()
    val playerState by container.segmentPlayer.state.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val snackbarHostState = remember { SnackbarHostState() }
    // 재생 실패(원본 삭제·권한 소실 등)는 플레이어가 상태를 리셋해 미니플레이어가
    // 조용히 사라진다 — 사용자에게는 "눌러도 재생이 안 됨"으로 보이므로 알려준다.
    LaunchedEffect(playerState.error) {
        if (playerState.error != null) {
            snackbarHostState.showSnackbar("재생할 수 없어요 · 원본 파일이 없거나 접근할 수 없습니다")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                MiniPlayerBar(
                    state = playerState,
                    onPlayPause = {
                        if (playerState.isPlaying) container.segmentPlayer.pause()
                        else container.segmentPlayer.resume()
                    },
                    onSeek = { container.segmentPlayer.seekTo(it) },
                    onClose = { container.segmentPlayer.stop() },
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    listOf(Tab.Home, Tab.Songs).forEach { tab ->
                        val selected =
                            currentDestination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (tab is Tab.Home) Icons.Filled.Mic
                                    else Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Tab.Home.route) {
                HomeScreen(onOpenRecording = { id -> navController.navigate("recording/$id") })
            }
            composable("recording/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                RecordingDetailScreen(
                    recordingId = id,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Tab.Songs.route) {
                SongsScreen(onOpenSong = { id -> navController.navigate("song/$id") })
            }
            composable("song/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                SongDetailScreen(
                    songId = id,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
