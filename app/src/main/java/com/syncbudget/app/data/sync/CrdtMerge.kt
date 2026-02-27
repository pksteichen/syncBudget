package com.syncbudget.app.data.sync

import com.syncbudget.app.data.AmortizationEntry
import com.syncbudget.app.data.Category
import com.syncbudget.app.data.IncomeSource
import com.syncbudget.app.data.RecurringExpense
import com.syncbudget.app.data.SavingsGoal
import com.syncbudget.app.data.SharedSettings
import com.syncbudget.app.data.Transaction

object CrdtMerge {

    fun shouldAcceptRemote(
        localClock: Long,
        remoteClock: Long,
        localDeviceId: String,
        remoteDeviceId: String
    ): Boolean {
        if (remoteClock > localClock) return true
        if (remoteClock == localClock) return remoteDeviceId > localDeviceId
        return false
    }

    fun mergeTransaction(
        local: Transaction,
        remote: Transaction,
        localDeviceId: String
    ): Transaction {
        val remoteDeviceId = remote.deviceId
        // Standard LWW — highest clock wins (commutative)
        val remoteWinsDeleted = shouldAcceptRemote(local.deleted_clock, remote.deleted_clock, localDeviceId, remoteDeviceId)
        val mergedDeleted = if (remoteWinsDeleted) remote.deleted else local.deleted
        val mergedDeletedClock = maxOf(local.deleted_clock, remote.deleted_clock)

        return Transaction(
            id = local.id,
            type = if (shouldAcceptRemote(local.type_clock, remote.type_clock, localDeviceId, remoteDeviceId)) remote.type else local.type,
            date = if (shouldAcceptRemote(local.date_clock, remote.date_clock, localDeviceId, remoteDeviceId)) remote.date else local.date,
            source = if (shouldAcceptRemote(local.source_clock, remote.source_clock, localDeviceId, remoteDeviceId)) remote.source else local.source,
            description = if (shouldAcceptRemote(local.description_clock, remote.description_clock, localDeviceId, remoteDeviceId)) remote.description else local.description,
            categoryAmounts = if (shouldAcceptRemote(local.categoryAmounts_clock, remote.categoryAmounts_clock, localDeviceId, remoteDeviceId)) remote.categoryAmounts else local.categoryAmounts,
            amount = if (shouldAcceptRemote(local.amount_clock, remote.amount_clock, localDeviceId, remoteDeviceId)) remote.amount else local.amount,
            isUserCategorized = if (shouldAcceptRemote(local.isUserCategorized_clock, remote.isUserCategorized_clock, localDeviceId, remoteDeviceId)) remote.isUserCategorized else local.isUserCategorized,
            isBudgetIncome = if (shouldAcceptRemote(local.isBudgetIncome_clock, remote.isBudgetIncome_clock, localDeviceId, remoteDeviceId)) remote.isBudgetIncome else local.isBudgetIncome,
            deviceId = if (shouldAcceptRemote(local.deviceId_clock, remote.deviceId_clock, localDeviceId, remoteDeviceId)) remote.deviceId else local.deviceId,
            deleted = mergedDeleted,
            source_clock = maxOf(local.source_clock, remote.source_clock),
            description_clock = maxOf(local.description_clock, remote.description_clock),
            amount_clock = maxOf(local.amount_clock, remote.amount_clock),
            date_clock = maxOf(local.date_clock, remote.date_clock),
            type_clock = maxOf(local.type_clock, remote.type_clock),
            categoryAmounts_clock = maxOf(local.categoryAmounts_clock, remote.categoryAmounts_clock),
            isUserCategorized_clock = maxOf(local.isUserCategorized_clock, remote.isUserCategorized_clock),
            isBudgetIncome_clock = maxOf(local.isBudgetIncome_clock, remote.isBudgetIncome_clock),
            deleted_clock = mergedDeletedClock,
            deviceId_clock = maxOf(local.deviceId_clock, remote.deviceId_clock)
        )
    }

