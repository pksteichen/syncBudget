package com.syncbudget.app.data

data class Category(
    val id: Int,
    val name: String,
    val iconName: String,
    val tag: String = "",
    // Sync fields
    val deviceId: String = "",
    val deleted: Boolean = false,
    val name_clock: Long = 0L,
    val iconName_clock: Long = 0L,
    val deleted_clock: Long = 0L
)
