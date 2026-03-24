package com.syncbudget.app.data

data class Category(
    val id: Int,
    val name: String,
    val iconName: String,
    val tag: String = "",
    val charted: Boolean = true,
    val widgetVisible: Boolean = true,
    // Sync fields
    val deviceId: String = "",
    val deleted: Boolean = false
)
