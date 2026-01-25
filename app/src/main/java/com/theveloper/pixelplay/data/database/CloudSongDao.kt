package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.theveloper.pixelplay.data.model.CloudSong
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudSongDao {
    @Query("SELECT * FROM cloud_songs ORDER BY dateAdded DESC")
    fun getAllCloudSongs(): Flow<List<CloudSong>>

    @Query("SELECT * FROM cloud_songs WHERE id = :id")
    fun getSongById(id: Long): Flow<CloudSong?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<CloudSong>)

    @Query("DELETE FROM cloud_songs")
    suspend fun clearAll()
    
    // Album queries
    @Query("SELECT DISTINCT album FROM cloud_songs WHERE metadataExtracted = 1 ORDER BY album")
    fun getAllAlbums(): Flow<List<String>>
    
    @Query("SELECT * FROM cloud_songs WHERE album = :album ORDER BY trackNumber, title")
    fun getSongsByAlbum(album: String): Flow<List<CloudSong>>
    
    @Query("SELECT * FROM cloud_songs WHERE album = :album ORDER BY dateAdded DESC LIMIT 1")
    suspend fun getLatestSongInAlbum(album: String): CloudSong?
    
    // Artist queries
    @Query("SELECT DISTINCT artist FROM cloud_songs WHERE metadataExtracted = 1 AND artist != '' ORDER BY artist")
    fun getAllArtists(): Flow<List<String>>
    
    @Query("SELECT * FROM cloud_songs WHERE artist = :artist ORDER BY album, trackNumber, title")
    fun getSongsByArtist(artist: String): Flow<List<CloudSong>>
    
    @Query("SELECT * FROM cloud_songs WHERE artist = :artist ORDER BY dateAdded DESC LIMIT 1")
    suspend fun getLatestSongByArtist(artist: String): CloudSong?
    
    // Metadata extraction support
    @Update
    suspend fun updateSong(song: CloudSong)
    
    @Query("UPDATE cloud_songs SET metadataExtracted = 0, album = 'Unknown Album', artworkPath = NULL")
    suspend fun resetAllMetadata()
    
    @Query("SELECT * FROM cloud_songs WHERE metadataExtracted = 0 LIMIT :limit")
    suspend fun getSongsNeedingMetadata(limit: Int = 10): List<CloudSong>
    
    @Query("SELECT COUNT(*) FROM cloud_songs WHERE metadataExtracted = 0")
    fun getPendingMetadataCount(): Flow<Int>
}
