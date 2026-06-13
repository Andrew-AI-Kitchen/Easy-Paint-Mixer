# Easy-Paint-Mixer

English | [简体中文](README.zh-CN.md)

An open-source Android field assistant for comparing a target color with a live paint mix.

![Platform](https://img.shields.io/badge/platform-Android-brightgreen)
![Version](https://img.shields.io/badge/version-0.1.0--alpha-blue)
![License](https://img.shields.io/badge/license-MIT-green)

---

## Why

Color matching in the field is often more practical than perfect. A phone camera is not a calibrated lab instrument, but it can still help you compare:

- a color picked from a reference photo
- a target point in a live camera view
- the current color of a paint mix, sample, or surface

Easy-Paint-Mixer focuses on fast visual comparison and repeatable sampling points. It is meant for artists, makers, students, and anyone who wants a lightweight open-source alternative to closed color-matching helper apps.

## Features

- **Image target workflow** — pick a target color from a photo, then compare it with the live camera sample.
- **Live dual-point workflow** — place one point on the target and one point on the current mix in the camera view.
- **Continuous live sampling** — selected camera points update about every 200 ms.
- **Color readouts** — Hex, RGB, and Delta E distance.
- **Visual guidance suggestions** — ranked color directions in Lab, HSV, HSI, or RGB mode.
- **Camera controls** — camera on/off, concrete camera device selection, exposure lock toggle, and basic presets.
- **Chinese and English UI** — switch between English and Simplified Chinese.
- **Local-first privacy** — images and camera frames stay on the device.
- **Phone layout support** — portrait/landscape tuning and navigation-bar-safe spacing.

## Important Disclaimer

Phone cameras and screens are not calibrated measurement instruments.

Easy-Paint-Mixer provides **visual guidance only**. It does not certify paint, textile, reagent, automotive, lab, or industrial color compliance. Color suggestions are based on color-distance heuristics, not a strict pigment-mixing formula.

## Download

Download the latest APK from the [Releases](https://github.com/Andrew-AI-Kitchen/Easy-Paint-Mixer/releases) page.

| Build | File | Use Case |
|---|---|---|
| Alpha | `Easy-Paint-Mixer-0.1.0-alpha.apk` | Real-device installation via file manager |

## Android Usage

1. Download the APK from Releases.
2. Open the APK on your Android device and tap "Install".
3. Open Easy-Paint-Mixer.
4. Choose either **Image target** or **Live dual point**.
5. Place the target/current sampling points and compare the color distance.

## Build from Source

Requirements:

- Android Studio or Android SDK command-line tools
- JDK 17
- Android SDK 36

```bash
cd android
./gradlew assembleDebug
./gradlew assembleAlpha
```

APK output:

```text
android/app/build/outputs/apk/alpha/app-alpha.apk
```

## Project Scope

Easy-Paint-Mixer is a lightweight visual assistant. It focuses on:

- live color sampling
- side-by-side target/current comparison
- simple visual guidance for possible color additions
- open-source, local-first operation

It does **not**:

- replace calibrated colorimeters or spectrophotometers
- guarantee industrial color compliance
- provide a strict pigment-mixing model yet
- upload camera frames or sample images

## Known Limitations

- Alpha quality: bugs and incomplete features are expected.
- Color output depends on camera sensor, exposure, white balance, screen, and lighting.
- Exposure lock helps stabilize live comparison, but it is not full device calibration.
- Suggestions are heuristic visual guidance, not final mixing instructions.
- Camera labels use Android camera IDs, which can vary by device.

## Roadmap

- [ ] Optional gray-card or white-card calibration workflow
- [ ] Better camera lens naming and device capability display
- [ ] User-defined paint/pigment library
- [ ] Mixing history and project notes
- [ ] Exportable comparison records
- [ ] F-Droid metadata and reproducible open-source release workflow

## License

[MIT](LICENSE)