    fun mergeRecurringExpense(
        local: RecurringExpense,
        remote: RecurringExpense,
        localDeviceId: String
    ): RecurringExpense {
        val remoteDeviceId = remote.deviceId
        val remoteWinsDeleted = shouldAcceptRemote(local.deleted_clock, remote.deleted_clock, localDeviceId, remoteDeviceId)
        val mergedDeleted = if (remoteWinsDeleted) remote.deleted else local.deleted
        val mergedDeletedClock = maxOf(local.deleted_clock, remote.deleted_clock)

        return RecurringExpense(
            id = local.id,
            source = if (shouldAcceptRemote(local.source_clock, remote.source_clock, localDeviceId, remoteDeviceId)) remote.source else local.source,
            description = if (shouldAcceptRemote(local.description_clock, remote.description_clock, localDeviceId, remoteDeviceId)) remote.description else local.description,
            amount = if (shouldAcceptRemote(local.amount_clock, remote.amount_clock, localDeviceId, remoteDeviceId)) remote.amount else local.amount,
            repeatType = if (shouldAcceptRemote(local.repeatType_clock, remote.repeatType_clock, localDeviceId, remoteDeviceId)) remote.repeatType else local.repeatType,
            repeatInterval = if (shouldAcceptRemote(local.repeatInterval_clock, remote.repeatInterval_clock, localDeviceId, remoteDeviceId)) remote.repeatInterval else local.repeatInterval,
            startDate = if (shouldAcceptRemote(local.startDate_clock, remote.startDate_clock, localDeviceId, remoteDeviceId)) remote.startDate else local.startDate,
            monthDay1 = if (shouldAcceptRemote(local.monthDay1_clock, remote.monthDay1_clock, localDeviceId, remoteDeviceId)) remote.monthDay1 else local.monthDay1,
            monthDay2 = if (shouldAcceptRemote(local.monthDay2_clock, remote.monthDay2_clock, localDeviceId, remoteDeviceId)) remote.monthDay2 else local.monthDay2,
            deviceId = if (shouldAcceptRemote(local.deviceId_clock, remote.deviceId_clock, localDeviceId, remoteDeviceId)) remote.deviceId else local.deviceId,
            deleted = mergedDeleted,
            source_clock = maxOf(local.source_clock, remote.source_clock),
            description_clock = maxOf(local.description_clock, remote.description_clock),
            amount_clock = maxOf(local.amount_clock, remote.amount_clock),
            repeatType_clock = maxOf(local.repeatType_clock, remote.repeatType_clock),
            repeatInterval_clock = maxOf(local.repeatInterval_clock, remote.repeatInterval_clock),
            startDate_clock = maxOf(local.startDate_clock, remote.startDate_clock),
            monthDay1_clock = maxOf(local.monthDay1_clock, remote.monthDay1_clock),
            monthDay2_clock = maxOf(local.monthDay2_clock, remote.monthDay2_clock),
            deleted_clock = mergedDeletedClock,
            deviceId_clock = maxOf(local.deviceId_clock, remote.deviceId_clock)
        )
    }

