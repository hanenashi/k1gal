# Pentax K-1 Wi-Fi API Knowledge Base

Research notes for extending `k1gal` beyond file listing and downloads. This is
an implementation reference, not an official API specification.

Last researched: 2026-07-13.

### Verified K-1 test environment

The user's `PENTAX K-1` running firmware `2.51` was probed directly on
2026-07-13. Beechan used Ethernet for internet/Codex and bound camera requests
to its Wi-Fi interface, because both the home LAN and camera LAN use
`192.168.0.0/24` with a host at `192.168.0.1`.

Verified on this camera:

- `/v1/ping`, `/v1/apis`, scoped property endpoints, both SD file lists,
  per-photo `/info`, and `thumb`/`view`/`full` image sizes return HTTP 200.
- SD1 reported paired JPEG/RAW files; SD2 correctly returned an empty list.
- The advertised API list includes every route recorded below.
- Sensitive device properties really do include `key`, `ssid`, `serialNo`, and
  `macAddress`; diagnostics must continue to allowlist fields.

## Evidence levels

- **Confirmed in k1gal**: already exercised by this app against the user's K-1.
- **Strong cross-model evidence**: implemented by open-source Pentax K-S2
  clients and reported to work almost completely with the K-1. The K-1 and
  K-S2 expose the same discoverable `/v1` API family, but response details may
  vary by camera firmware.
- **Unverified on our K-1**: discoverable or seen in another Pentax client, but
  must be probed on the user's camera before becoming a product feature.

The camera is the final authority. Always read `GET /v1/apis` from the connected
K-1 before assuming an endpoint exists.

## Connection model

- The camera creates the `PENTAX_...` Wi-Fi network and normally serves HTTP at
  `192.168.0.1`.
- The API is cleartext HTTP and is reachable only while connected to the
  camera's local network.
- Android may prefer a separate internet-capable network. `k1gal` currently
  binds the process to the active non-VPN Wi-Fi network before camera work.
- K-1 Wi-Fi can become inactive after the camera powers off or auto-power-off.
  The official K-1 documentation says auto power-off is unavailable while a
  Wi-Fi connection or transfer is active, but Wi-Fi is disabled if the camera
  powers off or enters movie mode.
- Current `k1gal` timeouts are 2 seconds for connection and 25 seconds for
  reads. Streaming and RAW downloads should use operation-specific timeouts.

## Discovery and safe probing

### API catalog

```http
GET /v1/apis
```

The camera returns its recognized routes as JSON. This should be the first
diagnostic request on a new model or firmware.

Known advertised routes from the Pentax API family include:

```text
/v1/photos/ath
/v1/photos/ath/info
/v1/photos/latest/info
/v1/photos
/v1/props[/camera|lens|liveview|device]
/v1/variables[/camera|lens|liveview|device]
/v1/status[/camera|lens|liveview|device]
/v1/params[/camera|lens|liveview|device]
/v1/constants[/camera|lens|liveview|device]
/v1/ping
/v1/liveview
/v1/camera/shoot
/v1/camera/shoot/start
/v1/camera/shoot/finish
/v1/lens/focus
/v1/liveview/zoom
/v1/changes
```

`/v1/photos/ath` appears verbatim in the published list but its meaning is
unknown and may be a firmware artifact or typo. Do not build around it.

### Probe policy

Start with GET-only requests:

1. `/v1/ping`
2. `/v1/apis`
3. `/v1/props/camera`, `/v1/props/lens`, `/v1/props/liveview`, and
   `/v1/props/device` if advertised
4. `/v1/status/...` and `/v1/constants/...` if needed
5. One known photo's `/info`
6. One known photo with each `size` value
7. `/v1/liveview`, manually opened and immediately closed

Do not persist or print raw aggregate `/v1/props` responses. They may contain
the camera Wi-Fi key, SSID, serial number, and MAC address. A diagnostic export
must use an allowlist and redact identifiers.

## Photos

### File list — confirmed in k1gal

```http
GET /v1/photos?storage=sd1
GET /v1/photos?storage=sd2
```

Observed shape used by `k1gal`:

```json
{
  "dirs": [
    {
      "name": "100_0001",
      "files": ["IMGP0001.JPG", "IMGP0001.PEF"]
    }
  ]
}
```

`k1gal` pairs JPEG and RAW files by case-insensitive filename stem. Supported
RAW suffixes are currently `.PEF`, `.DNG`, and `.RAW`.

### Image retrieval

Current confirmed form:

```http
GET /v1/photos/{dir}/{filename}?storage=sd1
```

Strong cross-model evidence supports server-side sizes:

```http
GET /v1/photos/{dir}/{filename}?size=thumb
GET /v1/photos/{dir}/{filename}?size=view
GET /v1/photos/{dir}/{filename}?size=full
```

The K-1 accepts `size` together with `storage=sdN`. A representative XS3 JPEG
returned these results:

| Request | Dimensions | Representative bytes |
| --- | ---: | ---: |
| No `size` | 1920×1280 | 1,521,260 |
| `size=thumb` | 160×120 | 7,728 |
| `size=view` | 720×480 | 54,247 |
| `size=full` | 1920×1280 | 1,521,260 |

