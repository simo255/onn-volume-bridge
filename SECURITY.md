# Security

Please use this repository's GitHub private vulnerability reporting for security
issues. Do not post keys, pairing tokens, PSKs, device identifiers, LAN addresses
or full device dumps in public issues.

The app requires owner-approved loopback ADB. This is a significant shell identity,
kept encrypted on the box and restricted to a fixed local command set. Normal input
monitoring is read-only. An explicit setup action can add this app to the enabled
accessibility list, enable accessibility, verify the result and inspect HDMI volume
behavior. It preserves other listed services and encrypts the original list before
a change. There is no generic shell endpoint or silent debugging authorization.
It does not require root or bypass TV authorization. Debugging may expose ADB to
the local network independently of the app; only use it on an owner-controlled LAN.

Samsung certificate trust is explicit at enrollment and pinned thereafter.
Roku/Sony/UPnP follow their local HTTP protocols, including any TV-side local-control
settings. No public-network endpoint, telemetry or credential-upload service is used.
