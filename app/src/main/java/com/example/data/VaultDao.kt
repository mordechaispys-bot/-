package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    // Vault Items
    @Query("SELECT * FROM vault_items WHERE isDecoy = :isDecoy ORDER BY timestamp DESC")
    fun getVaultItems(isDecoy: Boolean): Flow<List<VaultItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultItem(item: VaultItem)

    @Delete
    suspend fun deleteVaultItem(item: VaultItem)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteVaultItemById(id: Int)

    // Break-In Logs
    @Query("SELECT * FROM break_in_logs ORDER BY timestamp DESC")
    fun getBreakInLogs(): Flow<List<BreakInLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreakInLog(log: BreakInLog)

    @Query("DELETE FROM break_in_logs")
    suspend fun clearBreakInLogs()

    // Settings
    @Query("SELECT * FROM vault_settings")
    suspend fun getAllSettings(): List<VaultSetting>

    @Query("SELECT * FROM vault_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): VaultSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: VaultSetting)

    @Query("DELETE FROM vault_settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)
}
