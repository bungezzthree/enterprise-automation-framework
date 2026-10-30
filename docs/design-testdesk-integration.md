# Rancangan: mengganti TestLink dengan TestDesk

Status: **usulan, belum ada kode yang diubah.** Disusun 2026-10-30. Semua asumsi API TestDesk di bawah sudah diuji pada instance sementara; asumsi lain ditandai.

## 1. Tujuan dan batasan

Framework memakai TestDesk (`C:\Users\solui\IdeaProjects\test-management-tools`) sebagai sistem pencatat test case, hasil eksekusi, dan **test evidence** untuk SIT/UAT/BAST, menggantikan TestLink.

| Termasuk | Tidak termasuk (fase ini) |
|---|---|
| Kirim spesifikasi (langkah Gherkin) dan hasil ke TestDesk | Mengubah kode TestDesk (hanya diusulkan di bagian 9) |
| Upload evidence (screenshot, request/response API) | Menarik katalog dari TestDesk (butuh endpoint baru) |
| Menghapus kode TestLink | Menghapus dashboard EAF (tetap dipakai untuk tren dan coverage) |
| Update Jenkinsfile, README, konfigurasi | Pilot Mobile di device (tidak berubah) |

## 2. Keputusan desain

| # | Keputusan | Alasan |
|---|---|---|
| D1 | **`@tc:<ID>` = `case_key` TestDesk.** Prefix project TestDesk = `API`, `WEB`, `MOB`. | Diverifikasi: impor CSV mempertahankan ID kustom, jadi satu ID di seluruh feature, katalog, dashboard, dan TestDesk. |
| D2 | **Kirim di akhir run (`TestRunFinished`)**, bukan per scenario. | Satu sinkron, satu pengiriman hasil, satu plan. Aman untuk eksekusi paralel (Web memakai 3 thread). Hasil per scenario tetap tersimpan langsung oleh `DbResultPublisher`. |
| D3 | **Test case yang belum ada dibuat lewat sinkron spesifikasi, bukan lewat pengiriman hasil** (`testdesk.auto.create=true`, diubah 2026-10-30 atas permintaan pengguna; semula mati). | Sinkron membawa langkah Gherkin dan suite per feature. Konsekuensi: TestDesk memberi ID sendiri (`API-1`) kecuali TestDesk dipatch, lihat bagian 12. |
| D4 | **Kirim `automation_ref` dan `case_key` sama-sama berisi `@tc`.** | Sinkron pertama menghubungkan (`linked`) test case hasil impor; setelah itu cocok lewat `automation_ref`. |
| D5 | **Evidence diunggah memakai `case_key`** (bukan `automation_ref`). | Percobaan dengan `automation_ref` gagal 404 untuk test case yang baru terhubung lewat `case_key`. |
| D6 | **Mode evidence: `none`, `failed` (default), `all`.** | UAT biasanya butuh bukti walau test lulus, jadi `all` tersedia untuk plan UAT. |
| D7 | **Kegagalan TestDesk tidak pernah menggagalkan test run.** | Sama seperti `DbResultPublisher`: hanya WARN di log. Payload disimpan di `target/testdesk/` agar bisa dikirim ulang. |
| D8 | **Default `testdesk.enabled=false`.** | Run lokal tidak mengotori TestDesk. Diaktifkan lewat Jenkins atau `-Dtestdesk.enabled=true`. |
| D9 | **Token hanya dari variabel lingkungan `TESTDESK_TOKEN`.** | `Config` sudah memetakan kunci `testdesk.token` ke `TESTDESK_TOKEN`; tidak pernah ditulis di file properties. |

## 3. Alur end to end

```
Persiapan satu kali per project
  catalog.csv --(tools/catalog-to-testdesk.mjs)--> import-<project>.csv --(TestDesk: Import)--> test case ber-ID kita

Setiap run (testdesk.enabled=true)
  Cucumber jalan
    |- tiap scenario: status, durasi, error, langkah Gherkin, lampiran (screenshot / API exchange)
    '- TestRunFinished:
         1. PUT  /api/projects/{prefix}/automation/cases     (spesifikasi: judul, tag, prioritas, langkah)
         2. POST /api/projects/{prefix}/automation/results   (status, durasi, pesan, output)  -> plan_id
         3. POST /api/plans/{plan_id}/automation/evidence    (per test case, multipart)
         4. ringkasan ke log: "TestDesk plan #7: 10 recorded, 0 unmatched" + tautan /#/plans/7
```

## 4. Pemetaan data

