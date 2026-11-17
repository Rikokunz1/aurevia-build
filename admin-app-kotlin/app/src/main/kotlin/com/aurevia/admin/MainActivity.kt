/* Aurevia Admin (Kotlin/Compose) — owner & developer control panel. GPL-3.0. */
package com.aurevia.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private val AdminRed = Color(0xFFF2545B)
private val AdminBg = Color(0xFF0B0B0F)
private val AdminCardBg = Color(0xFF15161C)
private val AdminStroke = Color(0xFF262832)
private val AdminInk = Color(0xFFF3F0F5)
private val AdminMuted = Color(0xFF9A94A3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = AdminRed,
                    onPrimary = Color.White,
                    background = AdminBg,
                    onBackground = AdminInk,
                    surface = AdminCardBg,
                    onSurface = AdminInk,
                    surfaceVariant = Color(0xFF1D1F27),
                    onSurfaceVariant = AdminMuted,
                    outline = AdminStroke,
                ),
            ) {
                Surface(Modifier.fillMaxSize(), color = AdminBg) { AdminApp() }
            }
        }
    }
}

@Composable
private fun AdminApp() {
    val ctx = LocalContext.current
    var stage by remember { mutableStateOf("boot") }
    LaunchedEffect(Unit) {
        AdminSession.load(ctx)
        stage = when {
            AdminSession.token.isBlank() -> "login"
            !AdminSession.isStaff -> "denied"
            else -> "home"
        }
    }
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        when (stage) {
            "boot" -> LoadingPane("Memeriksa sesi…")
            "login" -> LoginScreen(onLogged = { stage = if (AdminSession.isStaff) "home" else "denied" })
            "denied" -> DeniedScreen(onBack = {
                AdminSession.clear(ctx)
                stage = "login"
            })
            else -> AdminShell(onLogout = {
                AdminSession.clear(ctx)
                stage = "login"
            })
        }
    }
}

/* ------------------------------------------------------------------ login */

@Composable
private fun LoginScreen(onLogged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun submit() {
        if (busy) return
        if (username.isBlank() || password.isBlank()) {
            error = "Isi username dan kata sandi."
            return
        }
        busy = true
        error = ""
        scope.launch {
            try {
                val result = AdminApi.login(username.trim(), password)
                AdminSession.save(ctx, result.s("token"), result.optJSONObject("user") ?: JSONObject())
                password = ""
                if (!AdminSession.isStaff) error = "Akun ini bukan owner/developer/admin." else onLogged()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Gagal masuk."
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = 26.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(AdminRed, Color(0xFF7B1E2B)))),
            contentAlignment = Alignment.Center,
        ) { Text("A", fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White) }
        Text("AUREVIA ADMIN", letterSpacing = 4.sp, fontSize = 11.sp, color = AdminRed, fontWeight = FontWeight.Bold)
        Text("Panel pemilik", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = AdminInk)
        Text(
            "Masuk dengan akun owner atau developer. Panel ini hanya mengelola akun, centang, dan pengumuman.",
            color = AdminMuted, fontSize = 13.sp,
        )
        OutlinedTextField(
            username, { username = it.take(24).trim() }, Modifier.fillMaxWidth(),
            label = { Text("Username") }, singleLine = true, enabled = !busy,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            password, { password = it }, Modifier.fillMaxWidth(),
            label = { Text("Kata sandi") }, singleLine = true, enabled = !busy,
            shape = RoundedCornerShape(16.dp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        )
        if (error.isNotBlank()) Text(error, color = AdminRed, fontSize = 13.sp)
        Button(
            onClick = { submit() }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AdminRed, contentColor = Color.White),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            else Text("Masuk", fontWeight = FontWeight.Bold)
        }
        Text(AdminApi_base_hint(), color = AdminMuted, fontSize = 11.sp)
    }
}

private fun AdminApi_base_hint(): String = "Server: $BASE_URL"

@Composable
private fun DeniedScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(60.dp))
        Text("Akses ditolak", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AdminInk)
        Text(
            "Akun @${AdminSession.username} tidak punya role owner/developer. Minta pemilik memberi role lewat menu Pengguna.",
            color = AdminMuted, fontSize = 13.sp,
        )
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = AdminRed, contentColor = Color.White)) {
            Text("Keluar")
        }
    }
}

