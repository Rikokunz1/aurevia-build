/*
 * Aurevia Admin — panel Kotlin/Compose asli (tanpa WebView).
 *
 * Batch 240. Panel ini menggantikan shell lama: seluruh layar digambar dengan
 * Compose, permintaan HTTP memakai HttpURLConnection (tanpa pustaka tambahan),
 * dan tidak ada satu pun HTML yang dimuat.
 *
 * Paket: com.aurevia.admin — SAMA dengan APK admin versi sebelumnya supaya
 * pembaruan bisa menimpa (kalau sertifikatnya sama). Sertifikat berbeda berarti
 * versi lama perlu dicopot sekali.
 */
package com.aurevia.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** Warna merek Aurevia; dipakai di seluruh panel. */
private val AureviaRed = Color(0xFFFA2D48)
private val Latar = Color(0xFF0B0B10)
private val Kartu = Color(0xFF15151D)
private val KartuTinggi = Color(0xFF1E1E28)
private val TeksUtama = Color(0xFFF5F5FA)
private val TeksRedup = Color(0xFF9E9EAE)

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = AureviaRed,
                    background = Latar,
                    surface = Kartu,
                    onPrimary = Color.White,
                    onBackground = TeksUtama,
                    onSurface = TeksUtama,
                ),
            ) {
                PanelAdmin()
            }
        }
    }
}

// ---------------------------------------------------------------- state

private enum class Tab(val judul: String) {
    BERANDA("Beranda"),
    PENGGUNA("Pengguna"),
    EFEK("Efek"),
    ROOM("Room"),
    SIARAN("Siaran"),
}

private data class Pengguna(
    val id: String,
    val username: String,
    val nama: String,
    val role: String,
    val efek: String,
    val terverifikasi: Boolean,
    val badge: String,
)

private fun JSONObject.kePengguna() = Pengguna(
    id = optString("id"),
    username = optString("username"),
    nama = optString("name").ifBlank { optString("username") },
    role = optString("role").ifBlank { "user" },
    efek = optString("efek"),
    terverifikasi = optBoolean("verified"),
    badge = optString("badge"),
)

// ---------------------------------------------------------------- panel

@Composable
private fun PanelAdmin() {
    val ctx = LocalContext.current
    // Sesi disimpan terenkripsi oleh AdminSession (Android Keystore), jadi
    // menutup lalu membuka panel tidak perlu login ulang.
    var token by remember { mutableStateOf("") }
    var pesan by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        AdminSession.load(ctx)
        token = AdminSession.token
    }

    Surface(Modifier.fillMaxSize(), color = Latar) {
        Column(Modifier.fillMaxSize()) {
            pesan?.let { teks ->
                Surface(color = AureviaRed.copy(alpha = 0.15f)) {
                    Text(
                        teks,
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        color = AureviaRed,
                        fontSize = 13.sp,
                    )
                }
            }
            if (token.isBlank()) {
                LayarMasuk(
                    onMasuk = { pesan = null; token = AdminSession.token },
                    onGalat = { pesan = it },
                )
            } else {
                IsiPanel(
                    onKeluar = { AdminSession.clear(ctx); token = "" },
                    onGalat = { pesan = it },
                )
            }
        }
    }
}

@Composable
private fun LayarMasuk(onMasuk: () -> Unit, onGalat: (String) -> Unit) {
    val ctx = LocalContext.current
    var user by remember { mutableStateOf("") }
    var sandi by remember { mutableStateOf("") }
    var sibuk by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Aurevia Admin", color = TeksUtama, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text("Panel owner & developer", color = TeksRedup, fontSize = 13.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = user,
            onValueChange = { user = it.trim() },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            colors = TextFieldDefaults.colors(focusedContainerColor = Kartu, unfocusedContainerColor = Kartu),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = sandi,
            onValueChange = { sandi = it },
            label = { Text("Kata sandi") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = TextFieldDefaults.colors(focusedContainerColor = Kartu, unfocusedContainerColor = Kartu),
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                if (user.isBlank() || sandi.isBlank()) {
                    onGalat("Username dan kata sandi wajib diisi.")
                    return@Button
                }
                sibuk = true
                scope.launch {
                    try {
                        val hasil = AdminApi.login(user, sandi)
                        AdminSession.save(ctx, hasil.s("token"), hasil.optJSONObject("user") ?: JSONObject())
                        onMasuk()
                    } catch (e: Exception) {
                        onGalat(e.message ?: "Gagal masuk")
                    } finally {
                        sibuk = false
                    }
                }
            },
            enabled = !sibuk,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(26.dp),
        ) {
            if (sibuk) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Masuk", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun IsiPanel(onKeluar: () -> Unit, onGalat: (String) -> Unit) {
    var tab by remember { mutableStateOf(Tab.BERANDA) }

    Column(Modifier.fillMaxSize()) {
        // ---------------- kepala
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AureviaRed, Color(0xFF8B5CF6)))),
                contentAlignment = Alignment.Center,
            ) {
                Text("A", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Aurevia Admin", color = TeksUtama, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Panel owner & developer", color = TeksRedup, fontSize = 11.sp)
            }
            TextButton(onClick = onKeluar) { Text("Keluar", color = AureviaRed, fontSize = 13.sp) }
        }

        // ---------------- isi tab
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.BERANDA -> TabBeranda(onGalat)
                Tab.PENGGUNA -> TabPengguna(onGalat)
                Tab.EFEK -> TabEfek(onGalat)
                Tab.ROOM -> TabRoom(onGalat)
                Tab.SIARAN -> TabSiaran(onGalat)
            }
        }

        // ---------------- navigasi bawah
        Surface(color = Kartu) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Tab.entries.forEach { t ->
                    val aktif = t == tab
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp))
                            .clickable { tab = t }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            t.judul,
                            color = if (aktif) AureviaRed else TeksRedup,
                            fontSize = 12.sp,
                            fontWeight = if (aktif) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- komponen

