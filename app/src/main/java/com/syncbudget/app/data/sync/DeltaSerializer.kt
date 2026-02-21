package com.syncbudget.app.data.sync

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class FieldDelta(
    val value: Any?,
    val clock: Long
)

data class RecordDelta(
    val type: String,
    val action: String,
    val id: Int,
    val deviceId: String,
    val fields: Map<String, FieldDelta>
)

data class DeltaPacket(
    val sourceDeviceId: String,
    val timestamp: Instant,
    val changes: List<RecordDelta>
)

object DeltaSerializer {

    fun serialize(packet: DeltaPacket): JSONObject {
        val json = JSONObject()
        json.put("sourceDeviceId", packet.sourceDeviceId)
        json.put("timestamp", packet.timestamp.toString())
        val changesArray = JSONArray()
        for (delta in packet.changes) {
            val deltaJson = JSONObject()
            deltaJson.put("type", delta.type)
            deltaJson.put("action", delta.action)
            deltaJson.put("id", delta.id)
            deltaJson.put("deviceId", delta.deviceId)
            val fieldsJson = JSONObject()
            for ((key, fd) in delta.fields) {
                val fdJson = JSONObject()
                fdJson.put("value", fd.value ?: JSONObject.NULL)
                fdJson.put("clock", fd.clock)
                fieldsJson.put(key, fdJson)
            }
            deltaJson.put("fields", fieldsJson)
            changesArray.put(deltaJson)
        }
        json.put("changes", changesArray)
        return json
    }

    fun deserialize(json: JSONObject): DeltaPacket {
        val sourceDeviceId = json.getString("sourceDeviceId")
        val timestamp = Instant.parse(json.getString("timestamp"))
        val changesArray = json.getJSONArray("changes")
        val changes = mutableListOf<RecordDelta>()
        for (i in 0 until changesArray.length()) {
            val deltaJson = changesArray.getJSONObject(i)
            val fieldsJson = deltaJson.getJSONObject("fields")
            val fields = mutableMapOf<String, FieldDelta>()
            for (key in fieldsJson.keys()) {
                val fdJson = fieldsJson.getJSONObject(key)
                val value = if (fdJson.isNull("value")) null else fdJson.get("value")
                fields[key] = FieldDelta(value, fdJson.getLong("clock"))
            }
            changes.add(
                RecordDelta(
                    type = deltaJson.getString("type"),
                    action = deltaJson.getString("action"),
                    id = deltaJson.getInt("id"),
                    deviceId = deltaJson.getString("deviceId"),
                    fields = fields
                )
            )
        }
        return DeltaPacket(sourceDeviceId, timestamp, changes)
    }
}
