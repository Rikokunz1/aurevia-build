/* Aurevia Admin (Kotlin) — network + session layer. GPL-3.0. */
package com.aurevia.admin

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Same server as the Aurevia music app. */
const val BASE_URL = "https://aurevia.rixxstore.my.id"

fun jof(vararg pairs: Pair<String, Any?>): JSONObject = JSONObject().apply {
    pairs.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) }
}

fun JSONArray.list(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONObject.s(key: String): String = if (isNull(key)) "" else optString(key, "")

class AdminApiException(val status: Int, message: String) : Exception(message)

/**
 * Tiny AES-GCM seal backed by the Android keystore: the staff session token never touches disk
 * in plain text, and uninstalling the app invalidates it.
 */
internal object Seal {
    private const val ALIAS = "aurevia.admin.v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    fun close(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val body = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        JSONObject()
            .put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(body, Base64.NO_WRAP))
            .toString()
    }.getOrNull()

    fun open(sealed: String): String? = runCatching {
        val json = JSONObject(sealed)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE, key(),
                GCMParameterSpec(128, Base64.decode(json.getString("iv"), Base64.NO_WRAP))
            )
        }
        String(cipher.doFinal(Base64.decode(json.getString("data"), Base64.NO_WRAP)), StandardCharsets.UTF_8)
    }.getOrNull()
}

object AdminSession {
    var token: String = ""
    var username: String = ""
    var name: String = ""
    var role: String = ""

    private fun prefs(c: Context) = c.getSharedPreferences("aurevia_admin", Context.MODE_PRIVATE)

    val isStaff: Boolean
        get() = role == "owner" || role == "developer" || role == "admin"

    val isOwnerLevel: Boolean
        get() = role == "owner" || role == "developer"

    fun load(c: Context) {
        if (token.isNotBlank()) return
        val sealed = prefs(c).getString("session", null) ?: return
        val plain = Seal.open(sealed) ?: return
        runCatching {
            val json = JSONObject(plain)
            token = json.s("token")
            username = json.s("username")
            name = json.s("name")
            role = json.s("role")
        }
    }

    fun save(c: Context, newToken: String, user: JSONObject) {
        token = newToken
        username = user.s("username")
        name = user.s("name").ifBlank { user.s("username") }
        role = user.s("role").ifBlank { "user" }
        val payload = jof("token" to token, "username" to username, "name" to name, "role" to role).toString()
        val sealed = Seal.close(payload)
        if (sealed != null) prefs(c).edit().putString("session", sealed).apply()
    }

    fun clear(c: Context) {
        token = ""; username = ""; name = ""; role = ""
        prefs(c).edit().remove("session").apply()
    }
}

object AdminApi {
    suspend fun call(
        path: String,
        body: JSONObject? = null,
        token: String? = AdminSession.token,
        method: String = if (body == null) "GET" else "POST",
    ): JSONObject = withContext(Dispatchers.IO) {
        require(path.startsWith("/v1/"))
        val url = URL(BASE_URL + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("Accept", "application/json")
            if (token?.isNotBlank() == true) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val parsed = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code !in 200..299) {
                val message = parsed.s("error").ifBlank { "Permintaan gagal ($code)" }
                throw AdminApiException(code, message)
            }
            parsed
        } catch (e: AdminApiException) {
            throw e
        } catch (e: IOException) {
            throw AdminApiException(0, "Tidak bisa menghubungi server. Periksa internet lalu coba lagi.")
        } finally {
            conn.disconnect()
        }
    }

    suspend fun login(user: String, pass: String): JSONObject {
        val result = call("/v1/auth/login", jof("username" to user, "password" to pass), token = "", method = "POST")
        if (result.s("token").isBlank()) throw AdminApiException(0, "Server tidak mengirim sesi.")
        return result
    }

    suspend fun overview(): JSONObject = call("/v1/admin/overview")

    suspend fun users(query: String, limit: Int = 60): List<JSONObject> {
        val suffix = if (query.isBlank()) "" else "&q=" + URLEncoder.encode(query, "UTF-8")
        return call("/v1/admin/users?limit=$limit$suffix").optJSONArray("users")?.list().orEmpty()
    }

    suspend fun rooms(): List<JSONObject> = call("/v1/admin/rooms").optJSONArray("rooms")?.list().orEmpty()

    suspend fun setRole(username: String, role: String? = null, verified: Boolean? = null, badge: String? = null): JSONObject {
        val body = JSONObject().put("username", username)
        if (role != null) body.put("role", role)
        if (verified != null) body.put("verified", verified)
        if (badge != null) body.put("badge", badge)
        return call("/v1/admin/role", body, method = "POST")
    }

    // ---------- Batch 210: ban permanen + blokir IP, mute, toast saat buka aplikasi ----------
    /** Ban / lepas ban. `blockIp` ikut memblokir IP terakhir akun itu (default dari server). */
    suspend fun ban(username: String, banned: Boolean, reason: String = "", blockIp: Boolean = true): JSONObject =
        call("/v1/admin/ban", jof("username" to username, "banned" to banned, "reason" to reason, "blockIp" to blockIp), method = "POST")

    /** Mute: akun tetap bisa masuk, tetapi tidak bisa membuat Live/room. */
    suspend fun mute(username: String, muted: Boolean): JSONObject =
        call("/v1/admin/mute", jof("username" to username, "muted" to muted), method = "POST")

    suspend fun bannedList(): JSONObject = call("/v1/admin/banned")

    /** Lepas blokir IP manual (mis. WiFi publik yang dipakai banyak orang). */
    suspend fun banIp(ip: String, banned: Boolean): JSONObject =
        call("/v1/admin/ban-ip", jof("ip" to ip, "banned" to banned), method = "POST")

    /** Toast yang muncul saat pengguna membuka aplikasi (bukan toast login/daftar). */
    suspend fun setAppToast(text: String, enabled: Boolean): JSONObject =
        call("/v1/admin/app-toast", jof("text" to text, "enabled" to enabled), method = "POST")

    suspend fun appToast(): JSONObject = call("/v1/app/toast", token = "")

    /**
     * Batch 240 — efek premium profil.
     *
     * Efek hanya kosmetik: tidak menyentuh hak akses. "none" menghapus efek.
     * Server yang memutuskan varian sah; panel hanya mengirim pilihan admin.
     */
    suspend fun setEffect(username: String, effect: String): JSONObject =
        call("/v1/admin/effects", jof("username" to username, "effect" to effect), method = "POST")

    suspend fun effects(): JSONObject = call("/v1/admin/effects")

    suspend fun broadcast(text: String): JSONObject =
        call("/v1/admin/broadcast", jof("text" to text), method = "POST")

    suspend fun notify(title: String, message: String, audience: String, username: String? = null): JSONObject {
        val body = jof("title" to title, "body" to message, "audience" to audience, "openNotifications" to true)
        if (!username.isNullOrBlank()) body.put("username", username)
        return call("/v1/admin/push", body, method = "POST")
    }
}
