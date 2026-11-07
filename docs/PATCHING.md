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

Private setup is saved in ignored `.extended.local.json`. Passwords, signing keys and outputs are excluded from Git. Root APK/APKS inputs can now be intentionally committed to trigger CI; check `git status` before committing. To create your first key:

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

**Compact packaging:** the internal Facebook APK is aligned before embedding; LSPatch writes shared ZIP entries with 16 KiB alignment and performs final signing. Do not rewrite the resulting APK with ZIP tools, zipalign or apksigner: doing so expands those shared entries into duplicate copies. The verifier checks the embedded origin's exact hash/CRC and each shared entry's original offset and metadata. This preserves features while avoiding the previous ~2× APK size.

**App-label compatibility:** branding changes launcher labels only. Facebook's application label must remain its original nonzero string-resource reference; its notification code reads that resource directly. Both main and clone builds check this in the outer and internal-origin manifests to prevent incoming-message crashes.

This reduces the download/APK size, not necessarily installed storage: the runtime still extracts its internal origin into app storage. Some generic ZIP readers flag intentional nested entries as overlapping; use the included verifier and Android signature/alignment tools rather than repacking the APK to “fix” it.

The main edition uses `com.facebook.katana`; it cannot update vendor-signed Facebook. The clone uses `com.tas33n.facebook.extended` and can coexist with the main app. Exclude either embedded app from rooted Vector/LSPosed injection.

## When Facebook updates

Reviewed versions: **585.0.0.47.54 and 586.0.0.52.53**. Player/photo hook mappings live in [`compatibility.json`](../compatibility.json). The script validates those mappings against the APK and generates the module constants.

If a version is unknown or its mapped methods changed, the build stops. Review the new mappings rather than starting the whole project again. A discovery helper finds candidate classes:

```sh
python scripts/discover.py /path/to/merged.apk --out /path/to/new-discovery
```

After updating the mapping, rerun the normal patch command. Structural checks cannot guarantee every hook or future layout. On 586, both builds passed 122 module tests each, installed and launched on Android 16; main menu/restart, photos, video qualities and observed feed controls were user-confirmed. Clone login was not tested.

## Automatic GitHub builds

Repository: **[tas33n/Facebook-Extended](https://github.com/tas33n/Facebook-Extended)**.

### One-time setup

In **Settings → Secrets and variables → Actions**, add:

| Secret | Value |
|---|---|
| `EXTENDED_KEYSTORE_BASE64` | Base64 encoding of your existing signing keystore |
| `EXTENDED_KEYSTORE_PASSWORD` | Its password (store and key passwords must match for this pipeline) |
| `EXTENDED_KEY_ALIAS` | The key alias |

**Reuse the existing release key** so users can update installed builds. Do not generate a new key each run. Example to copy the base64 on Windows:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\private\extended.p12')) | Set-Clipboard
```

Paste that into the repository secret, not a source file. GitHub Actions must be enabled and repository policy must allow the workflow's `contents: write` permission.

### Trigger a build

- **Git push / web upload:** add or update a root `.apk` / `.apks` on **main**. The workflow builds only added/changed root inputs. A web-upload commit to main triggers it too.
- **Large files (recommended for Facebook):** upload the original to a release asset or another HTTPS host, then choose **Actions → Build and release Facebook Extended → Run workflow**. Supply `input_url` and its exact `input_sha256`.
- **Rebuild an existing tracked input:** Run workflow with `input_file` set to its root filename instead of a URL.

GitHub's normal Git limit is **100 MiB per file**, and browser repository uploads are limited to **25 MiB**. Our ~280 MB Facebook APKS cannot be uploaded as ordinary Git/web source. Use the URL workflow, or configure Git LFS for root APK/APKS inputs (subject to your LFS storage/bandwidth quota); checkout downloads LFS content. The workflow rejects unresolved LFS pointer files.

The Ubuntu runner sets up Python, JDK 21 and Android SDK, restores the signing key, then runs the **same patcher** for main and clone. Unsupported Facebook versions still require a reviewed compatibility mapping; CI does not bypass that check. A failed build does not publish a release. Multiple changed inputs build sequentially, each producing its own release.

Successful runs publish a release with:

- `Facebook-Extended-main.apk` and `Facebook-Extended-clone.apk` — fixed filenames so README buttons always work.
- `SHA256SUMS` and `verification.json`.
- Automatic notes with Facebook version, source commit, recent commit messages, input hash, verification scope and credits.

Each tag includes the Facebook version and workflow run/attempt identity. Assets upload into a draft first; it becomes public/latest after upload succeeds. CI does not install on a phone or claim a VirusTotal result. Only trusted main-branch builds access signing secrets; PR checks do not.

## Publish prebuilt files manually

Review [credits/licensing](../NOTICE.md), update [CHANGELOG.md](../CHANGELOG.md), authenticate with GitHub CLI and push a release tag. Then:

```sh
python scripts/release.py --tag v1.1.0 --dist /path/to/output --notes CHANGELOG.md --draft
```

This verifies artifact hashes and uploads a draft release with APKs and verification reports. The separate **Publish verified local artifacts** workflow supports an existing self-hosted release runner. Its versioned filenames are not the fixed names used by the automated-build download buttons. [Scan reports](SCANS.md) are recorded separately for each exact APK hash.
