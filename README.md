# k1gal

Android gallery for the Pentax K-1 Wi-Fi API.

The app is designed for the "small JPEG previews on SD card" workflow:

1. Connect the Pixel to the `PENTAX_...` Wi-Fi network.
2. Scan the selected SD card.
3. k1gal downloads JPEG previews into app cache.
4. Browse the local cache quickly.
5. Select photos and download matching RAW files to `Download/k1gal`.

The initial RAW download logic tries `.PEF` and `.DNG` names derived from the
JPEG preview name. JPEG previews stay in temporary app cache and can be cleared
from inside the app.

## Build

```sh
./gradlew assembleDebug
```

Install the debug APK:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
