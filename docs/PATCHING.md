# Patching Facebook Extended

## Build an APK

Requires **Python 3.11+, JDK 21, Android SDK 36 / build-tools 36.0.0**, and a signing keystore.

1. Place an **original Facebook APK/APKS** in the repository root.
2. Run `patch.bat` on Windows or `python patch.py`.
3. Complete first-run setup. **Reuse your signing key for future updates.**

Both main and clone APKs are built by default. Outputs go into a new `artifacts/` folder; `latest-build.json` points to the result. With several input files, specify one:

```sh
python patch.py Facebook_586.0.0.52.53.apks --variant both
```

Private setup is saved in ignored `.extended.local.json`. Passwords, signing keys, inputs and outputs are not committed. To create your first key:

```sh
keytool -genkeypair -keystore /private/extended.p12 -storetype PKCS12 -alias extended -keyalg RSA -keysize 3072 -validity 10000
```

## What gets patched

| Part | How it works |
|---|---|
| Split APKs | Merge matching resources, code and libraries into one installable APK. |
| Extension runtime | Embed LSPatch and the FBAR-based module so hooks run without root. |
| Settings | Add the profile sliders entry, local preferences, styled panel and private restart helper. |
| Media tools | Add source-image viewing/Save/Copy and direct video-quality picker entries. |
| Clone | Change package, provider authorities, custom permissions and matching identity references; preserve Java class names and set the module's clone scope. |
| Final build | Update DEX metadata, align, sign and verify payloads, signatures and clone conflicts. Original inputs stay unchanged. |

The main edition uses `com.facebook.katana`; it cannot update vendor-signed Facebook. The clone uses `com.tas33n.facebook.extended` and can coexist with the main app. Exclude either embedded app from rooted Vector/LSPosed injection.

## When Facebook updates

Reviewed versions: **585.0.0.47.54 and 586.0.0.52.53**. Player/photo hook mappings live in [`compatibility.json`](../compatibility.json). The script validates those mappings against the APK and generates the module constants.

If a version is unknown or its mapped methods changed, the build stops. Review the new mappings rather than starting the whole project again. A discovery helper finds candidate classes:

```sh
python scripts/discover.py /path/to/merged.apk --out /path/to/new-discovery
```

After updating the mapping, rerun the normal patch command. Structural checks cannot guarantee every hook or future layout. On 586, both builds passed 122 module tests each, installed and launched on Android 16; main menu/restart, photos, video qualities and observed feed controls were user-confirmed. Clone login was not tested.

## Publish a release

Review [credits/licensing](../NOTICE.md), update [CHANGELOG.md](../CHANGELOG.md), authenticate with GitHub CLI and push a release tag. Then:

```sh
python scripts/release.py --tag v1.1.0 --dist /path/to/output --notes CHANGELOG.md --draft
```

This verifies artifact hashes and uploads a draft release with APKs and verification reports. The manual GitHub Actions workflow does the same on a configured `extended-release` self-hosted runner. [Scan reports](SCANS.md) are recorded separately for each exact APK hash.
