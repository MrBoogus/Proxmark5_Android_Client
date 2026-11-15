package com.proxmark5.client.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class CapturedTag(
    val id: String = UUID.randomUUID().toString(),
    val uidHex: String,
    val technology: String,        // "EM4100", "HID Prox", "AWID", "IO Prox", "MIFARE Classic", etc.
    val frequency: String = "LF",  // "LF" (125 kHz) or "HF" (13.56 MHz)
    val cardId: String = "",
    val facilityCode: String? = null,
    val formatDescription: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("uidHex", uidHex)
            put("technology", technology)
            put("frequency", frequency)
            put("cardId", cardId)
            put("facilityCode", facilityCode ?: "")
            put("formatDescription", formatDescription ?: "")
            put("timestamp", timestamp)
            put("note", note)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): CapturedTag {
            return CapturedTag(
                id = json.optString("id", UUID.randomUUID().toString()),
                uidHex = json.optString("uidHex", ""),
                technology = json.optString("technology", "Unknown"),
                frequency = json.optString("frequency", "LF"),
                cardId = json.optString("cardId", ""),
                facilityCode = json.optString("facilityCode").ifEmpty { null },
                formatDescription = json.optString("formatDescription").ifEmpty { null },
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                note = json.optString("note", "")
            )
        }
    }
}

sealed class VaultResult {
    data class Success(val tags: List<CapturedTag>) : VaultResult()
    data class Error(
        val message: String,
        val cachedTags: List<CapturedTag> = emptyList()
    ) : VaultResult()
}

/**
 * AEAD envelope provider for the local credential vault.
 * Wire format (PM5V2): [6B magic "PM5V2\0"][12B IV][AES-256-GCM ciphertext + 16B tag],
 * with the 6-byte magic header authenticated as GCM AAD.
 */
interface VaultAead {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray
    fun decrypt(envelope: ByteArray, associatedData: ByteArray): ByteArray
}

object AndroidKeystoreVaultAead : VaultAead {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "pm5_vault_aes256_gcm_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val MAGIC_SIZE = 6
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        if (associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }
        val iv = cipher.iv
        require(iv.size == IV_SIZE) { "Unexpected GCM IV length: ${iv.size}" }
        val ciphertext = cipher.doFinal(plaintext)

        return ByteArray(associatedData.size + iv.size + ciphertext.size).apply {
            System.arraycopy(associatedData, 0, this, 0, associatedData.size)
            System.arraycopy(iv, 0, this, associatedData.size, iv.size)
            System.arraycopy(ciphertext, 0, this, associatedData.size + iv.size, ciphertext.size)
        }
    }

    override fun decrypt(envelope: ByteArray, associatedData: ByteArray): ByteArray {
        val headerSize = if (associatedData.isNotEmpty()) associatedData.size else MAGIC_SIZE
        if (envelope.size <= headerSize + IV_SIZE + 16) {
            throw GeneralSecurityException("Truncated vault ciphertext")
        }
        val iv = envelope.copyOfRange(headerSize, headerSize + IV_SIZE)
        val ciphertext = envelope.copyOfRange(headerSize + IV_SIZE, envelope.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        if (associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }
        return cipher.doFinal(ciphertext)
    }
}

/**
 * Pure JCE/Kotlin AES-256-GCM implementation used for unit tests on the JVM.
 */
class SoftwareAesGcmVaultAead(
    private val keyBytes: ByteArray
) : VaultAead {
    private val secretKey = SecretKeySpec(keyBytes, "AES")
    private val rng = SecureRandom()

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val iv = ByteArray(12).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        if (associatedData.isNotEmpty()) cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)

        return ByteArray(associatedData.size + iv.size + ciphertext.size).apply {
            System.arraycopy(associatedData, 0, this, 0, associatedData.size)
            System.arraycopy(iv, 0, this, associatedData.size, iv.size)
            System.arraycopy(ciphertext, 0, this, associatedData.size + iv.size, ciphertext.size)
        }
    }

    override fun decrypt(envelope: ByteArray, associatedData: ByteArray): ByteArray {
        val headerLen = associatedData.size
        if (envelope.size <= headerLen + 12 + 16) {
            throw GeneralSecurityException("Truncated vault ciphertext")
        }
        val iv = envelope.copyOfRange(headerLen, headerLen + 12)
        val ciphertext = envelope.copyOfRange(headerLen + 12, envelope.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        if (associatedData.isNotEmpty()) cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext)
    }
}

