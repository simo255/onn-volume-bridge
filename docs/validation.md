# Scoped hardware check

Date: 2026-10-24. The report deliberately identifies the TV only as **Samsung with
TizenOS**. No exact TV model, address, serial, account, credential or device dump
is included.

## Independent-app core check

- App package: `io.github.simo255.onnvolumebridge`.
- Tested environment: onn Streaming Device 4K Pro, Android TV 12; Samsung/TizenOS.
- The previous relay's accessibility service was removed from the enabled list;
  only the independent app's volume service was bound during the test.
- Android authenticated the new app's own loopback ADB identity.
- The Samsung authorized the independent app and its saved pairing survived an update.
- Physical short volume presses changed Samsung volume; the onn HDMI output stayed
  fixed at 25/25. Mute and normal arrows/OK were reported working.
- In v0.1.1, the user confirmed a held +/− advances TV volume and stops on release;
  short press and mute were confirmed in the same check.
- A temporary read-only observation of the TV's actual volume recorded increases
  and decreases while those physical buttons were tested.
- v0.1.2 reduces the repeat interval from 150 to 75 ms and the initial hold delay
  from 400 to 250 ms. Deterministic tests verify cadence, cancellation, pause/route
  guards, stale callbacks, direction changes and the lost-release safety cap.

## Limits

This is a core button smoke test, not completion of the full acceptance checklist.
A full box reboot without a PC, playback in streaming apps, headphones, prolonged
network interruption and TV standby recovery still require explicit checks.
Roku, Sony and UPnP were not hardware tested here.

Build verification for v0.1.2: 37 JVM tests passed; release compilation succeeded;
Android lint reported zero errors. Tests and build do not establish other TV models'
compatibility. See [the acceptance checklist](compatibility.md).
