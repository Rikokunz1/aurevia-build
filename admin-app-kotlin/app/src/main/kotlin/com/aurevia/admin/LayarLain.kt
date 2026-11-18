/*
 * Aurevia Admin — Beranda, Efek, Room, Siaran, dan pemilih role/efek.
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.OutlinedTextField
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

// ---------------------------------------------------------------- Beranda

@Composable
internal fun TabBeranda(onGalat: (String) -> Unit) {
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var memuat by remember { mutableStateOf(true) }
    var segarkan by remember { mutableStateOf(0) }

    LaunchedEffect(segarkan) {
        memuat = true
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
            CircularProgressIndicator(color = AureviaRed, strokeWidth = 2.dp)
        }
        return
    }

    val d = data
    val pengguna = d?.optInt("users", 0) ?: 0
    val staff = d?.optInt("staff", 0) ?: 0
    val terverifikasi = d?.optInt("checked", 0) ?: 0
    val room = d?.optInt("rooms", 0) ?: 0
    val perangkat = d?.optInt("devices", 0) ?: 0
    val push = d?.optString("push").orEmpty()

    KolomGulir {
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            JudulBagian("Ringkasan", "Keadaan server saat ini")
            Box(Modifier.weight(1f))
            TombolPil("Segarkan") { segarkan++ }
        }

        // angka utama dibuat lebar penuh: ini yang paling sering dilihat
        KartuMetrik("Total pengguna", pengguna.toString(), AureviaRed, "◉")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KartuMetrik("Staff", staff.toString(), Hijau, "★", Modifier.weight(1f))
            KartuMetrik("Berverifikasi", terverifikasi.toString(), Kuning, "✓", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KartuMetrik("Room live", room.toString(), Biru, "◍", Modifier.weight(1f))
            KartuMetrik("Perangkat", perangkat.toString(), Ungu, "▤", Modifier.weight(1f))
        }

        // status ditulis dalam bahasa manusia, bukan "configured"
        KartuDaftar {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                LingkaranIkon("◔", if (push.isBlank()) Kuning else Hijau)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (push.isBlank()) "Notifikasi belum aktif" else "Notifikasi aktif",
                        color = TeksUtama,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (push.isBlank()) "Belum ada kunci push di server. Notifikasi tidak akan terkirim."
                        else "Perangkat terdaftar menerima notifikasi dari panel ini.",
                        color = TeksRedup,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

// ---------------------------------------------------------------- Efek

@Composable
internal fun TabEfek(onGalat: (String) -> Unit) {
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
            CircularProgressIndicator(color = AureviaRed, strokeWidth = 2.dp)
        }
        return
    }

    val pakai = data?.optJSONObject("usage")
    KolomGulir {
        Spacer(Modifier.height(2.dp))
        JudulBagian("Efek premium", "Hiasan avatar di profil. Pilih di tab Pengguna → Efek.")
        EfekPremium.semua.forEach { e ->
            val jumlah = pakai?.optJSONArray(e.kunci)?.length() ?: 0
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Kartu),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    // pratinjau cincin efek, bukan sekadar titik warna
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(e.warna.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(e.warna.copy(alpha = 0.55f)),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.nama, color = TeksUtama, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (jumlah == 0) "Belum dipakai" else "$jumlah akun memakai",
                            color = if (jumlah > 0) e.warna else TeksRedup,
                            fontSize = 11.sp,
                        )
                    }
                    Kapsul(e.kunci, e.warna)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ---------------------------------------------------------------- Room

@Composable
internal fun TabRoom(onGalat: (String) -> Unit) {
    var rooms by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var memuat by remember { mutableStateOf(true) }
    var segarkan by remember { mutableStateOf(0) }

    LaunchedEffect(segarkan) {
        memuat = true
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
            CircularProgressIndicator(color = AureviaRed, strokeWidth = 2.dp)
        }
        return
    }

    KolomGulir {
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            JudulBagian("Room live", if (rooms.isEmpty()) "Tidak ada siaran berjalan" else "${rooms.size} siaran berjalan")
            Box(Modifier.weight(1f))
            TombolPil("Segarkan") { segarkan++ }
        }
        if (rooms.isEmpty()) {
            Kosong("◍", "Sunyi sekarang", "Belum ada room live yang aktif di server.")
        }
        rooms.forEach { r ->
            val jumlah = r.optInt("listeners")
            val nama = r.optString("name").ifBlank { "Room tanpa nama" }
            val host = r.optString("host").ifBlank { r.optString("hostId") }
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Kartu),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    LingkaranIkon("◍", Biru, 36)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                nama,
                                color = TeksUtama,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (r.optBoolean("hasPassword")) {
                                Spacer(Modifier.width(6.dp))
                                Kapsul("kunci", Kuning)
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Host ${host.ifBlank { "—" }} · ID ${r.optString("id")}",
                            color = TeksRedup,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$jumlah", color = TeksUtama, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("pendengar", color = TeksRedup, fontSize = 10.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// ---------------------------------------------------------------- Siaran

@Composable
internal fun TabSiaran(onGalat: (String) -> Unit, onPesan: (String) -> Unit) {
    var teks by remember { mutableStateOf("") }
    var sibuk by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    KolomGulir {
        Spacer(Modifier.height(2.dp))
        JudulBagian("Siaran", "Pesan singkat yang muncul di semua akun Aurevia.")
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 130.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Kartu)
                .padding(4.dp),
        ) {
            OutlinedTextField(
                value = teks,
                onValueChange = { if (it.length <= 500) teks = it },
                placeholder = { Text("Tulis pengumuman…", color = TeksRedup, fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 122.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Kartu,
                    unfocusedContainerColor = Kartu,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text("${teks.length}/500", color = TeksRedup, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("Tidak bisa dibatalkan", color = TeksRedup, fontSize = 11.sp)
        }
        Button(
            onClick = {
                if (teks.isBlank()) {
                    onGalat("Pesan masih kosong.")
                    return@Button
                }
                sibuk = true
                scope.launch {
                    try {
                        val hasil = withContext(Dispatchers.IO) { AdminApi.broadcast(teks.trim()) }
                        onPesan("Siaran terkirim ke ${hasil.optInt("sent", 0)} akun")
                        teks = ""
                    } catch (e: Exception) {
                        onGalat(e.message ?: "Gagal mengirim siaran")
                    } finally {
                        sibuk = false
                    }
                }
            },
            enabled = !sibuk && teks.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AureviaRed),
        ) {
            Text(if (sibuk) "Mengirim…" else "Kirim siaran", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        JudulBagian("Toast aplikasi", "Tulisan singkat di layar saat aplikasi dibuka.")
        BagianToastAplikasi(onGalat, onPesan)
        Spacer(Modifier.height(4.dp))
    }
}

// ------------------------------------------------------------ toast aplikasi

/**
 * Toast yang muncul saat pengguna membuka aplikasi.
 *
 * Ini sengaja dipisah dari siaran: siaran membuat notifikasi, toast hanya
 * tulisan singkat di layar. Server menyimpannya di /v1/admin/app-toast.
 */
