# Security and data handling

No “fully safe”, “undetectable” or “ban-proof” claim is made. APK signing proves file integrity under a key, not benign behavior. VirusTotal results are hash/date-specific observations, not guarantees.

## Current behavior

- Runs code inside Facebook using an embedded native hook runtime. No root is required.
- Existing upstream download/repost paths can use Facebook session data. Session export/import explicitly handles login material; protect exports and do not upload them to issues or scans.
- Video resolution/download and optional DASH handling have upstream network behavior. Audit `DownloadHook.kt` and `Repost.kt` for the version you build; “rootless” does not mean “offline” or “no third-party requests.”
- Post-menu Download invokes that selected post's native link action internally. Only the short-lived selected-link result is diverted into the picker; no recipient selection, message send or public post is automated. Reels uses the live-player observer directly; an earlier Share-panel fallback was rejected and is not the current primary UX.
- Available profile images use exact Facebook-supplied HTTPS CDN URLs with redirects disabled, no screenshot fallback and bounded decoding. This does not unlock private originals.
- Save/Copy image writes PNG to MediaStore. Copy image leaves its backing image in `Pictures/Facebook/Clipboard` so pasted URI access can work. It does not silently delete user files.
- Existing upstream diagnostics may log signed media URLs/session-related details. Do not post raw logs. Source UI does not intentionally log full image URLs, but that is not a whole-program privacy audit.
- User-visible restart saves preferences, restarts matching same-UID Facebook processes and relaunches. No app-data clear or reboot.

## Verified versus unknown

Structural checks include signatures, ZIP CRC/alignment, module/origin hashes, namespace collisions, DEX checksum updates and bounded semantic comparison for cloned DEX. Some preceding builds were tested on one Android 16 phone. Not all features, servers, devices, languages or future versions are tested. Clone package/signature-bound login, notifications and deep links require separate testing.

Report reproducible issues with app version, build SHA-256, Android version, package variant and sanitized logs. Do not include credentials, session exports or complete signed URLs. Keep rooted Vector injection out of embedded targets: double injection caused native startup crashes in development.
