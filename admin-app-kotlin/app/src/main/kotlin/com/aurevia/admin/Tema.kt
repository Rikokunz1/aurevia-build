/*
 * Aurevia Admin — tema & komponen dasar.
 *
 * Palet gelap dengan satu aksen merah Aurevia. Semua ukuran memakai skala 4 dp
 * supaya jarak antar unsur konsisten; tidak ada warna yang ditulis langsung di
 * layar (selalu lewat Nalar*), jadi tema bisa diganti dari satu berkas ini.
 */
package com.aurevia.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------------ warna

internal val AureviaRed = Color(0xFFFA2D48)
internal val Latar = Color(0xFF08080C)
internal val Kartu = Color(0xFF14141C)
internal val KartuTinggi = Color(0xFF1D1D28)
internal val Garis = Color(0xFF26262F)
internal val TeksUtama = Color(0xFFF4F4F8)
internal val TeksRedup = Color(0xFF8E8E9E)
internal val Hijau = Color(0xFF2BD46E)
internal val Kuning = Color(0xFFE5B53C)
internal val Biru = Color(0xFF1FA2E8)
internal val Ungu = Color(0xFFC77DFF)
internal val Oranye = Color(0xFFFF7A3D)
internal val Cyan = Color(0xFF6EE7F9)

internal val TemaGelap = darkColorScheme(
    primary = AureviaRed,
    background = Latar,
    surface = Kartu,
    onPrimary = Color.White,
    onBackground = TeksUtama,
    onSurface = TeksUtama,
    outline = Garis,
)

// ------------------------------------------------------------------ komponen

/** Judul bagian; dipakai di atas daftar supaya layar tidak terasa menggantung. */
@Composable
internal fun JudulBagian(teks: String, catatan: String? = null) {
    Column {
        Text(teks, color = TeksUtama, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (catatan != null) {
            Spacer(Modifier.height(3.dp))
            Text(catatan, color = TeksRedup, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

/**
 * Kartu metrik: label kecil di atas, angka besar di bawah, ikon berwarna di
 * kanan. Angka memakai tabular supaya tidak bergeser saat nilainya berubah.
 */
@Composable
internal fun KartuMetrik(
    label: String,
    nilai: String,
    aksen: Color,
    ikon: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Kartu),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = TeksRedup, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                LingkaranIkon(ikon, aksen)
            }
            Spacer(Modifier.height(10.dp))
            Text(nilai, color = TeksUtama, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Ikon teks di dalam kapsul berwarna lembut. */
@Composable
internal fun LingkaranIkon(glyph: String, warna: Color, ukuran: Int = 30) {
    Box(
        Modifier.size(ukuran.dp).clip(CircleShape).background(warna.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = warna, fontSize = (ukuran * 0.45).sp, fontWeight = FontWeight.Bold)
    }
}

/** Kapsul label status. `isi` pendek: "Ban", "Mute", "Owner". */
@Composable
internal fun Kapsul(isi: String, warna: Color) {
    Surface(shape = RoundedCornerShape(50), color = warna.copy(alpha = 0.16f)) {
        Text(
            isi,
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = warna,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Tombol pil kecil untuk aksi per baris. */
@Composable
internal fun TombolPil(
    teks: String,
    warna: Color = TeksUtama,
    latar: Color = KartuTinggi,
    aktif: Boolean = true,
    onClick: () -> Unit,
) {
    val isi = if (aktif) warna else TeksRedup.copy(alpha = 0.5f)
    Surface(
        shape = RoundedCornerShape(50),
        color = latar,
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = aktif) { onClick() },
    ) {
        Text(
            teks,
            Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            color = isi,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Garis pemisah tipis; dipakai di dalam kartu. */
@Composable
internal fun GarisTipis() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Garis))
}

/** Kartu pembungkus daftar; isi dipisah garis, bukan kartu terpisah per baris. */
@Composable
internal fun KartuDaftar(isi: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Kartu),
    ) {
        Column { isi() }
    }
}

/** Baris kosong yang ramah, bukan teks "tidak ada data" yang dingin. */
@Composable
internal fun Kosong(ikon: String, judul: String, catatan: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(KartuTinggi),
            contentAlignment = Alignment.Center,
        ) { Text(ikon, color = TeksRedup, fontSize = 20.sp) }
        Spacer(Modifier.height(12.dp))
        Text(judul, color = TeksUtama, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(catatan, color = TeksRedup, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

/** Aksen gradien merek; dipakai untuk avatar & garis atas kepala layar. */
internal val GradienMerek = Brush.linearGradient(listOf(AureviaRed, Color(0xFF8B5CF6)))
