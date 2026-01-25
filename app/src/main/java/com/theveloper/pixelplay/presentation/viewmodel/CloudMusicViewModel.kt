package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.manager.CloudSyncManager
import com.theveloper.pixelplay.data.model.CloudAlbum
import com.theveloper.pixelplay.data.model.CloudArtist
import com.theveloper.pixelplay.data.model.CloudSong
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class CloudMusicViewModel @Inject constructor(
    private val cloudSyncManager: CloudSyncManager
) : ViewModel() {

    val savedSongs: StateFlow<List<CloudSong>> = cloudSyncManager.savedSongs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val albums: StateFlow<List<CloudAlbum>> = cloudSyncManager.getAlbumsWithArtwork()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val artists: StateFlow<List<CloudArtist>> = cloudSyncManager.getArtistsWithArtwork()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val pendingMetadataCount: StateFlow<Int> = cloudSyncManager.pendingMetadataCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
    
    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()
    
    init {
        // Auto-start metadata extraction when there are pending songs
        viewModelScope.launch {
            pendingMetadataCount.collect { count ->
                if (count > 0 && !_isExtracting.value) {
                    extractMetadataForPending()
                }
            }
        }
    }
    
    fun refresh() {
        viewModelScope.launch {
            cloudSyncManager.syncCloudSongs()
        }
    }
    
    fun extractMetadataForPending() {
        if (_isExtracting.value) return
        
        viewModelScope.launch {
            _isExtracting.value = true
            try {
                // Extract in batches until all done
                while (pendingMetadataCount.first() > 0) {
                    val extracted = cloudSyncManager.extractPendingMetadata(3)
                    Timber.d("CloudMusicViewModel: Extracted metadata for $extracted songs")
                    if (extracted == 0) break
                    delay(100) // Small delay between batches to avoid overwhelming
                }
            } catch (e: Exception) {
                Timber.e(e, "CloudMusicViewModel: Failed to extract metadata")
            } finally {
                _isExtracting.value = false
            }
        }
    }
    
    fun clearCache() {
        viewModelScope.launch {
            cloudSyncManager.clearCloudCache()
        }
    }
}
