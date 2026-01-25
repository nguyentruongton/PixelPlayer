package com.theveloper.pixelplay.presentation.screens.cloud

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.theveloper.pixelplay.data.model.CloudAlbum
import com.theveloper.pixelplay.data.model.CloudArtist
import com.theveloper.pixelplay.data.model.CloudSong
import com.theveloper.pixelplay.presentation.viewmodel.CloudMusicViewModel
import kotlinx.coroutines.launch
import java.io.File

private enum class CloudTab(val title: String, val icon: ImageVector) {
    SONGS("Songs", Icons.Default.MusicNote),
    ALBUMS("Albums", Icons.Default.Album),
    ARTISTS("Artists", Icons.Default.Person)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudMusicScreen(
    navController: NavController,
    viewModel: CloudMusicViewModel = hiltViewModel(),
    playerViewModel: com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
) {
    val songs by viewModel.savedSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val pendingCount by viewModel.pendingMetadataCount.collectAsState()
    val isExtracting by viewModel.isExtracting.collectAsState()
    
    val pagerState = rememberPagerState(pageCount = { CloudTab.entries.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("PixelPlay Cloud")
                        if (isExtracting && pendingCount > 0) {
                            Text(
                                "Extracting metadata... ($pendingCount remaining)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                CloudTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(tab.title) },
                        icon = { Icon(tab.icon, contentDescription = tab.title) }
                    )
                }
            }
            
            // Pager Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (CloudTab.entries[page]) {
                    CloudTab.SONGS -> SongsContent(
                        songs = songs,
                        onSongClick = { song -> playerViewModel.playCloudSongs(songs, song) }
                    )
                    CloudTab.ALBUMS -> AlbumsContent(
                        albums = albums,
                        onAlbumClick = { album -> 
                            // Play all songs in album
                            if (album.songs.isNotEmpty()) {
                                playerViewModel.playCloudSongs(album.songs, album.songs.first())
                            }
                        }
                    )
                    CloudTab.ARTISTS -> ArtistsContent(
                        artists = artists,
                        onArtistClick = { /* TODO: Navigate to artist detail */ }
                    )
                }
            }
        }
    }
}

@Composable
private fun SongsContent(
    songs: List<CloudSong>,
    onSongClick: (CloudSong) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyState("No songs found. Tap refresh to sync.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(songs) { song ->
                CloudSongItem(song = song) { onSongClick(song) }
            }
        }
    }
}

@Composable
private fun AlbumsContent(
    albums: List<CloudAlbum>,
    onAlbumClick: (CloudAlbum) -> Unit
) {
    if (albums.isEmpty()) {
        EmptyState("No albums found. Refresh to extract metadata.")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(albums) { album ->
                CloudAlbumItem(album = album) { onAlbumClick(album) }
            }
        }
    }
}

@Composable
private fun ArtistsContent(
    artists: List<CloudArtist>,
    onArtistClick: (CloudArtist) -> Unit
) {
    if (artists.isEmpty()) {
        EmptyState("No artists found. Refresh to extract metadata.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(artists) { artist ->
                CloudArtistItem(artist = artist) { onArtistClick(artist) }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CloudSongItem(
    song: CloudSong,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { 
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            ) 
        },
        supportingContent = { 
            Text(
                "${song.artist} • ${song.album}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            ) 
        },
        leadingContent = {
            if (song.artworkPath != null) {
                AsyncImage(
                    model = File(song.artworkPath),
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        modifier = Modifier.clickable { onClick() }
    )
    HorizontalDivider()
}

@Composable
private fun CloudAlbumItem(
    album: CloudAlbum,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Album Artwork
            if (album.artworkPath != null) {
                AsyncImage(
                    model = File(album.artworkPath),
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        Icons.Default.Album,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Album Info
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    album.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${album.artist ?: "Unknown Artist"} • ${album.songCount} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CloudArtistItem(
    artist: CloudArtist,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { 
            Text(
                artist.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            ) 
        },
        supportingContent = { 
            Text("${artist.songCount} songs • ${artist.albumCount} albums") 
        },
        leadingContent = {
            if (artist.artworkPath != null) {
                AsyncImage(
                    model = File(artist.artworkPath),
                    contentDescription = "Artist Image",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        modifier = Modifier.clickable { onClick() }
    )
    HorizontalDivider()
}

