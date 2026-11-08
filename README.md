# JustTorrent — Faz 1 Revize Test Planı

Faz 1: HLS kaynağından segment indirme → tek dosyalık torrent üretimi → magnet URI'li yerel manifest.

> **Önemli (adb düzeltmesi):** Private `files/` dizini için aşağıdaki **YANLIŞ** komut çalışmaz:
>
> ```bash
> adb shell run-as com.example.p2pstream pull files/segments/seg_000000.ts   # ÇALIŞMAZ
> ```
>
> **Doğru yöntem:** `adb exec-out run-as ... cat <dosya> > <yerel>` kullan.

---

## Aşama A — Pipeline doğrulama (VOD, ~30 sn)

VOD playlist: `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8` (Big Buck Bunny, `#EXT-X-ENDLIST` içerir).

1. Uygulamayı kur, `urlInput`'a `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8` yapıştır.
2. **"Yayını Yakala"** → 15 sn bekle (eşzamanlı indirme Semaphore(3) ile sınırlı).
3. Kontrol:

   ```bash
   adb shell run-as com.example.p2pstream ls files/segments/   # en az 3 .ts
   adb shell run-as com.example.p2pstream ls files/torrents/   # en az 3 .torrent
   adb exec-out run-as com.example.p2pstream cat files/manifest.m3u8 > m.m3u8
   # m.m3u8 içinde magnet:?xt=urn:btih: var mı?
   ```

4. Segment oynat:

   ```bash
   adb exec-out run-as com.example.p2pstream cat files/segments/seg_000000.ts > s.ts
   vlc s.ts
   # (veya VLC GUI'de aç)
   ```

5. Torrent dosyasını çek:

   ```bash
   adb exec-out run-as com.example.p2pstream cat files/torrents/<infohash>.torrent > test.torrent
   ```

6. Logcat'te `"SEGMENT N → <infohash>"` satırları (`Log.i(TAG, "SEGMENT ${seg.sequence} → $infoHash")`) görünüyor mu?
7. Aynı segment için **iki kez** indirme tetiklenmemiş mi (`seenSequences` çalışıyor mu)?

✅ Aşama A geçtiyse → Aşama B.

---

## Aşama B — Kayan pencere (canlı, ~60 sn)

Canlı playlist: `https://test-streams.mux.dev/pts_shift/master.m3u8`

Yedek: `https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8`

1. `urlInput`'a `https://test-streams.mux.dev/pts_shift/master.m3u8` yapıştır.
2. **"Yayını Yakala"** → 60 sn bekle.
3. Her 10 sn'de bir manifest'i çek:

   ```bash
   # bash / zsh
   adb exec-out run-as com.example.p2pstream cat files/manifest.m3u8 > m_$(date +%s).m3u8

   # Windows PowerShell
   adb exec-out run-as com.example.p2pstream cat files/manifest.m3u8 > m_$(Get-Date -Format yyyyMMdd_HHmmss).m3u8
   ```

4. Kontrol:

   - manifest'teki segment sayısı ~15 (`windowSize`) civarında sabit mi?
   - `#EXT-X-MEDIA-SEQUENCE` her çekimde **artıyor** mu?
   - En eski segment pencereden **düşüyor** mu?

✅ Aşama B geçtiyse Faz 1 tamam → Faz 2 (DHT/tracker announce) başlanabilir.

---

## Test URL'leri (kodda sabit olarak tanımlıdır)

| URL | Tür | Kullanım |
|---|---|---|
| `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8` | VOD | Aşama A — pipeline doğrulama, throttle aktif (varsayılan) |
| `https://test-streams.mux.dev/pts_shift/master.m3u8` | Canlı | Aşama B — kayan pencere (elle yapıştırılır) |
| `https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8` | Canlı (yedek) | Aşama B yedek |
