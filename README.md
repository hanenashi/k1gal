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
