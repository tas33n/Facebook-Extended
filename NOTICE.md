# Credits, provenance and licensing

## Original module

**Loukious — Facebook App Ads Remover**

- https://github.com/Xposed-Modules-Repo/tn.loukious.facebookappadsremover
- https://buymeacoffee.com/loukious
- Imported local source baseline: module 1.24, code 25; folder `tn.loukious.facebookappadsremover-main` supplied by the workspace owner.
- Ad/feed/theme/privacy/media hooks and discovery originate primarily from this module. Its own source comments credit earlier ports and Morphe-inspired behaviors; those comments are preserved.

**No LICENSE file was present in the supplied source; upstream GitHub API reported license=null when checked.** Public source availability is not an unrestricted redistribution license. This scaffold does not relicense Loukious's code. Resolve permission/license terms with the author before publishing a derivative source or binary release. That is an observed provenance gap, not a claim that a particular license applies.

## Extended additions — @tas33n project

Embedded/local settings initialization, profile-only menu entry, refined dialog/restart helper, source-image viewer/actions, direct media UI entries, clone packaging, verification and build/release documentation. New files and changes are tracked in this repository; historical local analysis is outside the publishable tree. No claim is made to authorship of the upstream hooks.

## Runtime and tools

- [JingMatrix/LSPatch](https://github.com/JingMatrix/LSPatch), v1.2/487, GPL-3.0: embedded runtime/patcher. Its Vector/LSPlant and bundled component notices remain applicable. Distributing its binaries carries corresponding license/source obligations.
- [Vector](https://github.com/JingMatrix/Vector) and [LSPlant](https://github.com/JingMatrix/LSPlant): underlying framework/hook engine; consult exact upstream component licenses.
- [libxposed API/service](https://github.com/libxposed), [DexKit](https://github.com/LuckyPray/DexKit), OkHttp, Jsoup, Kotlin and AndroidX: dependency licenses remain with their authors.
- [APKEditor](https://github.com/REAndroid/APKEditor), 1.4.9; dexlib2 2.5.2; Guava 27.1-android: build-time dependencies pinned in tools.lock.json. Inspect their licenses if distributing the tools themselves.
- Android SDK tools are supplied by the builder, not committed here.

No blanket license is asserted for the combined tree pending upstream permission clarification. Facebook code/resources remain Meta's material; this source scaffold does not include an input or output APK. Release automation must be used only for artifacts the publisher has the right to distribute.
