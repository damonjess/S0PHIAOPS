# S0PHIA OPS

An Android network security scanning application built with Kotlin, Jetpack Compose, and on-device AI.

## Features

- **Wi-Fi Network Scanning**: Detect nearby Wi-Fi networks, assess security protocols, and calculate risk scores
- **Bluetooth Device Discovery**: Scan for Bluetooth devices, track signal history, and identify recurring devices
- **Radar Visualization**: Real-time radar-style display of detected networks and devices
- **On-Device AI**: Local LLM (Gemma 2B) for threat assessment chat — no cloud calls
- **Risk Scoring**: Rule-based risk engine for Wi-Fi and Bluetooth devices
- **Scan History**: Persistent Room database of all scan sessions
- **Local Alerts**: In-device notifications for high-severity threats
- **Device Management**: Mark devices as trusted, watchlisted, or ignored

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material3
- **Database**: Room (SQLite)
- **AI**: MediaPipe Tasks GenAI (Gemma 2B IT, int4 quantized)
- **Min SDK**: 29 (Android 10)
- **Target SDK**: 36

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build (with R8 minification)
./gradlew assembleRelease

# Run unit tests
./gradlew test
```

## Downloading the AI Model

The app requires a MediaPipe Gemma 2B model file (`model.task`). Use the included script:

```bash
./download_gemma3.sh
# Then push to device:
adb push model.task /data/data/com.sophia.ops/files/model.task
```

Or download directly from the app's Settings screen.

## Project Structure

```
app/src/main/java/com/sophia/ops/
├── ai/            # AI agent and assessment models
├── alerts/        # Local notification system
├── bluetooth/     # Bluetooth scanning and risk engine
├── data/          # Room database, DAOs, entities, OUI lookup
├── model/         # NetworkDevice and DeviceType models
├── navigation/    # Compose Navigation graph
├── services/      # Foreground services
├── ui/            # Compose screens (dashboard, radar, devices, etc.)
├── utils/         # Shared utilities (DeviceDisplayName, etc.)
├── viewmodel/     # ViewModels (Dashboard, Devices)
└── wifi/          # Wi-Fi scanning and risk engine
```

## License

This project is provided as-is without warranty. See [LICENSE](LICENSE).
