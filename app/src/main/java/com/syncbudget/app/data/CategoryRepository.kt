package com.syncbudget.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object CategoryRepository {

    private const val FILE_NAME = "categories.json"

    fun save(context: Context, categories: List<Category>) {
        val jsonArray = JSONArray()
        for (c in categories) {
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("iconName", c.iconName)
            jsonArray.put(obj)
        }
        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { fos ->
            fos.write(jsonArray.toString().toByteArray())
        }
    }

    fun load(context: Context): List<Category> {
        val file = context.getFileStreamPath(FILE_NAME)
        if (!file.exists()) return emptyList()
        val json = context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
        if (json.isBlank()) return emptyList()
        val jsonArray = JSONArray(json)
        val list = mutableListOf<Category>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                Category(
                    id = obj.getInt("id"),
                    name = obj.getString("name"),
                    iconName = obj.getString("iconName")
                )
            )
        }
        return list
    }
}
