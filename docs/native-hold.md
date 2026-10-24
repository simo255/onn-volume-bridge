# Native volume hold: implementation and research

Checked 2026-10-24. A remote protocol that accepts keydown and keyup is a
candidate for TV-owned repeat timing; those messages alone do not establish
that a particular firmware reproduces its physical remote's volume behavior.

| Target | Evidence | Project status / next step |
|---|---|---|
| Samsung Tizen | Paired WebSocket Press / Release; physical onn test on Samsung with TizenOS | Implemented; core hardware check recorded in validation.md |
| Roku TV | Official ECP defines POST keydown/key and keyup/key and includes VolumeUp/VolumeDown | Implemented in v0.1.7; JVM and loopback HTTP tests, no physical Roku test |
| Sony with Android/Google TV | Android TV Remote v2 open-source schema defines START_LONG and END_LONG with volume key codes | Best next candidate: add an independently paired Android TV Remote target; validate actual TV speaker volume and repeat/release on hardware |
| Sony BRAVIA REST / IRCC-IP | Audio API changes volume; Sony documents IRCC volume keys, but no explicit release field was found in the reviewed IRCC guide | Keep progressive REST fallback; do not label IRCC native hold without evidence |
| Other Android/Google TVs (including compatible TCL, Hisense and Philips) | Same Android TV Remote v2 candidate if Android TV Remote Service is present | Pair with the television, not the onn box; OS/service availability and speaker/output routing need checking |
| Vizio SmartCast | Maintainer-authored local API implementation documents KEYDOWN, KEYUP and KEYPRESS | Promising native-hold candidate; requires a new paired/pinned local adapter and hardware checks |
| LG webOS | LG Connect SDK uses SSAP volumeUp/volumeDown; its pointer socket button method sends a single button message | A volume adapter is feasible; no verified volume keydown/keyup pair found in reviewed LG sources. Start with progressive fallback and investigate actual firmware before promising native hold |
| UPnP RenderingControl | Standard SetVolume sets a level rather than a held remote key | Retain progressive fallback; vendor-specific extensions must be treated separately |
| Philips jointSPACE / Panasonic VIERA | No universal native-hold contract established in this review | Prefer Android TV Remote when available; otherwise investigate the exact TV protocol before adding a hold adapter |

## Roku lifecycle

The existing TV-only probe remains mandatory: a Roku player is not accepted as
a Roku television. The app sends one keydown when the physical volume button goes
down and one keyup on release. Mute remains keypress. No repeated keydown traffic
is generated while the TV owns the hold.

The shared input reader still cancels on route loss, pause, disconnect or a
10-second lost-release limit. HTTP work is serialized and bounded. Ordinary
clicks cannot consume the queue slot reserved for release; release tasks do not
expire. Closing a target discards queued commands but attempts keyup after an
in-flight keydown, including a keydown whose response failed. Delivery cannot be
guaranteed across network loss or process death. Busy/non-native targets decline
native hold and use the existing repetition path.

Roku requires Control by mobile apps to be enabled for ECP key commands. Its
current documentation also states restrictions on ECP use from third-party
platforms. This implementation is experimental; protocol support is not a claim
of Roku endorsement, distribution approval or firmware-wide availability.

## Next adapter priority

1. Android TV Remote v2: local pairing, encrypted private credentials and a pinned
   TLS session to the TV; expose only volume/mute and long-start/long-end. Reject
   targeting the onn box itself. Check capability negotiation, route to TV speakers,
   key release on pause/close, and uncertain-delivery recovery.
2. Vizio SmartCast: explicit TV pairing and approved certificate pin, volume-only
   key actions with release cleanup; test physical TV behavior.
3. LG webOS: paired local SSAP volume adapter first; add native hold only after a
   real release mechanism is established. On-TV Luna APIs are not directly a LAN
   remote transport from an Android box.

For every adapter compare a short press and 1/2/4-second holds with the TV's own
remote, then check release, direction change, pause, headphones, standby and
network interruption. Do not infer compatibility solely from brand or HTTP 200.

## Sources

- [Roku ECP specification](https://developer.roku.com/dev/docs/external-control-api)
- [Sony IRCC-IP guide](https://pro-bravia.sony.net/remote-display-control/ircc-ip/)
- [Android TV Remote v2 project](https://github.com/tronikos/androidtvremote2)
- [Remote v2 message schema](https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/remotemessage.proto)
- [LG Connect SDK volume implementation](https://github.com/ConnectSDK/Connect-SDK-Android-Core/blob/master/src/com/connectsdk/service/WebOSTVService.java)
- [LG Connect SDK pointer button implementation](https://github.com/ConnectSDK/Connect-SDK-Android-Core/blob/master/src/com/connectsdk/service/webos/WebOSTVMouseSocketConnection.java)
- [LG on-TV audio API (not an Android LAN API)](https://webostv.developer.lge.com/develop/references/audio)
- [SmartCast implementation documentation](https://github.com/heathbar/vizio-smart-cast)
- [SmartCast local protocol research](https://github.com/exiva/Vizio_SmartCast_API)
- [UPnP RenderingControl specification](https://openconnectivity.org/wp-content/uploads/2015/11/UPnP-av-RenderingControl-v3-Service-20101231.pdf)
