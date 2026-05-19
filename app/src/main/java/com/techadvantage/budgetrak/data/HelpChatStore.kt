package com.techadvantage.budgetrak.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class HelpChatMessage(
    val timestamp: Long,
    val fromUser: Boolean,
    val text: String,
)

/**
 * Local persistence for the in-app Help Chat. One active chat at a time,
 * keyed by [chatId] (generated when the first message is appended, cleared
 * on [clear]). Messages older than [TTL_MS] are pruned on every load.
 *
 * File format (filesDir/help_chat.json):
 *   { "chatId": "<uuid>", "messages": [ {"t": <ms>, "u": <bool>, "x": "<text>"}, ... ] }
 *
 * Firebase upload + the 24-hour backstop are wired in a later phase — this
 * class exposes only the local side for now.
 */
object HelpChatStore {
    private const val FILE_NAME = "help_chat.json"
    private const val TTL_MS = 48L * 60 * 60 * 1000  // 48 hours

    private val _messages = MutableStateFlow<List<HelpChatMessage>>(emptyList())
    val messages: StateFlow<List<HelpChatMessage>> = _messages.asStateFlow()

    private val _chatId = MutableStateFlow<String?>(null)
    val chatId: StateFlow<String?> = _chatId.asStateFlow()

    // Upload-tracking state. `lastUploadAt` powers the 5-minute debounce in
    // [HelpChatUploader.uploadIfStale]; `lastUploadedCount` is the message
    // count at last successful upload — `messages.size > lastUploadedCount`
    // means new content has landed since the last write. Both persist to
    // JSON so app restarts don't reset them.
    private val _lastUploadAt = MutableStateFlow(0L)
    val lastUploadAt: StateFlow<Long> = _lastUploadAt.asStateFlow()

    private val _lastUploadedCount = MutableStateFlow(0)

    fun isDirty(): Boolean = _messages.value.size > _lastUploadedCount.value

    private val mutex = Mutex()
    @Volatile private var loaded: Boolean = false

    suspend fun load(context: Context) {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            withContext(Dispatchers.IO) {
                val file = File(context.filesDir, FILE_NAME)
                if (!file.exists()) {
                    loaded = true
                    return@withContext
                }
                val raw = runCatching { file.readText() }.getOrNull()
                if (raw.isNullOrBlank()) {
                    loaded = true
                    return@withContext
                }
                val obj = runCatching { JSONObject(raw) }.getOrNull()
                if (obj == null) {
                    loaded = true
                    return@withContext
                }
                val id = obj.optString("chatId").takeIf { it.isNotEmpty() }
                val arr = obj.optJSONArray("messages") ?: JSONArray()
                val cutoff = System.currentTimeMillis() - TTL_MS
                val msgs = buildList {
                    for (i in 0 until arr.length()) {
                        val m = arr.optJSONObject(i) ?: continue
                        val t = m.optLong("t", 0L)
                        if (t < cutoff) continue
                        add(
                            HelpChatMessage(
                                timestamp = t,
                                fromUser = m.optBoolean("u", false),
                                text = m.optString("x", ""),
                            )
                        )
                    }
                }
                if (msgs.isEmpty()) {
                    _chatId.value = null
                    _messages.value = emptyList()
                    _lastUploadAt.value = 0L
                    _lastUploadedCount.value = 0
                    file.delete()
                } else {
                    _chatId.value = id
                    _messages.value = msgs
                    _lastUploadAt.value = obj.optLong("lastUploadAt", 0L)
                    // Clamp lastUploadedCount to current message count —
                    // pruning may have dropped messages that were uploaded.
                    _lastUploadedCount.value = obj.optInt("lastUploadedCount", 0)
                        .coerceAtMost(msgs.size)
                    if (msgs.size < arr.length()) persistLocked(context)
                }
                loaded = true
            }
        }
    }

    suspend fun addMessage(context: Context, fromUser: Boolean, text: String) {
        mutex.withLock {
            if (_chatId.value == null) _chatId.value = UUID.randomUUID().toString()
            _messages.value = _messages.value + HelpChatMessage(
                timestamp = System.currentTimeMillis(),
                fromUser = fromUser,
                text = text,
            )
            withContext(Dispatchers.IO) { persistLocked(context) }
        }
    }

    /**
     * Clears the local buffer and chatId. Firebase upload of the previous
     * transcript is handled by the caller — capture a snapshot via
     * [messages] + [chatId] and hand it to [HelpChatUploader.uploadNow]
     * BEFORE invoking this so the discarded content still lands in the log.
     */
    suspend fun clear(context: Context) {
        mutex.withLock {
            _messages.value = emptyList()
            _chatId.value = null
            _lastUploadAt.value = 0L
            _lastUploadedCount.value = 0
            withContext(Dispatchers.IO) {
                File(context.filesDir, FILE_NAME).delete()
            }
        }
    }

    /**
     * Records a successful upload of the current message list. Resets the
     * dirty flag and arms the 5-minute debounce.
     */
    suspend fun markUploaded(context: Context) {
        mutex.withLock {
            _lastUploadAt.value = System.currentTimeMillis()
            _lastUploadedCount.value = _messages.value.size
            withContext(Dispatchers.IO) { persistLocked(context) }
        }
    }

    private fun persistLocked(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        val obj = JSONObject()
        obj.put("chatId", _chatId.value ?: "")
        val arr = JSONArray()
        _messages.value.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("t", m.timestamp)
                    put("u", m.fromUser)
                    put("x", m.text)
                }
            )
        }
        obj.put("messages", arr)
        obj.put("lastUploadAt", _lastUploadAt.value)
        obj.put("lastUploadedCount", _lastUploadedCount.value)
        file.writeText(obj.toString())
    }
}
