# Debug / Performance Report

A lightweight, user-exportable diagnostic snapshot for the **high-res audio
performance investigation**. It exists so we can tell *where* lag comes from —
scanning, metadata, artwork extraction, artwork decoding, playback prepare,
transitions/crossfade, audio offload, widgets, MediaSession, Android Auto, or
UI work — **without asking the user for logcat**.

This first step is observability only. It does **not** change playback,
artwork, or offload behaviour.

## How a user exports it

1. Open **Settings → Device capabilities**.
2. Scroll to the **Performance report** card at the bottom.
3. Tap **Generate report**.
4. Tap **Copy** (to paste into a bug report) or **Share** (to send via any app).

The exported payload is human-readable text followed by a `--- JSON ---`
appendix with the same data in machine-readable form.

## Privacy

The report contains **only** device/build info, library aggregates, format
metadata, capability flags, and timing statistics. It never includes file
paths, titles, artists, album names, or any library content, so it is safe to
share publicly.

## What it captures

| Section | Highlights |
| --- | --- |
| **Device** | manufacturer, model, brand, Android version + SDK, ABIs, RAM class, total/low-RAM flags |
| **App** | versionName / versionCode, build type, applicationId |
| **Library** | total / local / cloud counts, per-MIME counts, max bitrate, sample-rate range, estimated file-size min/avg/max |
| **Hi-res classification** | hi-res (>48 kHz), ultra-hi-res (≥176.4 kHz), lossless-codec, "likely expensive to decode", multichannel & large-artwork observations |
| **Artwork** | max embedded bytes, max *decoded* dimensions, cache hit/miss, fresh extractions, extraction & decode timing |
| **Playback** | current MIME / sample rate / bitrate / channels / PCM bit depth, decoder name + hardware/software class, offload enabled + fallback count, Hi-Fi / crossfade / EQ / ReplayGain state |
| **Controllers** | widget active, Wear active, Android Auto active, connected controller packages |
| **Timings (ms)** | count / min / avg / max / last for every instrumented operation |
| **Offload events** | bounded log of HAL offload-reset fallbacks with reasons |

## How the data is collected (cheaply)

The report is assembled on demand from data that is **already known** or
collected *while existing work happens* — there are no extra probes:

- **`PerformanceMetrics`** (`data/diagnostics/PerformanceMetrics.kt`) is a
  process-wide, in-memory recorder. Producers call it on paths they already
  execute; each sample is an O(1) atomic op or a short `synchronized` block.
  It holds only aggregates (count/min/max/sum), a few max-trackers, counters,
  and a 20-entry offload-event ring buffer, so memory stays flat.
- **DB aggregates** come from two single-pass SQL queries
  (`MusicDao.getLibraryAudioStats`, `getMimeTypeCounts`) — the whole library is
  never materialized into memory. File sizes are *estimated* from
  `bitrate × duration` because raw byte sizes are not stored.
- **Live playback** values (decoder, format, offload state) are read straight
  off the `DualPlayerEngine` / ExoPlayer instance.
- **Settings** (Hi-Fi, crossfade, EQ, ReplayGain) are read once from DataStore.

### Instrumentation points

| Metric | Where it is recorded |
| --- | --- |
| Full scan time, songs scanned | `SyncWorker.doWork` |
| Per-file metadata read time, JAudioTagger fallbacks | `AudioMetadataReader.read` |
| Artwork extraction time, embedded byte size, fresh extractions | `AlbumArtUtils.extractEmbeddedAlbumArtBytes` |
| Artwork cache hit/miss | `AlbumArtUtils.ensureAlbumArtCachedFile` |
| Artwork decode time + decoded dimensions (piggybacked on the real decode) | `CoilBitmapLoader` |
| Audio decoder init time, live format (channels / sample rate / bit depth) | `DualPlayerEngine` analytics listener |
| Playback prepare (buffering→ready), transition/crossfade time | `DualPlayerEngine` |
| Audio offload fallbacks (with reason) | `DualPlayerEngine.disableAudioOffloadForSession` |
| External controllers (Android Auto / Wear / other) | `MusicService.onConnect` |
| Widget update time + widget-active flag | `WidgetUpdateManager.updateGlanceWidgets` |

