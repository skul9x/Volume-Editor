# 🏗️ Project Structure & Architecture

**VolumeEditor** follows a standard Android App architecture using Kotlin and XML Views. It is designed to be lightweight, efficient, and robust for automotive environments.

## 📂 Directory Tree

```
VolumeEditor/
├── app/
│   ├── src/main/
│   │   ├── java/com/volumeeditor/
│   │   │   ├── MainActivity.kt        # [Core] Main UI & Logic
│   │   │   └── FloatingService.kt     # [Service] Foreground Service for Widget
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml       # [UI] Landscape Main Interface
│   │   │   │   ├── floating_button.xml     # [UI] Circle Floating Widget
│   │   │   │   └── floating_expanded.xml   # [UI] Popup Slider Panel
│   │   │   ├── drawable/
│   │   │   │   ├── bg_*.xml           # Background gradients & panels
│   │   │   │   ├── btn_*.xml          # Button selectors & effects
│   │   │   │   ├── ic_*.xml           # Vector Icons
│   │   │   │   └── seekbar_*.xml      # Custom Seekbar components
│   │   │   └── values/
│   │   │       ├── colors.xml         # Neon Palette definitions
│   │   │       ├── themes.xml         # App Theme (NoActionBar)
│   │   │       └── strings.xml
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

### 2. `FloatingService.kt`
- **Role**: A `Foreground Service` that keeps the floating widget alive on top of other apps.
- **Responsibilities**:
  - Draws the system overlay window (`SYSTEM_ALERT_WINDOW`).
  - Implements Gesture Detection (Single/Double Tap, Long Press).
  - Manages the `Handler` for timing tasks (preventing memory leaks).
  - Provides a "Mini Logic" version of the volume control for quick access.
  - Shows/Hides the `floating_expanded.xml` overlay.

### 3. XML Resources (UI)
- **Design System**: Atomic resource design.
  - **Colors**: Defined in `colors.xml` (Neon Cyan/Pink, Deep gradients).
  - **Drawables**: Reusable background shapes (`bg_glass_panel`) and button states (`btn_modern_primary`).
  - **Icons**: Vector assets for scalability.

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

## 🛡️ Security & Performance
- **Lifecycle Management**: `FloatingService` uses a `Handler` attached to `Looper.getMainLooper()` to manage delayed tasks, ensuring no context leaks occur on service destruction.
- **Permissions**: Explicitly requests Overlay and Notification permissions at runtime.
- **Threading**: All UI operations run on the Main Thread (lightweight). No background threads required.