    fun mergeIncomeSource(
        local: IncomeSource,
        remote: IncomeSource,
        localDeviceId: String
    ): IncomeSource {
        val remoteDeviceId = remote.deviceId
        val remoteWinsDeleted = shouldAcceptRemote(local.deleted_clock, remote.deleted_clock, localDeviceId, remoteDeviceId)
        val mergedDeleted = if (remoteWinsDeleted) remote.deleted else local.deleted
        val mergedDeletedClock = maxOf(local.deleted_clock, remote.deleted_clock)

        return IncomeSource(
            id = local.id,
            source = if (shouldAcceptRemote(local.source_clock, remote.source_clock, localDeviceId, remoteDeviceId)) remote.source else local.source,
            description = if (shouldAcceptRemote(local.description_clock, remote.description_clock, localDeviceId, remoteDeviceId)) remote.description else local.description,
            amount = if (shouldAcceptRemote(local.amount_clock, remote.amount_clock, localDeviceId, remoteDeviceId)) remote.amount else local.amount,
            repeatType = if (shouldAcceptRemote(local.repeatType_clock, remote.repeatType_clock, localDeviceId, remoteDeviceId)) remote.repeatType else local.repeatType,
            repeatInterval = if (shouldAcceptRemote(local.repeatInterval_clock, remote.repeatInterval_clock, localDeviceId, remoteDeviceId)) remote.repeatInterval else local.repeatInterval,
            startDate = if (shouldAcceptRemote(local.startDate_clock, remote.startDate_clock, localDeviceId, remoteDeviceId)) remote.startDate else local.startDate,
            monthDay1 = if (shouldAcceptRemote(local.monthDay1_clock, remote.monthDay1_clock, localDeviceId, remoteDeviceId)) remote.monthDay1 else local.monthDay1,
            monthDay2 = if (shouldAcceptRemote(local.monthDay2_clock, remote.monthDay2_clock, localDeviceId, remoteDeviceId)) remote.monthDay2 else local.monthDay2,
            deviceId = if (shouldAcceptRemote(local.deviceId_clock, remote.deviceId_clock, localDeviceId, remoteDeviceId)) remote.deviceId else local.deviceId,
            deleted = mergedDeleted,
            source_clock = maxOf(local.source_clock, remote.source_clock),
            description_clock = maxOf(local.description_clock, remote.description_clock),
            amount_clock = maxOf(local.amount_clock, remote.amount_clock),
            repeatType_clock = maxOf(local.repeatType_clock, remote.repeatType_clock),
            repeatInterval_clock = maxOf(local.repeatInterval_clock, remote.repeatInterval_clock),
            startDate_clock = maxOf(local.startDate_clock, remote.startDate_clock),
            monthDay1_clock = maxOf(local.monthDay1_clock, remote.monthDay1_clock),
            monthDay2_clock = maxOf(local.monthDay2_clock, remote.monthDay2_clock),
            deleted_clock = mergedDeletedClock,
            deviceId_clock = maxOf(local.deviceId_clock, remote.deviceId_clock)
        )
    }

    fun mergeSavingsGoal(
        local: SavingsGoal,
        remote: SavingsGoal,
        localDeviceId: String
    ): SavingsGoal {
        val remoteDeviceId = remote.deviceId
        val remoteWinsDeleted = shouldAcceptRemote(local.deleted_clock, remote.deleted_clock, localDeviceId, remoteDeviceId)
        val mergedDeleted = if (remoteWinsDeleted) remote.deleted else local.deleted
        val mergedDeletedClock = maxOf(local.deleted_clock, remote.deleted_clock)

        return SavingsGoal(
            id = local.id,
            name = if (shouldAcceptRemote(local.name_clock, remote.name_clock, localDeviceId, remoteDeviceId)) remote.name else local.name,
            targetAmount = if (shouldAcceptRemote(local.targetAmount_clock, remote.targetAmount_clock, localDeviceId, remoteDeviceId)) remote.targetAmount else local.targetAmount,
            targetDate = if (shouldAcceptRemote(local.targetDate_clock, remote.targetDate_clock, localDeviceId, remoteDeviceId)) remote.targetDate else local.targetDate,
            totalSavedSoFar = if (shouldAcceptRemote(local.totalSavedSoFar_clock, remote.totalSavedSoFar_clock, localDeviceId, remoteDeviceId)) remote.totalSavedSoFar else local.totalSavedSoFar,
            contributionPerPeriod = if (shouldAcceptRemote(local.contributionPerPeriod_clock, remote.contributionPerPeriod_clock, localDeviceId, remoteDeviceId)) remote.contributionPerPeriod else local.contributionPerPeriod,
            isPaused = if (shouldAcceptRemote(local.isPaused_clock, remote.isPaused_clock, localDeviceId, remoteDeviceId)) remote.isPaused else local.isPaused,
            deviceId = if (shouldAcceptRemote(local.deviceId_clock, remote.deviceId_clock, localDeviceId, remoteDeviceId)) remote.deviceId else local.deviceId,
            deleted = mergedDeleted,
            name_clock = maxOf(local.name_clock, remote.name_clock),
            targetAmount_clock = maxOf(local.targetAmount_clock, remote.targetAmount_clock),
            targetDate_clock = maxOf(local.targetDate_clock, remote.targetDate_clock),
            totalSavedSoFar_clock = maxOf(local.totalSavedSoFar_clock, remote.totalSavedSoFar_clock),
            contributionPerPeriod_clock = maxOf(local.contributionPerPeriod_clock, remote.contributionPerPeriod_clock),
            isPaused_clock = maxOf(local.isPaused_clock, remote.isPaused_clock),
            deleted_clock = mergedDeletedClock,
            deviceId_clock = maxOf(local.deviceId_clock, remote.deviceId_clock)
        )
    }

