package com.techadvantage.budgetrak.data

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.techadvantage.budgetrak.data.sync.ReceiptManager
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    private const val TAG = "BackupManager"
    private const val SYSTEM_SUFFIX = "_system.enc"
    private const val PHOTOS_SUFFIX = "_photos.enc"
    private const val PREFS_NAME = "backup_prefs"
    private val PHOTOS_MAGIC = byteArrayOf(0x42, 0x4B, 0x50, 0x48) // "BKPH"
    private const val PHOTOS_VERSION = 1

    data class BackupEntry(
        val date: String,
        val systemFile: File,
        val photosFile: File?,
        val systemSizeBytes: Long,
        val photosSizeBytes: Long
    )

    // ── Directory Helpers ───────────────────────────────────────

    fun getBudgetrakDir(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, "BudgeTrak")
        dir.mkdirs()
        return dir
    }

    fun getSupportDir(): File {
        val dir = File(getBudgetrakDir(), "support")
        dir.mkdirs()
        return dir
    }

    fun getBackupDir(): File {
        val dir = File(getBudgetrakDir(), "backups")
        dir.mkdirs()
        return dir
    }

    // ── Backup Creation ─────────────────────────────────────────

    private fun checkDiskSpace(context: Context): Boolean {
        val backupDir = getBackupDir()
        val usable = backupDir.usableSpace
        // Require at least 50MB free for system backup + photos
        return usable > 50 * 1024 * 1024
    }

    fun performBackup(context: Context, password: CharArray): Result<Pair<File, File?>> {
        return try {
            if (!checkDiskSpace(context)) {
                return Result.failure(java.io.IOException("Insufficient storage space for backup (need 50MB free)"))
            }
            val systemResult = createSystemBackup(context, password)
            if (systemResult.isFailure) return Result.failure(systemResult.exceptionOrNull()!!)

            val systemFile = systemResult.getOrThrow()
            val tag = systemFile.name.removePrefix("backup_").removeSuffix(SYSTEM_SUFFIX)
            val photosResult = createPhotosBackup(context, password, tag)
            val photosFile = photosResult.getOrNull()

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("last_backup_date", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
                .putBoolean("last_backup_success", true)
                .apply()

            enforceRetention(context)
            Result.success(Pair(systemResult.getOrThrow(), photosFile))
        } catch (e: Exception) {
            Log.w(TAG, "Backup failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun nextAvailableSuffix(dir: File, dateStr: String, fileSuffix: String): String {
        val base = File(dir, "backup_${dateStr}${fileSuffix}")
        if (!base.exists()) return dateStr
        for (c in 'b'..'z') {
            val candidate = File(dir, "backup_${dateStr}${c}${fileSuffix}")
            if (!candidate.exists()) return "${dateStr}${c}"
        }
        return "${dateStr}z"
    }

    fun createSystemBackup(context: Context, password: CharArray): Result<File> {
        return try {
            val json = FullBackupSerializer.serialize(context)
            val encrypted = CryptoHelper.encrypt(json.toByteArray(Charsets.UTF_8), password)
            val dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val tag = nextAvailableSuffix(getBackupDir(), dateStr, SYSTEM_SUFFIX)
            val file = File(getBackupDir(), "backup_${tag}${SYSTEM_SUFFIX}")
            file.writeBytes(encrypted)
            Log.d(TAG, "System backup created: ${file.name} (${encrypted.size} bytes)")
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun createPhotosBackup(context: Context, password: CharArray, tag: String? = null): Result<File?> {
        return try {
            val transactions = TransactionRepository.load(context)
            val allReceiptIds = ReceiptManager.collectAllReceiptIds(transactions)
            val localReceipts = allReceiptIds.filter { ReceiptManager.hasLocalFile(context, it) }

            if (localReceipts.isEmpty()) return Result.success(null)

            // Derive key from password (one-time PBKDF2)
            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)
            val derivedKey = CryptoHelper.deriveKey(password, salt)

            val tempEntries = File(context.cacheDir, "backup_entries.tmp")
            val effectiveTag = tag ?: nextAvailableSuffix(getBackupDir(),
                LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE), PHOTOS_SUFFIX)
            val finalFile = File(getBackupDir(), "backup_${effectiveTag}${PHOTOS_SUFFIX}")

            // Build entries to temp file
            val manifestEntries = JSONArray()
            var currentOffset = 0L

            tempEntries.outputStream().buffered().use { out ->
                for (receiptId in localReceipts) {
                    val receiptFile = ReceiptManager.getReceiptFile(context, receiptId)
                    if (!receiptFile.exists()) continue
                    val plaintext = receiptFile.readBytes()
                    val encrypted = CryptoHelper.encryptWithKey(plaintext, derivedKey)

                    manifestEntries.put(JSONObject().apply {
                        put("receiptId", receiptId)
                        put("offset", currentOffset)
                        put("length", encrypted.size)
                    })
                    out.write(encrypted)
                    currentOffset += encrypted.size
                }
            }

            // Build manifest and encrypt it
            val manifest = JSONObject().apply {
                put("builtAt", System.currentTimeMillis())
                put("receiptCount", manifestEntries.length())
                put("entries", manifestEntries)
            }
            val manifestBytes = CryptoHelper.encryptWithKey(
                manifest.toString().toByteArray(Charsets.UTF_8), derivedKey
            )

            // Assemble final archive
            finalFile.outputStream().buffered().use { out ->
                out.write(PHOTOS_MAGIC)
                out.write(intToBytes(PHOTOS_VERSION))
                out.write(salt)
                out.write(intToBytes(manifestBytes.size))
                out.write(manifestBytes)
                tempEntries.inputStream().buffered().use { it.copyTo(out) }
            }
            tempEntries.delete()

            Log.d(TAG, "Photos backup created: ${finalFile.name} (${manifestEntries.length()} photos)")
            Result.success(finalFile)
        } catch (e: Exception) {
            // Clean up temp file on failure to prevent cache accumulation
            try { File(context.cacheDir, "backup_entries.tmp").delete() } catch (_: Exception) {}
            Result.failure(e)
        }
    }

    // ── Restore ─────────────────────────────────────────────────

    fun restoreSystemBackup(context: Context, backupFile: File, password: CharArray): Result<Unit> {
        return try {
            val encryptedBytes = backupFile.readBytes()
            val decryptedBytes = CryptoHelper.decrypt(encryptedBytes, password)
            val content = String(decryptedBytes, Charsets.UTF_8)

            if (!FullBackupSerializer.isFullBackup(content)) {
                return Result.failure(IllegalArgumentException("Not a valid system backup"))
            }

            FullBackupSerializer.restoreFullState(context, content)
            Result.success(Unit)
        } catch (e: javax.crypto.AEADBadTagException) {
            Result.failure(IllegalArgumentException("Wrong password or corrupted backup"))
        } catch (e: javax.crypto.BadPaddingException) {
            Result.failure(IllegalArgumentException("Wrong password or corrupted backup"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun restorePhotosBackup(context: Context, backupFile: File, password: CharArray): Result<Int> {
        return try {
            var restored = 0
            var failed = 0
            backupFile.inputStream().buffered().use { inp ->
                // Header
                val magic = ByteArray(4); inp.read(magic)
                if (!magic.contentEquals(PHOTOS_MAGIC)) {
                    return Result.failure(IllegalArgumentException("Not a valid photos backup"))
                }
                val version = readInt(inp)
                val salt = ByteArray(16); inp.read(salt)

                // Derive key
                val derivedKey = CryptoHelper.deriveKey(password, salt)

                // Manifest
                val manifestLen = readInt(inp)
                val manifestEncrypted = ByteArray(manifestLen); inp.read(manifestEncrypted)
                val manifestJson = String(
                    CryptoHelper.decryptWithKey(manifestEncrypted, derivedKey), Charsets.UTF_8
                )
                val manifest = JSONObject(manifestJson)
                val entries = manifest.getJSONArray("entries")

                // Extract entries
                for (i in 0 until entries.length()) {
                    val e = entries.getJSONObject(i)
                    val receiptId = e.getString("receiptId")
                    val length = e.getInt("length")

                    val encrypted = ByteArray(length)
                    var read = 0
                    while (read < length) {
                        val n = inp.read(encrypted, read, length - read)
                        if (n < 0) break
                        read += n
                    }

                    try {
                        val plaintext = CryptoHelper.decryptWithKey(encrypted, derivedKey)
                        // Save receipt file
                        val receiptFile = ReceiptManager.getReceiptFile(context, receiptId)
                        receiptFile.writeBytes(plaintext)
                        // Generate thumbnail
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(plaintext, 0, plaintext.size)
                        if (bitmap != null) {
                            val thumb = android.graphics.Bitmap.createScaledBitmap(
                                bitmap,
                                minOf(200, bitmap.width),
                                minOf(200, bitmap.height),
                                true
                            )
                            ReceiptManager.getThumbFile(context, receiptId).outputStream().use { out ->
                                thumb.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
                            }
                            if (thumb !== bitmap) bitmap.recycle()
                        }
                        restored++
                    } catch (ex: Exception) {
                        failed++
                        Log.w(TAG, "Failed to restore receipt $receiptId: ${ex.message}")
                    }
                }
            }
            Log.d(TAG, "Photos restore complete: $restored photos restored, $failed failed")
            Result.success(restored)
        } catch (e: javax.crypto.AEADBadTagException) {
            Result.failure(IllegalArgumentException("Wrong password or corrupted backup"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Listing & Scheduling ────────────────────────────────────

    fun listAvailableBackups(): List<BackupEntry> {
        val dir = getBackupDir()
        if (!dir.exists()) return emptyList()

        val systemFiles = dir.listFiles { f -> f.name.startsWith("backup_") && f.name.endsWith(SYSTEM_SUFFIX) }
            ?: return emptyList()

        return systemFiles.mapNotNull { sysFile ->
            val date = sysFile.name.removePrefix("backup_").removeSuffix(SYSTEM_SUFFIX)
            val photosFile = File(dir, "backup_${date}${PHOTOS_SUFFIX}")
            BackupEntry(
                date = date,
                systemFile = sysFile,
                photosFile = if (photosFile.exists()) photosFile else null,
                systemSizeBytes = sysFile.length(),
                photosSizeBytes = if (photosFile.exists()) photosFile.length() else 0L
            )
        }.sortedByDescending { it.date }
    }

    fun isBackupDue(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("backups_enabled", false)) return false

        val lastDate = prefs.getString("last_backup_date", null) ?: return true
        val frequencyWeeks = prefs.getInt("backup_frequency_weeks", 1)

        return try {
            val last = LocalDate.parse(lastDate)
            val next = last.plusWeeks(frequencyWeeks.toLong())
            !LocalDate.now().isBefore(next)
        } catch (_: Exception) {
            true
        }
    }

    fun getNextBackupDate(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("backups_enabled", false)) return null
        val lastDate = prefs.getString("last_backup_date", null) ?: return "Now"
        val frequencyWeeks = prefs.getInt("backup_frequency_weeks", 1)
        return try {
            LocalDate.parse(lastDate).plusWeeks(frequencyWeeks.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) { null }
    }

    // ── Retention ───────────────────────────────────────────────

    fun enforceRetention(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val retention = prefs.getInt("backup_retention", 10)
        if (retention == -1) return // Keep all

        val backups = listAvailableBackups()
        if (backups.size <= retention) return

        val toDelete = backups.drop(retention)
        for (entry in toDelete) {
            entry.systemFile.delete()
            entry.photosFile?.delete()
            Log.d(TAG, "Retention cleanup: deleted backup ${entry.date}")
        }
    }

    fun deleteAllBackups() {
        val dir = getBackupDir()
        dir.listFiles()?.forEach { it.delete() }
    }

    // ── Password Storage ────────────────────────────────────────

    fun getPassword(context: Context): CharArray? {
        return try {
            val prefs = getSecurePrefs(context)
            prefs.getString("backup_password", null)?.toCharArray()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read backup password: ${e.message}")
            null
        }
    }

    fun savePassword(context: Context, password: CharArray) {
        try {
            getSecurePrefs(context).edit()
                .putString("backup_password", String(password))
                .commit()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save backup password: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun getSecurePrefs(context: Context): android.content.SharedPreferences {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "backup_secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable, using fallback: ${e.message}")
            context.getSharedPreferences("backup_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    // ── Binary Helpers ──────────────────────────────────────────

    private fun intToBytes(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()

    private fun readInt(inp: InputStream): Int {
        val buf = ByteArray(4)
        inp.read(buf)
        return ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN).int
    }
}
