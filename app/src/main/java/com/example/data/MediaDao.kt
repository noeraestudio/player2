package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    // Track queries
    @Query("SELECT * FROM media_tracks ORDER BY isFavorite DESC, dateAdded DESC")
    fun getAllTracksFlow(): Flow<List<MediaTrack>>

    @Query("SELECT * FROM media_tracks ORDER BY isFavorite DESC, dateAdded DESC")
    suspend fun getAllTracksList(): List<MediaTrack>

    @Query("SELECT COUNT(*) FROM media_tracks")
    suspend fun getTracksCount(): Int

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    suspend fun getAllPlaylistsList(): List<Playlist>

    @Query("SELECT * FROM media_tracks WHERE isVideo = :isVideo ORDER BY title ASC")
    fun getTracksByTypeFlow(isVideo: Boolean): Flow<List<MediaTrack>>

    @Query("SELECT * FROM media_tracks WHERE id = :id")
    suspend fun getTrackById(id: Long): MediaTrack?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: MediaTrack): Long

    @Update
    suspend fun updateTrack(track: MediaTrack)

    @Query("DELETE FROM media_tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Long)

    // Playlist queries
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylistsFlow(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    // Playlist track associations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(playlistTrack: PlaylistTrack)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Long)

    @Query("""
        SELECT t.* FROM media_tracks t 
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId 
        WHERE pt.playlistId = :playlistId 
        ORDER BY t.title ASC
    """)
    fun getTracksForPlaylistFlow(playlistId: Long): Flow<List<MediaTrack>>

    // Equalizer preset queries
    @Query("SELECT * FROM equalizer_presets")
    fun getAllPresetsFlow(): Flow<List<EqualizerPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: EqualizerPreset)

    @Query("SELECT * FROM equalizer_presets WHERE name = :name")
    suspend fun getPresetByName(name: String): EqualizerPreset?
}