    fun mergeAmortizationEntry(
        local: AmortizationEntry,
        remote: AmortizationEntry,
        localDeviceId: String
    ): AmortizationEntry {
        val remoteDeviceId = remote.deviceId
        val remoteWinsDeleted = shouldAcceptRemote(local.deleted_clock, remote.deleted_clock, localDeviceId, remoteDeviceId)
        val mergedDeleted = if (remoteWinsDeleted) remote.deleted else local.deleted
        val mergedDeletedClock = maxOf(local.deleted_clock, remote.deleted_clock)

        return AmortizationEntry(
            id = local.id,
            source = if (shouldAcceptRemote(local.source_clock, remote.source_clock, localDeviceId, remoteDeviceId)) remote.source else local.source,
            description = if (shouldAcceptRemote(local.description_clock, remote.description_clock, localDeviceId, remoteDeviceId)) remote.description else local.description,
            amount = if (shouldAcceptRemote(local.amount_clock, remote.amount_clock, localDeviceId, remoteDeviceId)) remote.amount else local.amount,
            totalPeriods = if (shouldAcceptRemote(local.totalPeriods_clock, remote.totalPeriods_clock, localDeviceId, remoteDeviceId)) remote.totalPeriods else local.totalPeriods,
            startDate = if (shouldAcceptRemote(local.startDate_clock, remote.startDate_clock, localDeviceId, remoteDeviceId)) remote.startDate else local.startDate,
            isPaused = if (shouldAcceptRemote(local.isPaused_clock, remote.isPaused_clock, localDeviceId, remoteDeviceId)) remote.isPaused else local.isPaused,
            deviceId = if (shouldAcceptRemote(local.deviceId_clock, remote.deviceId_clock, localDeviceId, remoteDeviceId)) remote.deviceId else local.deviceId,
            deleted = mergedDeleted,
            source_clock = maxOf(local.source_clock, remote.source_clock),
            description_clock = maxOf(local.description_clock, remote.description_clock),
            amount_clock = maxOf(local.amount_clock, remote.amount_clock),
            totalPeriods_clock = maxOf(local.totalPeriods_clock, remote.totalPeriods_clock),
            startDate_clock = maxOf(local.startDate_clock, remote.startDate_clock),
            deleted_clock = mergedDeletedClock,
            isPaused_clock = maxOf(local.isPaused_clock, remote.isPaused_clock),
            deviceId_clock = maxOf(local.deviceId_clock, remote.deviceId_clock)
        )
    }

