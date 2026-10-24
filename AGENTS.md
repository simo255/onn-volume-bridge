# Project scope

Only onn volume/mute bridging belongs here. Read README.md and docs/compatibility.md
first. Preserve the distinction between original hardware evidence, protocol tests,
and a new independent-app hardware acceptance run.

- No root, firmware changes, attestation bypasses, Home takeover or debloating.
- No personal device settings, addresses, keys or capture files in Git or public issues.
- Do not change a user's installed TV as part of ordinary source work.
- TV endpoints remain private IPv4; never relax pins or redirect rules for convenience.
- Retain opt-in defaults, bounded network work, no typed-text retention and disabled backups.
- Unknown remote interfaces need explicit selection rather than arbitrary keyboard reading.
- Keep protocols behind the target interface; no manufacturer coupling in the input reader.
- Run testReleaseUnitTest, lintRelease and assembleRelease after meaningful code changes.
- Only call an adapter hardware-verified after the compatibility acceptance checklist passes.
