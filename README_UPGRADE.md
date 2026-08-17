# S0PHIA OPS Radar Upgrade

This update turns the flat tactical screen into an interactive **3D radar view** and restores a useful, locally persisted signal timeline beneath it.

## What changed

| Area | Upgrade |
|---|---|
| Radar | The radar now renders a perspective grid with a live sweep and depth-aware signal markers. Drag in **Orbit** mode to rotate the full 360° scene, pinch to zoom, twist to rotate, or switch to **Pan** mode to reposition the field. Use **Reset** to return to the default camera. |
| Signal history | Saved Bluetooth observations are loaded from Room whenever the app starts. The radar history panel now shows the most recently seen devices, an RSSI sparkline, last-seen time, sample count, current signal, and risk score. This fixes the previous dependence on only in-memory discovery callbacks. |
| Settings | Added persistent controls for active scan cadence, background scanning, auto-rotate, target labels, radar range, radar-history visibility and size, automatic post-scan analysis, and clearing locally stored observations. |
| Persistence | Display preferences are stored locally. Clearing stored history removes saved Wi-Fi, Bluetooth, and scan-session data while retaining preferences. |

## Radar controls

| Control | Action |
|---|---|
| **Orbit** | Drag to rotate the 3D camera; pinch to zoom; twist to rotate. |
| **Pan** | Drag to move the tactical field; pinch to zoom. |
| **Tap a marker** | Opens the existing selected-target detail card. |
| **Reset** | Returns camera heading, tilt, zoom, and panning to their default values. |

## Build verification

The reconstructed project was validated with:

```bash
gradle :app:compileDebugKotlin --no-daemon --no-configuration-cache --console=plain
```

The Kotlin compilation completed successfully. Existing project warnings relating to deprecated MediaPipe, Room, and Material APIs remain unchanged.

> The source archive deliberately excludes local SDK paths, build outputs, and Gradle caches. Open the project in Android Studio or configure your local Android SDK before building.

## Scanner reliability repair

The scanner repair adds explicit checks and visible status messages for Android conditions that can otherwise return zero results without explanation. Wi-Fi scanning now preserves all visible networks rather than silently hiding weaker ones, verifies Wi-Fi, Location, and runtime permissions before scanning, handles Android scan-rate throttling, and reports timeouts. Bluetooth scanning now always completes the scan state, verifies Nearby Devices permissions and radio state, and reports BLE/classic-discovery failures.

Before testing the repaired APK, ensure **Wi-Fi**, **Bluetooth**, and device-wide **Location** are enabled. In Android system settings, grant the app **Location** and **Nearby devices** permissions. The dashboard now reports the specific blocked condition instead of showing a misleading empty live scan.

## Manual Signal Atlas redesign

The radar has been replaced with **Signal Atlas**, a stable top-down tactical map that does not spin or animate by itself. Use **Rotate** or **Pan**, then drag the map; use the bearing buttons, pinch-to-zoom, range buttons, or Reset for exact control. The former auto-rotate control has been removed from the settings interface.

Scanning now defaults to **manual** control. The primary Scan button changes to a red **Stop Scan** button while discovery is active. Stopping cancels both Wi-Fi and Bluetooth discovery and restores the interface immediately. Continuous scanning is available as an explicit opt-in setting under **Scanning**.

## Structured local AI analyst

The AI analyst now produces a **Local Assessment** instead of a single unstructured message. Each assessment shows severity, confidence, observed evidence, scan-to-scan changes, uncertainty, and a safe next step. The analyst separates observed radio metadata from claims it cannot support: it does not infer ownership, identity, intent, or compromise from a scan alone.

The current and previous analyst snapshots are compared for new devices, no-longer-visible devices, meaningful signal movement, and material threat-score movement. Chat now receives a compact, bounded local context containing only the selected device and up to five notable current signals. Device names are sanitized before inclusion in that context.

Model downloads now require HTTPS, use explicit connection and read timeouts, stream into a temporary file, reject unexpectedly small downloads, and only replace the working model after validation. The former Global Intel label has been replaced by **Guidance**; this build explicitly reports that no live intelligence connector is attached instead of presenting simulated data as current intelligence.

## Investigation workflow

The app now stores a compact local **Investigation Timeline** containing the most recent structured assessments. Open **History** to review the severity, confidence, headline, observed change, and recommended next step for each saved incident. Clearing scan history also clears this incident timeline.

When a signal marker is selected in Signal Atlas, its investigation panel now includes three local review states: **Trust**, **Watch**, and **Clear**. A trusted device is excluded from the analyst’s newly observed elevated-risk list, while watchlist devices are explicitly cited as evidence whenever they appear. These labels remain local to the device and do not alter raw observations or execute network actions.

## Alerting and Response Center

The Alerting and Response Center is **on-device only**. It considers an Android notification only after an active S0PHIA OPS scan completes and the automatic local analyst produces a **high**, **critical**, or **watchlist-related** incident. Manual assessments never generate a notification, and the configured cooldown prevents repeated alerts. Android notification permission is requested on supported devices and may be changed in the device’s app settings.

