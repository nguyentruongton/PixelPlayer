package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.theveloper.pixelplay.data.model.CloudSong
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudSongDao {
    @Query("SELECT * FROM cloud_songs ORDER BY dateAdded DESC")
    fun getAllCloudSongs(): Flow<List<CloudSong>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<CloudSong>)

    @Query("DELETE FROM cloud_songs")
    suspend fun clearAll()
}