/* ------------------------------------------------------------------ shell */

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun AdminShell(onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        Tab("Ringkasan", Icons.Filled.Home),
        Tab("Pengguna", Icons.Filled.Person),
        Tab("Siarkan", Icons.Filled.Send),
        Tab("Moderasi", Icons.Filled.Warning),
        Tab("Info", Icons.Filled.Info),
    )
    Scaffold(
        containerColor = AdminBg,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF101117), tonalElevation = 0.dp) {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.icon, item.label) },
                        label = { Text(item.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AdminRed,
                            selectedTextColor = AdminRed,
                            indicatorColor = AdminRed.copy(alpha = 0.12f),
                            unselectedIconColor = AdminMuted,
                            unselectedTextColor = AdminMuted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> OverviewScreen()
                1 -> UsersScreen()
                2 -> BroadcastScreen()
                3 -> ModerationScreen()
                else -> InfoScreen(onLogout = onLogout)
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String, onRefresh: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = AdminInk)
            Text(subtitle, fontSize = 12.sp, color = AdminMuted)
        }
        if (onRefresh != null) IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, "Muat ulang", tint = AdminMuted)
        }
    }
}

/* ------------------------------------------------------------------ overview */

@Composable
private fun OverviewScreen() {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var data by remember { mutableStateOf(JSONObject()) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick) {
        loading = true
        error = ""
        try {
            data = AdminApi.overview()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Gagal memuat ringkasan."
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Ringkasan", "Server Aurevia • @${AdminSession.username}", onRefresh = { tick++ })
        if (loading) {
            LoadingPane("Mengambil ringkasan…")
        } else {
            if (error.isNotBlank()) ErrorBanner(error)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Pengguna", data.optInt("users").toString(), Modifier.weight(1f))
                    StatTile("Staf", data.optInt("staff").toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Bersentang", data.optInt("checked").toString(), Modifier.weight(1f))
                    StatTile("Perangkat", data.optInt("devicesActive").toString() + "/" + data.optInt("devices"), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Room Live", data.optInt("rooms").toString(), Modifier.weight(1f))
                    StatTile("Pendengar", data.optInt("listeners").toString(), Modifier.weight(1f))
                }
                InfoCard(
                    "Push (FCM)",
                    if (data.s("push") == "configured") "Aktif — notifikasi HP siap dikirim."
                    else "Belum siap (${data.s("push").ifBlank { "unknown" }}). Cek kredensial server.",
                )
                InfoCard(
                    "Notifikasi Beranda",
                    "Setiap pengumuman dari tab Siarkan masuk ke lonceng Beranda semua pengguna, bukan cuma tray HP.",
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(AdminCardBg)
            .border(1.dp, AdminStroke, RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = AdminInk)
        Text(label, fontSize = 12.sp, color = AdminMuted)
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AdminCardBg)
            .border(1.dp, AdminStroke, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AdminInk)
        Text(body, fontSize = 12.sp, color = AdminMuted)
    }
}

/* ------------------------------------------------------------------ users */

@Composable
private fun UsersScreen() {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(query, tick) {
        loading = true
        error = ""
        if (query.isNotBlank()) delay(350)
        try {
            users = AdminApi.users(query.trim())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Gagal memuat pengguna."
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Pengguna", "Beri centang, warna badge, dan role.", onRefresh = { tick++ })
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Cari username…") },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = AdminMuted) },
            singleLine = true, shape = RoundedCornerShape(16.dp),
        )
        if (status.isNotBlank()) Text(
            status, color = AdminRed, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        if (error.isNotBlank()) ErrorBanner(error)
        if (loading) LoadingPane("Memuat akun…")
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(users) { user ->
                UserRow(user) { selected = user }
            }
        }
    }

    val target = selected
    if (target != null) {
        UserDialog(
            user = target,
            onDismiss = { selected = null },
            onSaved = { message ->
                status = message
                selected = null
                scope.launch { tick++ }
            },
            onError = { message -> status = message },
        )
    }
}

@Composable
private fun UserRow(user: JSONObject, onClick: () -> Unit) {
    val initial = user.s("username").take(1).uppercase().ifBlank { "?" }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF23252E)),
            contentAlignment = Alignment.Center,
        ) { Text(initial, color = AdminInk, fontWeight = FontWeight.Bold) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    user.s("username"), color = AdminInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (user.optBoolean("verified")) {
                    val badge = user.s("badge")
                    Box(
                        Modifier.size(14.dp).clip(CircleShape)
                            .background(if (badge.isBlank()) AdminRed else Color(badgeColor(badge))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(9.dp)) }
                }
                if (user.s("role") != "user") Pill(user.s("role"))
            }
            Text(user.s("name"), color = AdminMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (user.optBoolean("verified") && user.s("badge").isNotBlank()) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(badgeColor(user.s("badge")))))
        }
    }
}

