<div align="center">

# Facebook Extended

### Less distraction. More control. No root required.

Customize your feed, filter ads, download media and make Facebook feel like yours.

**No root · In-app settings · Main & side-by-side clone**

[Releases](https://github.com/tas33n/Extended/releases) · [Changelog](CHANGELOG.md) · [Patching guide](docs/PATCHING.md)

Made by **[@tas33n](https://github.com/tas33n)**

</div>

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

**[Get releases →](https://github.com/tas33n/Extended/releases)** · Public release publication is pending. [VirusTotal reports →](docs/SCANS.md) *(scan links pending)*

### Already have root?

We recommend **[Loukious’s original Facebook App Ads Remover](https://github.com/Xposed-Modules-Repo/tn.loukious.facebookappadsremover)** with **[Vector](https://github.com/JingMatrix/Vector)** / compatible LSPosed and stock Facebook. You can use the embedded build too—just exclude it from rooted framework injection to avoid running both runtimes together.

## 🛠️ Patch your own APK

Place an original Facebook **APK/APKS** in this folder and run:

```powershell
.\patch.bat
```

Or use `python patch.py`. First-run setup remembers your local tool and signing-key paths. Both editions are built by default; completed outputs are listed in `latest-build.json`.

[Patching, updates & releases →](docs/PATCHING.md)

## ❤️ Credits

**Facebook Extended — [@tas33n](https://github.com/tas33n)**  
Built on **[Facebook App Ads Remover by Loukious](https://github.com/Xposed-Modules-Repo/tn.loukious.facebookappadsremover)**, which provides the foundation for many hooks and features. [Support Loukious ☕](https://buymeacoffee.com/loukious)

Root-free embedding powered by **[JingMatrix/LSPatch](https://github.com/JingMatrix/LSPatch)** and **Vector/LSPlant**. [Full credits & licensing →](NOTICE.md)

---

Not affiliated with Meta/Facebook. Modified-client behavior and scan results are not a guarantee of safety or compatibility. [Security notes](SECURITY.md) · [License/publication status](NOTICE.md)