| Sumber (Cucumber) | TestDesk | Catatan |
|---|---|---|
| `@tc:API-011` | `case_key` dan `automation_ref` | D1, D4 |
| Nama scenario | `title` | Pada sinkron, judul menjadi milik kode untuk test case otomatis |
| `@priority:high/medium/low` | `priority` High/Medium/Low | `@priority:critical` juga didukung |
| `@regression @sanity @newfeature` + tag feature | `tags` (dipisah koma) | Tag teknis (`@tc`, `@priority`) tidak ikut |
| `@req:FSD-TRF-03` (konvensi baru, opsional) | `requirement_ref` | Untuk keterlacakan ke BRD/FSD |
| `@defect:JIRA-9` (konvensi baru, opsional) | `defect_ref` pada hasil | |
| Langkah Given/When/And | `steps[].action` | Hanya `PickleStepTestStep`, hook diabaikan |
| Langkah Then (dan And/But sesudahnya) | `steps[].expected_result` pada langkah aksi terakhir | |
| PASSED | `passed` | |
| FAILED, AMBIGUOUS | `failed` | pesan error menjadi *actual result* |
| UNDEFINED, PENDING | `blocked` | Keputusan terbuka K1 |
| SKIPPED | `skipped` | |
| Durasi | `duration_ms` | |
| Stack trace + `WriteEvent` | `output` | Dipotong maks 20.000 karakter |
| `scenario.attach(...)` (`EmbedEvent`) | file evidence | Nama file dari nama lampiran + ekstensi dari media type |

Contoh payload sinkron untuk API-011 (struktur diverifikasi pada instance uji):

```json
{ "suite_root": "", "cases": [ {
    "automation_ref": "API-011", "case_key": "API-011",
    "title": "Paginate posts with _page and _limit",
    "priority": "Low", "tags": "newfeature,posts,api",
    "steps": [
      { "action": "Given the query parameter \"_page\" is \"2\"" },
      { "action": "When I send a GET request to \"/posts\"",
        "expected_result": "the response status code should be 200; the response should be a list of 5 items" } ] } ] }
```

## 5. Komponen dan perubahan file

**Baru**

| File | Isi |
|---|---|
| `automation-core/.../testdesk/TestDeskResultListener.java` | Plugin Cucumber: mengumpulkan data, mengirim di akhir run |
| `.../testdesk/TestDeskClient.java` | HTTP (`java.net.http`), multipart, retry satu kali pada 5xx/koneksi, timeout 10 dtk sambung / 60 dtk baca |
| `.../testdesk/TestDeskPayloads.java` | Pemetaan data ke JSON (kelas murni, mudah diuji) |
| `.../testdesk/ScenarioRecord.java` | Rekaman per scenario: kunci, tag, langkah, status, lampiran (disimpan ke file sementara) |
| `automation-core/src/test/java/.../testdesk/*Test.java` | Uji unit (lihat bagian 8) |
| `tools/catalog-to-testdesk.mjs` | Node tanpa dependensi: `catalog.csv` menjadi CSV impor TestDesk (kolom `Test Case ID`, `Test Case Title`, `Priority`, `Test Type`, `Execution Type`, `Tags`) |

**Diubah**

| File | Perubahan |
|---|---|
| `automation-core/pom.xml` | Tambah `gson` eksplisit (sudah tersedia lewat Playwright, versi 2.11.0), `junit-jupiter` (test), `surefire includes` agar uji di core ikut jalan (pom induk hanya menjalankan `RunCucumberTest`) |
| `CommonApiSteps` dan `ApiContext` | Catat ringkasan request; pada `evidence.mode=all` selalu lampirkan "request + response" (tanpa header Authorization, dengan masking field `password`/`token`) |
| `WebHooks` | Screenshot akhir juga saat `evidence.mode=all` |
| `api-tests` dan `web-tests`: `junit-platform.properties` | Plugin `TestLinkResultListener` diganti `TestDeskResultListener` |
| `api-tests` dan `web-tests`: `config/default.properties` | Blok `testlink.*` diganti `testdesk.*` |
| `catalog.csv` (api, web) | Kolom `testlink_id` dihapus (parser sudah toleran: `TestCaseCatalog.col()` mengembalikan kosong bila kolom tidak ada) |
| `TestCaseInfo`, `ResultRepository` | Hilangkan parameter `testlinkId`. Kolom DB `testlink_id` dibiarkan (deprecated) agar tidak perlu migrasi |
| `dashboard/public/*`, `server.js` | Hapus tampilan `testlink_id`; teks "test cases in TestLink" menjadi "in the catalog" |
| `Jenkinsfile` | Parameter `TESTLINK` diganti `TESTDESK` (boolean), `TESTDESK_ENV` (SIT/UAT), `TESTDESK_PLAN` (opsional); kredensial `TESTDESK_TOKEN`; `TESTDESK_URL` dari environment Jenkins |
| `README.md` | Bagian TestLink diganti bagian TestDesk |

