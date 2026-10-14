package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_qrs")
data class SavedQrEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examName: String,
    val examCode: String,
    val serverUrl: String,
    val schoolName: String,
    val qrPayload: String,
    val isEncrypted: Boolean,
    val sessionPin: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long // Epoch timestamp in milliseconds
)