> Some figures (channel count, bit depth, multichannel, embedded-artwork size)
> are *observed during this session* rather than probed across the whole
> library, because probing every file just to build a report would be exactly
> the kind of expensive work we are investigating. The report's **NOTES**
> section spells this out.

### Overhead & safety guarantees

- **No diagnostic hook does heavy work.** Hooks only record primitive
  timing/counter/max data. They never perform an extra file read, bitmap decode,
  metadata probe, JSON serialization, or DB query. (Embedded-artwork
  *dimensions* are intentionally *not* captured, because that would require a
  per-file decode during scan; only the free byte size is recorded.)
- **Generation is user-triggered only.** The report is built solely when the
  user taps Generate; it never runs on screen open, recomposition, copy, or
  share. Re-entry is guarded so concurrent taps can't stack work.
- **Player state is read on the player's thread.** ExoPlayer is thread-confined,
  so the collector reads decoder/format/offload state on `Dispatchers.Main`,
  then does all DB aggregation and assembly on `Dispatchers.IO`.
- **Recorder contention is negligible.** Counters/maxes are atomic;
  `ConcurrentHashMap.computeIfAbsent` takes a lock-free fast path for present
  keys; each `TimingStat.record` holds a per-metric lock for ~5 field writes.
  A concurrency test exercises 8 threads × 5 000 records and asserts zero lost
  updates.
- **Per-file scan logging is gated.** `AudioMetadataReader`'s verbose per-file
  `Log.w` lines (including one that stringifies the whole TagLib key set) are
  behind a `VERBOSE` flag, off by default, so scans don't pay for them.

## How to read it to classify lag

Match the symptom to the section:

- **Lag during library scan / first run** → **Timings → `full_scan`** large, and
  **`metadata_read` / `artwork_extract`** avg/max high. High
  `metadata_fallback_jaudiotagger` means many files force the slow tag reader.
- **Stutter when artwork appears (lockscreen / notification / widget)** →
  **Artwork → max embedded bytes/dimensions** large (10–30 MB covers), low
  cache-hit ratio, and **`artwork_decode`** max high. **`widget_update`** max
  high points at the widget path specifically.
- **Slow to start a track / gaps** → **Timings → `playback_prepare`** and
  **`audio_decoder_init`** high; cross-reference **Playback → decoder class =
  software** and hi-res sample rate.
- **Audio drops out mid-track, then recovers** → **Offload events** present and
  **`offloadFallbackCount` > 0**; the reason string says why the HAL reset.
- **Jank only with crossfade** → **`transition`** timing high while crossfade is
  on in **Playback**.
- **Only happens in the car / on the watch / with a widget** → **Controllers**
  shows Android Auto / Wear / widget active; correlate with the timing spikes.
- **Only happens with hi-res files** → **Hi-res classification** confirms the
  library actually contains hi-res / ultra-hi-res / multichannel / large-art /
  software-decode-likely files, so a tiny library with a few expensive files is
  still diagnosable.

## Schema / versioning

The JSON carries `schemaVersion` (`DebugPerformanceReport.SCHEMA_VERSION`).
Bump it when the model changes so older exports remain interpretable.

## Tests

- `app/src/test/.../diagnostics/PerformanceMetricsTest.kt` — recorder semantics
  (min/max/avg, counters, max-trackers, large-artwork flag, multichannel
  detection, bounded offload log, controller de-dup).
- `app/src/test/.../diagnostics/DebugPerformanceReportTest.kt` — JSON
  round-trip, presence of every text section, hi-res/offload signals, locale-
  independent byte formatting, PCM-encoding labels, and a no-path-leak check.
