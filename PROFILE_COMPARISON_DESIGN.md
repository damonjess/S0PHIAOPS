# Local Device Profiles and Change Comparison

## Privacy and safety boundary

Profiles are explicit local baselines stored only in the application-private SharedPreferences store. They do not send telemetry, initiate network traffic, connect to Wi-Fi, or cause a Bluetooth operation. Bluetooth topology and standard values are captured only from the existing user-initiated, read-only GATT result. The feature never writes, pairs, bonds, subscribes, or changes a remote device.

## Profile baseline

Each profile is keyed by the observed Bluetooth address or Wi-Fi BSSID and records the local label and notes, review disposition, display name, saved time, signal snapshot, and risk score. Bluetooth profiles additionally contain the last successful GATT service list and any successfully readable, allowlisted standard values. Wi-Fi profiles additionally contain the SSID and reported security capability.

| Device type | Baseline fields | Comparison evidence |
| --- | --- | --- |
| Bluetooth | Address, device name, local label and notes, trusted/watch state, RSSI summary, risk score, successfully discovered services, successful safe values | Name, GATT service additions/removals, changed safe values, current-vs-baseline signal delta, risk delta, review-state change |
| Wi-Fi | BSSID, SSID, reported security capability, trusted/watch state, RSSI, risk score | SSID, security capability, current-vs-baseline signal delta, risk delta, review-state change |

## Meaningful comparison rules

A service is only described as added or missing when a new successful GATT discovery is available; no service is treated as missing because the inspector is closed or a GATT attempt failed. Safe values are only compared when the new standard-value inspection produced results. Signal movement is shown as a signed dBm difference and classified as stronger, weaker, or broadly stable within a five-dBm tolerance. Risk movement is a signed score difference. All baseline replacement is explicit through a Save / Update Profile action.

## Interface flow

Bluetooth shows a save or update action after a successful GATT discovery. Each refreshed discovery recalculates the visible comparison against the current saved baseline. Wi-Fi can save a baseline directly from its existing scan details, then display the comparison on later observations. A concise panel uses green for additions or strengthening and red for missing services, changes of concern, or weakening; neutral facts remain gray.

## Persistence format

A dedicated `DeviceProfileStore` uses JSON stored in application-private SharedPreferences. It is separate from incident history and avoids a Room schema change while retaining a versioned storage key for future migration.
