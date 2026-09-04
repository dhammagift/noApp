# Privacy Policy — Not App

Not App does not collect, transmit, or share any personal data.

## Our philosophy

Not App has no analytics, no ad SDKs, and no telemetry of any kind that runs
on its own — nothing in the app phones home automatically, ever. We don't
know how you use the app, which shortcuts you've configured, or that you're
using it at all. There's no account, no sign-in, and nothing tying the app
to your identity. That's a deliberate choice, not just an oversight: the
whole point of Not App is to be a small, private tool that answers only to
you.

- The shortcut slots you configure (labels, URLs, intents, colors) are stored
  only in this app's local storage on your device, via Android SharedPreferences.
- Nothing is sent to any server operated by the developer or a third party.
- Using "Export config" writes that same local configuration to a file you
  choose, on your device only. Nothing leaves your device unless you share
  that file yourself.
- Not App keeps a local debug log (what it did and when) and, if it ever
  crashes, the crash details — both written only to this app's own storage
  on your device. Neither is ever sent anywhere automatically. The only way
  either leaves your device is if you deliberately choose to send it, via
  Settings > Debug log or the "Email crash report" button shown after a
  crash — both require you to tap Share/Email yourself and pick where it
  goes (including, optionally, emailing it to us to help fix a bug).
- Not App requests the following permissions:
  - Permission to list installed apps for the app-picker (a non-runtime,
    non-sensitive query).
  - Permission to display over other apps (SYSTEM_ALERT_WINDOW) — **optional**,
    used purely to improve the UX: flashing a Configure gear over the app
    Direct mode launches (instead of holding you on a blank screen or
    reserving a shortcut slot), and showing the floating button that keeps
    your list one tap away in List/Mix mode after you swipe it aside. Not
    App works without it — you just lose those two conveniences — and
    nothing about how it's used is ever transmitted anywhere.
  - Usage access (PACKAGE_USAGE_STATS) — **optional**, only requested if you
    turn on "Show recent apps at top of list" in Settings, purely to improve
    the UX by populating that row. Not App works without it — you just don't
    get the recent-apps row — and the data it reads never leaves your device
    or gets transmitted anywhere.

## What this policy doesn't cover

This policy describes Not App's own code — it has no control over, and this
policy makes no claims about, data collected by Google Play (if that's how
you installed it) or by Android itself (e.g. system diagnostics, install
attribution). Those are governed by Google's own privacy policy, not ours.

If you installed the APK directly (not through Google Play) — e.g. from
GitHub Releases — none of that applies either: there's no Play Store layer
in the picture at all, so as far as we're concerned you're using Not App
completely anonymously. If you installed it via Google Play, Google's own
services (Play Store, Google Play Services running on your device) may
collect their own data about the install and your device, same as for any
other app on Play — that's between you and Google, and this policy has no
visibility into or control over it.

_Last updated: 2026-09-04._
