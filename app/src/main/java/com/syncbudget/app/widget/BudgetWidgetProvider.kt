package com.syncbudget.app.widget

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
import com.syncbudget.app.MainActivity
import com.syncbudget.app.R
import com.syncbudget.app.ui.components.CURRENCY_DECIMALS

class BudgetWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
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
        const val ACTION_ADD_INCOME = "com.syncbudget.app.widget.ADD_INCOME"
        const val ACTION_ADD_EXPENSE = "com.syncbudget.app.widget.ADD_EXPENSE"

        fun updateAllWidgets(context: Context) {
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
            val availableCash = prefs.getString("availableCash", "0.0")?.toDoubleOrNull() ?: 0.0
            val currencySymbol = prefs.getString("currencySymbol", "$") ?: "$"
            val showDecimals = prefs.getBoolean("showDecimals", false)
            val decimalPlaces = if (showDecimals) (CURRENCY_DECIMALS[currencySymbol] ?: 2) else 0
            val digitCount = prefs.getInt("digitCount", 3)
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

            val result = WidgetRenderer.render(
                widgetWidth = widthPx,
                maxHeight = maxSolariHeight,
                isDarkMode = isDarkMode,
                isPaidUser = isPaidUser,
                amount = availableCash,
                currencySymbol = currencySymbol,
                decimalPlaces = decimalPlaces,
                minDigitCount = digitCount
            )

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

            // Tap Solari display or logo → open app
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
