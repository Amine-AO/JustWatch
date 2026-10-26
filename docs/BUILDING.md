# Building JustWatch

Develop on `development`, then merge reviewed changes into `main`.

## Local debug build

Use JDK 17, Android SDK 35, build tools 35.0.0 and Gradle 8.9. Prepare the bundled extraction engine before building:

```sh
python3 scripts/prepare_ytdlp.py
gradle :app:testDebugUnitTest :app:assembleDebug
```

The project currently has no Gradle wrapper. The debug workflow also runs emulator checks, including link conflicts and progressive local playback, and uploads screenshots and reports.

## Signed release APK

Keep your permanent keystore and passwords in secure backups. Add these repository secrets in **Settings > Secrets and variables > Actions**:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing key alias |
| `ANDROID_KEY_PASSWORD` | Signing key password |

After merging into `main`, use **Actions > Build Release APK > Run workflow**, selecting `main`. Download and extract **JustWatch-release-APK**. The workflow verifies the signature against the configured key, rejects debuggable APKs, and includes a checksum and public certificate report. By default it only uploads an artifact. To publish a public release after testing and merging, run the workflow on `main` with **Publish the verified APK and update metadata to GitHub Releases** checked. This publishes the APK, `update.json`, checksum, and public certificate report under a `v<versionName>` tag.

The release workflow also verifies changes to its signing workflow and app Gradle configuration on `development` and `main`. Debug builds do not require signing credentials. For local release builds, provide `ANDROID_KEYSTORE_FILE`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` as environment variables.

Increase both `versionCode` and `versionName` in `app/build.gradle.kts` for each distributed update, for example `11 / 1.0.0` → `12 / 1.0.1`. Use three numeric components and no `-dev` suffix. The release workflow reads the built APK and rejects versions that do not increase beyond the latest public release. Continue signing with the same key. Switching from a debug build with a different certificate requires uninstalling the debug build once, which clears local settings.

## Publish a download

Create a GitHub Release for a tested version and attach `JustWatch-release.apk` and its checksum. The README download badge opens the Releases page. A private repository requires access to download; make the distribution page public before sharing it widely.

## Engine and device checks

The build bundles an official pinned yt-dlp release with SHA-256 verification. In-app engine updates verify official stable release checksums and test the engine before replacing the installed copy. Native runtime or UI updates require a new APK.

CI checks local playback and simulated link routing. Verify real public Instagram, TikTok and Facebook links on a phone, including your DNS setup. Check cancellation, closing, background pause, audio, unavailable videos and cleanup. Live platform availability is not established by emulator fixtures.


## App update verification

The release workflow generates `update.json` from the built APK, with its package name, numeric version code, public version name, asset address, size and SHA-256. The app checks the latest non-prerelease GitHub Release once per day while in use, or manually from Settings. The JSON and APK must match assets from this repository’s release. Downloads use the app’s DNS client, are bounded in size, and must match the checksum, package, increasing version code and installed signing certificate before installation is offered.

A restricted FileProvider shares only the cached update APK with Android’s installer. Users approve installation themselves, including enabling JustWatch as an installation source the first time. No GitHub token is embedded in the app. Cancellation stops the network operation and removes partial downloads. An installed update’s cached APK is removed on the next update check.

For a real end-to-end update check, install a signed release, publish a newer signed release with its generated metadata, then confirm the prompt, download and installation on a phone. CI checks metadata parsing, downgrade and signing policy, content URI restrictions, release version rules, and existing playback/navigation.
