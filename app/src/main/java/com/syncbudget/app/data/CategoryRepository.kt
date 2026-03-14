package com.syncbudget.app.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object CategoryRepository {

    private const val FILE_NAME = "categories.json"
    private const val TAG = "CategoryRepo"

    fun save(context: Context, categories: List<Category>) {
        val jsonArray = JSONArray()
        for (c in categories) {
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("iconName", c.iconName)
            obj.put("tag", c.tag)
            obj.put("charted", c.charted)
            obj.put("widgetVisible", c.widgetVisible)
            // Sync fields
            obj.put("deviceId", c.deviceId)
            obj.put("deleted", c.deleted)
            obj.put("name_clock", c.name_clock)
            obj.put("iconName_clock", c.iconName_clock)
            obj.put("tag_clock", c.tag_clock)
            obj.put("charted_clock", c.charted_clock)
            obj.put("widgetVisible_clock", c.widgetVisible_clock)
            obj.put("deleted_clock", c.deleted_clock)
            obj.put("deviceId_clock", c.deviceId_clock)
            jsonArray.put(obj)
        }
        SafeIO.atomicWriteJson(context, FILE_NAME, jsonArray)
    }

    fun load(context: Context): List<Category> {
        val jsonArray = SafeIO.readJsonArray(context, FILE_NAME)
        val list = mutableListOf<Category>()
        for (i in 0 until jsonArray.length()) {
            try {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    Category(
                        id = obj.getInt("id"),
                        name = obj.getString("name"),
                        iconName = obj.getString("iconName"),
                        tag = obj.optString("tag", ""),
                        charted = obj.optBoolean("charted", true),
                        widgetVisible = obj.optBoolean("widgetVisible", true),
                        deviceId = obj.optString("deviceId", ""),
                        deleted = obj.optBoolean("deleted", false),
                        name_clock = obj.optLong("name_clock", 0L),
                        iconName_clock = obj.optLong("iconName_clock", 0L),
                        tag_clock = obj.optLong("tag_clock", 0L),
                        charted_clock = obj.optLong("charted_clock", 0L),
                        widgetVisible_clock = obj.optLong("widgetVisible_clock", 0L),
                        deleted_clock = obj.optLong("deleted_clock", 0L),
                        deviceId_clock = obj.optLong("deviceId_clock", 0L)
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping corrupt record at index $i: ${e.message}")
            }
        }
        return list
    }
}