**Dihapus**: `automation-core/.../testlink/TestLinkClient.java`, `TestLinkResultListener.java`.

## 6. Konfigurasi

| Kunci | Default | Keterangan |
|---|---|---|
| `testdesk.enabled` | `false` | Saklar utama |
| `testdesk.url` | - | Contoh `http://localhost:3100` |
| `testdesk.token` | env `TESTDESK_TOKEN` | Jangan tulis di file |
| `testdesk.project` | - | Prefix project TestDesk (`API`, `WEB`, `MOB`) |
| `testdesk.environment` | `SIT` | `SIT` atau `UAT` (dipakai saat plan dibuat) |
| `testdesk.plan.name` | `EAF <project.key> <env>` | Nama plan bergulir; bisa diisi per build (`Release 2.4 #123`) |
| `testdesk.run.id` | `BUILD_TAG`, atau `local-<user>-<waktu>` | Kunci idempotensi: kirim ulang tidak menggandakan |
| `testdesk.build` / `testdesk.ci.url` | `BUILD_NUMBER` / `BUILD_URL` | Muncul di tiap eksekusi |
| `testdesk.suite.root` | *(kosong)* | Folder opsional di atas suite modul. Kosong: test case langsung masuk suite modul (nama feature); jenis otomatis/manual sudah ada di Execution Type |
| `testdesk.auto.create` | `true` | Test case yang belum ada di TestDesk dibuat otomatis; `false` hanya melaporkannya. Perlu `testdesk.sync.spec=true` (bila sinkron mati, pembuatan dilakukan oleh pengiriman hasil, tanpa langkah) |
| `testdesk.sync.spec` | `true` | `false` bila judul dan langkah test case dikelola manual di TestDesk |
| `testdesk.dry.run` | `false` | Meneruskan `dry_run` ke API: lihat hasil tanpa menyimpan |
| `evidence.mode` | `failed` | `none`, `failed`, `all` |
| `testdesk.evidence.max.mb` | `20` | File lebih besar dilewati dengan WARN (batas TestDesk 25 MB) |

## 7. Penanganan kegagalan

| Situasi | Perilaku |
|---|---|
| TestDesk mati, token salah/kedaluwarsa (401) | WARN, test run tetap lulus/gagal sesuai hasil test, payload disimpan di `target/testdesk/` |
| `case_key` belum ada di TestDesk | Item masuk `errors` pada sinkron, dilaporkan di log dengan petunjuk "jalankan impor katalog dulu" |
| Plan sudah ditutup (409) | WARN dengan saran memakai nama plan baru (`testdesk.plan.name`) |
| Scenario tanpa `@tc` | Dilewati dengan WARN (sama dengan perilaku `DbResultPublisher`) |
| Hasil dikirim ulang dengan `run_id` sama | Tidak dobel (diverifikasi: `duplicates: 1`) |
| Evidence gagal diunggah | WARN per test case, hasil sudah tercatat |

Pengiriman ulang manual dari payload tersimpan cukup dengan `curl` (contoh dicantumkan di README).

## 8. Strategi pengujian dan kriteria penerimaan

**Uji unit** (JUnit 5, tanpa jaringan):
- pemetaan status, prioritas, tag, dan langkah (termasuk Then yang berurutan);
- pembentukan JSON dan pemotongan `output`;
- `TestDeskClient` terhadap server tiruan `com.sun.net.httpserver.HttpServer` (header Bearer, multipart, retry, 401/409).

**Uji end to end** terhadap instance TestDesk sementara (data di folder scratch, port 3101), memakai skrip PoC yang sama dengan verifikasi hari ini.

| # | Kriteria penerimaan |
|---|---|
| A1 | `api-tests` dengan `testdesk.enabled=true` menghasilkan satu plan berisi 10 eksekusi dengan status benar |
| A2 | Sinkron membuat test case bertipe *Automated* dengan langkah dari Gherkin; run kedua melaporkan `unchanged` |
| A3 | Scenario yang sengaja dirusak tercatat *Failed*, pesan error menjadi actual result, evidence terlampir |
| A4 | `evidence.mode=all`: test yang lulus juga punya evidence |
| A5 | TestDesk dimatikan atau token salah: hasil test tidak berubah, ada WARN, payload tersimpan |
| A6 | Run ulang dengan `run_id` sama tidak menggandakan hasil |
| A7 | `mvnw clean install` hijau dengan `testdesk.enabled=false` (default) dan uji unit lulus |
| A8 | Web diuji pada eksekusi paralel (3 thread) tanpa hasil atau lampiran tertukar |

## 9. Fase kerja