@Composable
private fun KartuInfo(judul: String, isi: String, aksen: Color = TeksUtama) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Kartu),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(judul, color = TeksRedup, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text(isi, color = aksen, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun KolomGulir(isi: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { isi() }
}

// ---------------------------------------------------------------- Beranda

@Composable
private fun TabBeranda(onGalat: (String) -> Unit) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var memuat by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            data = withContext(Dispatchers.IO) { AdminApi.overview() }
        } catch (e: Exception) {
            onGalat(e.message ?: "Gagal memuat ringkasan")
        } finally {
            memuat = false
        }
    }

    if (memuat) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AureviaRed)
        }
        return
    }
    val d = data
    KolomGulir {
        KartuInfo("Pengguna", d?.optInt("users", 0)?.toString() ?: "—")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                KartuInfo("Staff", d?.optInt("staff", 0)?.toString() ?: "—", Color(0xFF2BD46E))
            }
            Box(Modifier.weight(1f)) {
                KartuInfo("Berverifikasi", d?.optInt("checked", 0)?.toString() ?: "—", Color(0xFFE5B53C))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                KartuInfo("Room live", d?.optInt("rooms", 0)?.toString() ?: "—", Color(0xFF1FA2E8))
            }
            Box(Modifier.weight(1f)) {
                KartuInfo("Perangkat", d?.optInt("devices", 0)?.toString() ?: "—")
            }
        }
        Surface(color = KartuTinggi, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp)) {
                Text("Status push", color = TeksRedup, fontSize = 11.sp)
                Text(d?.optString("push").orEmpty().ifBlank { "—" }, color = TeksUtama, fontSize = 14.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- Pengguna

@Composable
private fun TabPengguna(onGalat: (String) -> Unit) {
    var daftar by remember { mutableStateOf<List<Pengguna>>(emptyList()) }
    var kueri by remember { mutableStateOf("") }
    var memuat by remember { mutableStateOf(true) }
    var sibukUser by remember { mutableStateOf<String?>(null) }
    var pilihRole by remember { mutableStateOf<Pengguna?>(null) }
    var pilihEfek by remember { mutableStateOf<Pengguna?>(null) }
    val scope = rememberCoroutineScope()

    fun muat() {
        memuat = true
        scope.launch {
            try {
                daftar = withContext(Dispatchers.IO) { AdminApi.users(kueri, 100) }.map { it.kePengguna() }
            } catch (e: Exception) {
                onGalat(e.message ?: "Gagal memuat pengguna")
            } finally {
                memuat = false
            }
        }
    }

    LaunchedEffect(Unit) { muat() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = kueri,
                onValueChange = { kueri = it },
                placeholder = { Text("Cari username / nama", color = TeksRedup, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(focusedContainerColor = Kartu, unfocusedContainerColor = Kartu),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { muat() }, shape = RoundedCornerShape(14.dp)) { Text("Cari") }
        }

        if (memuat) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AureviaRed)
            }
            return@Column
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            daftar.forEach { u ->
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Kartu),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(KartuTinggi),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    u.nama.take(1).uppercase(),
                                    color = AureviaRed,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(u.nama, color = TeksUtama, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "@${u.username}" + if (u.terverifikasi) " · ✔" else "" +
                                        if (u.efek.isNotBlank()) " · ✦ ${EfekPremium.label(u.efek)}" else "",
                                    color = TeksRedup,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            LabelRole(u.role)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TombolKecil("Ubah role") { pilihRole = u }
                            TombolKecil("Beri efek") { pilihEfek = u }
                            TombolKecil(
                                if (u.terverifikasi) "Hapus centang" else "Beri centang",
                                bahaya = u.terverifikasi,
                            ) {
                                if (sibukUser != null) return@TombolKecil
                                sibukUser = u.username
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            AdminApi.setRole(
                                                username = u.username,
                                                verified = !u.terverifikasi,
                                                badge = if (u.terverifikasi) "" else null,
                                            )
                                        }
                                        muat()
                                    } catch (e: Exception) {
                                        onGalat(e.message ?: "Gagal mengubah centang")
                                    } finally {
                                        sibukUser = null
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    pilihRole?.let { u ->
        DialogPilih(
            judul = "Role @${u.username}",
            catatan = "Role tetap memberi hak akses. Role kustom hanya label.",
            pilihan = RoleBaku.semua,
            terpilih = u.role,
            izinkanKustom = true,
            onTutup = { pilihRole = null },
            onSimpan = { nilai ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { AdminApi.setRole(username = u.username, role = nilai) }
                        pilihRole = null
                        muat()
                    } catch (e: Exception) {
                        onGalat(e.message ?: "Gagal menyimpan role")
                    }
                }
            },
        )
    }

    pilihEfek?.let { u ->
        DialogPilih(
            judul = "Efek premium @${u.username}",
            catatan = "Hiasan profil. Tidak menambah hak akses.",
            pilihan = EfekPremium.semua,
            terpilih = u.efek,
            izinkanKustom = false,
            onTutup = { pilihEfek = null },
            onSimpan = { nilai ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { AdminApi.setEffect(u.username, nilai.ifBlank { "none" }) }
                        pilihEfek = null
                        muat()
                    } catch (e: Exception) {
                        onGalat(e.message ?: "Gagal menyimpan efek")
                    }
                }
            },
        )
    }
}

