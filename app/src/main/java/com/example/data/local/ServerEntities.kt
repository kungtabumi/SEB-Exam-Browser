package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "server_config")
data class ServerConfigEntity(
    @PrimaryKey val id: Int = 1,
    val serverUrl: String,
    val schoolName: String = "SMAN 1 Kepanjen",
    val examCode: String? = null,
    val examName: String? = null,
    val isExamActive: Boolean = false,
    val adminPin: String = "1ctAdm1n!",
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "allowed_domains")
data class AllowedDomainEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val domain: String,
    val isDefault: Boolean = false
)

@Entity(tableName = "server_history")
data class ServerHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val serverUrl: String,
    val schoolName: String,
    val timestamp: Long = System.currentTimeMillis()
)