/**
 * One-time migration helper for v1.0.1 files written via androidx.security.crypto.EncryptedFile
 * (Tink AES256_GCM_HKDF_4KB streaming AEAD backed by AndroidKeyStore master key).
 */
internal object TinkLegacyVaultMigrator {
    private const val PREF_NAME = "__androidx_security_crypto_encrypted_file_pref__"
    private const val KEYSET_PREF_KEY = "__androidx_security_crypto_encrypted_file_keyset__"
    private const val MASTER_KEY_ALIAS = "_androidx_security_master_key_"
    private const val AES_GCM_HKDF_TYPE_URL = "type.googleapis.com/google.crypto.tink.AesGcmHkdfStreamingKey"

    fun decryptLegacyFileIfPresent(context: Context, fileName: String, rawFileBytes: ByteArray): ByteArray? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val keysetHex = prefs.getString(KEYSET_PREF_KEY, null) ?: return null
        val encryptedKeysetProto = decodeHex(keysetHex)
        val encryptedKeysetField = extractProtoBytesField(encryptedKeysetProto, targetFieldNumber = 2)
            ?: throw GeneralSecurityException("Missing Tink encrypted_keyset field")

        val keysetBytes = decryptWithAndroidKeystoreMasterKey(encryptedKeysetField)
        val streamingKey = parseStreamingKeyFromKeyset(keysetBytes)

