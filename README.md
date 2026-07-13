<p align="center">
  <img src="/icons/play_store_icon_512.png" width="220">
</p>

# k1gal

Android gallery for the Pentax K-1 Wi-Fi API. Current app version: `0.2.3`.

## Nerdy TL;DR

`k1gal` is a tiny native Android client for the Pentax K-1 HTTP API at
`192.168.0.1`. It binds camera traffic to Wi-Fi, asks `/v1/photos` for the SD
card file list, renders an empty local gallery first, and only pulls JPEG
previews into app cache when you ask for them. Selected downloads can save those
cached camera JPEGs or fetch matching RAW originals, and both are written to
`Download/k1gal`.

The point is to keep the slow K-1 Wi-Fi link out of the browsing loop: list
quickly, preview selectively, swipe through cached or lazily fetched neighbors,
zoom/pan in the fullscreen viewer, and download JPEGs or RAWs only after
deciding what is worth keeping.

The app is designed for the "small JPEG previews on SD card" workflow:

1. Connect the Pixel to the `PENTAX_...` Wi-Fi network.
2. Scan the selected SD card.
3. Scan lists the camera files without downloading every preview.
4. Tap individual cards to fetch only those JPEG previews, or use `All`.
5. Select photos and use `Down` to save either cached JPEGs or matching RAW
   files to `Download/k1gal`.

RAW downloads use the RAW filename reported by the K-1 file list. JPEG previews
stay in temporary app cache until saved through `Down`, and the cache can be
cleared from inside the app. The fullscreen viewer supports horizontal swipes,
double-tap zoom, pinch zoom, and one-finger pan while zoomed; swiping to an
uncached neighbor fetches that preview. Connection settings, help text, the
GitHub link, and version live behind `Set`.

## Build

```sh
./gradlew assembleDebug
```

Install the debug APK:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Roadmap

Detailed API research, evidence levels, privacy notes, and expected payloads
live in [knowledgebase.md](knowledgebase.md). The camera's own `/v1/apis`
response is authoritative; features stay capability-driven rather than
assuming every Pentax firmware behaves identically.

### 1. Learn the connected K-1 safely

- Add a developer diagnostic that performs GET-only capability discovery.
- Capture a sanitized `/v1/apis` response and allowlisted camera properties.
- Probe `thumb`, `view`, and `full` photo sizes, recording byte and pixel sizes.
- Verify per-photo `/info` fields on both SD slots without logging identifiers,
  Wi-Fi credentials, or location.

### 2. Faster gallery thumbnails

- Request server-generated `size=thumb` images for cards.
- Request `size=view` only when opening the fullscreen viewer.
- Keep thumbnail and viewer caches separate so a low-resolution file never
  masquerades as a fullscreen preview.
- Preserve lazy loading, stop/cancel behavior, and explicit full JPEG/RAW
  downloads.

### 3. Richer photo metadata

- Read capture time, aperture, shutter speed, ISO, exposure compensation, and
  orientation from `/v1/photos/{dir}/{file}/info`.
- Fetch metadata lazily with bounded concurrency so scanning stays quick.
- Add concise metadata to cards and the fullscreen overlay.
- Treat location metadata as private and hide it by default.

### 4. On-demand live view

- Add a **Live** action that opens `/v1/liveview` only when requested.
- Decode the MJPEG stream off the main thread and display only the freshest
  frame.
- Close the stream immediately when Live closes, the app backgrounds, Wi-Fi is
  lost, or the user leaves the screen.
- Test repeated open/close, rotation, reconnects, heat, battery use, and memory
  pressure. Live view must never start automatically.

### 5. Remote shutter

- Add a deliberate shutter button inside live view.
- Verify `POST /v1/camera/shoot` and autofocus values on the actual K-1.
- Prevent duplicate capture requests and use a bounded timeout.
- Poll `/v1/photos/latest/info` for a newly captured file, then add its
  thumbnail to the gallery.
- Report errors without automatically retrying a shutter action.

### 6. Optional camera controls

- Consider aperture, shutter, ISO, exposure compensation, and white balance
  only after viewing and capture are reliable.
- Populate choices from the camera's returned value lists, confirm every write
  by reading it back, and keep unsupported modes hidden.
- Leave Wi-Fi/device configuration, focus internals, live-view zoom, and
  start/finish shutter endpoints untouched until their K-1 behavior is known.
