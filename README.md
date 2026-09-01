# No App

A flexible Android launcher app that turns your home screen icon into a customizable command center.

![No App Android Mockup](icons/noApp%20android.jpg)

## What is No App?

Instead of cluttering your home screen with dozens of app shortcuts, **No App** gives you one icon that does many things. Configure it once, tap it endlessly.

## How It Works

Choose your interaction style:

### LIST Mode (Default)
Tap the icon → see all your shortcuts in a sleek bottom sheet menu. Perfect for frequently-used apps, websites, or quick actions without OS limits.

### DIRECT Mode
Tap the icon → launches your primary shortcut instantly. No UI, no delay. Long-press the icon for quick access to backups and alternatives — all managed by the OS.

## Features

- **Unlimited shortcuts**: App launches, URLs, custom intents — configure as many as you need
- **Two interaction modes**: LIST for exploration, DIRECT for speed
- **Drag-to-reorder**: Arrange your shortcuts exactly how you want them
- **Customizable icons**: Emoji badges, colored labels, or the original app icons
- **Smart sharing**: Share text directly to your shortcuts — use `{{word}}` placeholders in URLs to dynamically insert shared content
- **Import/export**: Backup and restore your entire config as JSON
- **OS integration**: Syncs with Android App Shortcuts for long-press menu support

## Build

```
./gradlew assembleDebug
```

Requires Android SDK platform 36 + build-tools 36.1.0 (`local.properties` with `sdk.dir=...`, gitignored).

## License

MIT (see `LICENSE`).
