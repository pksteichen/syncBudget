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
import java.time.LocalDate
import java.util.UUID

data class HelpChatMessage(
    val timestamp: Long,
    val fromUser: Boolean,
    val text: String,
    /**
     * 1-10 sentiment score the Gemini model assigned to the USER message
     * that prompted this BOT reply (1=very negative, 10=very positive).
     * Only set on bot messages produced by a successful Gemini call;
     * null on user messages, error-replies, and review-prompt messages.
     * Used for review-prompt gating and stored as metadata in the
     * uploaded log so we can audit Gemini's sentiment judgment.
     */
    val sentiment: Int? = null,
    /**
     * True for the bot-authored Play Store review request inserted after
     * a 9-10 sentiment turn (2-day debounce). The dialog renders these
     * as a tappable surface that opens the Play Store listing.
     */
    val isReviewPrompt: Boolean = false,
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

    // Per-tier daily Gemini-reply caps. Counted against SUCCESSFUL replies
    // only — transient failures don't burn the user's quota. Reset at local
    // midnight (system default zone).
    const val DAILY_CAP_FREE = 10
    const val DAILY_CAP_PAID = 25
    const val DAILY_CAP_SUBSCRIBER = 50

    // Review-prompt config. When the model scores a user message at
    // SENTIMENT_REVIEW_THRESHOLD or above AND the last in-chat review
    // prompt was shown more than REVIEW_PROMPT_DEBOUNCE_MS ago, the
    // dialog appends a tappable Play Store review request as a second
    // bot message. The debounce lives in SharedPrefs (not the chat
    // JSON) so it survives Clear and the 48-hour buffer prune.
    const val SENTIMENT_REVIEW_THRESHOLD = 9
    private const val REVIEW_PROMPT_DEBOUNCE_MS = 2L * 24 * 60 * 60 * 1000  // 2 days
    private const val REVIEW_PROMPT_PREF_KEY = "helpChatReviewPromptAt"

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

    // Daily-cap state. `_dailyCount` is the number of successful Gemini
    // replies received today; `_dailyResetEpochDay` is the LocalDate epoch
    // day on which `_dailyCount` was last reset. Both persist to the same
    // JSON file as messages so a crash mid-day doesn't lose the cap state.
    private val _dailyCount = MutableStateFlow(0)
    val dailyCount: StateFlow<Int> = _dailyCount.asStateFlow()
    private val _dailyResetEpochDay = MutableStateFlow(0L)

    fun isDirty(): Boolean = _messages.value.size > _lastUploadedCount.value

    /**
     * Daily cap for this device's current tier. Subscriber beats Paid beats
     * Free. Reads SharedPreferences directly so the dialog doesn't have to
     * plumb the tier flags through.
     */
    fun getDailyCap(context: Context): Int {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isSub = prefs.getBoolean("isSubscriber", false)
        val isPaid = prefs.getBoolean("isPaidUser", false)
        return when {
            isSub  -> DAILY_CAP_SUBSCRIBER
            isPaid -> DAILY_CAP_PAID
            else   -> DAILY_CAP_FREE
        }
    }

    /**
     * Reset the daily counter when the local date has changed since the
     * last successful reply. Caller must hold [mutex]; we don't persist
     * here because the caller will persist on its own write path.
     */
    private fun maybeResetCounterLocked() {
        val today = LocalDate.now().toEpochDay()
        if (_dailyResetEpochDay.value != today) {
            _dailyCount.value = 0
            _dailyResetEpochDay.value = today
        }
    }

    /** Number of replies still allowed today for the current tier. */
    suspend fun remainingToday(context: Context): Int {
        mutex.withLock {
            maybeResetCounterLocked()
            return (getDailyCap(context) - _dailyCount.value).coerceAtLeast(0)
        }
    }

    /** Returns true iff a fresh Send is allowed under today's cap. */
    suspend fun canSendToday(context: Context): Boolean = remainingToday(context) > 0

    /**
     * Bump the daily counter after a SUCCESSFUL Gemini reply. Resets on day
     * rollover before bumping so the count starts from 0 each local day.
     */
    suspend fun incrementDailyCount(context: Context) {
        mutex.withLock {
            maybeResetCounterLocked()
            _dailyCount.value += 1
            withContext(Dispatchers.IO) { persistLocked(context) }
        }
    }

    /**
     * True iff the model's sentiment score meets the review-prompt
     * threshold AND the last in-chat review prompt was shown more than
     * 2 days ago. The debounce uses `app_prefs` (not the chat JSON) so
     * the user can't bypass it by Clear-ing the chat.
     */
    fun shouldShowReviewPrompt(context: Context, sentiment: Int): Boolean {
        if (sentiment < SENTIMENT_REVIEW_THRESHOLD) return false
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val last = prefs.getLong(REVIEW_PROMPT_PREF_KEY, 0L)
        return (System.currentTimeMillis() - last) > REVIEW_PROMPT_DEBOUNCE_MS
    }

    /** Stamps "now" as the last time the in-chat review prompt was shown. */
    fun markReviewPromptShown(context: Context) {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putLong(REVIEW_PROMPT_PREF_KEY, System.currentTimeMillis())
            .apply()
    }

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
                        // `s` (sentiment) and `r` (isReviewPrompt) are
                        // optional fields added after v1; older messages
                        // load with the data-class defaults (null / false).
                        val sentiment = if (m.has("s")) m.optInt("s", 5).coerceIn(1, 10) else null
                        add(
                            HelpChatMessage(
                                timestamp = t,
                                fromUser = m.optBoolean("u", false),
                                text = m.optString("x", ""),
                                sentiment = sentiment,
                                isReviewPrompt = m.optBoolean("r", false),
                            )
                        )
                    }
                }
                // Daily-cap state survives message pruning — a user who hit
                // their cap and waited 48 h for messages to age out still
                // shouldn't get a free bonus reply via prune.
                _dailyCount.value = obj.optInt("dailyCount", 0)
                _dailyResetEpochDay.value = obj.optLong("dailyResetEpochDay", 0L)
                if (msgs.isEmpty()) {
                    _chatId.value = null
                    _messages.value = emptyList()
                    _lastUploadAt.value = 0L
                    _lastUploadedCount.value = 0
                    // Daily-cap state survives the local wipe — we only
                    // delete the file when message AND cap state are stale.
                    if (_dailyCount.value == 0) {
                        file.delete()
                    } else {
                        persistLocked(context)
                    }
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

    suspend fun addMessage(
        context: Context,
        fromUser: Boolean,
        text: String,
        sentiment: Int? = null,
        isReviewPrompt: Boolean = false,
    ) {
        mutex.withLock {
            if (_chatId.value == null) _chatId.value = UUID.randomUUID().toString()
            _messages.value = _messages.value + HelpChatMessage(
                timestamp = System.currentTimeMillis(),
                fromUser = fromUser,
                text = text,
                sentiment = sentiment,
                isReviewPrompt = isReviewPrompt,
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
            // Daily count is INTENTIONALLY preserved across Clear so the
            // cap can't be bypassed by repeatedly resetting the chat.
            withContext(Dispatchers.IO) {
                if (_dailyCount.value == 0) {
                    File(context.filesDir, FILE_NAME).delete()
                } else {
                    persistLocked(context)
                }
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
                    m.sentiment?.let { put("s", it) }
                    if (m.isReviewPrompt) put("r", true)
                }
            )
        }
        obj.put("messages", arr)
        obj.put("lastUploadAt", _lastUploadAt.value)
        obj.put("lastUploadedCount", _lastUploadedCount.value)
        obj.put("dailyCount", _dailyCount.value)
        obj.put("dailyResetEpochDay", _dailyResetEpochDay.value)
        file.writeText(obj.toString())
    }
}
