# Changelog

## [0.1.0-alpha] - 2026-06-13

### Added

- Initial alpha release.
- Android app built with Jetpack Compose and CameraX.
- Image-target workflow for selecting a target color from a photo.
- Live dual-point workflow for comparing target/current points in the camera view.
- Continuous live sampling, throttled to about 200 ms.
- Hex, RGB, Delta E, and visual-guidance color suggestions.
- Lab, HSV, HSI, and RGB suggestion modes.
- Concrete Android camera device selection.
- Exposure lock toggle for more stable on-site comparison.
- Chinese and English UI.
- Phone portrait/landscape layout tuning.
- Local-first processing: images and camera frames stay on device.

### Known Limitations

- Alpha quality; bugs and incomplete features are expected.
- Visual guidance only; not a calibrated measurement or compliance tool.
- Color suggestions are heuristic and not a strict pigment-mixing formula.
