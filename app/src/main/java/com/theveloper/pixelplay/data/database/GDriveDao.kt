package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GDriveDao {

    // --- Songs ---

    @Query("SELECT * FROM gdrive_songs ORDER BY date_added DESC")
    fun getAllGDriveSongs(): Flow<List<GDriveSongEntity>>

    @Query("SELECT * FROM gdrive_songs ORDER BY date_added DESC")
    suspend fun getAllGDriveSongsList(): List<GDriveSongEntity>

    @Query("SELECT * FROM gdrive_songs WHERE folder_id = :folderId ORDER BY title ASC")
    fun getSongsByFolder(folderId: String): Flow<List<GDriveSongEntity>>

    @Query("SELECT * FROM gdrive_songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<GDriveSongEntity>>

    @Query("SELECT * FROM gdrive_songs WHERE id IN (:ids)")
    fun getSongsByIds(ids: List<String>): Flow<List<GDriveSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<GDriveSongEntity>)

    @Query("DELETE FROM gdrive_songs WHERE id = :songId")
    suspend fun deleteSong(songId: String)

    @Query("DELETE FROM gdrive_songs WHERE folder_id = :folderId")
    suspend fun deleteSongsByFolder(folderId: String)

    // --- Folders ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: GDriveFolderEntity)

    @Query("SELECT * FROM gdrive_folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<GDriveFolderEntity>>

    @Query("SELECT * FROM gdrive_folders")
    suspend fun getAllFoldersList(): List<GDriveFolderEntity>

    @Query("DELETE FROM gdrive_folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: String)

    // --- Clear All ---

    @Query("DELETE FROM gdrive_songs")
    suspend fun clearAllSongs()

    @Query("DELETE FROM gdrive_folders")
    suspend fun clearAllFolders()
}
