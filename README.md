# No App

A minimal Android "smart shortcut container". No App is itself just a launcher shim:
configure up to 4 slots (App / URL / Intent / Custom), then trigger them via the
system App Shortcuts menu (long-press the icon) or a quick-pick bottom sheet on a
normal tap. Also works as a share target — receive shared text into a URL/Intent
slot via a `{{word}}` placeholder, or forward it natively to an app that accepts
shared text.

![No App Android Mockup](icons/noApp%20android.jpg)

## Build

```
./gradlew assembleDebug
```

Requires an Android SDK with platform 36 + build-tools 36.1.0 (`local.properties`
with `sdk.dir=...`, gitignored).

## Features

- 4 configurable shortcut slots: launch an app, open a URL, fire an arbitrary
  Intent, or a custom Intent-URI action.
- Dynamic App Shortcuts (`ShortcutManagerCompat`) synced on every save.
- Bottom-sheet quick picker on a direct tap.
- Share-target support: `wikipedia.org/wiki/{{word}}`-style URL templates, or
  forwarding shared text to another app via `ACTION_SEND`.
- Export/import the 4-slot config as a JSON file.

## License

MIT (see `LICENSE`, add one if publishing).
