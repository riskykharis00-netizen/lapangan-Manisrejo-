# Lapangan Bulutangkis Manisrejo

Website pemesanan serta pengelolaan jadwal sewa **Lapangan Bulutangkis Manisrejo** (Senin – Minggu, pukul **08:00 – 00:00**, **Lapangan A & Lapangan B**, tarif **Rp 15.000/jam**).

Codingan website dipecah rapi **per bagian file** dan dipisahkan antara halaman **Orang Pesan (Penyewa)** dan **Editor Website (Pengelola)** agar langsung terbuka saat link GitHub Pages dibuka tanpa perlu login Google.

---

## Struktur File Website (Siap GitHub Pages)

```text
├── index.html          # [Bagian 1] Halaman HTML khusus Orang Pesan (Penyewa)
├── editor.html         # [Bagian 2] Halaman HTML khusus Editor Website (PIN default: 00000)
├── _config.yml         # Pengaturan Jekyll agar hanya file website yang dipublikasikan
├── css/
│   └── styles.css      # [Bagian 3] Tampilan & layout website (Emerald & Lime)
└── js/
    ├── storage.js      # [Bagian 4] Penyimpanan data, sinkron otomatis antar-tab & validasi bentrok
    ├── renter.js       # [Bagian 5] Logika jadwal, pesan 1/2 lapangan & riwayat penyewa
    └── editor.js       # [Bagian 6] Logika PIN (00000), tagihan Lunas/DP, pengingat WA & editor lapangan
```

---

## Tutorial Upload ke GitHub & Aktifkan GitHub Pages

1. Upload file `index.html`, `editor.html`, `_config.yml`, folder `css/`, dan folder `js/` ke repository GitHub Anda (`main`).
2. Buka **Settings → Pages** di repository GitHub:
   - Pilih **GitHub Actions** (dengan `.github/workflows/jekyll-gh-pages.yml`) atau **Deploy from a branch (`main` / `root`)**.
3. Setelah deploy selesai, link langsung terbuka di website tanpa login Google:
   - **Link Orang Pesan (Penyewa)**: `https://USERNAME_ANDA.github.io/lapangan-bulutangkis-manisrejo/`
   - **Link Editor Website (Pengelola)**: `https://USERNAME_ANDA.github.io/lapangan-bulutangkis-manisrejo/editor.html` (PIN bawaan: `00000`)

---

## Kontak & Lokasi

- **WhatsApp Admin**: `082142272367`
- **Jam Operasional**: Senin – Minggu, `08:00 – 00:00`
- **Tarif**: Lapangan `Rp 15.000/jam`, Sewa Raket `Rp 10.000`, Beli Cock `Rp 12.000`
- **Google Maps**: [Lokasi Lapangan Bulutangkis Manisrejo](https://maps.app.goo.gl/xEdtA2gNiUx9pz9f9)
