# Install and set up

## Download

Download [onn-volume-bridge-v0.2.2.apk](https://github.com/simo255/onn-volume-bridge/releases/download/v0.2.2/onn-volume-bridge-v0.2.2.apk)
from the [v0.2.2 preview release](https://github.com/simo255/onn-volume-bridge/releases/tag/v0.2.2).
The release includes SHA-256 checksums and the signing certificate fingerprint.
Android TV 8.0 or newer is required. This is an early public preview; see
[compatibility](compatibility.md) before installing.

For a remote-only installation:

1. Install [Downloader by AFTVnews](https://play.google.com/store/apps/details?id=com.esaba.downloader) from Google Play on the onn.
2. Enter **`3586297`** and select **Go**. This code points to the official **v0.2.2 APK** on GitHub.
3. Open the downloaded APK and choose **Install**. If Android asks, allow installation
   from Downloader specifically, then return to the installer.
4. Open **onn Volume Bridge** and follow the setup below.

Short link: [aftv.news/3586297](https://aftv.news/3586297). The code is version-specific;
the old code `3237729` still downloads v0.2.1 and does not include problem reporting.

Alternatively, transfer the APK using a USB drive or your usual file-transfer method,
then open it with the Android package installer. If Android asks, allow installation
from that specific installer/source. No PC build or ADB command is needed for this
installation path. Do not bypass a firmware verification refusal.

Only run one volume bridge at a time. Installing an update with the same signing
identity preserves saved pairing. The package is `io.github.simo255.onnvolumebridge`.

## The short setup flow

Prefer pictures? **[Follow the screenshot guide](visual-guide.md)** for the three setup steps and ready screen.

## Reporting a problem

**Report a problem** is at the bottom of every setup screen and the ready screen.
It explains the current status with an `OVB-` code and previews a generic report.
No exact model numbers/IDs, device names, firmware identifiers, addresses,
credentials, typed input or raw logs are collected for the report.
It includes the app version, Android API level, generic TV system, setup step,
permission/connection/audio checks, the last safe failure category per component
(including the failing operation and an HTTP status number when applicable), and up to six recent technical
state transitions. Event ages are relative seconds, not dates or wall-clock times.
This bounded history exists only in memory for the current process; restarting
the app clears it. Exception messages, stack traces and response bodies are never
included. Open the report while the problem is happening when possible.

Choose **Use my phone (QR code)**, then one of these options:

- **Copy text for Reddit or other apps:** scan the text QR using a reader that
  supports copying decoded text, tap Copy on the phone and paste into your Reddit
  comment. Some camera apps need a QR reader such as Google Lens for text codes.
  The QR is generated locally; this option needs no GitHub account or upload.
- **Open a GitHub issue draft:** scan the QR to open GitHub with the same report
  already filled in. Sign in if needed, describe the problem and submit it yourself.
  Opening the link sends only that reviewed generic report to GitHub; nothing is
  posted automatically. Exact device/TV models can be added voluntarily on GitHub.

**Copy report on this TV** is an alternative for a browser on the onn. Its clipboard
is separate from your phone: copying on the TV does not copy to your phone.
No third-party QR service, shortener or background diagnostic upload is used.

## Setup steps

The app opens in English by default, even when Android is in another language.
Choose English or French with **Language / Langue** at the top of the app, before setup.

1. **Connect your TV.** Select **Find my TV**, then choose your TV from the results.
   The app identifies its system when advertised; Samsung and Roku connection starts
   after selection. Unknown systems require an explicit choice. Sony opens its
   settings for the optional key, and UPnP requires manual endpoint configuration.
   If discovery finds nothing, choose **TV not found? Set up manually**, select the
   system, enter the TV address from its network settings and choose **Save and connect**.
   Samsung requires certificate trust and **Allow** on the TV itself. Sony may
   need its IP-control pre-shared key. Roku TV requires Control by mobile apps.
2. Choose **Set up my remote automatically**. If USB debugging is off, the app
   sends you to Developer options. Enable it and return. Approve Android's local
   USB debugging request and select **Always allow** when asked. The app then
   enables its own accessibility service, preserves other enabled services, and
   checks whether HDMI volume is fixed. It advances when the connections are ready.
3. **Try the remote.** Check short presses, hold/release, mute and ordinary
   navigation. Choose **My remote works — finish** after testing.

If HDMI volume is not reported as fixed, the app opens the official remote settings
through a prompt. Choose **Volume control → Automatic (CEC)**, then return; setup
checks again. This proprietary setting cannot be changed reliably on every firmware.
Keep the existing power-button setup. If ordinary CEC already controls TV volume,
use it instead of enabling a second bridge.

Android does not let this ordinary app silently turn debugging on or approve its
own debugging identity. Those initial system approvals remain yours. Automatic
accessibility setup runs only after the explicit setup action and approved local
ADB authentication. It adds only this app's component; it does not disable other
services or grant broad permissions to other apps. No arbitrary shell command is
accepted by the UI or a network endpoint. The pre-change accessibility list is
stored encrypted on the box when a change is needed.

Keep the TV and onn on the same trusted network. Debugging must stay enabled for
the bridge; some firmware also exposes the ADB listener to the LAN. The app itself
connects only to the box's loopback address. No PC is needed during normal use.

## When automatic setup needs help

Use **Manual setup / troubleshooting** to see the individual controls.

- If Developer options are hidden: Android Settings → System / Device preferences
  → About → select Android TV OS build seven times. Enable USB debugging and return.
- If an authorization prompt was denied or timed out, approve it and retry setup.
- If firmware does not expose loopback ADB, the connection cannot be made by this
  app alone. Availability differs by firmware; do not assume USB debugging alone
  guarantees a network listener.
- If accessibility setup is refused, enable **onn Volume Bridge** in Android's
  Accessibility settings manually. The app cannot read screen contents.
- Advanced options hold a custom port, UPnP control path and remote-interface override.
  Unknown input interfaces need an exact explicit selection; do not guess event nodes.

Discovery is a short, user-initiated SSDP query, not a subnet scan. It does not pair,
fetch advertised URLs or send volume commands. Results are candidates, not verified
TV identities. Some TVs do not advertise; manual addressing remains available.

## Pause, reconnect and recovery

The completion screen has **Pause / resume**, **Reconnect**, **Review setup** and
**Forget TV pairing**. Forgetting clears TV credentials and pauses relaying while
preserving the separate app-owned local debugging identity. If the TV address or
certificate changes, forget pairing, enter the new address and approve it again.

If app buttons fail, check the TV address, local-control setting and on-TV pairing.
If app buttons work but the remote does not, check local debugging authorization
and accessibility. If both onn and TV volume change, check the remote volume mode.
If changes continue after release, pause and reconnect before trying again.

To fully stop the bridge, pause it and turn off **onn Volume Bridge** under Android
Accessibility. No package has been removed. You can then turn debugging off; doing
so stops local button reading. Do not revoke all debugging identities unless you
intend to revoke other authorized tools too.

Reboot, playback, headphones and standby acceptance still need checking per firmware.
Other TV adapters remain experimental; a protocol test is not a hardware report.

## Build from source or configure from a computer

Build with JDK 17, Android SDK 34 and build-tools 34.0.0:

```sh
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

On Windows use `gradlew.bat`. Set your own ignored SDK location. Local builds use
your developer signing identity. Published builds can use an external keystore
through `ONN_RELEASE_KEYSTORE`, `ONN_RELEASE_STORE_PASSWORD`, `ONN_RELEASE_KEY_ALIAS`
and `ONN_RELEASE_KEY_PASSWORD`. Keep the keystore and credentials outside Git;
retain the same identity for future updates.

`tools/configure.py --help` remains available for optional desktop provisioning.
It does not install packages or silently grant accessibility/debugging. Prefer
the app UI for credentials and certificate approval.
