package com.techadvantage.budgetrak.data

data class SharedSettings(
    val currency: String = "$",
    val budgetPeriod: String = "DAILY",
    val budgetStartDate: String? = null,
    val isManualBudgetEnabled: Boolean = false,
    val manualBudgetAmount: Double = 0.0,
    val weekStartSunday: Boolean = true,
    val resetDayOfWeek: Int = 7,
    val resetDayOfMonth: Int = 1,
    val resetHour: Int = 0,
    val familyTimezone: String = "",
    val matchDays: Int = 7,
    val matchPercent: Double = 1.0,
    val matchDollar: Int = 1,
    val matchChars: Int = 5,
    val showAttribution: Boolean = false,
    val availableCash: Double = 0.0,
    val incomeMode: String = "FIXED",
    val deviceRoster: String = "{}",   // JSON map: deviceId → nickname
    val receiptPruneAgeDays: Int? = null,  // null = no pruning (admin-only)
    val lastChangedBy: String = "",
    val archiveCutoffDate: String? = null,    // ISO date for transaction archiving
    val carryForwardBalance: Double = 0.0,    // cumulative cash effect of archived transactions
    val lastArchiveInfo: String? = null,       // JSON: {"date","count","totalArchived"}
    val archiveThreshold: Int = 10_000        // 0 = off, synced across devices
)
