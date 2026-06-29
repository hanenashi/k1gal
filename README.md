# k1gal

Android gallery for the Pentax K-1 Wi-Fi API.

The app is designed for the "small JPEG previews on SD card" workflow:

1. Connect the Pixel to the `PENTAX_...` Wi-Fi network.
2. Scan the selected SD card.
3. Scan lists the camera files without downloading every preview.
4. Tap individual cards to fetch only those JPEG previews, or use `Preview all`.
5. Select photos and download matching RAW files to `Download/k1gal`.

RAW downloads use the RAW filename reported by the K-1 file list. JPEG previews
stay in temporary app cache and can be cleared from inside the app. The viewer
supports horizontal swipes; swiping to an uncached neighbor fetches that preview.

## Build

```sh
./gradlew assembleDebug
```

Install the debug APK:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
