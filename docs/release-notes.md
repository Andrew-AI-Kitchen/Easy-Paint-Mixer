# Release Notes

## 0.1.0-alpha

- Supports image-target and live dual-point color comparison workflows.
- Shows target/current color, Delta E distance, and visual-guidance color additions.
- Includes Chinese and English UI switching.
- Adds camera on/off, front/back camera selection, and basic camera presets.
- Samples live camera points continuously, throttled to about 200ms for field use.
- Allows choosing a concrete camera device from the phone's available camera list.
- Adds a separate exposure lock toggle for stable on-site comparison.
- Keeps images and camera frames local to the device.
- Includes phone portrait/landscape layout tuning and navigation-bar-safe spacing.

Known limits:

- Suggestions are visual guidance based on color distance, not a strict pigment-mixing formula.
- Phone camera and screen output are not calibrated measurement instruments.
