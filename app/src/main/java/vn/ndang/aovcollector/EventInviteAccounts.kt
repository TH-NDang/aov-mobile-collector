package vn.ndang.aovcollector

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/**
 * A SEPARATE collection of Garena credentials used only by the anniversary invite task.
 * It never consults Accounts, never requires a game UID, and never generates a UUID.
 *
 * Credentials and the invitation code are encrypted with an Android Keystore AES-GCM key.
 * Neither passwords nor the invitation code are stored inside exported run macros/ZIP files.
 */
object EventInviteAccounts {
    private const val PREFS = "event-invite-private-v1"
    private const val KEY_ALIAS = "aov-event-invite-v1"
    private const val RECORDS = "garena-accounts"
    private const val CODE = "invite-code"
    private const val MAX_ACCOUNTS = 80

    data class Saved(val key: String, val label: String)
    data class Secret(val key: String, val label: String, val username: String, val password: String)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A deterministic natural account key, NOT a UID, UUID, or generated account number. */
    fun accountKey(username: String): String {
        val canonical = username.trim().lowercase(Locale.ROOT)
        require(canonical.isNotBlank() && canonical.length <= 150) { "Tên đăng nhập không hợp lệ" }
        return digest(canonical)
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()
            )
        }.generateKey()
    }

    private fun seal(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun unseal(value: String): String {
        val parts = value.split(':')
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
        )
        return String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }

    @Synchronized private fun records(context: Context): JSONArray =
        JSONArray(prefs(context).getString(RECORDS, "[]") ?: "[]")

    @Synchronized fun list(context: Context): List<Saved> = try {
        val array = records(context)
        (0 until array.length()).map { i ->
            val row = array.getJSONObject(i)
            Saved(row.getString("key"), row.optString("label").ifBlank { "Tài khoản ${i + 1}" })
        }
    } catch (_: Exception) {
        emptyList()
    }

    @Synchronized fun load(context: Context, keyId: String): Secret? = try {
        val array = records(context)
        (0 until array.length()).asSequence().map { array.getJSONObject(it) }
            .firstOrNull { it.getString("key") == keyId }
            ?.let { row ->
                val user = unseal(row.getString("username"))
                val pass = unseal(row.getString("password"))
                Secret(keyId, row.optString("label"), user, pass).takeIf {
                    it.password.isNotEmpty() && accountKey(it.username) == keyId
                }
            }
    } catch (_: Exception) {
        null
    }

    @Synchronized fun save(
        context: Context,
        username: String,
        password: String,
        label: String = ""
    ): String {
        val clean = username.trim()
        require(clean.isNotBlank() && clean.length <= 150) { "Nhập tài khoản Garena" }
        require(password.isNotEmpty() && password.length <= 200) { "Nhập mật khẩu Garena" }
        val id = accountKey(clean)
        val array = records(context)
        val rows = (0 until array.length()).map { array.getJSONObject(it) }
        require(rows.size < MAX_ACCOUNTS || rows.any { it.optString("key") == id }) {
            "Tối đa $MAX_ACCOUNTS tài khoản cho tác vụ này"
        }
        val replacement = JSONObject().put("key", id).put("label", label.trim().take(60).ifBlank { clean })
            .put("username", seal(clean)).put("password", seal(password))
        val updated = rows.map { if (it.optString("key") == id) replacement else it }.toMutableList()
        if (rows.none { it.optString("key") == id }) updated.add(replacement)
        require(prefs(context).edit().putString(RECORDS, JSONArray(updated).toString()).commit()) {
            "Không lưu được tài khoản"
        }
        return id
    }

    @Synchronized fun remove(context: Context, accountKey: String) {
        val array = records(context)
        val remaining = JSONArray((0 until array.length()).map { array.getJSONObject(it) }
            .filterNot { it.optString("key") == accountKey })
        require(prefs(context).edit().putString(RECORDS, remaining.toString()).commit()) {
            "Không xóa được tài khoản"
        }
    }

    fun validCode(code: String) = code.length in 4..40 && code.matches(Regex("[A-Za-z0-9_-]+"))

    /** The stable fingerprint is saved in macro.json; the actual code stays encrypted here. */
    fun fingerprint(code: String): String = digest(code)

    @Synchronized fun saveCode(context: Context, code: String): String {
        require(validCode(code)) { "Mã mời cần 4–40 ký tự chữ, số, _ hoặc -" }
        require(prefs(context).edit().putString(CODE, seal(code)).commit()) {
            "Không lưu được mã mời"
        }
        return fingerprint(code)
    }

    fun loadCode(context: Context, expectedFingerprint: String? = null): String? = try {
        val code = unseal(prefs(context).getString(CODE, null) ?: return null)
        code.takeIf { validCode(it) && (expectedFingerprint == null || fingerprint(it) == expectedFingerprint) }
    } catch (_: Exception) {
        null
    }

    fun validateSelection(context: Context, keys: List<String>): Boolean =
        keys.isNotEmpty() && keys.size <= MAX_ACCOUNTS && keys.distinct().size == keys.size &&
            keys.all { it.matches(Regex("[0-9a-f]{64}")) && load(context, it) != null }
}
