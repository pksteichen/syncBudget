package com.techadvantage.budgetrak.data.sync

data class ImageLedgerEntry(
    val receiptId: String,
    val originatorDeviceId: String,
    val createdAt: Long,
    val possessions: Map<String, Boolean>,  // deviceId -> has file
    val uploadAssignee: String? = null,     // device responsible for re-upload
    val assignedAt: Long = 0L,             // when re-upload assignment was made
    val uploadedAt: Long = 0L,             // 0 = not yet in cloud
    val contentVersion: Long = 0L,         // incremented when photo content changes (e.g., rotation)
    val lastEditBy: String? = null         // device that last bumped contentVersion / completed re-upload / created entry
)

data class SnapshotLedgerEntry(
    val requestedBy: String,
    val requestedAt: Long,
    val builderId: String? = null,
    val builderAssignedAt: Long = 0L,
    val status: String = "requested",      // requested|building|uploading|ready|error
    val progressPercent: Int = 0,
    val errorMessage: String? = null,
    val lastProgressUpdate: Long = 0L,
    val snapshotReceiptCount: Int = 0,
    val readyAt: Long = 0L,
    val consumedBy: Map<String, Boolean> = emptyMap()
)
