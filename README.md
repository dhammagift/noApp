# Not App

A flexible Android launcher app that turns your home screen icon into a customizable command center.

![Not App Android Mockup](icons/notApp-android.jpg)

## What is Not App?

Instead of cluttering your home screen with dozens of app shortcuts, **Not App** gives you one icon that does many things. Configure it once, tap it endlessly.

## How It Works

Choose your interaction style:

### LIST Mode (Default)
Tap the app icon to open — it brings up your full shortcut list in a sleek bottom sheet. Long tap for direct OS shortcuts to individual items.

### DIRECT Mode
Tap the app icon to open — it launches your primary shortcut instantly, no UI, no delay. Long tap for shortcuts to your backups and alternatives, managed by the OS.

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
