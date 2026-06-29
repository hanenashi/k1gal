<p align="center">
  <img src="/icons/play_store_icon_512.png" width="220">
  
# k1gal
</p>

Android gallery for the Pentax K-1 Wi-Fi API. Current app version: `0.2.3`.

## Nerdy TL;DR

`k1gal` is a tiny native Android client for the Pentax K-1 HTTP API at
`192.168.0.1`. It binds camera traffic to Wi-Fi, asks `/v1/photos` for the SD
card file list, renders an empty local gallery first, and only pulls JPEG
previews into app cache when you ask for them. Selected RAW downloads use the
RAW filenames reported by the camera list and are written to `Download/k1gal`.

The point is to keep the slow K-1 Wi-Fi link out of the browsing loop: list
quickly, preview selectively, swipe through cached or lazily fetched neighbors,
and download RAWs only after deciding what is worth keeping.

The app is designed for the "small JPEG previews on SD card" workflow:

1. Connect the Pixel to the `PENTAX_...` Wi-Fi network.
2. Scan the selected SD card.
3. Scan lists the camera files without downloading every preview.
4. Tap individual cards to fetch only those JPEG previews, or use `All`.
5. Select photos and download matching RAW files to `Download/k1gal`.

RAW downloads use the RAW filename reported by the K-1 file list. JPEG previews
stay in temporary app cache and can be cleared from inside the app. The viewer
supports horizontal swipes; swiping to an uncached neighbor fetches that preview.
Connection settings, help text, the GitHub link, and version live behind `Set`.

## Build

```sh
./gradlew assembleDebug
```

Install the debug APK:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
