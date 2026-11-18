# Bilibili Local History (LSPosed Module)

An LSPosed (API 102) module for Android Bilibili (`tv.danmaku.bili`) that provides device-local watch history storage and playback resume when cloud history synchronization is disabled.

## Capabilities

- **Cloud History Isolation**: Intercepts and suppresses watch progress uploads across `PlayerHistoryCloudSyncHelper`, `theseus2`, legacy `theseus`, and `BiliCall` whenever cloud history is turned off, while recording watched items locally.
- **Mutually Exclusive Settings Controls**: Extends the three-dot History Settings menu inside Bilibili (`HistoryMoreDialog` and `ListThreePointMenuDialogFragment`) with mutually exclusive controls for Cloud History, Local Device Cloud Filter, and Local History. Cloud history and local history cannot be enabled simultaneously, while both can remain turned off together.
- **Native History UI Integration**: Serves paginated cursor lists, keyword search, item deletion, and clear operations from a private SQLite database (`bili_local_history.db`) through `HistoryMoss` gRPC hooks, hiding the closed-history warning banner and triggering live list refreshes on toggle.
- **Local Playback Resume**: Synthesizes local resume timestamps on `KPlayViewUniteReply.getHistory` while bypassing cloud and KVDB resume caches when cloud history is closed.
- **Playback Speed Unlocking & Long-Press Customization**: Unlocks built-in playback speed presets (`0.25X`, `0.5X`, `0.75X`, `1.0X`, `1.25X`, `1.5X`, `2.0X`, `2.5X`, `3.0X`, `4.0X`), removes the high-framerate speed restriction on 50fps+ videos, adds one-time custom playback speed adjustment (`0.1X ~ 8.0X`), and embeds persistent custom long-press speed configuration (`0.1X ~ 8.0X` with quick presets and manual input) inside video player speed panels across both `theseus` and `theseus2`.

## Architecture

```text
app/src/main/java/pochita/
├── xposed/                     # LSPosed API 102 entrypoint, hook DSL, reflection utilities, coroutine probe
├── config/                     # Preference constants and host SharedPreferences accessors
├── bilibili/
│   ├── contract/               # Host package/class descriptors and localized UI strings
│   ├── proto/                  # HistoryMoss protobuf reply and resume payload builders
│   └── ui/                     # History settings dialogs, speed panel views/controllers, and Compose interop bridge
├── speed/
│   ├── hook/                   # Playback speed hook registration and host option list interception
│   └── SpeedSpec.kt            # Normalization, formatting, preset definitions, and slider mapping
└── history/
    ├── data/                   # Record model, HistoryStore interface, SQL spec, SQLite store
    ├── domain/                 # Formatters, MetadataCache, PlaybackHistoryRecorder, HistorySwitchCoordinator
    ├── hook/                   # Feature hook installers (cloud isolation, capture, resume, moss, UI)
    ├── HistoryModuleContext.kt # Shared host Context, store, preferences, coordinator, and recorder
    └── BiliLocalHistoryModule.kt
```

## Build & Test

Install toolchain dependencies with [mise](https://mise.jdx.dev/), then run:

```bash
mise install
mise run debug            # Build debug APK (alias for build:debug)
mise run release          # Build release APK (aliases: build, build:release)
mise run install          # Build and install debug APK (alias for install:debug)
mise run install:release  # Build and install release APK
mise run bili:restart     # Force-stop and relaunch Bilibili on connected device
mise run test             # Run JVM unit tests (./gradlew testDebugUnitTest)
mise run check            # Run unit tests and release lint verification
mise run clean            # Clean Gradle outputs
```

## Target Compatibility

- **Target Package**: `tv.danmaku.bili` (verified against v9.18.0 `9180300`)
- **Xposed API**: `io.github.libxposed:api:102.0.0` (`autoHotReload=true`, `staticScope=true`)
- **Android SDK**: `minSdk = 26`, `compileSdk = 37`, `targetSdk = 37`
