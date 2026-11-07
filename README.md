<div align="center">

# Facebook Extended

### Less distraction. More control. No root required.

Customize your feed, filter ads, download media and make Facebook feel like yours.

**No root · In-app settings · Main & side-by-side clone**

[Releases](https://github.com/tas33n/Facebook-Extended/releases) · [Changelog](CHANGELOG.md) · [Patching guide](docs/PATCHING.md)

Made by **[@tas33n](https://github.com/tas33n)**

</div>

## 📥 Download & install

**No Git or build tools needed.** Tap a button on your Android phone, download the APK, then open it to install.

[![Download Main App](https://img.shields.io/badge/Download-Main_App-0866FF?style=for-the-badge&logo=android&logoColor=white)](https://github.com/tas33n/Facebook-Extended/releases/latest/download/Facebook-Extended-main.apk)
[![Download Clone App](https://img.shields.io/badge/Download-Clone_App-238636?style=for-the-badge&logo=android&logoColor=white)](https://github.com/tas33n/Facebook-Extended/releases/latest/download/Facebook-Extended-clone.apk)

- **Main app:** use Facebook Extended as your main app; cannot install over stock Facebook.
- **Clone app:** keep your existing Facebook and install Extended alongside it.

**Android 11+ · ARM64.** If prompted, allow installation from your browser or file manager.

*Buttons always point to the latest automated release; they become available after the first successful publication.* [All releases](https://github.com/tas33n/Facebook-Extended/releases) · [VirusTotal reports](docs/SCANS.md)

## ✨ Your Facebook, your way

| | Features |
|---|---|
| **🚫 Ad filtering** | Separate controls for **News Feed, Stories, Reels, Marketplace and game ads**, plus extra cached-feed protection. |
| **🧹 Cleaner feed** | Hide **Threads, suggested posts, People You May Know, Reels and Stories** from your feed. Filter **AI-labeled content** and your own **keywords**. Optionally block automatic feed refresh. |
| **⬇️ Video downloads** | Open the **quality picker directly** from the download icon on supported full-screen videos and Reels—including those opened from posts, profiles and suggestions. Video-post menus also offer **Download video**. Choose available resolutions or audio, and download in-app or through your browser. |
| **🖼️ Profile & cover photos** | Open available image sources even when the profile disables normal photo taps. **Pinch to zoom, drag and double-tap to reset.** Long-press for **Save image** or **Copy image**. Uses the image Facebook supplies—not a screenshot; private originals are not unlocked. |
| **🌙 Appearance** | **Force dark mode** or enable **AMOLED black** backgrounds. |
| **🛡️ Privacy & Stories** | Allow screenshots/recording, block targeted capture-detection hooks, block the targeted **story seen-reporting** controller, and hide the Stories tray. |
| **🧭 Navigation & links** | Hide **Reels, Marketplace and Games tabs**. Open external links in your browser and clean Facebook redirect links. |
| **▶️ Playback** | Optional **background playback** and **resume video position**. |
| **🔁 Repost & account tools** | Contextual **Story/Reel repost** actions where supported; local **session export/import**. Session backups contain login data—keep them private. |
| **⚙️ Convenient controls** | A profile-only settings icon, rounded light/dark panel, automatically saved preferences and a built-in **Restart app** button. Optional activity diagnostics for troubleshooting. |

Every feature is configurable. Availability can vary with Facebook’s version, layout and account experiments.

## 🎛️ Open the settings

1. Go to **your profile** using the bottom navigation.
2. Tap the **sliders icon** beside **Edit / Search / More**.
3. Choose your settings, then tap **Restart app** when needed.

No persistent floating button. No separate module app needed. Settings save immediately; restarting applies startup-only changes too.

## 📦 Choose your edition

| Edition | Best for |
|---|---|
| **Facebook Extended** | Using Extended as your main Facebook app. Its different signature means it cannot install over stock Facebook. |
| **Facebook Extended Clone** | Keeping stock Facebook installed alongside Extended, with separate app data and login. |

**Android 11+ · ARM64 · Single APK installation**  
Reviewed Facebook versions: **585.0.0.47.54** and **586.0.0.52.53**.

### Already have root?

We recommend **[Loukious’s original Facebook App Ads Remover](https://github.com/Xposed-Modules-Repo/tn.loukious.facebookappadsremover)** with **[Vector](https://github.com/JingMatrix/Vector)** / compatible LSPosed and stock Facebook. You can use the embedded build too—just exclude it from rooted framework injection to avoid running both runtimes together.

## 🛠️ Patch your own APK

Place an original Facebook **APK/APKS** in this folder and run:

```powershell
.\patch.bat
```

Or use `python patch.py`. First-run setup remembers your local tool and signing-key paths. Both editions are built by default; completed outputs are listed in `latest-build.json`.

[Patching, updates & releases →](docs/PATCHING.md)

**Build on GitHub:** after the one-time signing-secret setup, pushing a root APK/APKS triggers builds and a release of both editions. For large files, use **Actions → Build and release Facebook Extended → Run workflow** with a download URL and SHA-256. [Setup →](docs/PATCHING.md#automatic-github-builds)

## 💬 Bugs & feature requests

Found a problem or have an idea? **[Report a bug](https://github.com/tas33n/Facebook-Extended/issues/new?title=Bug%3A%20)** or **[request a feature](https://github.com/tas33n/Facebook-Extended/issues/new?title=Feature%20request%3A%20)** in GitHub Issues.

For bugs, include your Android version, Facebook Extended version, main/clone edition and steps to reproduce. Screenshots help—hide personal information first.

## ❤️ Credits

**Facebook Extended — [@tas33n](https://github.com/tas33n)**  
Built on **[Facebook App Ads Remover by Loukious](https://github.com/Xposed-Modules-Repo/tn.loukious.facebookappadsremover)**, which provides the foundation for many hooks and features. [Support Loukious ☕](https://buymeacoffee.com/loukious)

Root-free embedding powered by **[JingMatrix/LSPatch](https://github.com/JingMatrix/LSPatch)** and **Vector/LSPlant**. [Full credits & licensing →](NOTICE.md)

---

Not affiliated with Meta/Facebook. Modified-client behavior and scan results are not a guarantee of safety or compatibility. [Security notes](SECURITY.md) · [License/publication status](NOTICE.md)
