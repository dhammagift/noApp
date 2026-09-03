# Privacy Policy — Not App

Not App does not collect, transmit, or share any personal data.

- The shortcut slots you configure (labels, URLs, intents, colors) are stored
  only in this app's local storage on your device, via Android SharedPreferences.
- Nothing is sent to any server operated by the developer or a third party.
- Using "Export config" writes that same local configuration to a file you
  choose, on your device only. Nothing leaves your device unless you share
  that file yourself.
- The optional "recent apps" row reads your recently-used apps on-device via
  Android's Usage access, purely to display that row. This data is never
  stored beyond what's needed to render the row, and is never transmitted
  anywhere.
- Not App requests the following permissions:
  - Permission to list installed apps for the app-picker (a non-runtime,
    non-sensitive query).
  - Permission to display over other apps (SYSTEM_ALERT_WINDOW) — optional,
    used for two things: flashing a Configure gear over the app Direct mode
    launches (instead of holding you on a blank screen or reserving a
    shortcut slot), and showing the floating button that keeps your list one
    tap away in List/Mix mode after you swipe it aside. Neither turns on
    without you explicitly granting this permission.
  - Usage access (PACKAGE_USAGE_STATS) — optional, only requested if you turn
    on "Show recent apps at top of list" in Settings; used solely to read
    which apps you've opened recently, on-device, to populate that row.

_Last updated: 2026-09-03._
