package com.theveloper.pixelplay.presentation.screens.cloud

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.theveloper.pixelplay.data.model.CloudSong
import com.theveloper.pixelplay.presentation.viewmodel.CloudMusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudMusicScreen(
    navController: NavController,
    viewModel: CloudMusicViewModel = hiltViewModel(),
    playerViewModel: com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
) {
    val songs by viewModel.savedSongs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PixelPlay Cloud") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync")
                    }
                }
            )
        }
    ) { padding ->
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No songs found. Tap refresh to sync.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(songs) { song ->
                    CloudSongItem(song = song) {
                        playerViewModel.playCloudSongs(songs, song)
                    }
                }
            }
        }
    }
}

@Composable
fun CloudSongItem(
    song: CloudSong,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(song.title) },
        supportingContent = { Text("${song.artist} • ${(song.size / 1024 / 1024)} MB") },
        modifier = Modifier.clickable { onClick() }
    )
    HorizontalDivider()
}
