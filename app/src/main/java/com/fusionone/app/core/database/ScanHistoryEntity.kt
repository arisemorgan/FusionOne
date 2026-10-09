package com.fusionone.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val urlEncrypted: String,
    val urlIvBase64: String,
    val displayDomain: String,
    val verdict: String,
    val reputationScore: Int,
    val threatTypes: List<String>,
    val sslValid: Boolean,
    val domainAgeDays: Int?,
    val ipAddress: String?,
    val ipCountry: String?,
    val redirectChain: List<String>,
    val scannedAtEpochMillis: Long,
    val isFavorite: Boolean = false
)
