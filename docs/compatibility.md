# Compatibility and validation

## Evidence levels

- **Original hardware baseline**: actual physical remote and television behavior
  were checked in the precursor fix.
- **Protocol tested**: deterministic tests verify requests, parsing and failures.
  This does not establish TV firmware compatibility, LAN reachability or pairing.
- **Independent-app hardware verified**: requires an acceptance run of this package,
  including restart, headphones and other apps. No such report is recorded yet.

| Component | Current evidence | Remaining verification |
|---|---|---|
| onn 4K Pro / Android TV 12, firmware SGZ3.231226.086.A1.12555768 | Physical volume on Onn-Remote Keyboard; mute/navigation on Consumer Control in original fix | New independent package, reboot/reconnect, broader remote revisions |
| Samsung / TizenOS | Original fix: physical +/− and mute changed TV only; arrows/OK worked | Independent package setup and encrypted-token migration-free enrollment |
| Other Samsung Tizen TVs | Implemented pinned WSS remote protocol | Models, pairing, certificate lifetime, output routes |
| Roku TV | API request tests plus actual loopback HTTP transport tests | Physical Roku TVs, Roku OS versions and local-control settings |
| Sony BRAVIA Audio API | Relative speaker volume, PSK header, mute state and API-error tests | Real compatible models and PSK/IP-control settings |
| UPnP RenderingControl | Get/SetVolume, clamp, Get/SetMute and write-failure tests | Explicit endpoint, 0–100 scale and writable audio control on real TVs |
| LG webOS SSAP | Not implemented | Adapter, pairing, protocol tests and hardware |
| IR-only TVs / arbitrary Android TVs / receivers | Not supported by default | A supported target API or another transport is necessary |

Roku is selected by TV operating system, not brand. A Roku player returning an HTTP
success for a key is not treated as proof of TV volume control; this adapter
requires `is-tv=true`.

Sony targets the TV `speaker` output. Receivers and HDMI sound-system routing may
need a distinct adapter/target selection. UPnP read access does not prove write
access: the original Samsung accepted GetVolume but rejected SetVolume, so it
uses the Samsung WebSocket adapter instead.

## Acceptance checklist for community reports

1. Record onn model, Android version, firmware, TV model/OS and app version.
2. Confirm the app's +/− and mute test buttons work on the actual TV.
3. Confirm the physical onn +/− change **only TV volume**, with the box level fixed.
4. Confirm physical mute, arrows, OK, Back, Home and power retain their behavior.
5. Check while another app is open and playing media.
6. Restart the box and check reconnect without leaving a PC attached.
7. Check a held volume button, a temporary network interruption and TV standby.
8. Connect headphones: the bridge must not send those volume presses to the TV.
9. Pause/disable the bridge and confirm that it stops issuing network commands.

No screenshots with account details, city names, IP addresses or device identifiers.
No raw logs/backups/keys. Use the compatibility issue template for a sanitized report.