    fun mergeCategory(
        local: Category,
        remote: Category,
        localDeviceId: String
    ): Category {
        val remoteDeviceId = remote.deviceId
        val remoteWinsDeleted = shouldAcceptRemote(local.deleted_clock, remote.deleted_clock, localDeviceId, remoteDeviceId)
        val mergedDeleted = if (remoteWinsDeleted) remote.deleted else local.deleted
        val mergedDeletedClock = maxOf(local.deleted_clock, remote.deleted_clock)

        return Category(
            id = local.id,
            name = if (shouldAcceptRemote(local.name_clock, remote.name_clock, localDeviceId, remoteDeviceId)) remote.name else local.name,
            iconName = if (shouldAcceptRemote(local.iconName_clock, remote.iconName_clock, localDeviceId, remoteDeviceId)) remote.iconName else local.iconName,
            tag = if (shouldAcceptRemote(local.tag_clock, remote.tag_clock, localDeviceId, remoteDeviceId)) remote.tag else local.tag,
            deviceId = if (shouldAcceptRemote(local.deviceId_clock, remote.deviceId_clock, localDeviceId, remoteDeviceId)) remote.deviceId else local.deviceId,
            deleted = mergedDeleted,
            name_clock = maxOf(local.name_clock, remote.name_clock),
            iconName_clock = maxOf(local.iconName_clock, remote.iconName_clock),
            tag_clock = maxOf(local.tag_clock, remote.tag_clock),
            deleted_clock = mergedDeletedClock,
            deviceId_clock = maxOf(local.deviceId_clock, remote.deviceId_clock)
        )
    }

