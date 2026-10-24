# Security

Please use this repository's GitHub private vulnerability reporting for security
issues. Do not post keys, pairing tokens, PSKs, device identifiers, LAN addresses
or full device dumps in public issues.

The app requires owner-approved loopback ADB. This is a significant shell identity,
kept encrypted on the box and restricted in code to local read-only input commands.
It does not require root or bypass TV authorization. Debugging may expose ADB to
the local network independently of the app; only use it on an owner-controlled LAN.

Samsung certificate trust is explicit at enrollment and pinned thereafter.
Roku/Sony/UPnP follow their local HTTP protocols, including any TV-side local-control
settings. No public-network endpoint, telemetry or credential-upload service is used.