The Dashboard now opens with a Daily Local Brief summarizing today’s incident count, unresolved work, watchlist observations, and highest local severity. The History timeline supports acknowledgement and resolution of each incident; those response states remain local and do not change any network device or connection.

## Local reports and risk tuning

Each incident in the History timeline now has an **Export Local PDF** action. The report includes its local assessment, evidence summary, scan changes, recommended next step, response status, and acknowledgement or resolution timestamps. The document is generated on the phone and shared only through the Android share sheet that the user opens.

The new **Risk Tuning** settings adjust local prioritization without altering saved raw observations. They include a bounded score-sensitivity adjustment, a nearby-signal threshold, a trusted-device exclusion for new-risk findings, and an option to let watchlist observations qualify for focused active-scan alerts. The same score and proximity settings apply to foreground and background observations.

## Device investigation workspace and GATT explorer

Bluetooth and Wi-Fi details now function as local investigation workspaces. They show proximity and risk summaries, metadata, review states, and trusted/watchlist controls. Bluetooth records additionally show signal trend and recent sampled history, support local labels and notes, and can be excluded from future Bluetooth updates with the ignore control.

The **Read-only BLE GATT Explorer** is available only from an explicitly selected Bluetooth device. It connects temporarily to discover the GATT service topology, then disconnects. It does **not** pair, bond, read characteristic values, subscribe to notifications, write descriptors or characteristics, or alter the selected device. Discovery can fail for devices that are not BLE GATT peripherals, are out of range, or decline a connection.

The offline catalog packages the `service_uuids.json`, `characteristic_uuids.json`, and `descriptor_uuids.json` data from Nordic Semiconductor’s Bluetooth Numbers Database, alongside its required license notice under `app/src/main/assets/uuid_catalog/`. The catalog labels standard 16-bit Bluetooth service, characteristic, and descriptor UUIDs and identifies unrecognized 128-bit UUIDs as proprietary or unknown. Reference sources: [Bluetooth SIG Assigned Numbers](https://www.bluetooth.com/specifications/assigned-numbers/) and [Bluetooth Numbers Database](https://github.com/NordicSemiconductor/bluetooth-numbers-database).

## Name-first GATT display

GATT results now put the assigned human-readable name first for every standard service, characteristic, and descriptor. Numeric UUID strings are hidden by default behind **VIEW ID** and can be expanded only when needed; expanded rows provide a compact identifier and a **COPY** action. Unknown or proprietary attributes are explicitly labelled rather than being mistaken for standard Bluetooth names.

## Reliable GATT connection and read-only inspector

The GATT explorer now uses up to three controlled Bluetooth LE connection attempts with short recovery pauses for common transient Android failures. Its diagnostics distinguish generic connection failures, timeouts, remote disconnections, rejected connections, unavailable permissions, and stalled service discovery. It does not retry indefinitely.

After a successful GATT discovery, **Read Safe Standard Values** is an explicit, optional control. It reads only standard characteristics that both appear in the limited local allowlist and advertise the Bluetooth GATT **Read** property. The allowlist covers Device Name, Appearance, Battery Level, System ID, Model Number, Serial Number, Firmware Revision, Hardware Revision, Software Revision, Manufacturer Name, and PnP ID. It never reads arbitrary characteristics, subscribes to notifications, pairs, writes, or changes the remote device.


## Local Device Profiles and Change Comparison

This upgrade adds **explicit, local-only baseline profiles** for Bluetooth and Wi-Fi observations. A profile is only created or replaced when the operator selects the save action; it is not created automatically from a scan. Profiles are stored in the app’s private preferences and are excluded from Android cloud backup and device-transfer export.

| Observation type | Saved baseline | Compared on later observation |
|---|---|---|
| Bluetooth | Device name, local label and notes, review state, recent local RSSI signal pattern, risk score, successful GATT service topology, and successfully read allowlisted values | Device-name changes, new or absent GATT services, changed allowlisted values, signal movement, risk movement, and review-state changes |
| Wi-Fi | SSID, reported security capability, review state, RSSI, and risk score | SSID changes, reported security-capability changes, signal movement, risk movement, and review-state changes |

Bluetooth service differences are displayed only after a **new successful user-initiated read-only GATT discovery**. The app does not infer that a service is missing when a discovery was skipped, closed, or failed. Likewise, standard values are compared only after the optional read-only value inspector was explicitly completed. This prevents an absence of evidence from being displayed as a device change.

To use it, open a device investigation. For Wi-Fi, select **SAVE LOCAL PROFILE** to record the observed baseline. For Bluetooth, complete a GATT discovery, optionally inspect safe standard values, then select **SAVE TO LOCAL PROFILE**. On later observations, the **CURRENT COMPARISON** panel shows verified differences and identifies data that has not yet been refreshed.

> Profile saving and comparison perform no network request, Wi-Fi connection, Bluetooth pairing, subscription, write, or device alteration. GATT discovery and value inspection remain read-only.

### Build verification

The Local Device Profiles and Change Comparison build was assembled with:

```bash
/home/ubuntu/tools/gradle-9.4.1/bin/gradle :app:assembleDebug --no-daemon --no-configuration-cache --console=plain
```

The build completed successfully. Existing non-blocking Android deprecation warnings remain unchanged.