    fun mergeSharedSettings(
        local: SharedSettings,
        remote: SharedSettings,
        localDeviceId: String
    ): SharedSettings {
        val remoteDeviceId = remote.lastChangedBy
        // Derive lastChangedBy deterministically from the highest clock across
        // all fields so merge(A,B) == merge(B,A) — commutative.
        val localMaxClock = maxOf(local.currency_clock, local.budgetPeriod_clock,
            local.budgetStartDate_clock, local.isManualBudgetEnabled_clock,
            local.manualBudgetAmount_clock, local.weekStartSunday_clock,
            local.resetDayOfWeek_clock, local.resetDayOfMonth_clock,
            local.resetHour_clock, local.familyTimezone_clock,
            local.matchDays_clock, local.matchPercent_clock,
            local.matchDollar_clock, local.matchChars_clock,
            local.showAttribution_clock, local.availableCash_clock)
        val remoteMaxClock = maxOf(remote.currency_clock, remote.budgetPeriod_clock,
            remote.budgetStartDate_clock, remote.isManualBudgetEnabled_clock,
            remote.manualBudgetAmount_clock, remote.weekStartSunday_clock,
            remote.resetDayOfWeek_clock, remote.resetDayOfMonth_clock,
            remote.resetHour_clock, remote.familyTimezone_clock,
            remote.matchDays_clock, remote.matchPercent_clock,
            remote.matchDollar_clock, remote.matchChars_clock,
            remote.showAttribution_clock, remote.availableCash_clock)
        val mergedLastChangedBy = when {
            remoteMaxClock > localMaxClock -> remote.lastChangedBy
            localMaxClock > remoteMaxClock -> local.lastChangedBy
            else -> if (remoteDeviceId > localDeviceId) remote.lastChangedBy else local.lastChangedBy
        }
        return SharedSettings(
            currency = if (shouldAcceptRemote(local.currency_clock, remote.currency_clock, localDeviceId, remoteDeviceId)) remote.currency else local.currency,
            budgetPeriod = if (shouldAcceptRemote(local.budgetPeriod_clock, remote.budgetPeriod_clock, localDeviceId, remoteDeviceId)) remote.budgetPeriod else local.budgetPeriod,
            budgetStartDate = if (shouldAcceptRemote(local.budgetStartDate_clock, remote.budgetStartDate_clock, localDeviceId, remoteDeviceId)) remote.budgetStartDate else local.budgetStartDate,
            isManualBudgetEnabled = if (shouldAcceptRemote(local.isManualBudgetEnabled_clock, remote.isManualBudgetEnabled_clock, localDeviceId, remoteDeviceId)) remote.isManualBudgetEnabled else local.isManualBudgetEnabled,
            manualBudgetAmount = if (shouldAcceptRemote(local.manualBudgetAmount_clock, remote.manualBudgetAmount_clock, localDeviceId, remoteDeviceId)) remote.manualBudgetAmount else local.manualBudgetAmount,
            weekStartSunday = if (shouldAcceptRemote(local.weekStartSunday_clock, remote.weekStartSunday_clock, localDeviceId, remoteDeviceId)) remote.weekStartSunday else local.weekStartSunday,
            resetDayOfWeek = if (shouldAcceptRemote(local.resetDayOfWeek_clock, remote.resetDayOfWeek_clock, localDeviceId, remoteDeviceId)) remote.resetDayOfWeek else local.resetDayOfWeek,
            resetDayOfMonth = if (shouldAcceptRemote(local.resetDayOfMonth_clock, remote.resetDayOfMonth_clock, localDeviceId, remoteDeviceId)) remote.resetDayOfMonth else local.resetDayOfMonth,
            resetHour = if (shouldAcceptRemote(local.resetHour_clock, remote.resetHour_clock, localDeviceId, remoteDeviceId)) remote.resetHour else local.resetHour,
            familyTimezone = if (shouldAcceptRemote(local.familyTimezone_clock, remote.familyTimezone_clock, localDeviceId, remoteDeviceId)) remote.familyTimezone else local.familyTimezone,
            matchDays = if (shouldAcceptRemote(local.matchDays_clock, remote.matchDays_clock, localDeviceId, remoteDeviceId)) remote.matchDays else local.matchDays,
            matchPercent = if (shouldAcceptRemote(local.matchPercent_clock, remote.matchPercent_clock, localDeviceId, remoteDeviceId)) remote.matchPercent else local.matchPercent,
            matchDollar = if (shouldAcceptRemote(local.matchDollar_clock, remote.matchDollar_clock, localDeviceId, remoteDeviceId)) remote.matchDollar else local.matchDollar,
            matchChars = if (shouldAcceptRemote(local.matchChars_clock, remote.matchChars_clock, localDeviceId, remoteDeviceId)) remote.matchChars else local.matchChars,
            showAttribution = if (shouldAcceptRemote(local.showAttribution_clock, remote.showAttribution_clock, localDeviceId, remoteDeviceId)) remote.showAttribution else local.showAttribution,
            availableCash = if (shouldAcceptRemote(local.availableCash_clock, remote.availableCash_clock, localDeviceId, remoteDeviceId)) remote.availableCash else local.availableCash,
            lastChangedBy = mergedLastChangedBy,
            currency_clock = maxOf(local.currency_clock, remote.currency_clock),
            budgetPeriod_clock = maxOf(local.budgetPeriod_clock, remote.budgetPeriod_clock),
            budgetStartDate_clock = maxOf(local.budgetStartDate_clock, remote.budgetStartDate_clock),
            isManualBudgetEnabled_clock = maxOf(local.isManualBudgetEnabled_clock, remote.isManualBudgetEnabled_clock),
            manualBudgetAmount_clock = maxOf(local.manualBudgetAmount_clock, remote.manualBudgetAmount_clock),
            weekStartSunday_clock = maxOf(local.weekStartSunday_clock, remote.weekStartSunday_clock),
            resetDayOfWeek_clock = maxOf(local.resetDayOfWeek_clock, remote.resetDayOfWeek_clock),
            resetDayOfMonth_clock = maxOf(local.resetDayOfMonth_clock, remote.resetDayOfMonth_clock),
            resetHour_clock = maxOf(local.resetHour_clock, remote.resetHour_clock),
            familyTimezone_clock = maxOf(local.familyTimezone_clock, remote.familyTimezone_clock),
            matchDays_clock = maxOf(local.matchDays_clock, remote.matchDays_clock),
            matchPercent_clock = maxOf(local.matchPercent_clock, remote.matchPercent_clock),
            matchDollar_clock = maxOf(local.matchDollar_clock, remote.matchDollar_clock),
            matchChars_clock = maxOf(local.matchChars_clock, remote.matchChars_clock),
            showAttribution_clock = maxOf(local.showAttribution_clock, remote.showAttribution_clock),
            availableCash_clock = maxOf(local.availableCash_clock, remote.availableCash_clock)
        )
    }
}
