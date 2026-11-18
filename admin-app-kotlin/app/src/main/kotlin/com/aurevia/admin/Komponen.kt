/*
 * Aurevia Admin — potongan layar yang dipakai berulang:
 * kepala, navigasi bawah, kolom gulir, dan dialog.
 */
package com.aurevia.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------------ kepala

/**
 * Kepala panel.
 *
 * Dulu nama "Aurevia Admin" bertumpuk dengan lencana centang karena keduanya
 * ditaruh di satu Column tanpa jarak. Sekarang status akun dipindah ke baris
 * sendiri sebagai kapsul, jadi tidak mungkin bertabrakan lagi.
 */
@Composable
internal fun Kepala(
    nama: String,
    role: String,
    onKeluar: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(GradienMerek),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    nama.take(1).uppercase().ifBlank { "A" },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    nama.ifBlank { "Aurevia Admin" },
                    color = TeksUtama,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Kapsul(roleLabel(role), roleWarna(role))
                    Spacer(Modifier.width(6.dp))
                    Text("Panel admin", color = TeksRedup, fontSize = 11.sp)
                }
            }
            TextButton(onClick = onKeluar) {
                Text("Keluar", color = AureviaRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

internal fun roleWarna(role: String): Color = when (role.lowercase()) {
    "owner" -> Hijau
    "developer", "dev" -> Biru
    "admin" -> Kuning
    "user" -> TeksRedup
    else -> Ungu
}

// ------------------------------------------------------------------ navigasi

/** Satu tujuan navigasi: glyph + label. Keduanya satu kolom, jadi tidak menumpuk. */
internal enum class Tujuan(val glyph: String, val label: String) {
    BERANDA("◈", "Beranda"),
    PENGGUNA("◉", "Pengguna"),
    EFEK("✦", "Efek"),
    ROOM("◍", "Room"),
    SIARAN("◐", "Siaran"),
}

@Composable
internal fun NavigasiBawah(aktif: Tujuan, onPilih: (Tujuan) -> Unit) {
    Surface(color = Kartu) {
        Column {
            GarisTipis()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Tujuan.entries.forEach { t ->
                    val menyala = t == aktif
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onPilih(t) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            t.glyph,
                            color = if (menyala) AureviaRed else TeksRedup,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            t.label,
                            color = if (menyala) AureviaRed else TeksRedup,
                            fontSize = 10.sp,
                            fontWeight = if (menyala) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ kolom

@Composable
internal fun KolomGulir(
    jarak: Int = 12,
    isi: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(jarak.dp),
        content = isi,
    )
}

/** Kolom di dalam kartu, tanpa gulir sendiri. */
@Composable
internal fun KolomIsi(jarak: Int = 10, isi: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(jarak.dp),
        content = isi,
    )
}

// ------------------------------------------------------------------ dialog

/**
 * Dialog satu-aksi: judul, penjelasan, isian opsional, lalu tombol.
 *
 * Dipakai untuk ubah role, beri efek, alasan ban, dan konfirmasi berbahaya —
 * supaya semua aksi punya bentuk yang sama dan tidak ada yang "liar".
 */
@Composable
internal fun DialogAksi(
    judul: String,
    catatan: String,
    labelTombol: String,
    warnaTombol: Color = AureviaRed,
    teksIsian: String? = null,
    nilaiAwal: String = "",
    onTutup: () -> Unit,
    onSimpan: (String) -> Unit,
) {
    var isi by remember { mutableStateOf(nilaiAwal) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onTutup) {
        Surface(shape = RoundedCornerShape(22.dp), color = KartuTinggi) {
            Column(Modifier.padding(20.dp)) {
                Text(judul, color = TeksUtama, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                Text(catatan, color = TeksRedup, fontSize = 12.sp, lineHeight = 18.sp)
                if (teksIsian != null) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = isi,
                        onValueChange = { if (it.length <= 120) isi = it },
                        placeholder = { Text(teksIsian, color = TeksRedup, fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Kartu,
                            unfocusedContainerColor = Kartu,
                        ),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onTutup) { Text("Batal", color = TeksRedup) }
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = { onSimpan(isi.trim()) }, shape = RoundedCornerShape(20.dp)) {
                        Text(labelTombol)
                    }
                }
            }
        }
    }
}

/** Isian teks dengan gaya panel (dipakai di login, pencarian, siaran). */
@Composable
internal fun Isian(
    nilai: String,
    onUbah: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    samar: Boolean = false,
    satuBaris: Boolean = true,
    aksiIme: ImeAction = ImeAction.Done,
) {
    OutlinedTextField(
        value = nilai,
        onValueChange = onUbah,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = satuBaris,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(imeAction = aksiIme),
        visualTransformation = if (samar) androidx.compose.ui.text.input.PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Kartu,
            unfocusedContainerColor = Kartu,
            focusedIndicatorColor = AureviaRed,
            unfocusedIndicatorColor = Garis,
        ),
    )
}