@Composable
internal fun BagianToastAplikasi(onGalat: (String) -> Unit, onPesan: (String) -> Unit) {
    var teks by remember { mutableStateOf("") }
    var aktif by remember { mutableStateOf(false) }
    var memuat by remember { mutableStateOf(true) }
    var sibuk by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val d = withContext(Dispatchers.IO) { AdminApi.appToast() }
            teks = d.optString("text")
            aktif = d.optBoolean("enabled")
        } catch (e: Exception) {
            onGalat(e.message ?: "Gagal memuat toast aplikasi")
        } finally {
            memuat = false
        }
    }

    if (memuat) return

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Kartu),
    ) {
        KolomIsi {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LingkaranIkon(if (aktif) "✓" else "◌", if (aktif) Hijau else TeksRedup)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Toast saat aplikasi dibuka", color = TeksUtama, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (aktif) "Aktif — muncul sekali setiap aplikasi dibuka." else "Nonaktif.",
                        color = TeksRedup,
                        fontSize = 11.sp,
                    )
                }
                TombolPil(
                    if (aktif) "Matikan" else "Nyalakan",
                    warna = if (aktif) AureviaRed else Hijau,
                    latar = (if (aktif) AureviaRed else Hijau).copy(alpha = 0.14f),
                ) {
                    sibuk = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { AdminApi.setAppToast(teks, !aktif) }
                            aktif = !aktif
                            onPesan(if (aktif) "Toast aplikasi dinyalakan" else "Toast aplikasi dimatikan")
                        } catch (e: Exception) {
                            onGalat(e.message ?: "Gagal mengubah toast")
                        } finally {
                            sibuk = false
                        }
                    }
                }
            }
            Isian(teks, { if (it.length <= 200) teks = it }, "Teks toast (maks. 200)", Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (teks.isBlank()) {
                        onGalat("Teks toast masih kosong.")
                        return@Button
                    }
                    sibuk = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { AdminApi.setAppToast(teks.trim(), aktif) }
                            onPesan("Teks toast disimpan")
                        } catch (e: Exception) {
                            onGalat(e.message ?: "Gagal menyimpan toast")
                        } finally {
                            sibuk = false
                        }
                    }
                },
                enabled = !sibuk,
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                shape = RoundedCornerShape(23.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KartuTinggi),
            ) { Text(if (sibuk) "Menyimpan…" else "Simpan teks", color = TeksUtama) }
        }
    }
}

