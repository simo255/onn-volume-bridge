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
- v0.1.6 delegates Samsung holds to native WebSocket `Press` / `Release`.
  A bounded 1.2-second volume-down protocol probe changed the real TV volume,
  and readback stabilized after release. The user then reported the physical
  onn remote test was successful with the native mode.
- Other targets retain a progressive fallback (150 ms initial delay, interval
  decreasing from 120 ms to 17 ms over 1.2 seconds). This is a send cadence,
  not a claim about the TV's actual volume rate.
- Deterministic tests cover native press/release, route loss and lost-release
  timeout, plus fallback acceleration, release/reset, stale callbacks and direction.



## Limits

This is a core button smoke test, not completion of the full acceptance checklist.
A full box reboot without a PC, playback in streaming apps, headphones, prolonged
network interruption and TV standby recovery still require explicit checks.
Roku, Sony and UPnP were not hardware tested here.

Build verification for v0.1.6: 40 JVM tests passed; release compilation succeeded;
Android lint reported zero errors. Tests and build do not establish other TV models'
compatibility. See [the acceptance checklist](compatibility.md).

## v0.1.7 Roku native hold (protocol only)

Roku keydown/keyup was added after the Samsung hardware check. Tests cover exact
volume paths, empty POST bodies on a real loopback HTTP server, short press and
direction ordering, release under queue pressure, release after an uncertain
keydown failure, cleanup after close, and declining unsupported/native-mute cases.
No Roku, Sony, LG or Vizio hardware was used for this change. The installed
Samsung baseline remains the physically tested v0.1.6.

Build verification for v0.1.7: 48 JVM tests passed, lint passed with zero errors,
and the release APK compiled successfully. No hardware compatibility is inferred
from those checks.

## v0.2.0 setup and release check

The English-first UI was installed on the existing French-language onn while
preserving the signing identity and saved TV pairing. Real SSDP discovery listed
the Samsung/TizenOS candidate. The automatic remote action advanced to the test
screen after verifying local authentication, accessibility and fixed HDMI volume;
the enabled accessibility-service list was unchanged. The user confirmed volume
up/down and holding still worked. Navigation screens were inspected on the TV.

This was an update of an already paired and authorized installation, not a fresh
factory-state enrollment. First-use debugging prompts, refused settings, other
firmwares and non-Samsung hardware still need community validation. The public
preview is signed with the same identity as the early local installation and
includes checksums; private signing material and device captures are not published.

Build verification: 57 JVM tests passed, lint passed with zero errors, and the
release APK compiled. Tests include discovery filtering and injection-resistant
accessibility-list merging that preserves other services. The full acceptance
limits above still apply.

## v0.2.1 setup usability and pairing fix

Buttons now use amber focus/hover states, language selection is at the top of the
app, and the TV step starts with discovery instead of a required system/address
form. Manual setup remains available; unknown discovery results require an
explicit system choice. Sony credentials and UPnP endpoints remain manual.

An enabled accessibility service previously closed an explicit TV setup connection
when forwarding was paused. Setup now retains that connection without enabling
forwarding, and releases it when setup ends or the user explicitly disconnects.
On the existing authorized onn/Samsung installation, saving while paused reached
Samsung approval, connected, and advanced to the remote step. This was checked
after installing the fix with matching-signature update; app data was retained.

The four documentation screenshots show only the app UI on that already paired
installation. They exclude addresses, certificates, tokens, account information
and device identifiers. They are navigation examples, not evidence of fresh-device
enrollment or completion of the full hardware acceptance checklist.

Release checks: 57 JVM tests passed; release lint completed with zero errors and
five warnings; the signed APK was built and its certificate checked against the
previous public preview. No broader hardware support is inferred.

## v0.2.2 preview: generic problem reports

This preview adds predefined error explanations, a generic report preview,
TV-local clipboard copy, a plain-text QR for Reddit/other apps, and a QR for a
prefilled GitHub issue draft. QR encoding runs locally. No report is automatically
uploaded or posted. Exact model IDs, device names, addresses, credentials, raw
exception messages, response bodies and device logs are excluded by construction.
Users may voluntarily add exact models when editing a report on their phone.

Reports include the app version, Android API level, selected generic TV system,
setup step, connection/permission/audio checks, the last safe failure per component
and up to six in-memory technical events with relative ages. Failure categories
and operations are enum values; only bounded HTTP status numbers are retained.
This diagnostic context is cleared when the process restarts.

Validation: 69 JVM tests passed; release lint completed with zero errors and five
warnings; the release APK built with the QR library license bundled. Tests cover
private text in exception messages and nested errors, certificate categorization,
bounded/deduplicated event history, HTTP denial reporting, draft URL encoding and
QR decode round trips. Both QR types were also decoded from emulator screenshots
and verified to carry the same reviewed report.

In a fresh, isolated Android emulator, a loopback fake Roku endpoint returned HTTP
403 with a private marker in its response. The app report retained
`TV/PROBE/HTTP_DENIED/403`, while excluding the fake endpoint and private marker.
Report preview, clipboard action and phone-sharing controls were inspected there.
No GitHub issue or Reddit comment was submitted. No installed physical TV was
changed for this source work; real-phone scanning and physical onn acceptance of
this new UI remain unverified. The v0.2.2 APK includes this feature; the v0.2.1 APK does not.