@Composable
private fun Pill(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(AdminRed.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) { Text(text, color = AdminRed, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun UserDialog(
    user: JSONObject,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var role by remember { mutableStateOf(user.s("role").ifBlank { "user" }) }
    var verified by remember { mutableStateOf(user.optBoolean("verified")) }
    var badge by remember { mutableStateOf(user.s("badge")) }
    var busy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf("") }
    var confirmRole by remember { mutableStateOf(false) }
    // Batch 210: mute & ban langsung dari detail akun.
    var muted by remember { mutableStateOf(user.optBoolean("muted")) }
    var banned by remember { mutableStateOf(user.optBoolean("banned")) }
    var confirmBan by remember { mutableStateOf(false) }
    var banReason by remember { mutableStateOf(user.s("banReason")) }
    var blockIp by remember { mutableStateOf(true) }
    // Changing your own role from the panel is exactly how a developer locked themselves out;
    // the server refuses it now, and the UI must not even offer the option.
    val isSelf = user.s("username").equals(AdminSession.username, ignoreCase = true)
    // Developer is the highest role: an owner may not touch a developer account, and only a
    // developer may hand out the developer role. Mirrors the server policy exactly.
    val iAmDeveloper = AdminSession.role == "developer"
    val targetIsDeveloper = user.s("role") == "developer"
    val roleEditable = AdminSession.isOwnerLevel && !isSelf && (iAmDeveloper || !targetIsDeveloper)
    // Developer dilindungi: owner tidak boleh menindak akun developer, dan akun sendiri tidak
    // bisa dimoderasi (biar tidak terkunci sendiri). Aturan sama dengan server.
    val canModerate = AdminSession.isOwnerLevel && !isSelf && (iAmDeveloper || !targetIsDeveloper)

    fun save() {
        if (busy) return
        busy = true
        localError = ""
        scope.launch {
            try {
                val colour = if (verified) badge else ""
                AdminApi.setRole(
                    username = user.s("username"),
                    role = if (AdminSession.isOwnerLevel) role else null,
                    verified = verified,
                    badge = colour,
                )
                onSaved("Tersimpan untuk @${user.s("username")}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                localError = e.message ?: "Gagal menyimpan."
                onError(localError)
            } finally {
                busy = false
            }
        }
    }

    fun toggleMute() {
        if (busy) return
        busy = true
        localError = ""
        scope.launch {
            try {
                val r = AdminApi.mute(user.s("username"), !muted)
                muted = r.optBoolean("muted", !muted)
                onSaved(if (muted) "@${user.s("username")} dibisukan (tidak bisa Live/room)" else "@${user.s("username")} bisa Live lagi")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                localError = e.message ?: "Gagal mengubah mute."
            } finally {
                busy = false
            }
        }
    }

    fun applyBan(bannedNow: Boolean) {
        if (busy) return
        busy = true
        localError = ""
        scope.launch {
            try {
                val r = AdminApi.ban(user.s("username"), bannedNow, banReason.trim(), blockIp)
                banned = r.optBoolean("banned", bannedNow)
                val ip = r.s("blockedIp")
                onSaved(
                    when {
                        !banned -> "@${user.s("username")} dibuka blokirnya"
                        ip.isNotBlank() -> "@${user.s("username")} diblokir permanen + IP $ip diblokir"
                        else -> "@${user.s("username")} diblokir permanen"
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                localError = e.message ?: "Gagal menerapkan blokir."
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = AdminCardBg,
        titleContentColor = AdminInk,
        textContentColor = AdminMuted,
        title = { Text("@${user.s("username")}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                Modifier.height(420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Beri centang", fontSize = 13.sp, color = AdminInk, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = verified, onCheckedChange = { checked ->
                        verified = checked
                        if (checked && badge.isBlank()) badge = "blue"
                    })
                    Spacer(Modifier.width(10.dp))
                    Text(if (verified) "Akun bersentang" else "Tanpa centang", fontSize = 12.sp, color = AdminMuted)
                }
                Text("Warna badge", fontSize = 13.sp, color = AdminInk, fontWeight = FontWeight.SemiBold)
                BADGE_COLORS.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { colour ->
                            val active = verified && badge == colour
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(Color(badgeColor(colour)))
                                    .border(if (active) 3.dp else 1.dp, if (active) AdminInk else AdminStroke, CircleShape)
                                    .clickable { verified = true; badge = colour },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Role", fontSize = 13.sp, color = AdminInk, fontWeight = FontWeight.SemiBold)
                if (!AdminSession.isOwnerLevel) {
                    Text("Hanya owner yang bisa mengubah role.", fontSize = 11.sp, color = AdminMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("user", "owner", "developer").forEach { option ->
                        FilterChip(
                            selected = role == option,
                            enabled = roleEditable && !busy && (option != "developer" || iAmDeveloper),
                            onClick = { if (option != role) { role = option; confirmRole = true } },
                            label = { Text(option, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AdminRed.copy(alpha = 0.18f),
                                selectedLabelColor = AdminRed,
                                labelColor = AdminMuted,
                            ),
                        )
                    }
                }
                if (isSelf) Text(
                    "Ini akun kamu sendiri — role tidak bisa diubah dari panel supaya tidak terkunci lagi.",
                    color = AdminMuted, fontSize = 11.sp,
                )
                if (!isSelf && targetIsDeveloper && !iAmDeveloper) Text(
                    "Akun developer dilindungi: hanya developer lain yang bisa mengubah rolenya.",
                    color = AdminMuted, fontSize = 11.sp,
                )
                if (!isSelf && AdminSession.isOwnerLevel && !iAmDeveloper) Text(
                    "Hanya developer yang bisa mengangkat akun menjadi developer.",
                    color = AdminMuted, fontSize = 11.sp,
                )
                HorizontalDivider(color = AdminStroke, modifier = Modifier.padding(vertical = 6.dp))
                Text("Moderasi", fontSize = 13.sp, color = AdminInk, fontWeight = FontWeight.SemiBold)
                if (!canModerate) {
                    Text(
                        when {
                            isSelf -> "Akun kamu sendiri tidak bisa dimoderasi dari panel."
                            targetIsDeveloper && !iAmDeveloper -> "Akun developer dilindungi."
                            AdminSession.role == "admin" -> "Butuh role owner/developer untuk mute & blokir."
                            else -> "Tidak tersedia untuk akun ini."
                        },
                        fontSize = 11.sp, color = AdminMuted,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = muted,
                        enabled = canModerate && !busy,
                        onCheckedChange = { toggleMute() },
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Bisukan akun", fontSize = 12.sp, color = AdminInk, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tetap bisa masuk, tapi tidak bisa membuat Live/room.",
                            fontSize = 10.5.sp, color = AdminMuted,
                        )
                    }
                }
                if (banned) {
                    Text(
                        "Akun ini DIBLOKIR PERMANEN" + (user.s("banReason").ifBlank { "" }.let { if (it.isBlank()) "" else " · alasan: $it" }),
                        fontSize = 11.sp, color = AdminRed, fontWeight = FontWeight.SemiBold,
                    )
                }
                Button(
                    onClick = { if (banned) applyBan(false) else confirmBan = true },
                    enabled = canModerate && !busy,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (banned) AdminStroke else AdminRed.copy(alpha = 0.16f),
                        contentColor = if (banned) AdminInk else AdminRed,
                    ),
                ) {
                    Text(if (banned) "Lepas blokir permanen" else "Blokir permanen + IP", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
                if (localError.isNotBlank()) Text(localError, color = AdminRed, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { save() }, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AdminRed)
                else Text("Simpan", color = AdminRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Tutup", color = AdminMuted) } },
    )

    if (confirmBan) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmBan = false },
            containerColor = AdminCardBg,
            titleContentColor = AdminInk,
            textContentColor = AdminMuted,
            title = { Text("Blokir @${user.s("username")} permanen?", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Semua sesi akun ini langsung diputus dan akun tidak bisa masuk lagi sampai diblokir dibuka.",
                        fontSize = 12.5.sp,
                    )
                    OutlinedTextField(
                        banReason, { banReason = it.take(160) }, Modifier.fillMaxWidth(),
                        label = { Text("Alasan (opsional)") }, singleLine = true,
                        shape = RoundedCornerShape(14.dp), enabled = !busy,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = blockIp, onCheckedChange = { blockIp = it }, enabled = !busy)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Blokir IP terakhir", fontSize = 12.sp, color = AdminInk, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Mencegah akun baru dibuat dari jaringan yang sama. Hati-hati kalau akun memakai WiFi publik.",
                                fontSize = 10.5.sp, color = AdminMuted,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmBan = false; applyBan(true) }, enabled = !busy) {
                    Text("Blokir sekarang", color = AdminRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBan = false }, enabled = !busy) { Text("Batal", color = AdminMuted) }
            },
        )
    }

    if (confirmRole) {
        val current = user.s("role").ifBlank { "user" }
        AlertDialog(
            onDismissRequest = { confirmRole = false; role = current },
            containerColor = AdminCardBg,
            titleContentColor = AdminInk,
            textContentColor = AdminMuted,
            title = { Text("Ubah role akun?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "@${user.s("username")} akan menjadi \"$role\" (sekarang \"$current\"). " +
                        "Akun dengan role \"user\" tidak bisa membuka panel ini.",
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmRole = false; save() }) {
                    Text("Ya, ubah", color = AdminRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRole = false; role = current }) { Text("Batal", color = AdminMuted) }
            },
        )
    }
}

/* ------------------------------------------------------------------ broadcast */

@Composable
private fun BroadcastScreen() {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("Aurevia") }
    var message by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf("all") }
    var username by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    fun send() {
        if (busy) return
        if (message.isBlank()) {
            error = "Isi pesan dulu."
            return
        }
        if (audience == "user" && username.isBlank()) {
            error = "Tulis username tujuan."
            return
        }
        busy = true
        error = ""
        result = ""
        scope.launch {
            try {
                val response = AdminApi.notify(
                    title = title.ifBlank { "Aurevia" },
                    message = message.trim(),
                    audience = audience,
                    username = username.trim().ifBlank { null },
                )
                val delivered = response.optInt("delivered")
                val feed = response.optInt("feed")
                val targets = response.optInt("targets")
                result = "Terkirim ke $targets akun • $delivered notifikasi HP • $feed entri Beranda"
                message = ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Gagal mengirim."
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        ScreenHeader("Siarkan", "Muncul di notifikasi HP dan lonceng Beranda.")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                title, { title = it.take(80) }, Modifier.fillMaxWidth(),
                label = { Text("Judul") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                enabled = !busy,
            )
            OutlinedTextField(
                message, { message = it.take(300) }, Modifier.fillMaxWidth().height(150.dp),
                label = { Text("Pesan") }, shape = RoundedCornerShape(16.dp), enabled = !busy,
            )
            Text("Penerima", fontSize = 13.sp, color = AdminInk, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "Semua", "verified" to "Bersentang", "staff" to "Staf", "user" to "Satu akun").forEach { (key, label) ->
                    FilterChip(
                        selected = audience == key,
                        onClick = { audience = key },
                        enabled = !busy,
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AdminRed.copy(alpha = 0.18f),
                            selectedLabelColor = AdminRed,
                            labelColor = AdminMuted,
                        ),
                    )
                }
            }
            if (audience == "user") {
                OutlinedTextField(
                    username, { username = it.trim() }, Modifier.fillMaxWidth(),
                    label = { Text("Username tujuan") }, singleLine = true,
                    shape = RoundedCornerShape(16.dp), enabled = !busy,
                )
            }
            if (error.isNotBlank()) ErrorBanner(error)
            if (result.isNotBlank()) InfoCard("Terkirim", result)
            Button(
                onClick = { send() }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AdminRed, contentColor = Color.White),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Kirim pengumuman", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/* ------------------------------------------------------------------ moderasi */

/**
 * Moderasi (batch 210): pesan singkat yang muncul SAAT APLIKASI DIBUKA — sengaja dipisah dari
 * "Siarkan" supaya tidak tertukar dengan notifikasi dan dengan toast donasi login/daftar —
 * plus daftar akun yang diblokir permanen, IP yang diblokir, dan akun yang dibisukan.
 */
@Composable
private fun ModerationScreen() {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(false) }
    var savedVersion by remember { mutableStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    var bannedUsers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var bannedIps by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var mutedUsers by remember { mutableStateOf<List<String>>(emptyList()) }
    var reloadTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadTick) {
        try {
            val data = AdminApi.bannedList()
            bannedUsers = data.optJSONArray("users")?.list().orEmpty()
            bannedIps = data.optJSONArray("ips")?.list().orEmpty()
            mutedUsers = data.optJSONArray("muted")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } }.orEmpty()
            error = ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Gagal memuat daftar moderasi."
        }
    }

    LaunchedEffect(Unit) {
        // Isi dulu dengan teks toast yang sedang aktif di server.
        runCatching { AdminApi.appToast() }.getOrNull()?.let {
            text = it.s("text")
            enabled = it.optBoolean("enabled")
            savedVersion = it.optLong("version")
        }
    }

    fun saveToast() {
        if (busy) return
        busy = true
        error = ""
        result = ""
        scope.launch {
            try {
                val r = AdminApi.setAppToast(text.trim(), enabled && text.isNotBlank())
                savedVersion = r.optLong("version")
                enabled = r.optBoolean("enabled")
                result = if (enabled) "Toast aktif — akan muncul sekali per perangkat saat aplikasi dibuka."
                else "Toast dimatikan."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Gagal menyimpan toast."
            } finally {
                busy = false
            }
        }
    }

    fun unban(username: String) {
        scope.launch {
            try {
                AdminApi.ban(username, false)
                result = "@$username dibuka blokirnya."
                reloadTick++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Gagal membuka blokir."
            }
        }
    }

    fun unmute(username: String) {
        scope.launch {
            try {
                AdminApi.mute(username, false)
                result = "@$username bisa Live lagi."
                reloadTick++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Gagal mengubah mute."
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        ScreenHeader("Moderasi", "Toast saat buka aplikasi, blokir permanen, dan mute.", onRefresh = { reloadTick++ })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Text("Toast saat aplikasi dibuka", fontSize = 14.sp, color = AdminInk, fontWeight = FontWeight.Bold)
            Text(
                "Muncul sekali di bawah layar setiap kali pengguna membuka aplikasi (teks baru = tampil lagi). " +
                    "Ini bukan notifikasi HP dan bukan toast donasi setelah login.",
                fontSize = 11.sp, color = AdminMuted,
            )
            OutlinedTextField(
                text, { text = it.take(300) }, Modifier.fillMaxWidth().height(110.dp),
                label = { Text("Teks toast") },
                shape = RoundedCornerShape(16.dp), enabled = !busy && AdminSession.isOwnerLevel,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    enabled = !busy && AdminSession.isOwnerLevel,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (enabled) "Aktif" else "Mati", fontSize = 12.5.sp, color = AdminInk, fontWeight = FontWeight.SemiBold)
                    if (savedVersion > 0) {
                        Text("Versi terakhir: $savedVersion", fontSize = 10.5.sp, color = AdminMuted)
                    }
                }
            }
            Button(
                onClick = { saveToast() },
                enabled = !busy && AdminSession.isOwnerLevel,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AdminRed, contentColor = Color.White),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Simpan toast", fontWeight = FontWeight.Bold)
            }
            if (error.isNotBlank()) ErrorBanner(error)
            if (result.isNotBlank()) InfoCard("Berhasil", result)

            HorizontalDivider(color = AdminStroke, modifier = Modifier.padding(vertical = 6.dp))

            Text("Diblokir permanen (${bannedUsers.size})", fontSize = 14.sp, color = AdminInk, fontWeight = FontWeight.Bold)
            if (bannedUsers.isEmpty()) {
                Text("Belum ada akun yang diblokir.", fontSize = 11.5.sp, color = AdminMuted)
            } else {
                bannedUsers.forEach { u ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AdminCardBg)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("@${u.s("username")}", fontSize = 13.sp, color = AdminInk, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(
                                    u.s("reason").ifBlank { null }?.let { "alasan: $it" },
                                    u.s("lastIp").ifBlank { null }?.let { "IP: $it" },
                                ).joinToString(" · ").ifBlank { "tanpa keterangan" },
                                fontSize = 10.5.sp, color = AdminMuted,
                            )
                        }
                        TextButton(onClick = { unban(u.s("username")) }, enabled = AdminSession.isOwnerLevel) {
                            Text("Buka blokir", color = AdminRed, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Text("IP diblokir (${bannedIps.size})", fontSize = 14.sp, color = AdminInk, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            if (bannedIps.isEmpty()) {
                Text("Belum ada IP yang diblokir.", fontSize = 11.5.sp, color = AdminMuted)
            } else {
                bannedIps.forEach { ip ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AdminCardBg)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ip.s("ip"), fontSize = 12.5.sp, color = AdminInk, fontWeight = FontWeight.SemiBold)
                            Text("@${ip.s("username")} · oleh ${ip.s("by")}", fontSize = 10.5.sp, color = AdminMuted)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Text("Dibisukan (${mutedUsers.size})", fontSize = 14.sp, color = AdminInk, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            if (mutedUsers.isEmpty()) {
                Text("Tidak ada akun yang dibisukan.", fontSize = 11.5.sp, color = AdminMuted)
            } else {
                mutedUsers.forEach { username ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AdminCardBg)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("@$username", Modifier.weight(1f), fontSize = 12.5.sp, color = AdminInk, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { unmute(username) }, enabled = AdminSession.isOwnerLevel) {
                            Text("Bunyikan", color = AdminRed, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ------------------------------------------------------------------ info */

@Composable
private fun InfoScreen(onLogout: () -> Unit) {
    var rooms by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            rooms = AdminApi.rooms().size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: ""
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Info", "Akun & server yang dipakai.")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard(
                "Masuk sebagai",
                "@${AdminSession.username} • ${AdminSession.name}\nRole: ${AdminSession.role}",
            )
            InfoCard("Server", BASE_URL)
            InfoCard("Room Live", rooms.toString())
            InfoCard(
                "Aplikasi",
                "Aurevia Admin 2.0.2 (Kotlin/Compose)\nPanel ini terpisah dari aplikasi musik.",
            )
            if (error.isNotBlank()) Text(error, color = AdminMuted, fontSize = 11.sp)
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF23252E), contentColor = AdminInk),
            ) {
                Icon(Icons.Filled.ExitToApp, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Keluar")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ------------------------------------------------------------------ bits */

@Composable
private fun LoadingPane(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp, color = AdminRed)
        Text(text, color = AdminMuted, fontSize = 12.sp)
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp)).background(AdminRed.copy(alpha = 0.12f))
            .padding(12.dp),
    ) { Text(message, color = AdminRed, fontSize = 12.sp) }
}

/** Badge palette accepted by POST /v1/admin/role (same colours as the music app). */
private val BADGE_COLORS = listOf(
    "blue", "green", "pink", "purple", "gold", "yellow", "amber", "red", "white", "silver",
)

private fun badgeColor(name: String): Long = when (name) {
    "blue" -> 0xFF3B82F6
    "green" -> 0xFF22C55E
    "pink" -> 0xFFEC4899
    "purple" -> 0xFF8B5CF6
    "gold" -> 0xFFEAB308
    "yellow" -> 0xFFFACC15
    "amber" -> 0xFFF59E0B
    "red" -> 0xFFEF4444
    "white" -> 0xFFE5E7EB
    "silver" -> 0xFFCBD5E1
    else -> 0xFF64748B
}
