# Architecture

`LocalAdbVolumeKeys` authenticates only to the box's loopback ADB endpoint. It
opens a read-only `getevent -pl` shell command, selects a remote interface through
`InputDeviceSelector`, then listens to that node. It parses only volume up/down
press/release and discards every other event without retaining it.
`VolumeHoldRepeater` first asks the selected target for native hold support.
Samsung sends one `Press` on down and one `Release` on up; the TV owns its repeat
cadence. Other adapters currently use progressive click repetition: first repeat
after 150 ms, then 120 ms to 17 ms over 1.2 seconds. Both modes stop on release,
pause, route loss, connection loss or reader shutdown. Duplicate down/native
repeat input events do not double the cadence. A 10-second safety cap releases
native holds or cancels repeats when an input release is lost.

Native hold is a target capability, not a manufacturer condition in the input
reader. Roku ECP also documents keydown/keyup, but that path is not implemented
or hardware-validated here yet. Sony's current audio API and generic UPnP adapter
set volume rather than forwarding held-button state.

Selection prefers the observed **Onn-Remote Keyboard** interface, even when the
consumer-control interface advertises volume capabilities. Event numbers are
rediscovered after connection. Unknown or ambiguous interfaces require an explicit
name override; the app never selects an unrelated USB keyboard or virtual device.

`VolumeService` keeps the relay lifecycle tied to enabled accessibility. It cannot
retrieve screen contents. It filters mute, ignores ordinary navigation, watches
input-device changes and suppresses relaying while headphones are connected.
It never calls Android setStreamVolume or observes/recenters Android volume.

`VolumeRelay` selects a `VolumeTarget` from validated configuration. Samsung uses
its existing asynchronous paired TLS client. HTTP adapters share a bounded worker
and implement the synchronous `TvProtocol` probe/send contract. Requests never
run on the UI thread. The input reader calls the provider-neutral volume enum.

The UI owns explicit certificate enrollment for Samsung; the normal connection
requires an exact saved leaf fingerprint. Samsung tokens and Sony PSKs are
Android-Keystore encrypted. The loopback ADB RSA identity is separately generated
and wrapped with a Keystore AES key. No PC's ADB private key is copied.

Private IPv4 validation occurs before provider creation. HTTP does not follow
redirects, responses are limited to 64 KiB, XML rejects DTD/external entities,
queued commands are bounded and stale presses are dropped. Diagnostic logs omit
addresses, credentials, certificates, packet bodies and typed keys.

The UI can save/configure and explicitly opt in. An optional desktop tool uses a
setup receiver protected by WRITE_SECURE_SETTINGS, which ordinary third-party apps
cannot call. Neither setup path disables system packages or changes Home.

There is no cloud service, telemetry, runtime dependency, launcher integration,
firmware modification, infrared attestation bypass or generic remote shell API.
The ADB client has fixed read-only command construction and a fixed loopback endpoint.
