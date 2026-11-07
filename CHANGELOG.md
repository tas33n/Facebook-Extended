# Changelog

## Unreleased — compact APK packaging

- Preserve LSPatch's shared nested ZIP entries instead of duplicating Facebook payload during final alignment/signing.
- Align the origin first, configure 16 KiB alignment before patching, and retain the patcher's final signature.
- Validate bounded nested-origin CRC/hash and shared-entry offsets; no general ZIP-overlap checks disabled.
- Facebook 586 main proof build reduced from 599,561,340 to 305,263,541 bytes; installed/cold-launched and tester confirmed menu/restart, video picker and source photos.

## Unreleased — automatic builds and repository links

- Update project, credits, downloads and issue links to `tas33n/Facebook-Extended`.
- Automatic main-branch builds/releases for changed root APK/APKS inputs; manual HTTPS URL + SHA-256 input for large files.
- Hosted-runner SDK setup, persistent signing secrets, verified main/clone outputs and generated release notes.
- Stable release asset names keep README download buttons independent of Facebook version.
- Root inputs are no longer ignored; output APKs, local config and signing materials remain excluded.

## 1.1.0 — Facebook 586 support and drop-in updater

- Root `patch.bat` / `patch.sh` / `python patch.py` with one-time ignored local configuration, deterministic input selection and unique work/output directories.
- Versioned compatibility.json and generated HostCompatibility constants for player/profile-source hooks.
- Static typed DEX-chain validation before module builds; missing/invalid mappings stop instead of reusing stale names.
- Facebook 586.0.0.52.53 class/getter mapping traced and statically validated; see [patching guide](docs/PATCHING.md) for test scope.
- Existing 585 mapping retained and validated for backward compatibility.
- Candidate discovery helper for future updates; build/sign/clone stages reused unchanged.

## 1.0.0 — repository baseline (release pending)

- Name and credits: Extended by @tas33n; upstream Loukious FBAR and JingMatrix runtime credited.
- Profile-only settings icon, rounded light/dark panel and restart helper.
- Source-image viewer with bounded zoom/pan and long-press Save image / Copy image.
- Direct video-menu download picker and Reels sidebar entry.
- Reels action now appears based on controls rather than capture availability, with coalesced layout/scroll/player refreshes and click-time binding.
- Follow-up: fresh viewport binding after each swipe, failed-constructor capture no longer suppresses later data, immediate captured quality display and exact-video lookup for manifest-only captures. Stale responses are discarded after navigation.
- Subsequent correction: some Reel views expose no typed player at all. Primary action now uses that Reel's own Share/Copy link result internally without clipboard changes; viewport binding is only a fallback.
- Final direct-picker design: the Share route selected correctly but showed an unwanted panel. A read-only live-player observer replaces it for Reels; no Share sheet is requested by the arrow.
- Quality completeness correction: direct Reels picker now runs full page extraction instead of immediately showing only the currently buffered SD capture. Captured data is a fallback, not the primary quality list.
- Fullscreen-route correction: icon detection no longer requires a Reels-tab heading; recognizes player markers from suggested/post/profile-opened fullscreen videos, with sidebar or toolbar-area placement.
- Repeatable original APK/APKS/split-directory → main/clone single-APK pipeline.
- Dedicated clone identity and namespace verification.
- Hash-pinned tools, per-build verification, scan-report recording and explicit CLI/Actions release tooling.

Inherited feature inventory and defaults remain credited to Loukious's module. Version-specific behavior and test scope are documented rather than implied by this changelog.