Exact byte counts vary by image. Record response status, `Content-Type`, byte
count, and decoded pixel dimensions—not image contents—in diagnostics.

The K-1 rejects `HEAD` with HTTP 405, but a normal file `GET` supplies an exact
`Content-Length` before the response body. Close that response immediately when
probing metadata so the RAW is not downloaded. This supports an on-demand RAW
size in the fullscreen viewer; do not probe every file during a gallery scan.
During an actual download, use the same length with streamed byte counts for
`downloaded/total MB` progress. A tested DNG reported 51,956,532 bytes.

Proposed use:

- `thumb`: grid cache and gallery cards.
- `view`: fullscreen viewer cache.
- `full`: explicit full JPEG download only.
- RAW filename: unchanged binary download; validate minimum length before
  publishing to MediaStore.

Keep thumbnail and viewer files in separate cache paths. Do not let a thumbnail
silently satisfy a viewer request. A useful key is:

```text
sd{card}/{dir}/{stem}.{thumb|view|full}.jpg
```

### Per-photo metadata

```http
GET /v1/photos/{dir}/{filename}/info
GET /v1/photos/latest/info
```

Strong cross-model evidence shows these fields:

| Field | Meaning / proposed use |
| --- | --- |
| `captured` | Capture completion flag, especially for `latest` |
| `dir`, `file` | Camera storage identity |
| `datetime` | Capture time without downloading/reading EXIF |
| `av` | Aperture |
| `tv` | Shutter speed; Pentax uses strings such as `1.160` for 1/160 s |
| `sv` | ISO/sensitivity |
| `xv` | Exposure compensation |
| `orientation` | Viewer/grid orientation hint |
| `cameraModel` | Camera model |
| `latlng` | Optional location; treat as private and do not display by default |
| `errCode`, `errMsg` | API result status |

All fields in this table were present on the tested K-1. A representative
response identified `cameraModel` as `PENTAX K-1`, used integer orientation
`1`, returned `captured: true`, and supplied aperture, shutter, ISO,
compensation, and timestamp. The tested photo had an empty location value.

Metadata should be fetched lazily or in a bounded queue after listing. A scan
must remain fast and must not launch one unbounded request per image.

`/v1/photos/latest/info` is also the likely completion signal after a remote
capture. Save the previous `dir/file` before shooting, then wait for `captured`
and a changed identity to avoid mistaking an older shot for the new one.

## Camera properties

```http
GET /v1/props
GET /v1/props/camera
GET /v1/props/lens
GET /v1/props/liveview
GET /v1/props/device
```

The aggregate response seen in the compatible K-S2 client contains both
current values and lists of permitted values.

Useful read-only fields:

- Camera state and live-view state
- Battery percentage and heat warning
- Camera model and firmware version
- Aperture (`av`), shutter (`tv`), ISO (`sv`), compensation (`xv`)
- Exposure, shooting, focus, and white-balance modes
- Whether focus has been acquired and reported focus centers
- Equipped/available/writable/active storage slots
- Remaining shot count plus current directory/file
- Lists such as `avList`, `tvList`, `svList`, `xvList`, `WBModeList`,
  `shootModeList`, and `exposureModeList`

Sensitive fields that must be discarded or redacted:

- `key`
- `ssid`
- `serialNo`
- `macAddress`
- Photo `latlng`

Never hard-code exposure choices from another model. Populate any future
controls from lists returned by the connected camera.

## On-demand live view

```http
GET /v1/liveview
```

Existing Pentax clients treat this response as an MJPEG stream. They start live
view by opening the URL and stop it by closing the stream. No periodic polling
is required while live view is closed.

Verified on the user's K-1:

- Response type is `multipart/x-mixed-replace` with boundary
  `--boundarydonotcross`.
- Each part contains `Content-type: image/jpg` followed by a JPEG frame.
- Frames are 720×480; a short sample produced roughly ten frames per second.
- Closing/cancelling the HTTP response stops the client stream cleanly.
- `/v1/props/liveview` continued reporting `liveState: idle` while streaming,
  so that property is not a reliable indicator of an active client stream.

Desired `k1gal` behavior:

1. User explicitly opens **Live**.
2. Bind the request to camera Wi-Fi.
3. Open one streaming HTTP call.
4. Parse multipart MJPEG boundaries and decode only the newest complete JPEG.
5. Render the latest frame; discard stale sessions instead of allowing an old
   stream to overwrite a newly opened one.
6. Close and cancel the HTTP call when the user closes Live, backgrounds the
   app, loses Wi-Fi, or navigates away.
7. Return to the existing cached gallery without a rescan.

Implementation cautions:

- Use a dedicated OkHttp client/call with no ordinary read timeout.
- Keep stream decoding off the main thread.
- Decode near the display size to control allocations.
- Maintain at most one pending/rendered frame.
- Show a visible reconnect/error state rather than retrying forever.
- Do not automatically start live view on launch.
- Check `liveState` if available, but treat the actual stream lifecycle as the
  source of truth.
