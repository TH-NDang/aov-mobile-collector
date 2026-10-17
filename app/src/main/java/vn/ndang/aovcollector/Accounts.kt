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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Saved Garena accounts, selected and pinned per run. Legacy ciphertext migrates without decryption.
 * A lost encryption key leaves the UID visible for repair. The UID is kept as is; the
 * account name and password are encrypted with a key held by the phone's Android Keystore, which never leaves
 * it. Kept apart from the captures, so no ZIP export carries them; the app has no Internet permission.
 */
object Accounts {
    class Account(val uid:String,val user:String,val password:String)
    data class Summary(val id:String,val uid:String,val label:String,val user:String?,val ready:Boolean) {
        val title get()=label.ifBlank { user?:"Tài khoản" }
        val caption get()="$title · UID $uid"+if(ready) "" else " · cần nhập lại mật khẩu"
    }
    const val OPEN="manageAccounts"
    private const val RECORDS="accounts-v2"
    private const val SELECTED="selected-id"
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
    /** Migrate ciphertext unchanged, atomically; a lost keystore key must not erase the old UID. */
    @Synchronized private fun records(c:Context):List<JSONObject> {
        val p=prefs(c)
        val raw=p.getString(RECORDS,null)
        if(raw!=null) {
            val a=JSONArray(raw)
            return (0 until a.length()).map { a.getJSONObject(it).also { row ->
                require(listOf("id","uid","user","password").all { row.has(it) }) {
                    "Dữ liệu tài khoản chưa đọc được; giữ lại dữ liệu hiện có."
                }
            } }
        }
        val uid=p.getString("uid",null)?:return emptyList()
        val id="legacy-"+uid
        val row=JSONObject().put("id",id).put("uid",uid).put("label","")
            .put("user",p.getString("user","")).put("password",p.getString("password",""))
        require(p.edit().putString(RECORDS,JSONArray().put(row).toString()).putString(SELECTED,id)
            .remove("uid").remove("user").remove("password").commit()) { "Không chuyển được tài khoản cũ; dữ liệu cũ được giữ lại." }
        return listOf(row)
    }
    private fun decode(value:String):String?=try { open(value) } catch(_:Exception) { null }
    fun problem(c:Context):String?=try { records(c);null } catch(_:Exception) { "Chưa đọc được danh sách tài khoản đã lưu; dữ liệu hiện có được giữ nguyên." }
    fun list(c:Context):List<Summary> = try { records(c).map { row ->
        val user=decode(row.getString("user"))?.takeIf { it.isNotBlank() }
        Summary(row.getString("id"),row.getString("uid"),row.optString("label"),user,
            user!=null&&!decode(row.getString("password")).isNullOrEmpty())
    } } catch(_:Exception) { emptyList() }
    fun selectedId(c:Context):String? = try {
        val all=records(c);val id=prefs(c).getString(SELECTED,null)
        all.firstOrNull { it.getString("id")==id }?.getString("id")?:all.firstOrNull()?.getString("id")
    } catch(_:Exception) { null }
    @Synchronized fun select(c:Context,id:String) {
        require(list(c).any { it.id==id }) { "Tài khoản này không còn được lưu." }
        require(prefs(c).edit().putString(SELECTED,id).commit()) { "Không lưu được tài khoản mặc định." }
    }
    @Synchronized fun save(c:Context,uid:String,user:String,password:String,id:String?=null,label:String=""):String {
        require(uid.matches(Regex("\\d{10,20}"))) { "UID gồm 10–20 chữ số" }
        require(user.isNotBlank()) { "Nhập tài khoản Garena." }
        val all=records(c)
        val old=id?.let { selected -> all.firstOrNull { it.getString("id")==selected }
            ?:throw IllegalArgumentException("Tài khoản đang sửa không còn được lưu.") }
        require(all.none { it.getString("uid")==uid&&it.getString("id")!=id }) { "UID này đã có trong danh sách; hãy sửa tài khoản đó." }
        val sealedPassword=if(password.isNotEmpty()) seal(password) else {
            require(old!=null&&!decode(old.getString("password")).isNullOrEmpty()) { "Nhập lại mật khẩu để lưu tài khoản." }
            old.getString("password")
        }
        val accountId=id?:UUID.randomUUID().toString()
        val row=JSONObject().put("id",accountId).put("uid",uid).put("label",label.trim().take(60))
            .put("user",seal(user.trim())).put("password",sealedPassword)
        val updated=all.filter { it.getString("id")!=accountId }+row
        require(prefs(c).edit().putString(RECORDS,JSONArray(updated).toString()).putString(SELECTED,accountId).commit()) {
            "Không lưu được tài khoản; hãy thử lại."
        }
        return accountId
    }
    fun uid(c:Context):String? = list(c).firstOrNull { it.id==selectedId(c) }?.uid
    /** The account name, to show which account is saved; never the password. */
    fun user(c:Context):String? = list(c).firstOrNull { it.id==selectedId(c) }?.user
    /** Null when nothing is saved or the phone's key is gone (as after clearing the lock screen). */
    fun load(c:Context,id:String?=selectedId(c)):Account? = try {
        val row=records(c).firstOrNull { it.getString("id")==id }
        if(row==null) null else Account(row.getString("uid"),open(row.getString("user")),open(row.getString("password")))
            .takeIf { it.uid.matches(Regex("\\d{10,20}"))&&it.user.isNotBlank()&&it.password.isNotEmpty() }
    } catch(e:Exception) { null }
    @Synchronized fun remove(c:Context,id:String) {
        val all=records(c).filter { it.getString("id")!=id }
        val selected=selectedId(c)?.takeIf { current -> all.any { it.getString("id")==current } }
            ?:all.firstOrNull()?.getString("id")
        require(prefs(c).edit().putString(RECORDS,JSONArray(all).toString()).putString(SELECTED,selected).commit()) {
            "Không xóa được tài khoản."
        }
    }
    @Synchronized fun clear(c:Context) {
        require(prefs(c).edit().clear().commit()) { "Không xóa được tài khoản." }
        try { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY) } catch(_:Exception) {}
    }
}

