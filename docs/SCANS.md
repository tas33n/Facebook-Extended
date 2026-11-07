# VirusTotal reports

**Status: pending user-provided scan links. No clean/detection count has been claimed.**

Machine-readable reports: [scans.json](scans.json).

Use only the final accepted build from `dist`; several development candidates were superseded during Reels testing. A report for 8.2/8.3 does not cover a later 8.4 artifact even when the Facebook version name is unchanged. The module/version and SHA-256 distinguish them.

After scanning each exact release APK, record its report:

```sh
python scripts/record_scan.py /builds/dist/Extended-585.0.0.47.54-main.apk https://www.virustotal.com/gui/file/EXACT_SHA256
```

The command validates that the file hash matches the URL; it does not upload files or fabricate engine results. Use `--summary` only for a result you actually reviewed, with the scan date. Scan main and clone separately. Rebuilt files have different hashes and need new reports.

Antivirus classifications can change and hook/repackaging frameworks may trigger detections. Report observed counts/labels honestly, including detections. A zero-detection report is not proof of safety, absence of vulnerabilities, server acceptance or privacy protection. See SECURITY.md.
