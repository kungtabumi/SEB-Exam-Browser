package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerConfigDao {
    @Query("SELECT * FROM server_config WHERE id = 1 LIMIT 1")
    fun getServerConfigFlow(): Flow<ServerConfigEntity?>

    @Query("SELECT * FROM server_config WHERE id = 1 LIMIT 1")
    suspend fun getServerConfig(): ServerConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveServerConfig(config: ServerConfigEntity)

    @Query("UPDATE server_config SET isExamActive = :isActive WHERE id = 1")
    suspend fun setExamActive(isActive: Boolean)

    @Query("UPDATE server_config SET adminPin = :newPin WHERE id = 1")
    suspend fun updateAdminPin(newPin: String)

    @Query("DELETE FROM server_config")
    suspend fun deleteServerConfig()
}

@Dao
interface AllowedDomainDao {
    @Query("SELECT * FROM allowed_domains ORDER BY id ASC")
    fun getAllowedDomainsFlow(): Flow<List<AllowedDomainEntity>>

    @Query("SELECT domain FROM allowed_domains")
    suspend fun getAllowedDomainsList(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDomain(domain: AllowedDomainEntity)

    @Query("DELETE FROM allowed_domains WHERE domain = :domain")
    suspend fun deleteDomain(domain: String)

    @Query("DELETE FROM allowed_domains WHERE isDefault = 1 OR domain IN ('moodle.sman1kepanjen.sch.id', 'ujian.sman1kepanjen.sch.id', 'lms.sman1kepanjen.sch.id')")
    suspend fun deleteDefaultDomains()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDomains(domains: List<AllowedDomainEntity>)
}

@Dao
interface ServerHistoryDao {
    @Query("SELECT * FROM server_history ORDER BY timestamp DESC LIMIT 10")
    fun getHistoryFlow(): Flow<List<ServerHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ServerHistoryEntity)

    @Query("DELETE FROM server_history")
    suspend fun clearHistory()
}
