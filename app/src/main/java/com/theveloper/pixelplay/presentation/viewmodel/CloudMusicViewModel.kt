package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.manager.CloudSyncManager
import com.theveloper.pixelplay.data.model.CloudSong
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    
    // Simple state for sync loading/error
    // For MVP, we might just expose a simple boolean or error flow if needed
    
    fun refresh() {
        viewModelScope.launch {
            cloudSyncManager.syncCloudSongs()
        }
    }
}
