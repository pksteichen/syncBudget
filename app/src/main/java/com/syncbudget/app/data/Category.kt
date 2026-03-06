package com.syncbudget.app.data

data class Category(
    val id: Int,
    val name: String,
    val iconName: String,
    val tag: String = "",
    val charted: Boolean = true,
    // Sync fields
    val deviceId: String = "",
    val deleted: Boolean = false,
    val name_clock: Long = 0L,
    val iconName_clock: Long = 0L,
    val tag_clock: Long = 0L,
    val charted_clock: Long = 0L,
    val deleted_clock: Long = 0L,
    val deviceId_clock: Long = 0L
)
