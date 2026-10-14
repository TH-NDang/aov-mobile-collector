package vn.ndang.aovcollector

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Garena test account a run logs in with when the game is on another account. The UID is kept as is; the
 * account name and password are encrypted with a key held by the phone's Android Keystore, which never leaves
 * it. Kept apart from the captures, so no ZIP export carries them; the app has no Internet permission.
 */
object Accounts {
    class Account(val uid:String,val user:String,val password:String)
    private const val KEY="aov-test-account"
    private fun prefs(c:Context)=c.getSharedPreferences("test-account",Context.MODE_PRIVATE)
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY,null) as? SecretKey)?.let { return it }
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
    private fun seal(text:String):String {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val data=cipher.doFinal(text.toByteArray())
        return Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(data,Base64.NO_WRAP)
    }
    private fun open(sealed:String):String {
        val (iv,data)=sealed.split(':').map { Base64.decode(it,Base64.NO_WRAP) }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv)) }
        return String(cipher.doFinal(data))
    }
    fun save(c:Context,uid:String,user:String,password:String) {
        require(uid.matches(Regex("\\d{10,20}"))) { "UID gồm 10–20 chữ số" }
        require(user.isNotBlank()&&password.isNotEmpty()) { "Nhập tài khoản và mật khẩu" }
        prefs(c).edit().putString("uid",uid).putString("user",seal(user.trim())).putString("password",seal(password)).commit()
    }
    fun uid(c:Context):String? = prefs(c).getString("uid",null)
    /** The account name, to show which account is saved; never the password. */
    fun user(c:Context):String? = try { prefs(c).getString("user",null)?.let { open(it) } } catch(e:Exception) { null }
    /** Null when nothing is saved or the phone's key is gone (as after clearing the lock screen). */
    fun load(c:Context):Account? = try {
        val p=prefs(c); val uid=p.getString("uid",null); val user=p.getString("user",null); val password=p.getString("password",null)
        if(uid==null||user==null||password==null) null else Account(uid,open(user),open(password))
    } catch(e:Exception) { null }
    fun clear(c:Context) {
        prefs(c).edit().clear().commit()
        try { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY) } catch(_:Exception) {}
    }
}
