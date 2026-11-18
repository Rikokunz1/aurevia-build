/*
 * Aurevia Admin — layar Pengguna.
 *
 * Inilah layar yang dulu kurang: tombol ban dan mute tidak pernah dipasang
 * walaupun AdminApi sudah punya fungsinya, dan status moderasi (banned/muted)
 * tidak dibaca dari server. Sekarang tiap baris menampilkan keadaan akun
 * sebenarnya, dan setiap aksi punya konfirmasi sebelum dikirim.
 */
package com.aurevia.admin

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Satu akun seperti yang dikirim /v1/admin/users. */
internal data class Pengguna(
    val id: String,
    val username: String,
    val nama: String,
    val role: String,
    val efek: String,
    val terverifikasi: Boolean,
    val badge: String,
    val muted: Boolean,
    val banned: Boolean,
    val banReason: String,
    val lastIp: String,
) {
    val adaHukuman: Boolean get() = muted || banned
}

private fun JSONObject.kePengguna() = Pengguna(
    id = optString("id"),
    username = optString("username"),
    nama = optString("name").ifBlank { optString("username") },
    role = optString("role").ifBlank { "user" },
    efek = optString("efek").ifBlank { optString("effect") },
    terverifikasi = optBoolean("verified"),
    badge = optString("badge"),
    muted = optBoolean("muted"),
    banned = optBoolean("banned"),
    banReason = optString("banReason"),
    lastIp = optString("lastIp"),
)