- Test camera temperature, battery drain, rotation, app background/foreground,
  Wi-Fi loss, and repeated open/close cycles.

`/v1/liveview/zoom` exists in the advertised family but its method and payload
are unverified. It is not needed for the first live-view implementation.

## Remote shutter

Strong cross-model evidence:

```http
POST /v1/camera/shoot
Content-Type: application/x-www-form-urlencoded

af=auto
```

Verified on the user's K-1 with drive mode set to `single`:

- `POST /v1/camera/shoot` with form body `af=auto` returned HTTP 200,
  `errCode: 200`, and `focused: true`.
- Completion appeared after seven 250 ms polls in the direct probe.
- The same flow succeeded while the app kept `/v1/liveview` open.
- `/v1/photos/latest/info` first reported the new JPEG and later the paired DNG
  under the same stem, so clients should normalize either filename to the JPEG
  gallery identity.
- The app fetched the new thumbnail and kept live view active after capture.
- With a legacy/manual-focus lens, the K-1 reported `focusMode: mf` and rejected
  `af=auto` with error 412 (`Precondition Failed`). Sending `af=off` succeeded
  and captured normally. Clients should select the autofocus request from
  `/v1/props/lens` rather than assuming autofocus is available.

Other autofocus values reported by clients are `on` and `off`, but they remain
unverified on our K-1. Older code sent the same plain body with a nonstandard
`text/xml` content type, so the camera may be permissive. `k1gal` uses standard
form encoding.

Proposed capture flow:

1. Require an explicit shutter tap; never fire on entering Live.
2. Disable duplicate shutter actions while capture is active.
3. Snapshot `/v1/photos/latest/info`.
4. Read `/v1/props/lens`, then POST `af=auto` in autofocus mode or `af=off`
   when `focusMode` is `mf`/only the `manual` focus setting is available.
5. Poll `latest/info` at a modest interval until `captured` is true and the
   `dir/file` changes, with a bounded timeout.
6. Update the gallery and optionally fetch the new `thumb`/`view` image.
7. Report timeout or camera error without blindly retrying the shutter.

Before POST, `k1gal` reads `/v1/props/camera` and refuses capture unless
`shootMode` is `single`. This prevents an accidental burst during API testing.

The advertised split endpoints `/shoot/start` and `/shoot/finish` may support
bulb/press-and-release behavior, but their methods and payloads are unknown.
Keep them out of the first shutter feature.

## Writable camera parameters — later work

Strong cross-model evidence uses plain form-like bodies:

```http
PUT /v1/params/camera
Content-Type: application/x-www-form-urlencoded

av=4.0
tv=1.160
sv=400
xv=+0.7
WBMode=daylight
```

An empty body reportedly acts like the camera's green button on the compatible
model. Do not expose that behavior without K-1 testing.

Existing client notes say aperture, shutter, ISO, compensation, white balance,
and image size worked, while some changes to exposure mode, shoot mode, and
filters did not. Parameter controls are therefore lower priority than viewing
and shutter release.

Each write must:

- Use a value offered by the current camera's property list.
- Be initiated by a deliberate UI action.
- Serialize with capture and other writes.
- Read properties back and confirm the accepted value.
- Display camera rejection; never loop writes automatically.

## Other advertised endpoints

- `/v1/lens/focus`: likely focus-point or focus-action control; request shape
  unverified.
- `/v1/changes`: likely state-change polling/long polling; semantics unverified.
- `/v1/variables`, `/v1/status`, `/v1/constants`: potentially useful for a
  typed capability layer, but first capture sanitized K-1 responses.
- `/v1/params/device`: writable device/Wi-Fi configuration may be exposed.
  `k1gal` should not modify it.

## Proposed internal architecture

The current app keeps networking, cache, state, and Compose UI in
`MainActivity.kt`. Before adding live view, split the feature boundaries:

```text
CameraApi
  discoverApis()
  listPhotos(storage)
  getPhotoInfo(photo)
  downloadPhoto(photo, size)
  openLiveView()
  shoot(af)

PhotoCache
  thumbFile(photo)
  viewFile(photo)
  clear()

Gallery state
Live-view state
Capture state
```

The model should retain the stable camera identity `(storage, dir, filename)`
and track thumb/view cache files separately. UI state should not own live HTTP
calls.

## Sources

- [Pentax K-S2 WebAPI overview](https://sourceforge.net/p/pentaxks2wifiremote/wiki/K-S2%20WebAPI%20Overview/)
- [Pentax-K-S2-WiFiRemote source](https://github.com/cszielke/pentaxks2wifiremote)
- [PentaxForums PentaxWifi client](https://github.com/PentaxForums/PentaxWifi)
- [Official Pentax K-1 FAQ: Image Sync and remote capture](https://www.ricoh-imaging.co.jp/english/support/qa/k-1/)
- [WiFi Commander for Pentax: reports K-1 remote shooting use](https://www.wificommanderpentax.com/)

These sources are evidence of the shared Pentax API and client behavior, not a
guarantee that every endpoint or value works on every K-1 firmware. Update this
file with sanitized observations when the user's camera is next connected.