        return decryptAesGcmHkdfStream(
            rawFileBytes = rawFileBytes,
            ikm = streamingKey.ikm,
            derivedKeySize = streamingKey.derivedKeySize,
            ciphertextSegmentSize = streamingKey.ciphertextSegmentSize,
            associatedData = fileName.toByteArray(StandardCharsets.UTF_8)
        )
    }

    fun clearLegacyKeysetPref(context: Context) {
        try {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEYSET_PREF_KEY)
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun decryptWithAndroidKeystoreMasterKey(encrypted: ByteArray): ByteArray {
        if (encrypted.size <= 12 + 16) {
            throw GeneralSecurityException("Legacy Tink keyset payload is truncated")
        }
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val masterKey = ks.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
            ?: throw GeneralSecurityException("Legacy AndroidKeyStore master key not found")

        val iv = encrypted.copyOfRange(0, 12)
        val ciphertext = encrypted.copyOfRange(12, encrypted.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    internal data class ParsedStreamingKey(
        val ikm: ByteArray,
        val derivedKeySize: Int,
        val ciphertextSegmentSize: Int
    )

    internal fun parseStreamingKeyFromKeyset(keysetBytes: ByteArray): ParsedStreamingKey {
        val primaryKeyId = extractProtoVarintField(keysetBytes, targetFieldNumber = 1)?.toInt()
        val keys = extractAllProtoBytesFields(keysetBytes, targetFieldNumber = 2)
        if (keys.isEmpty()) throw GeneralSecurityException("Legacy Tink keyset is empty")

        var fallbackMatch: ParsedStreamingKey? = null
        for (keyEntry in keys) {
            val keyData = extractProtoBytesField(keyEntry, targetFieldNumber = 1) ?: continue
            val keyId = extractProtoVarintField(keyEntry, targetFieldNumber = 3)?.toInt()
            val typeUrlBytes = extractProtoBytesField(keyData, targetFieldNumber = 1) ?: continue
            val typeUrl = String(typeUrlBytes, StandardCharsets.UTF_8)
            if (typeUrl != AES_GCM_HKDF_TYPE_URL) continue

            val valueBytes = extractProtoBytesField(keyData, targetFieldNumber = 2) ?: continue
            val paramsBytes = extractProtoBytesField(valueBytes, targetFieldNumber = 2)
            val segSize = paramsBytes?.let { extractProtoVarintField(it, targetFieldNumber = 1)?.toInt() } ?: 4096
            val derivedKeySize = paramsBytes?.let { extractProtoVarintField(it, targetFieldNumber = 2)?.toInt() } ?: 32
            val ikm = extractProtoBytesField(valueBytes, targetFieldNumber = 3)
                ?: throw GeneralSecurityException("Missing AesGcmHkdfStreamingKey material")

            val parsed = ParsedStreamingKey(
                ikm = ikm,
                derivedKeySize = derivedKeySize,
                ciphertextSegmentSize = segSize
            )
            if (primaryKeyId == null || keyId == primaryKeyId) {
                return parsed
            }
            if (fallbackMatch == null) fallbackMatch = parsed
        }
        return fallbackMatch ?: throw GeneralSecurityException("No AesGcmHkdfStreamingKey found in keyset")
    }

    internal fun decryptAesGcmHkdfStream(
        rawFileBytes: ByteArray,
        ikm: ByteArray,
        derivedKeySize: Int,
        ciphertextSegmentSize: Int,
        associatedData: ByteArray
    ): ByteArray {
        val headerLength = 1 + derivedKeySize + 7
        if (rawFileBytes.size < headerLength + 16) {
            throw GeneralSecurityException("Legacy encrypted vault file shorter than header")
        }
        val firstByte = rawFileBytes[0].toInt() and 0xFF
        if (firstByte != headerLength) {
            throw GeneralSecurityException("Unexpected Tink header length byte: $firstByte (expected $headerLength)")
        }

        val salt = rawFileBytes.copyOfRange(1, 1 + derivedKeySize)
        val noncePrefix = rawFileBytes.copyOfRange(1 + derivedKeySize, headerLength)
        val subKeyBytes = hkdfSha256(ikm, salt, associatedData, derivedKeySize)
        val subKey = SecretKeySpec(subKeyBytes, "AES")

        val out = ByteArrayOutputStream()
        var pos = headerLength
        var segmentNr = 0
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        while (pos < rawFileBytes.size) {
            val maxSegLen = if (segmentNr == 0) {
                ciphertextSegmentSize - headerLength
            } else {
                ciphertextSegmentSize
            }
            if (maxSegLen <= 16) throw GeneralSecurityException("Invalid segment size: $maxSegLen")

            val end = minOf(rawFileBytes.size, pos + maxSegLen)
            val isLast = (end == rawFileBytes.size)
            val nonce = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN).apply {
                put(noncePrefix)
                putInt(segmentNr)
                put(if (isLast) 1.toByte() else 0.toByte())
            }.array()

            cipher.init(Cipher.DECRYPT_MODE, subKey, GCMParameterSpec(128, nonce))
            val plainSeg = cipher.doFinal(rawFileBytes, pos, end - pos)
            out.write(plainSeg)

            pos = end
            segmentNr++
        }

        return out.toByteArray()
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val actualSalt = if (salt.isEmpty()) ByteArray(32) else salt
        mac.init(SecretKeySpec(actualSalt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val result = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val copyLen = minOf(t.size, length - offset)
            System.arraycopy(t, 0, result, offset, copyLen)
            offset += copyLen
            counter++
        }
        return result
    }

    private fun decodeHex(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "Invalid hex length" }
        return ByteArray(clean.length / 2) { idx ->
            clean.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
        }
    }

    private fun readVarint(bytes: ByteArray, startOffset: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var offset = startOffset
        while (offset < bytes.size && shift < 64) {
            val b = bytes[offset].toInt() and 0xFF
            offset++
            result = result or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) {
                return result to offset
            }
            shift += 7
        }
        throw GeneralSecurityException("Malformed protobuf varint")
    }

    private fun extractProtoBytesField(proto: ByteArray, targetFieldNumber: Int): ByteArray? =
        extractAllProtoBytesFields(proto, targetFieldNumber).firstOrNull()

    private fun extractAllProtoBytesFields(proto: ByteArray, targetFieldNumber: Int): List<ByteArray> {
        val matches = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < proto.size) {
            val (tag, afterTag) = readVarint(proto, offset)
            val fieldNumber = (tag ushr 3).toInt()
            val wireType = (tag and 0x07).toInt()
            offset = afterTag
            when (wireType) {
                0 -> {
                    val (_, next) = readVarint(proto, offset)
                    offset = next
                }
                1 -> offset += 8
                2 -> {
                    val (len, afterLen) = readVarint(proto, offset)
                    val lenInt = len.toInt()
                    if (lenInt < 0 || afterLen + lenInt > proto.size) {
                        throw GeneralSecurityException("Truncated protobuf length-delimited field")
                    }
                    if (fieldNumber == targetFieldNumber) {
                        matches.add(proto.copyOfRange(afterLen, afterLen + lenInt))
                    }
                    offset = afterLen + lenInt
                }
                5 -> offset += 4
                else -> throw GeneralSecurityException("Unsupported protobuf wire type: $wireType")
            }
        }
        return matches
    }

    private fun extractProtoVarintField(proto: ByteArray, targetFieldNumber: Int): Long? {
        var offset = 0
        while (offset < proto.size) {
            val (tag, afterTag) = readVarint(proto, offset)
            val fieldNumber = (tag ushr 3).toInt()
            val wireType = (tag and 0x07).toInt()
            offset = afterTag
            when (wireType) {
                0 -> {
                    val (value, next) = readVarint(proto, offset)
                    if (fieldNumber == targetFieldNumber) return value
                    offset = next
                }
                1 -> offset += 8
                2 -> {
                    val (len, afterLen) = readVarint(proto, offset)
                    offset = afterLen + len.toInt()
                }
                5 -> offset += 4
                else -> throw GeneralSecurityException("Unsupported protobuf wire type: $wireType")
            }
        }
        return null
    }
}

