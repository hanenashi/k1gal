# k1gal planning notes

## Product direction

`k1gal` has grown from a small Wi-Fi gallery into a practical Pentax K-1 companion app. Its strongest identity is:

> Browse, inspect, selectively retrieve, live-view, and capture K-1 photos over the camera's slow local Wi-Fi link without pretending to be a full replacement camera remote.

The current direction is good because it prioritizes reliable viewing and capture before broad writable camera controls.

## What is already working well

- Capability-driven behavior: treat `/v1/apis` and camera-returned value lists as authoritative instead of assuming every Pentax model or firmware behaves identically.
- Separate thumbnail, viewer, and full-download paths so the grid stays fast and low-resolution files never masquerade as fullscreen previews.
- Explicit live view that starts only on user request and closes aggressively when dismissed, backgrounded, disconnected, or invalidated.
- Guarded remote shutter that prevents duplicate requests, checks drive mode, waits for a genuinely new capture, and reports errors without automatic retries.
- Privacy-aware diagnostics and metadata handling. Wi-Fi keys, SSID, serial number, MAC address, and location data must remain redacted or hidden by default.
- Deliberate RAW/JPEG downloads with byte progress and validation before publishing files to MediaStore.

## Completed: modularization

The architectural refactor moved app state into `MainViewModel` and split the camera, gallery, downloads, and Compose screens into source packages. `MainActivity.kt` previously owned all of these responsibilities:

- Android lifecycle and setup
- network binding
- K-1 API calls
- capability discovery
- photo listing and metadata
- thumbnail and viewer caches
- file downloads and MediaStore writes
- MJPEG parsing and live-view lifecycle
- guarded capture workflow
- Compose state
- gallery, viewer, and live-view UI
- gesture handling

The refactor must preserve current behavior. Avoid combining architecture changes with feature changes.

## Recommended refactor sequence

### 1. Extract pure models and helpers

Move data classes, parsing helpers, filename matching, formatting, and other side-effect-free code first.

Suggested files:

```text
model/PhotoItem.kt
model/CameraModels.kt
util/FileNameUtils.kt
util/PhotoMetadataFormatter.kt
```

This stage should be mostly mechanical and easy to verify.

### 2. Extract the K-1 API client

Create a `camera` package containing HTTP and protocol details only.

Suggested responsibilities:

```text
camera/K1Api.kt
camera/K1ApiClient.kt
camera/K1Endpoints.kt
camera/K1ResponseParser.kt
camera/WifiNetworkBinder.kt
```

The API layer should not know about Compose, selection state, gallery screens, or Android UI messages.

A useful boundary is conceptually similar to:

```kotlin
interface K1Api {
    fun listPhotos(storage: StorageSlot): List<RemotePhoto>
    fun getPhotoInfo(photo: RemotePhoto): PhotoMetadata
    fun downloadPhoto(...)
    fun getCameraProperties(): CameraProperties
    fun shoot(autofocus: AutofocusMode): ShootResult
    fun openLiveView(...)
}
```

The exact implementation may use coroutines later, but the first extraction should preserve behavior rather than redesign everything at once.

### 3. Extract gallery, cache, and download responsibilities

Suggested files:

```text
gallery/GalleryRepository.kt
gallery/PreviewCache.kt
download/DownloadRepository.kt
download/MediaStoreWriter.kt
```

Responsibilities:

- `GalleryRepository`: convert camera file lists and metadata into app-facing photo models.
- `PreviewCache`: own thumbnail/view cache keys, paths, recovery, and clearing.
- `DownloadRepository`: own streaming progress, temporary files, validation, cancellation, and MediaStore publishing.

### 4. Extract long-running camera workflows

Suggested files:

```text
camera/LiveViewController.kt
camera/CaptureController.kt
```

`LiveViewController` should own:

- one active MJPEG call
- session invalidation
- frame parsing
- stale-frame rejection
- shutdown on close/background/network loss

`CaptureController` should own:

- capture serialization
- duplicate prevention
- drive-mode guard
- previous-latest snapshot
- shutter request
- bounded completion polling
- newly captured photo identity
- clear failure reporting without retries

These workflows should expose states or callbacks rather than directly editing Compose variables.

### 5. Introduce a ViewModel

Add a `MainViewModel` after the lower-level responsibilities have clear boundaries.

The ViewModel should own application state and user actions:

```text
photos
selection
busy/cancel state
viewer state
live-view state
capture state
download progress
status and error messages
```

Compose should render immutable state and send user intents back to the ViewModel.

Avoid giving every composable direct access to repositories or camera clients.

### 6. Split UI files last

Once state ownership is settled, split the UI into focused files:

```text
ui/gallery/GalleryScreen.kt
ui/gallery/PhotoCard.kt
ui/viewer/ViewerScreen.kt
ui/live/LiveViewScreen.kt
ui/components/HeaderControls.kt
ui/theme/Theme.kt
```

Splitting composables before state and business logic are extracted risks moving spaghetti into several bowls.

### 7. Keep `MainActivity` small

Target responsibility:

- Android lifecycle integration
- window/system UI setup
- Compose entry point
- ViewModel acquisition
- forwarding lifecycle events when required

Do not create separate Gradle modules yet. Source packages and files are enough until build isolation or reuse provides a concrete benefit.

## Refactor safety rules

- Preserve behavior in each commit.
- Keep commits small and single-purpose.
- Build after every extraction step.
- Test against the physical K-1 after changes involving networking, live view, capture, or MediaStore.
- Do not redesign UI and architecture in the same commit.
- Keep operation-specific timeouts.
- Keep live view explicitly user-triggered.
- Keep all camera writes serialized and deliberate.
- Never log raw aggregate camera properties.
- Keep unsupported or unverified endpoints hidden.

## Recommended post-refactor priorities

Before broad manual controls, add high-value, low-risk improvements:

1. Clear camera connection state and connected model/firmware display.
2. Battery, storage availability, remaining-shot count, and heat warning.
3. Better selection tools, including select all and range selection.
4. Cache size reporting and cache policy controls.
5. Longer live-view testing across rotation, reconnects, app backgrounding, heat, battery use, and memory pressure.
6. Better cancellation and recovery tests for JPEG and RAW downloads.
7. Only then consider aperture, shutter speed, ISO, exposure compensation, and white balance controls.

Read-only status provides substantial value with less risk than writable controls.

## Rules for future writable camera controls

Treat every setting as a request, never as local truth:

```text
read current camera state
show only values advertised by the connected camera
let the user deliberately choose one value
serialize and send one write
read camera state back
show the accepted value or rejection
```

Requirements:

- Never hard-code choice lists from another Pentax model.
- Never optimistically assume a write succeeded.
- Do not retry writes automatically.
- Disable conflicting actions during capture or another write.
- Hide unsupported controls.
- Keep Wi-Fi/device configuration, focus internals, live-view zoom, and split shutter endpoints out until verified on the K-1.

## Product restraint

Do not rush toward becoming a complete remote-control replacement. The strongest version of `k1gal` remains focused on:

- fast gallery listing
- selective previews
- useful metadata
- fullscreen inspection
- deliberate JPEG/RAW retrieval
- on-demand live view
- safe remote capture

That scope is already valuable and keeps the app reliable on the K-1's constrained Wi-Fi interface. Add controls only when they strengthen that workflow rather than turning the app into a fragile wall of camera settings.
