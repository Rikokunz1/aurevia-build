# Setup build APK Aurevia di GitHub Actions

Repo: **`Rikokunz1/aurevia-build`** (private, sudah dibuat).
Secret keystore **sudah terpasang** (Contents secret PUT balas `201`):
`KEYSTORE_B64`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Yang masih ngeblokir

Token GitHub yang dikirim cuma punya izin **Contents: Read**. Bukti dari response header GitHub:

```
x-accepted-github-permissions: contents=read
```

Akibatnya: bikin repo & pasang secret **bisa**, tapi nulis file (workflow/README) dan
push `git` ditolak `403 Resource not accessible by personal access token`.

### Perbaikan (fine-grained token)
https://github.com/settings/personal-access-tokens → pilih token → **Edit**:

| Permission | Set ke |
|---|---|
| Repository access | **All repositories** (atau + `aurevia-build`) |
| Contents | **Read and write** ← ini yang kurang |
| Workflows | Read and write |
| Actions | Read and write |
| Secrets | Read and write (sudah cukup) |

Lalu **Update token** (bukan cuma Save). Kalau tetap ditolak, klik **Regenerate token**
dan kirim token barunya.

### Alternatif: token classic
`github.com/settings/tokens` → *Generate new token (classic)* → centang
`repo` + `workflow`. Selesai.

---

## Kalau tidak mau ubah token — paste manual (2 menit, tanpa CLI)

1. Buka `github.com/Rikokunz1/aurevia-build`
2. **Add file → Create new file**
3. Nama file: `.github/workflows/build-apk.yml`
4. Copy-paste seluruh isi **`build-apk-standalone.yml`** (ada di folder ini)
5. **Commit changes**
6. Tab **Actions** → **Build Aurevia APK** → **Run workflow**
7. ±15–30 menit → download artifact **aurevia-apk**

`build-apk-standalone.yml` **tidak butuh release asset**: source tree di-download
otomatis dari `https://aurevia.rixxstore.my.id/v1/app/fork/source`
(sudah dites: `200`, 5.202.103 byte, berisi `app/build.gradle.kts`).

## Dua file workflow

| File | Sumber kode | Kapan dipakai |
|---|---|---|
| `build-apk.yml` | release `src-latest` di repo | kalau push pakai token sudah bisa |
| `build-apk-standalone.yml` | URL server OTA (default) / input `source_url` | kalau mau paste manual |

## Isi kedua workflow
1. JDK 21 + Android SDK
2. `compileFossReleaseKotlin` (phase 1) → `assembleFossRelease` (phase 2)
3. `zipalign -p 4` + `apksigner` **v1 + v2 + v3** (fix "Paket tampaknya tidak valid")
4. `apksigner verify --print-certs` + `sha256sum`
5. Upload artifact `aurevia-apk` (retensi 30 hari)
