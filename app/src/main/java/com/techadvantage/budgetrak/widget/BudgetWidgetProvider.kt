package com.techadvantage.budgetrak.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.work.WorkManager
import com.techadvantage.budgetrak.MainActivity
import com.techadvantage.budgetrak.R
import com.techadvantage.budgetrak.data.BudgetPeriod
import com.techadvantage.budgetrak.data.getDoubleCompat
import com.techadvantage.budgetrak.ui.components.CURRENCY_DECIMALS
import java.util.Calendar

class BudgetWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
        // Ensure background refresh is scheduled whenever widgets exist
        com.techadvantage.budgetrak.data.sync.BackgroundSyncWorker.schedule(context)
        scheduleResetAlarm(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_RESET_REFRESH) {
            // Budget period just reset — trigger immediate refresh
            com.techadvantage.budgetrak.data.sync.BackgroundSyncWorker.runOnce(context)
            // Schedule the next reset alarm
            scheduleResetAlarm(context)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // BackgroundSyncWorker serves broader purpose (data sync + period refresh),
        // so we do NOT cancel it when the last widget is removed.
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        const val ACTION_ADD_INCOME = "com.techadvantage.budgetrak.widget.ADD_INCOME"
        const val ACTION_ADD_EXPENSE = "com.techadvantage.budgetrak.widget.ADD_EXPENSE"
        private const val ACTION_RESET_REFRESH = "com.techadvantage.budgetrak.widget.RESET_REFRESH"

        /**
         * Schedule an exact alarm at the next budget reset time so the
         * widget updates promptly when the period flips.
         */
        fun scheduleResetAlarm(context: Context) {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val budgetPeriod = try {
                BudgetPeriod.valueOf(prefs.getString("budgetPeriod", "DAILY") ?: "DAILY")
            } catch (_: Exception) { BudgetPeriod.DAILY }
            val resetHour = if (budgetPeriod == BudgetPeriod.DAILY) prefs.getInt("resetHour", 0) else 0
            val resetDayOfWeek = prefs.getInt("resetDayOfWeek", 7)
            val resetDayOfMonth = prefs.getInt("resetDayOfMonth", 1)

            val syncPrefs = context.getSharedPreferences("sync_engine", Context.MODE_PRIVATE)
            val isSynced = syncPrefs.getString("groupId", null) != null
            val familyTz = if (isSynced) {
                // Read familyTimezone from shared_settings.json
                try {
                    com.techadvantage.budgetrak.data.SharedSettingsRepository.load(context).familyTimezone
                } catch (_: Exception) { "" }
            } else ""
            val tz = if (familyTz.isNotEmpty()) java.util.TimeZone.getTimeZone(familyTz) else java.util.TimeZone.getDefault()
            val cal = Calendar.getInstance(tz)
            when (budgetPeriod) {
                BudgetPeriod.DAILY -> {
                    cal.set(Calendar.HOUR_OF_DAY, resetHour)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 30)
                    if (cal.timeInMillis <= System.currentTimeMillis()) {
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                BudgetPeriod.WEEKLY -> {
                    // Calendar uses Sunday=1, our resetDayOfWeek uses Monday=1..Sunday=7
                    val calDay = if (resetDayOfWeek == 7) Calendar.SUNDAY
                        else resetDayOfWeek + 1
                    cal.set(Calendar.DAY_OF_WEEK, calDay)
                    cal.set(Calendar.HOUR_OF_DAY, resetHour)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 30)
                    if (cal.timeInMillis <= System.currentTimeMillis()) {
                        cal.add(Calendar.WEEK_OF_YEAR, 1)
                    }
                }
                BudgetPeriod.MONTHLY -> {
                    cal.set(Calendar.DAY_OF_MONTH,
                        resetDayOfMonth.coerceAtMost(cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
                    cal.set(Calendar.HOUR_OF_DAY, resetHour)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 30)
                    if (cal.timeInMillis <= System.currentTimeMillis()) {
                        cal.add(Calendar.MONTH, 1)
                        cal.set(Calendar.DAY_OF_MONTH,
                            resetDayOfMonth.coerceAtMost(cal.getActualMaximum(Calendar.DAY_OF_MONTH)))
                    }
                }
            }

            val intent = Intent(context, BudgetWidgetProvider::class.java).apply {
                action = ACTION_RESET_REFRESH
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent
                )
            } catch (_: SecurityException) {
                // Exact alarms not permitted on Android 12+ without SCHEDULE_EXACT_ALARM
                // Fall back to inexact — still better than nothing
                alarmManager.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            }
        }

        private var lastWidgetRedraw = 0L
        @Volatile private var pendingRedraw = false
        private const val WIDGET_THROTTLE_MS = 5_000L

        /**
         * Throttled widget update. Redraws at most once every 5 seconds.
         * If a call arrives within the throttle window, one deferred redraw
         * is scheduled — additional calls during the wait are dropped.
         */
        fun updateAllWidgets(context: Context) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastWidgetRedraw
            if (elapsed >= WIDGET_THROTTLE_MS) {
                doUpdateAllWidgets(context)
                return
            }
            // Within throttle window — schedule one deferred redraw
            if (pendingRedraw) return  // already queued, don't stack
            pendingRedraw = true
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                pendingRedraw = false
                doUpdateAllWidgets(context)
            }, WIDGET_THROTTLE_MS - elapsed)
        }

        private fun doUpdateAllWidgets(context: Context) {
            lastWidgetRedraw = System.currentTimeMillis()
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, BudgetWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val isPaidUser = prefs.getBoolean("isPaidUser", false)
            val availableCash = prefs.getDoubleCompat("availableCash")
            val currencySymbol = prefs.getString("currencySymbol", "$") ?: "$"
            val showDecimals = prefs.getBoolean("showDecimals", false)
            val decimalPlaces = if (showDecimals) (CURRENCY_DECIMALS[currencySymbol] ?: 2) else 0
            // Auto-calculate digit count from actual amount, matching the app's MainScreen logic
            var div = 1; repeat(decimalPlaces) { div *= 10 }
            val wholePart = (kotlin.math.round(kotlin.math.abs(availableCash) * div).toLong() / div).toInt()
            val digitCount = maxOf(1, wholePart.toString().length)
            val showLogo = prefs.getBoolean("showWidgetLogo", true)

            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            // Get widget size in dp
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250))
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110))

            // Convert dp to pixels
            val density = context.resources.displayMetrics.density
            val widthPx = (widthDp * density).toInt().coerceAtLeast(200)
            val heightPx = (heightDp * density).toInt().coerceAtLeast(100)

            // Detect dark/light mode
            val nightMode = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
            val isDarkMode = nightMode == Configuration.UI_MODE_NIGHT_YES

            // Solari gets up to 75% of widget height; bitmap auto-sizes to card content
            val maxSolariHeight = (heightPx * 3) / 4

            // Get translated upgrade text for non-paid widget overlay
            val appLang = prefs.getString("appLanguage", "en") ?: "en"
            val widgetStrings = if (appLang == "es")
                com.techadvantage.budgetrak.ui.strings.SpanishStrings else com.techadvantage.budgetrak.ui.strings.EnglishStrings
            val result = WidgetRenderer.render(
                widgetWidth = widthPx,
                maxHeight = maxSolariHeight,
                isDarkMode = isDarkMode,
                isPaidUser = isPaidUser,
                amount = availableCash,
                currencySymbol = currencySymbol,
                decimalPlaces = decimalPlaces,
                minDigitCount = digitCount,
                upgradeText = widgetStrings.dashboard.upgradeForFullWidget
            )

            if (result == null) {
                // Render failed (OOM or invalid dimensions) — skip bitmap update
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }
            views.setImageViewBitmap(R.id.widget_solari, result.bitmap)

            // Toggle logo visibility
            val logoVisible = showLogo
            views.setViewVisibility(R.id.widget_logo,
                if (logoVisible) View.VISIBLE else View.GONE)
            if (logoVisible) {
                views.setInt(R.id.widget_logo, "setColorFilter", WidgetRenderer.LOGO_TINT)
            }

            // Align button bar horizontally with Solari card edges
            val leftPad = result.leftCardEdge
            val rightPad = (widthPx - result.rightCardEdge).coerceAtLeast(0)
            views.setViewPadding(R.id.widget_button_bar, leftPad, 0, rightPad, 0)

            // Tap Solari display or logo → open app (singleTask reuses existing)
            val openIntent = Intent(context, MainActivity::class.java)
            val openPending = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_solari, openPending)
            views.setOnClickPendingIntent(R.id.widget_logo, openPending)

            // + button
            val incomeIntent = Intent(context, WidgetTransactionActivity::class.java).apply {
                action = ACTION_ADD_INCOME
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val incomePending = PendingIntent.getActivity(
                context, 1, incomeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_add_income, incomePending)

            // - button
            val expenseIntent = Intent(context, WidgetTransactionActivity::class.java).apply {
                action = ACTION_ADD_EXPENSE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val expensePending = PendingIntent.getActivity(
                context, 2, expenseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_add_expense, expensePending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
