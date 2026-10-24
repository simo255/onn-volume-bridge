# Setup and recovery

## Before installing

Check [compatibility](compatibility.md). Prefer the TV's normal infrared/CEC setup
when it works. This workaround is useful when those paths fail for volume and the
TV exposes a supported local network control API.

Build with JDK 17 and SDK 34, then sideload your own APK. The package is
`io.github.simo255.onnvolumebridge`. It is independent of CleanYourTV and any launcher.
Do not run two volume relays at the same time; both may send the same press.

## TV-specific preparation

- **Samsung Tizen**: TV on the same LAN. Select Samsung, enter its private IPv4
  address and save. Approve initial certificate trust in the app, then approve
  **onn Volume** on the TV. Later connections require that exact certificate.
- **Roku TV**: enable Control by mobile apps in the TV's advanced system settings.
  Use port 8060. Only Roku TVs are accepted; arbitrary streaming players are not.
- **Sony BRAVIA**: use a model exposing the Audio JSON-RPC API. Enable its IP-control
  settings, configure a pre-shared key if required, and enter that key in the app.
  Port 80 is the default. Models/menus differ and need verification.
- **UPnP**: explicitly select a compatible RenderingControl endpoint, port and path.
  Only a 0–100 volume scale is assumed. A successful read is not proof of writes;
  verify using the app's test buttons. This mode is experimental.

## onn configuration

1. Turn on USB/ADB debugging in the box's developer settings. Some firmware exposes
   the listener on the LAN as well; use a trusted owner-controlled network.
2. Save the TV settings in onn Volume Bridge. Explicitly enable relaying.
3. Test volume up/down/mute from the app, then enable its accessibility service.
4. Android prompts to authorize a new local debugging identity. Tick Always allow
   and authorize. The identity belongs to this app on the box, not a remote PC.
5. In remote-button settings choose Volume control → Automatic (CEC). The app's
   shortcut opens the official Google TV settings panel when available.
6. Test the actual remote, while ensuring the box's HDMI audio level stays fixed.

Other accessibility services remain enabled. Window-content retrieval is disabled.
The app does not read text from the screen. Mute uses a key filter, and normal
navigation is left to Android.

## Optional desktop setup

`tools/configure.py --help` documents its arguments. It only configures an already
installed app; it does not install packages or silently grant accessibility/debugging.
The app UI is preferred for passwords and certificate approval.

```sh
python tools/configure.py --adb <adb executable> --device <box endpoint> --provider roku --host 192.168.1.50
```

`192.168.1.50` is an example, not a saved device address. Sony PSKs can optionally be
read from an environment variable; they are never printed, but the UI avoids
passing them through ADB command arguments. Samsung desktop setup requires an
explicit certificate pin or explicit first-use trust flag.

## Reconnect and disable

Use **Reconnect TV and button reader** after changing debugging, authorizations,
network or remote pairing. If the input interface is unknown, enter its exact
name in the advanced field; do not guess event numbers. Setup changes restart
input discovery automatically.

If the TV address or certificate changes, use **Forget TV pairing**, save the new
configuration and approve pairing again. This clears TV credentials and pauses
the relay, while preserving the separate local ADB identity. Reboot behavior depends on when the firmware makes ADB and the
remote available; it is not universally hardware-verified.

To stop the bridge, turn off Enable volume relay and disable this accessibility
service. No system package was removed or disabled. Restore the previous remote
volume mode if you changed it. You can then turn debugging off; doing so also
stops loopback button reading. Do not revoke all ADB authorizations unless you
intend to revoke other authorized tools as well.

If app test buttons work but physical +/− do not, report the remote model/interface
through a sanitized compatibility issue. If both fail, check the TV's protocol,
LAN reachability, pairing and local-control setting first.