| Fase | Isi | Catatan |
|---|---|---|
| **1. Inti** | `testdesk/*`, mode evidence, konfigurasi, tukar plugin, hapus kode TestLink, uji unit, E2E | Hasil: `-Dtestdesk.enabled=true` berfungsi |
| **2. Perkakas dan CI** | `catalog-to-testdesk.mjs`, `Jenkinsfile`, README, panduan bootstrap | Diperlukan untuk pemakaian pertama |
| **3. Bersih-bersih** | Dashboard, `TestCaseInfo`/`ResultRepository`, `catalog.csv`, kedua deck | Deck demo: sumber generator-nya hilang dari folder scratch, jadi perlu dibangun ulang atau diedit manual di PowerPoint |
| **4. Opsional di TestDesk** | `GET /api/projects/{prefix}/automation/cases` (hanya baca, untuk token); framework menarik katalog dari sana; tautan "buka di TestDesk" per run di dashboard | Mengubah repo TestDesk, perlu persetujuan terpisah |

Peluang di luar rencana: generator AI TestDesk (Claude atau model open-source) sudah membaca file `.feature`, sehingga bisa menjadi dasar fitur konversi test case ke Gherkin.

## 10. Risiko dan yang belum diketahui

| Risiko | Mitigasi |
|---|---|
| Sinkron menimpa judul/langkah yang diedit manual di web TestDesk | Itu desain TestDesk untuk test case otomatis; `testdesk.sync.spec=false` untuk mematikan |
| Executor di TestDesk tampil sebagai pemilik token | Buat user khusus `ci-bot` bertipe tester |
| Hasil otomatis pada plan UAT tetap butuh persetujuan klien | Sesuai desain TestDesk, bukan masalah |
| Instance TestDesk produksi belum pernah dicoba | Semua uji memakai instance sementara; satu uji awal di instance sungguhan sebelum dipakai Jenkins |
| `EmbedEvent` pada eksekusi paralel | Kelas dan metodenya terverifikasi (Cucumber 7.20.1); perilaku paralel diuji di A8 |
| Lampiran besar memakai memori | Data disimpan ke file sementara saat diterima, dihapus setelah dikirim |
| Port 3000 bentrok antara TestDesk dan dashboard | Jalankan TestDesk di port lain (mis. 3100) |

## 11. Keputusan terbuka

| # | Pertanyaan | Default yang saya pakai |
|---|---|---|
| K1 | Status UNDEFINED/PENDING di TestDesk | `blocked` (butuh perhatian), bukan `skipped` |
| K2 | Mode evidence bawaan | `failed`; Jenkins job UAT memakai `all` |
| K3 | Nama plan | Bergulir per project dan environment; untuk rilis diisi manual |
| K4 | Pemetaan environment | `local` dan `staging` jadi `SIT`; `UAT` hanya bila diatur eksplisit |
| K5 | Kode TestLink | Dihapus (riwayat git menyimpannya) |

## 12. Pembaruan: test case dibuat otomatis bila belum ada

Perilaku yang diminta untuk demo pilot (20 test case): **belum ada di TestDesk → masuk Test Specification; sudah ada → hanya hasil eksekusi pada test plan yang diperbarui.**

Cara kerja (`TestDeskReporter`):
1. Putaran 1: sinkron dengan `case_key` = `@tc`. Yang sudah ada **dihubungkan** (tidak diduplikasi, tidak dipindah suite, ID asli dipertahankan); yang sudah terhubung menjadi `unchanged`/`updated`.
2. Putaran 2 (hanya yang dijawab TestDesk "not found in this project"): dibuat **tanpa** `case_key`, dengan langkah Gherkin dan suite bernama feature/modul (`Posts read`, tanpa folder pembungkus).
3. Hasil dicatat pada plan; evidence diunggah (`automation_ref`, cadangan `case_key`).

Diverifikasi terhadap TestDesk asli (instance sementara): project kosong → 10 API + 10 Web dibuat dan hasilnya tercatat; run kedua → 0 baru, eksekusi bertambah pada plan yang sama; kasus campuran (4 sudah ada, 6 belum) → 4 terhubung, 6 dibuat, tanpa duplikat.

**Catatan ID.** TestDesk yang ada memberi nomor sendiri pada test case baru (`API-1`, tertaut ke `automation_ref` `API-001`). Agar ID identik, `docs/testdesk-keep-case-key.patch` (7 baris di `src/automation/service.js` TestDesk) membuat TestDesk memakai `case_key` yang diminta saat membuat. Diuji pada **salinan** TestDesk: hasilnya `API-001` … `API-010`. Dengan patch itu test case baru berada langsung di suite `Automation` (bukan suite modul), karena suite hanya dikirim pada putaran pembuatan; ini perlu disempurnakan bila patch dipakai. Patch belum diterapkan ke repo TestDesk.