@Composable
private fun LabelRole(role: String) {
    val warna = when (role.lowercase()) {
        "owner" -> Color(0xFF2BD46E)
        "developer", "dev" -> Color(0xFF1FA2E8)
        "admin" -> Color(0xFFE5B53C)
        "user" -> TeksRedup
        else -> Color(0xFFC77DFF)
    }
    Surface(shape = RoundedCornerShape(50), color = warna.copy(alpha = 0.18f)) {
        Text(
            role,
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = warna,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TombolKecil(teks: String, bahaya: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (bahaya) AureviaRed.copy(alpha = 0.16f) else KartuTinggi,
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onClick() },
    ) {
        Text(
            teks,
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (bahaya) AureviaRed else TeksUtama,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ---------------------------------------------------------------- Efek

@Composable
private fun TabEfek(onGalat: (String) -> Unit) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var memuat by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            data = withContext(Dispatchers.IO) { AdminApi.effects() }
        } catch (e: Exception) {
            onGalat(e.message ?: "Gagal memuat efek")
        } finally {
            memuat = false
        }
    }

    if (memuat) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AureviaRed)
        }
        return
    }
    val pakai = data?.optJSONObject("usage")
    KolomGulir {
        Text("Efek premium", color = TeksUtama, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "Efek adalah hiasan avatar di profil. Pilih dari tab Pengguna → Beri efek.",
            color = TeksRedup,
            fontSize = 12.sp,
        )
        EfekPremium.semua.forEach { e ->
            val pemakai = pakai?.optJSONArray(e.kunci)
            val jumlah = pemakai?.length() ?: 0
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Kartu),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(e.warna.copy(alpha = 0.22f)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.nama, color = TeksUtama, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(e.kunci, color = TeksRedup, fontSize = 11.sp)
                    }
                    Text(
                        if (jumlah > 0) "$jumlah akun" else "belum dipakai",
                        color = if (jumlah > 0) e.warna else TeksRedup,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Room

@Composable
private fun TabRoom(onGalat: (String) -> Unit) {
    var rooms by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var memuat by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            rooms = withContext(Dispatchers.IO) { AdminApi.rooms() }
        } catch (e: Exception) {
            onGalat(e.message ?: "Gagal memuat room")
        } finally {
            memuat = false
        }
    }

    if (memuat) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AureviaRed)
        }
        return
    }
    KolomGulir {
        Text("Room live", color = TeksUtama, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (rooms.isEmpty()) {
            Text("Belum ada room aktif.", color = TeksRedup, fontSize = 13.sp)
        }
        rooms.forEach { r ->
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Kartu),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(r.optString("name").ifBlank { "Room" }, color = TeksUtama, fontWeight = FontWeight.Bold)
                    Text(
                        "ID ${r.optString("id")} · host ${r.optString("host").ifBlank { r.optString("hostId") }}" +
                            " · ${r.optInt("listeners")} pendengar",
                        color = TeksRedup,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Siaran

@Composable
private fun TabSiaran(onGalat: (String) -> Unit) {
    var teks by remember { mutableStateOf("") }
    var sibuk by remember { mutableStateOf(false) }
    var hasil by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    KolomGulir {
        Text("Siaran", color = TeksUtama, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Pesan singkat ke semua akun Aurevia.", color = TeksRedup, fontSize = 12.sp)
        OutlinedTextField(
            value = teks,
            onValueChange = { if (it.length <= 500) teks = it },
            placeholder = { Text("Pengumuman…", color = TeksRedup, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            colors = TextFieldDefaults.colors(focusedContainerColor = Kartu, unfocusedContainerColor = Kartu),
        )
        Text("${teks.length}/500", color = TeksRedup, fontSize = 11.sp)
        Button(
            onClick = {
                if (teks.isBlank()) {
                    onGalat("Pesan masih kosong.")
                    return@Button
                }
                sibuk = true
                scope.launch {
                    try {
                        val hasilKirim = withContext(Dispatchers.IO) { AdminApi.broadcast(teks.trim()) }
                        hasil = "Terkirim ke ${hasilKirim.optInt("sent", 0)} akun"
                        teks = ""
                    } catch (e: Exception) {
                        onGalat(e.message ?: "Gagal mengirim siaran")
                    } finally {
                        sibuk = false
                    }
                }
            },
            enabled = !sibuk,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AureviaRed),
        ) { Text(if (sibuk) "Mengirim…" else "Kirim siaran", fontWeight = FontWeight.SemiBold) }
        AnimatedVisibility(hasil != null) {
            Text(hasil.orEmpty(), color = Color(0xFF2BD46E), fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------- dialog pilih

@Composable
private fun DialogPilih(
    judul: String,
    catatan: String,
    pilihan: List<Pilihan>,
    terpilih: String,
    izinkanKustom: Boolean,
    onTutup: () -> Unit,
    onSimpan: (String) -> Unit,
) {
    var nilai by remember { mutableStateOf(terpilih) }
    var kustom by remember {
        mutableStateOf(if (izinkanKustom && pilihan.none { it.kunci == terpilih }) terpilih else "")
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onTutup) {
        Surface(shape = RoundedCornerShape(22.dp), color = KartuTinggi) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Text(judul, color = TeksUtama, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))
                Text(catatan, color = TeksRedup, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                pilihan.forEach { p ->
                    val aktif = nilai == p.kunci && kustom.isBlank()
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (aktif) p.warna.copy(alpha = 0.20f) else Color.Transparent)
                            .clickable {
                                nilai = p.kunci
                                kustom = ""
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(p.warna))
                        Spacer(Modifier.width(10.dp))
                        Text(p.nama, color = if (aktif) p.warna else TeksUtama, fontSize = 14.sp)
                    }
                }
                if (izinkanKustom) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = kustom,
                        onValueChange = { if (it.length <= 20) kustom = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '.' || c == '_' || c == '-' } },
                        label = { Text("Role kustom", fontSize = 12.sp) },
                        placeholder = { Text("mis. fotografer", color = TeksRedup, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Kartu, unfocusedContainerColor = Kartu),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onTutup) { Text("Batal", color = TeksRedup) }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = { onSimpan(kustom.trim().ifBlank { nilai }) },
                        shape = RoundedCornerShape(20.dp),
                    ) { Text("Simpan") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- daftar pilihan

internal data class Pilihan(val kunci: String, val nama: String, val warna: Color)

internal object RoleBaku {
    val semua = listOf(
        Pilihan("user", "User", Color(0xFF9E9EAE)),
        Pilihan("admin", "Admin", Color(0xFFE5B53C)),
        Pilihan("owner", "Owner", Color(0xFF2BD46E)),
        Pilihan("developer", "Developer", Color(0xFF1FA2E8)),
    )
}

/** Enam efek; urutan & kunci sama dengan EFEK_PREMIUM di server. */
internal object EfekPremium {
    val semua = listOf(
        Pilihan("aurora", "Aurora", Color(0xFF6EE7F9)),
        Pilihan("emas", "Emas", Color(0xFFE9C46A)),
        Pilihan("neon", "Neon", Color(0xFF7CFF6B)),
        Pilihan("kristal", "Kristal", Color(0xFFB9E4FF)),
        Pilihan("api", "Api", Color(0xFFFF7A3D)),
        Pilihan("hologram", "Hologram", Color(0xFFC77DFF)),
    )

    fun label(kunci: String): String =
        semua.firstOrNull { it.kunci == kunci.trim().lowercase() }?.nama ?: kunci
}
