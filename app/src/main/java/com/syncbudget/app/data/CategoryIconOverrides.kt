package com.syncbudget.app.data

import android.content.Context

object CategoryIconOverrides {

    private const val PREFS_NAME = "category_icon_overrides"

    fun save(context: Context, overrides: Map<Int, String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.clear()
        for ((id, iconName) in overrides) {
            editor.putString(id.toString(), iconName)
        }
        editor.apply()
    }

    fun load(context: Context): Map<Int, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val result = mutableMapOf<Int, String>()
        for ((key, value) in prefs.all) {
            val id = key.toIntOrNull() ?: continue
            if (value is String) result[id] = value
        }
        return result
    }

    fun getIcon(context: Context, category: Category): String {
        val overrides = load(context)
        return overrides[category.id] ?: category.iconName
    }
}
