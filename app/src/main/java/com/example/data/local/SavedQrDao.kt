package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedQrDao {
    /**
     * Mengambil semua QR code yang masih aktif (expiresAt > waktu saat ini).
     * Otomatis terurut dari yang terbaru dibuat.
     */
    @Query("SELECT * FROM saved_qrs WHERE expiresAt > :currentTimeMs ORDER BY createdAt DESC")
    fun getActiveSavedQrsFlow(currentTimeMs: Long): Flow<List<SavedQrEntity>>

    @Query("SELECT * FROM saved_qrs WHERE expiresAt > :currentTimeMs ORDER BY createdAt DESC")
    suspend fun getActiveSavedQrs(currentTimeMs: Long): List<SavedQrEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedQr(qr: SavedQrEntity): Long

    @Query("DELETE FROM saved_qrs WHERE id = :id")
    suspend fun deleteSavedQr(id: Long)

    /**
     * Membersihkan QR code yang sudah kedaluwarsa dari database.
     */
    @Query("DELETE FROM saved_qrs WHERE expiresAt <= :currentTimeMs")
    suspend fun deleteExpiredQrs(currentTimeMs: Long)

    @Query("DELETE FROM saved_qrs")
    suspend fun clearAll()
}
