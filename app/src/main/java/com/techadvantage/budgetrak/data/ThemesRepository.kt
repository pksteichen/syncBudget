package com.techadvantage.budgetrak.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.techadvantage.budgetrak.ui.theme.BuiltInThemes
import com.techadvantage.budgetrak.ui.theme.ThemeColorSet
import com.techadvantage.budgetrak.ui.theme.ThemeProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists user-created custom color themes to `themes.json`. The built-in
 * Default profile lives in code (`DefaultThemeProfile.DEFAULT`) so it can
 * never be corrupted or deleted; only user-created profiles are written.
 *
 * Storage: JSONArray of profile objects. Backup mode of FullBackupSerializer
 * picks this up; joinSnapshot deliberately does not, so themes survive
 * group-join.
 *
 * Selection lives in `app_prefs` under `selectedThemeName`.
 */
object ThemesRepository {
    private const val FILE_NAME = "themes.json"
    private const val PREFS = "app_prefs"
    private const val KEY_SELECTED = "selectedThemeName"

    /** All available profiles — built-ins first, then user-created in saved order. */
    fun load(context: Context): List<ThemeProfile> {
        return BuiltInThemes.ALL + loadUserProfiles(context)
    }

    fun getSelected(context: Context): ThemeProfile {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED, BuiltInThemes.DEFAULT.name)
            ?: BuiltInThemes.DEFAULT.name
        return load(context).firstOrNull { it.name == name } ?: BuiltInThemes.DEFAULT
    }

    fun setSelected(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SELECTED, name).apply()
    }

    /** Replace the full user-profile list (Default is implicit and excluded). */
    fun saveUserProfiles(context: Context, profiles: List<ThemeProfile>) {
        val arr = JSONArray()
        profiles.filter { !it.isBuiltIn }.forEach { arr.put(toJson(it)) }
        SafeIO.atomicWriteJson(context, FILE_NAME, arr)
    }

    private fun loadUserProfiles(context: Context): List<ThemeProfile> {
        val file = context.getFileStreamPath(FILE_NAME)
        if (!file.exists()) return emptyList()
        val text = try { file.readText() } catch (_: Exception) { return emptyList() }
        if (text.isBlank()) return emptyList()
        val arr = try { JSONArray(text) } catch (_: Exception) { return emptyList() }
        val out = mutableListOf<ThemeProfile>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            fromJson(obj)?.let { out.add(it) }
        }
        return out
    }

    private fun toJson(p: ThemeProfile): JSONObject {
        val o = JSONObject()
        o.put("name", p.name)
        o.put("light", colorSetToJson(p.light))
        o.put("dark", colorSetToJson(p.dark))
        o.put("chartLight", chartToJson(p.chartLight))
        o.put("chartDark", chartToJson(p.chartDark))
        return o
    }

    private fun fromJson(o: JSONObject): ThemeProfile? {
        val name = o.optString("name").takeIf { it.isNotBlank() } ?: return null
        // Reject any user profile that collides with a built-in name.
        if (BuiltInThemes.ALL.any { it.name.equals(name, ignoreCase = true) }) return null
        val light = colorSetFromJson(o.optJSONObject("light") ?: return null) ?: return null
        val dark = colorSetFromJson(o.optJSONObject("dark") ?: return null) ?: return null
        val chartLight = chartFromJson(o.optJSONArray("chartLight")) ?: return null
        val chartDark = chartFromJson(o.optJSONArray("chartDark")) ?: return null
        return ThemeProfile(
            name = name, isBuiltIn = false,
            light = light, dark = dark,
            chartLight = chartLight, chartDark = chartDark,
        )
    }

    private fun chartToJson(c: List<Color>): JSONArray {
        val arr = JSONArray()
        c.forEach { arr.put(colorToHex(it)) }
        return arr
    }

    private fun chartFromJson(arr: JSONArray?): List<Color>? {
        if (arr == null || arr.length() != 12) return null
        return (0 until 12).map { parseHex(arr.optString(it)) ?: return null }
    }

    private fun colorSetToJson(c: ThemeColorSet): JSONObject {
        val o = JSONObject()
        o.put("primary", colorToHex(c.primary))
        o.put("cardBackground", colorToHex(c.cardBackground))
        o.put("cardText", colorToHex(c.cardText))
        o.put("background", colorToHex(c.background))
        o.put("surface", colorToHex(c.surface))
        o.put("onSurface", colorToHex(c.onSurface))
        o.put("displayBackground", colorToHex(c.displayBackground))
        o.put("displayBorder", colorToHex(c.displayBorder))
        o.put("incomeGreen", colorToHex(c.incomeGreen))
        o.put("expenseRed", colorToHex(c.expenseRed))
        return o
    }

    private fun colorSetFromJson(o: JSONObject): ThemeColorSet? {
        return ThemeColorSet(
            primary = parseHex(o.optString("primary")) ?: return null,
            cardBackground = parseHex(o.optString("cardBackground")) ?: return null,
            cardText = parseHex(o.optString("cardText")) ?: return null,
            background = parseHex(o.optString("background")) ?: return null,
            surface = parseHex(o.optString("surface")) ?: return null,
            onSurface = parseHex(o.optString("onSurface")) ?: return null,
            displayBackground = parseHex(o.optString("displayBackground")) ?: return null,
            displayBorder = parseHex(o.optString("displayBorder")) ?: return null,
            incomeGreen = parseHex(o.optString("incomeGreen")) ?: return null,
            expenseRed = parseHex(o.optString("expenseRed")) ?: return null,
        )
    }

    private fun colorToHex(c: Color): String =
        String.format("#%08X", c.toArgb())

    private fun parseHex(s: String?): Color? {
        if (s.isNullOrBlank()) return null
        val hex = s.removePrefix("#")
        return try {
            when (hex.length) {
                6 -> Color(android.graphics.Color.parseColor("#FF$hex"))
                8 -> Color(android.graphics.Color.parseColor("#$hex"))
                else -> null
            }
        } catch (_: Exception) { null }
    }
}
