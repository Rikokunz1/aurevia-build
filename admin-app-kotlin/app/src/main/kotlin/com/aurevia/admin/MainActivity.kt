/*
 * Aurevia Admin — panel Kotlin/Compose.
 *
 * Seluruh layar digambar dengan Compose; tidak ada HTML yang dimuat. Permintaan
 * HTTP ada di AdminApi.kt, potongan tampilan ada di Tema.kt, Komponen.kt,
 * LayarPengguna.kt, dan LayarLain.kt.
 *
 * Paket com.aurevia.admin sama dengan rilis sebelumnya supaya pembaruan bisa
 * menimpa versi lama bila sertifikatnya sama.
 */
package com.aurevia.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = TemaGelap) { PanelAdmin() }
        }
    }
}

/** Pesan singkat di atas layar: galat berwarna merah, sukses berwarna hijau. */
private data class Pesan(val teks: String, val galat: Boolean)

@Composable
private fun PanelAdmin() {
    val ctx = LocalContext.current
    var token by remember { mutableStateOf("") }
    var pesan by remember { mutableStateOf<Pesan?>(null) }
    var hitungPesan by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        AdminSession.load(ctx)
        token = AdminSession.token
    }

    // Pesan hilang sendiri setelah beberapa detik supaya tidak menumpuk di layar.
    LaunchedEffect(hitungPesan) {
        if (pesan != null) {
            delay(3600)
            pesan = null
        }
    }

    fun kabari(teks: String, galat: Boolean) {
        pesan = Pesan(teks, galat)
        hitungPesan++
    }

    Surface(Modifier.fillMaxSize(), color = Latar) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (token.isBlank()) {
                    LayarMasuk(
                        onMasuk = {
                            pesan = null
                            token = AdminSession.token
                        },
                        onGalat = { kabari(it, galat = true) },
                    )
                } else {
                    IsiPanel(
                        onKeluar = {
                            AdminSession.clear(ctx)
                            token = ""
                        },
                        onGalat = { kabari(it, galat = true) },
                        onPesan = { kabari(it, galat = false) },
                    )
                }
            }

            // Toast dalam-aplikasi, menempel di bawah
            AnimatedVisibility(
                visible = pesan != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            ) {
                val p = pesan
                if (p != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (p.galat) Color(0xFF2A1216) else Color(0xFF10241A),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (p.galat) "!" else "✓",
                                color = if (p.galat) AureviaRed else Hijau,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                p.teks,
                                color = TeksUtama,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
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
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(GradienMerek),
            contentAlignment = Alignment.Center,
        ) {
            Text("A", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text("Aurevia Admin", color = TeksUtama, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Masuk dengan akun owner atau developer.",
            color = TeksRedup,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(28.dp))
        Isian(user, { user = it.trim() }, "Username", Modifier.fillMaxWidth(), aksiIme = ImeAction.Next)
        Spacer(Modifier.height(10.dp))
        Isian(sandi, { sandi = it }, "Kata sandi", Modifier.fillMaxWidth(), samar = true)
        Spacer(Modifier.height(20.dp))
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
private fun IsiPanel(
    onKeluar: () -> Unit,
    onGalat: (String) -> Unit,
    onPesan: (String) -> Unit,
) {
    var tujuan by remember { mutableStateOf(Tujuan.BERANDA) }

    Column(Modifier.fillMaxSize()) {
        Kepala(
            nama = AdminSession.name.ifBlank { AdminSession.username },
            role = AdminSession.role,
            onKeluar = onKeluar,
        )
        GarisTipis()

        Box(Modifier.weight(1f)) {
            when (tujuan) {
                Tujuan.BERANDA -> TabBeranda(onGalat)
                Tujuan.PENGGUNA -> TabPengguna(onGalat, onPesan)
                Tujuan.EFEK -> TabEfek(onGalat)
                Tujuan.ROOM -> TabRoom(onGalat)
                Tujuan.SIARAN -> TabSiaran(onGalat, onPesan)
            }
        }

        NavigasiBawah(tujuan) { tujuan = it }
    }
}
