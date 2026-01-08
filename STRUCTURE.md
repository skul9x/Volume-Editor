# 🏗️ Project Structure & Architecture

**VolumeEditor** follows a standard Android App architecture using Kotlin and XML Views. It is designed to be lightweight, efficient, and robust for automotive environments.

## 📂 Directory Tree

```
VolumeEditor/
├── app/
│   ├── src/main/
│   │   ├── java/com/volumeeditor/
│   │   │   ├── MainActivity.kt          # [Core] Main UI & Logic
│   │   │   ├── SettingsActivity.kt      # [Settings] Configuration UI
│   │   │   ├── FloatingService.kt       # [Service] Foreground Service for Widget
│   │   │   └── SpeedVolumeService.kt    # [Service] GPS-based SDV Feature
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml       # [UI] Landscape Main Interface
│   │   │   │   ├── activity_settings.xml   # [UI] Settings Configuration
│   │   │   │   ├── floating_button.xml     # [UI] Circle Floating Widget
│   │   │   │   └── floating_expanded.xml   # [UI] Popup Slider Panel
│   │   │   ├── drawable/
│   │   │   │   ├── bg_*.xml           # Background gradients & panels
│   │   │   │   ├── btn_*.xml          # Button selectors & effects
│   │   │   │   ├── ic_*.xml           # Vector Icons (volume, speed, back...)
│   │   │   │   └── seekbar_*.xml      # Custom Seekbar components
│   │   │   └── values/
│   │   │       ├── colors.xml         # Neon Palette definitions
│   │   │       ├── themes.xml         # App Theme (NoActionBar)
│   │   │       └── strings.xml        # All text resources
│   │   └── AndroidManifest.xml        # Permission & Component declaration
│   └── build.gradle.kts               # App dependencies
├── build.gradle.kts                   # Project configuration
└── settings.gradle.kts
```

## 🧩 Key Components

### 1. `MainActivity.kt`
- **Role**: The primary entry point and configuration screen.
- **Responsibilities**:
  - Initializes `AudioManager`.
  - Manages the 100-step Seekbar linkage to system volume.
  - Implements the Logarithmic conversion algorithm (`percentToSystem` / `systemToPercent`).
  - Handles UI updates for the "Automotive Cyber-Glass" interface.
  - Controls the `FloatingService` (Start/Stop).

### 2. `SettingsActivity.kt`
- **Role**: Configuration screen for all app settings.
- **Responsibilities**:
  - Audio Curve Profile selection (Linear/Balanced/Deep).
  - Quick Panel Timeout setting (3s/5s/10s).
  - Widget Opacity adjustment (20%-100%).
  - **SDV Toggle & Sensitivity** (Low/Mid/High).
  - Handles Location permission request for SDV.
  - Controls `SpeedVolumeService` (Start/Stop).

### 3. `FloatingService.kt`
- **Role**: A `Foreground Service` that keeps the floating widget alive on top of other apps.
- **Responsibilities**:
  - Draws the system overlay window (`SYSTEM_ALERT_WINDOW`).
  - Implements Gesture Detection (Single/Double Tap, Long Press).
  - Manages the `Handler` for timing tasks (preventing memory leaks).
  - Provides a "Mini Logic" version of the volume control for quick access.
  - Shows/Hides the `floating_expanded.xml` overlay.

### 4. `SpeedVolumeService.kt` ⭐ NEW
- **Role**: A `Foreground Service` for Speed-Dependent Volume (SDV) feature.
- **Responsibilities**:
  - Listens to GPS location updates via `LocationManager`.
  - Extracts vehicle speed from `Location.getSpeed()`.
  - Calculates volume boost based on speed and sensitivity level.
  - Automatically adjusts `AudioManager` volume.
  - Resets volume to base level when service stops.
- **Sensitivity Levels**:
  | Level | Speed per +5% | Max Boost |
  |-------|---------------|-----------|
  | Low   | 30 km/h       | 20%       |
  | Mid   | 20 km/h       | 20%       |
  | High  | 10 km/h       | 20%       |

### 5. XML Resources (UI)
- **Design System**: Atomic resource design.
  - **Colors**: Defined in `colors.xml` (Neon Cyan/Pink, Deep gradients).
  - **Drawables**: Reusable background shapes (`bg_glass_panel`) and button states (`btn_modern_primary`).
  - **Icons**: Vector assets for scalability (`ic_volume_on`, `ic_speed`, `ic_back`...).

## 📐 Data Flow (Volume Control)

```
[Slider Input (0-100%)] 
       ⬇
[Algorithm: x^2.0]  <-- Logarithmic Curve
       ⬇
[System Volume (0-15)]
       ⬇
[AudioManager] --> [Hardware Output]
```

## � Data Flow (Speed-Dependent Volume)

```
[GPS Location Update]
       ⬇
[Extract Speed (m/s → km/h)]
       ⬇
[Calculate Boost % based on Sensitivity]
       ⬇
[Base Volume + Boost] --> Capped at 100%
       ⬇
[AudioManager] --> [Hardware Output]
```

## �🛡️ Security & Performance
- **Lifecycle Management**: Both services use a `Handler` attached to `Looper.getMainLooper()` to manage delayed tasks, ensuring no context leaks occur on service destruction.
- **Permissions**: Explicitly requests Overlay, Notification, and Location permissions at runtime.
- **Threading**: All UI operations run on the Main Thread (lightweight). GPS callbacks are delivered on the Main Thread by default.
- **Battery**: GPS updates are throttled (every 2 seconds, every 5 meters) to minimize battery impact on Android Boxes with continuous power.

## 📋 Permissions Required

| Permission | Purpose |
|------------|---------|
| `SYSTEM_ALERT_WINDOW` | Display floating widget overlay |
| `FOREGROUND_SERVICE` | Keep services running in background |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Floating widget service type |
| `FOREGROUND_SERVICE_LOCATION` | SDV service with GPS |
| `ACCESS_FINE_LOCATION` | Get precise GPS speed for SDV |
| `ACCESS_COARSE_LOCATION` | Fallback location for SDV |
| `POST_NOTIFICATIONS` | Show control notifications |