// ---------------------------------------------------------------- pemilih

internal data class Pilihan(val kunci: String, val nama: String, val warna: Color)

internal object RoleBaku {
    val semua = listOf(
        Pilihan("user", "User", TeksRedup),
        Pilihan("admin", "Admin", Kuning),
        Pilihan("owner", "Owner", Hijau),
        Pilihan("developer", "Developer", Biru),
    )
}

/** Enam efek; kunci sama persis dengan EFEK_PREMIUM di server. */
internal object EfekPremium {
    val semua = listOf(
        Pilihan("aurora", "Aurora", Cyan),
        Pilihan("emas", "Emas", Color(0xFFE9C46A)),
        Pilihan("neon", "Neon", Color(0xFF7CFF6B)),
        Pilihan("kristal", "Kristal", Color(0xFFB9E4FF)),
        Pilihan("api", "Api", Oranye),
        Pilihan("hologram", "Hologram", Ungu),
    )

    fun label(kunci: String): String =
        semua.firstOrNull { it.kunci == kunci.trim().lowercase() }?.nama ?: "Tanpa efek"

    fun warnaOf(kunci: String): Color =
        semua.firstOrNull { it.kunci == kunci.trim().lowercase() }?.warna ?: TeksRedup
}

@Composable
internal fun PilihNilaiDialog(
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
                Spacer(Modifier.height(5.dp))
                Text(catatan, color = TeksRedup, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(14.dp))
                pilihan.forEach { p ->
                    val aktif = nilai == p.kunci && kustom.isBlank()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (aktif) p.warna.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable {
                                nilai = p.kunci
                                kustom = ""
                            }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(p.warna))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            p.nama,
                            color = if (aktif) p.warna else TeksUtama,
                            fontSize = 14.sp,
                            fontWeight = if (aktif) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (aktif) Text("✓", color = p.warna, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (izinkanKustom) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = kustom,
                        onValueChange = {
                            if (it.length <= 20) {
                                kustom = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '.' || c == '_' || c == '-' }
                            }
                        },
                        label = { Text("Role kustom", fontSize = 12.sp) },
                        placeholder = { Text("mis. fotografer", color = TeksRedup, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Kartu, unfocusedContainerColor = Kartu),
                    )
                    Text(
                        "2–20 huruf kecil, angka, titik, garis bawah, atau strip. Hanya label.",
                        color = TeksRedup,
                        fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
