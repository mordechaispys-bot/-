package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val fileType: String,      // "IMAGE" or "VIDEO"
    val internalPath: String,  // Relative to app storage (filesDir) e.g., "vault_media/pic_1.jpg"
    val fileSize: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val isDecoy: Boolean = false // If user entered the fake decoy pin
)

@Entity(tableName = "break_in_logs")
data class BreakInLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val enteredPin: String,
    val photoPath: String? = null // Simulated or actual camera capture path
)

@Entity(tableName = "vault_settings")
data class VaultSetting(
    @PrimaryKey val key: String, // "PIN", "DECOY_PIN", "STEALTH_MODE", "SELF_DESTRUCT_COUNT", "FAILED_ATTEMPTS"
    val value: String
)