@Composable
internal fun TabPengguna(onGalat: (String) -> Unit, onPesan: (String) -> Unit) {
    var daftar by remember { mutableStateOf<List<Pengguna>>(emptyList()) }
    var kueri by remember { mutableStateOf("") }
    var memuat by remember { mutableStateOf(true) }
    var sibuk by remember { mutableStateOf<String?>(null) }

    // dialog yang sedang terbuka; null = tidak ada
    var ubahRole by remember { mutableStateOf<Pengguna?>(null) }
    var beriEfek by remember { mutableStateOf<Pengguna?>(null) }
    var banUser by remember { mutableStateOf<Pengguna?>(null) }
    var blokIp by remember { mutableStateOf<Pengguna?>(null) }
    var lepasBan by remember { mutableStateOf<Pengguna?>(null) }

    val scope = rememberCoroutineScope()

    fun muat(cari: String = kueri) {
        memuat = true
        scope.launch {
            try {
                daftar = withContext(Dispatchers.IO) { AdminApi.users(cari, 100) }.map { it.kePengguna() }
            } catch (e: Exception) {
                onGalat(e.message ?: "Gagal memuat pengguna")
            } finally {
                memuat = false
            }
        }
    }

    fun jalankan(aksi: suspend () -> JSONObject, pesanSukses: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { aksi() }
                onPesan(pesanSukses)
                muat()
            } catch (e: Exception) {
                onGalat(e.message ?: "Aksi gagal")
            } finally {
                sibuk = null
            }
        }
    }

    LaunchedEffect(Unit) { muat() }

    Column(Modifier.fillMaxSize()) {
        // ---------------- pencarian
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                Isian(
                    nilai = kueri,
                    onUbah = { kueri = it },
                    label = "Cari username atau nama",
                    modifier = Modifier.fillMaxWidth(),
                    aksiIme = androidx.compose.ui.text.input.ImeAction.Search,
                )
            }
            Spacer(Modifier.width(8.dp))
            TombolPil("Cari", warna = Color.White, latar = AureviaRed) { muat() }
        }

        if (memuat) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AureviaRed, strokeWidth = 2.dp)
            }
            return@Column
        }

        if (daftar.isEmpty()) {
            Kosong("◎", "Tidak ada akun", "Coba kata kunci lain, atau tekan Cari untuk memuat ulang.")
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${daftar.size} akun",
                        color = TeksRedup,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text("Ketuk baris untuk aksi lain", color = TeksRedup, fontSize = 11.sp)
                }
            }
            items(daftar, key = { it.id.ifBlank { it.username } }) { u ->
                BarisPengguna(
                    u = u,
                    sibuk = sibuk == u.username,
                    onUbahRole = { ubahRole = u },
                    onBeriEfek = { beriEfek = u },
                    onBan = { if (u.banned) lepasBan = u else banUser = u },
                    onMute = {
                        sibuk = u.username
                        jalankan(
                            { AdminApi.mute(u.username, !u.muted) },
                            if (u.muted) "@${u.username} bisa membuat room lagi" else "@${u.username} dibisukan",
                        )
                    },
                    onBlokIp = { blokIp = u },
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    // ---------------- dialog: role
    ubahRole?.let { u ->
        PilihNilaiDialog(
            judul = "Role @${u.username}",
            catatan = "Role tetap memberi hak akses. Role kustom hanya label di profil.",
            pilihan = RoleBaku.semua,
            terpilih = u.role,
            izinkanKustom = true,
            onTutup = { ubahRole = null },
            onSimpan = { nilai ->
                ubahRole = null
                sibuk = u.username
                jalankan({ AdminApi.setRole(username = u.username, role = nilai) }, "Role @${u.username} → $nilai")
            },
        )
    }

    // ---------------- dialog: efek
    beriEfek?.let { u ->
        PilihNilaiDialog(
            judul = "Efek premium @${u.username}",
            catatan = "Hiasan avatar di profil. Tidak menambah hak akses.",
            pilihan = EfekPremium.semua,
            terpilih = u.efek,
            izinkanKustom = false,
            onTutup = { beriEfek = null },
            onSimpan = { nilai ->
                beriEfek = null
                sibuk = u.username
                jalankan(
                    { AdminApi.setEffect(u.username, nilai.ifBlank { "none" }) },
                    "Efek @${u.username} → ${EfekPremium.label(nilai)}",
                )
            },
        )
    }

    // ---------------- dialog: ban (wajib alasan, bisa sekalian blokir IP)
    banUser?.let { u ->
        DialogAksi(
            judul = "Ban @${u.username}",
            catatan = "Akun tidak bisa masuk lagi. Alasan ini dikirim ke perangkat " +
                "pengguna saat mereka mencoba masuk, jadi tulis yang jelas.",
            labelTombol = "Ban akun",
            teksIsian = "Alasan (mis. spam link)",
            onTutup = { banUser = null },
            onSimpan = { alasan ->
                banUser = null
                sibuk = u.username
                jalankan(
                    { AdminApi.ban(u.username, banned = true, reason = alasan, blockIp = true) },
                    "@${u.username} diban",
                )
            },
        )
    }

    lepasBan?.let { u ->
        DialogAksi(
            judul = "Lepas ban @${u.username}",
            catatan = "Akun bisa masuk lagi seperti biasa." +
                (if (u.banReason.isNotBlank()) "\n\nAlasan ban sebelumnya: “${u.banReason}”" else ""),
            labelTombol = "Lepas ban",
            warnaTombol = Hijau,
            onTutup = { lepasBan = null },
            onSimpan = {
                lepasBan = null
                sibuk = u.username
                jalankan({ AdminApi.ban(u.username, banned = false) }, "Ban @${u.username} dicabut")
            },
        )
    }

    // ---------------- dialog: blokir IP (lepas blokir manual)
    blokIp?.let { u ->
        DialogAksi(
            judul = "Blokir IP @${u.username}",
            catatan = if (u.lastIp.isBlank())
                "Akun ini belum punya catatan IP, jadi tidak ada yang bisa diblokir."
            else
                "IP terakhir: ${u.lastIp}\n\nBlokir IP mencegah pendaftaran ulang dari jaringan " +
                    "yang sama. Hati-hati bila IP itu dipakai bersama (WiFi umum).",
            labelTombol = "Blokir IP",
            teksIsian = null,
            onTutup = { blokIp = null },
            onSimpan = {
                blokIp = null
                if (u.lastIp.isBlank()) {
                    onGalat("Tidak ada IP tercatat untuk @${u.username}")
                    return@DialogAksi
                }
                sibuk = u.username
                jalankan({ AdminApi.banIp(u.lastIp, true) }, "IP ${u.lastIp} diblokir")
            },
        )
    }
}

@Composable
private fun BarisPengguna(
    u: Pengguna,
    sibuk: Boolean,
    onUbahRole: () -> Unit,
    onBeriEfek: () -> Unit,
    onBan: () -> Unit,
    onMute: () -> Unit,
    onBlokIp: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Kartu),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar: huruf awal, diberi cincin warna efek bila punya
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(EfekPremium.warnaOf(u.efek).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        u.nama.take(1).uppercase(),
                        color = EfekPremium.warnaOf(u.efek),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            u.nama,
                            color = TeksUtama,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (u.terverifikasi) {
                            Spacer(Modifier.width(4.dp))
                            Text("✓", color = Biru, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "@${u.username}" + if (u.efek.isNotBlank() && u.efek != "none")
                            " · ✦ ${EfekPremium.label(u.efek)}" else "",
                        color = TeksRedup,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (sibuk) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = AureviaRed, strokeWidth = 2.dp)
                } else {
                    Kapsul(roleLabel(u.role), roleWarna(u.role))
                }
            }

            // Status moderasi — inilah yang dulu tidak pernah tampil
            if (u.adaHukuman) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (u.banned) Kapsul("BAN", AureviaRed)
                    if (u.muted) Kapsul("MUTE", Kuning)
                    if (u.banned && u.banReason.isNotBlank()) {
                        Text(
                            u.banReason,
                            color = TeksRedup,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
            }

            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TombolPil("Role", onClick = onUbahRole)
                TombolPil("Efek", onClick = onBeriEfek)
                TombolPil(
                    if (u.muted) "Suarakan" else "Bisukan",
                    warna = if (u.muted) Hijau else Kuning,
                    latar = (if (u.muted) Hijau else Kuning).copy(alpha = 0.14f),
                    onClick = onMute,
                )
                TombolPil(
                    if (u.banned) "Lepas ban" else "Ban",
                    warna = if (u.banned) Hijau else AureviaRed,
                    latar = (if (u.banned) Hijau else AureviaRed).copy(alpha = 0.14f),
                    onClick = onBan,
                )
                TombolPil("IP", onClick = onBlokIp)
            }
        }
    }
}

internal fun roleLabel(role: String): String = when (role.lowercase()) {
    "owner" -> "Owner"
    "developer", "dev" -> "Developer"
    "admin" -> "Admin"
    "user" -> "User"
    else -> role
}