class TagStorageManager(
    private val context: Context?,
    private val storageFileOverride: File? = null,
    private val vaultAead: VaultAead = AndroidKeystoreVaultAead
) {
    private val ioMutex = Mutex()
    private var vaultLockedWithError = false
    private var cachedTags: List<CapturedTag> = emptyList()

    private val storageFile: File by lazy {
        storageFileOverride ?: File(
            requireNotNull(context) { "Context is required when storageFileOverride is null" }.filesDir,
            DEFAULT_FILE_NAME
        )
    }

    private val tempStorageFile: File by lazy {
        File(storageFile.parentFile, "${storageFile.name}.tmp")
    }

    suspend fun loadAll(): VaultResult = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            loadInternalLocked()
        }
    }

    suspend fun save(tag: CapturedTag): VaultResult = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            when (val currentResult = loadInternalLocked()) {
                is VaultResult.Error -> currentResult
                is VaultResult.Success -> {
                    val current = currentResult.tags.toMutableList()
                    // Deduplicate if same UID was captured within 3 seconds
                    val existingIdx = current.indexOfFirst { it.uidHex.equals(tag.uidHex, ignoreCase = true) }
                    if (existingIdx >= 0 && (System.currentTimeMillis() - current[existingIdx].timestamp) < 3000) {
                        return@withContext VaultResult.Success(current)
                    }
                    current.add(0, tag)
                    persistLocked(current)
                }
            }
        }
    }

    suspend fun delete(id: String): VaultResult = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            when (val currentResult = loadInternalLocked()) {
                is VaultResult.Error -> currentResult
                is VaultResult.Success -> {
                    val next = currentResult.tags.filterNot { it.id == id }
                    persistLocked(next)
                }
            }
        }
    }

    suspend fun clear(): VaultResult = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            vaultLockedWithError = false
            persistLocked(emptyList())
        }
    }

    suspend fun exportJson(): Result<String> = withContext(Dispatchers.IO) {
        ioMutex.withLock {
            when (val result = loadInternalLocked()) {
                is VaultResult.Success -> {
                    val array = JSONArray()
                    result.tags.forEach { array.put(it.toJson()) }
                    Result.success(array.toString(2))
                }
                is VaultResult.Error -> Result.failure(IllegalStateException(result.message))
            }
        }
    }

    private fun loadInternalLocked(): VaultResult {
        if (!storageFile.exists()) {
            vaultLockedWithError = false
            cachedTags = emptyList()
            return VaultResult.Success(emptyList())
        }

        val rawBytes = try {
            storageFile.readBytes()
        } catch (e: Exception) {
            vaultLockedWithError = true
            return VaultResult.Error(
                "Unable to read vault file; existing file preserved (${e.message ?: "I/O error"})",
                cachedTags
            )
        }

        if (rawBytes.isEmpty()) {
            vaultLockedWithError = false
            cachedTags = emptyList()
            return VaultResult.Success(emptyList())
        }

        return try {
            val (jsonText, needsUpgradeMigration, migratedFromTink) = decodeVaultBytes(rawBytes)
            val parsed = parseTagList(jsonText)
            cachedTags = parsed
            vaultLockedWithError = false

            if (needsUpgradeMigration) {
                writeEncryptedAtomically(serializeTagList(parsed))
                if (migratedFromTink && context != null) {
                    TinkLegacyVaultMigrator.clearLegacyKeysetPref(context)
                }
            }
            VaultResult.Success(parsed)
        } catch (e: Exception) {
            vaultLockedWithError = true
            VaultResult.Error(
                "Vault decryption failed; existing vault file preserved untouched (${e.javaClass.simpleName})",
                cachedTags
            )
        }
    }

    private data class DecodedVaultPayload(
        val jsonText: String,
        val needsUpgradeMigration: Boolean,
        val migratedFromTink: Boolean
    )

    private fun decodeVaultBytes(rawBytes: ByteArray): DecodedVaultPayload {
        if (hasV2Magic(rawBytes)) {
            val plainBytes = vaultAead.decrypt(rawBytes, MAGIC_V2)
            return DecodedVaultPayload(
                jsonText = String(plainBytes, StandardCharsets.UTF_8),
                needsUpgradeMigration = false,
                migratedFromTink = false
            )
        }

        // One-time migration from v1.0.0 unencrypted JSON array
        val textPreview = String(rawBytes, StandardCharsets.UTF_8)
        if (textPreview.trimStart().startsWith("[")) {
            return DecodedVaultPayload(
                jsonText = textPreview,
                needsUpgradeMigration = true,
                migratedFromTink = false
            )
        }

        // One-time migration from v1.0.1 Tink EncryptedFile (security-crypto 1.0.0)
        val appContext = context
        if (appContext != null) {
            val tinkPlainBytes = TinkLegacyVaultMigrator.decryptLegacyFileIfPresent(
                context = appContext,
                fileName = storageFile.name,
                rawFileBytes = rawBytes
            )
            if (tinkPlainBytes != null) {
                return DecodedVaultPayload(
                    jsonText = String(tinkPlainBytes, StandardCharsets.UTF_8),
                    needsUpgradeMigration = true,
                    migratedFromTink = true
                )
            }
        }

        throw GeneralSecurityException("Unrecognized or undecryptable vault format")
    }

    private fun parseTagList(jsonText: String): List<CapturedTag> {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) return emptyList()
        val jsonArray = JSONArray(trimmed)
        val list = mutableListOf<CapturedTag>()
        for (i in 0 until jsonArray.length()) {
            list.add(CapturedTag.fromJson(jsonArray.getJSONObject(i)))
        }
        return list.sortedByDescending { it.timestamp }
    }

    private fun serializeTagList(list: List<CapturedTag>): String {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        return array.toString(2)
    }

    private fun persistLocked(list: List<CapturedTag>): VaultResult {
        if (vaultLockedWithError) {
            return VaultResult.Error(
                "Refusing to overwrite locked vault after decrypt failure; existing file preserved.",
                cachedTags
            )
        }
        return try {
            writeEncryptedAtomically(serializeTagList(list))
            cachedTags = list
            VaultResult.Success(list)
        } catch (e: Exception) {
            VaultResult.Error(
                "Failed to save vault cleanly; existing file preserved (${e.message ?: "write error"})",
                cachedTags
            )
        }
    }

    private fun writeEncryptedAtomically(jsonText: String) {
        val parent = storageFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val envelope = vaultAead.encrypt(jsonText.toByteArray(StandardCharsets.UTF_8), MAGIC_V2)

        try {
            FileOutputStream(tempStorageFile, false).use { fos ->
                fos.write(envelope)
                fos.flush()
                fos.fd.sync()
            }
            try {
                Files.move(
                    tempStorageFile.toPath(),
                    storageFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempStorageFile.toPath(),
                    storageFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (e: Exception) {
            try {
                tempStorageFile.delete()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    private fun hasV2Magic(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC_V2.size) return false
        for (i in MAGIC_V2.indices) {
            if (bytes[i] != MAGIC_V2[i]) return false
        }
        return true
    }

    companion object {
        private const val DEFAULT_FILE_NAME = "pm5_saved_tags.json"
        // "PM5V2\0"
        val MAGIC_V2: ByteArray = byteArrayOf(0x50, 0x4D, 0x35, 0x56, 0x32, 0x00)
    }
}
