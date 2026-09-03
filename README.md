# Not App

A flexible Android launcher app that turns your home screen icon into a customizable command center.

![Not App Android Mockup](icons/notApp-android.jpg)

## What is Not App?

Instead of cluttering your home screen with dozens of app shortcuts, **Not App** gives you one icon that does many things. Configure it once, tap it endlessly.

## How It Works

Choose your interaction style:

### LIST Mode (Default)
Tapping the app icon brings up your full shortcut list in a sleek bottom sheet. Long tap for direct OS shortcuts to individual items.

### DIRECT Mode
Tapping the app icon launches your primary shortcut instantly, no UI, no delay. Long tap for shortcuts to your backups and alternatives, managed by the OS.

### MIX Mode
Tapping the app icon launches your primary shortcut instantly, same as DIRECT — but your full shortcut list also opens on top of it, so the rest of your items are always one tap away. Long tap still works too, managed by the OS.

In LIST and MIX mode, swiping the list away collapses it into a small floating button instead of closing outright — drag it wherever's convenient and it stays on screen (even over other apps, with the "draw over other apps" permission) until you tap it to bring the list back. Drag it onto the trash target that appears mid-drag to dismiss it for good — that also flips off the "show floating button" setting, so it won't keep coming back on you. Can be turned off entirely in Settings if you'd rather swiping just close the list, like before.

## Features

- **Unlimited shortcuts**: App launches, URLs, custom intents — configure as many as you need
- **Three interaction modes**: LIST for exploration, DIRECT for speed, MIX for both at once
- **Drag-to-reorder**: Arrange your shortcuts exactly how you want them
- **Customizable icons**: Emoji badges, colored labels, or the original app icons
- **Floating peek button**: an optional draggable bubble that keeps your list one tap away after you swipe it aside, in LIST and MIX mode — drag-to-remove built in
- **Recent apps row**: an optional row of your recently-used apps at the top of the list, for one-tap access without adding them as shortcuts
- **8 launcher icon styles**: pick how the app icon itself looks from Settings, independent of your shortcut icons
- **Pin to home screen**: pin any single shortcut as its own standalone home-screen icon
- **Smart sharing**: Share text directly to your shortcuts — use `{{word}}` placeholders in URLs to dynamically insert shared content
- **Import/export**: Backup and restore your entire config as JSON
- **OS integration**: Syncs with Android App Shortcuts for long-press menu support, reliably across all three modes

## Build

```
./gradlew assembleDebug
```

Requires Android SDK platform 36 + build-tools 36.1.0 (`local.properties` with `sdk.dir=...`, gitignored).

## License

MIT (see `LICENSE`).
