<p align="center">
  <img src="docs/assets/logo.svg" width="112" height="112" alt="JustWatch app logo">
</p>

<p align="center"> 

# JustWatch

</p>

### Enjoy the video. Keep your time.

Open the videos shared with you wihtout the endless scrolling.

JustWatch opens shared Instagram, TikTok and Facebook videos on Android, without needing their apps. Watch what your friends send you, then carry on with your day.

[![Download Android APK](https://img.shields.io/badge/Download-Android_APK-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Amine-AO/JustWatch/releases)

Download the signed APK from **Releases**.

## A video viewer, without the feed

- Open a video from a link, Android's share menu, or the paste field.
- Watch one video at a time, with no feed or autoplay queue.
- Use fullscreen, seek through the video, or jump forward and back.
- Choose a light or dark theme, or follow your device.
- Receive app update prompts, download verified updates, and open Android’s installer.
- Update the extraction engine separately from Settings without reinstalling the app.

## Get started

1. Download the signed APK from [Releases](https://github.com/Amine-AO/JustWatch/releases).
2. Open the APK on your Android phone and allow installation from that source if prompted.
3. Follow the link setup guide to choose which links JustWatch should open.

You can also **share a link → JustWatch**, or paste it into the app. Some apps open links directly in their own browser, so Android's share menu is a useful alternative.

**Requires Android 8.0 or newer.**

## Make shared links open in JustWatch

The setup guide checks relevant social apps and Meta services that may already handle those links. It takes you to their Android settings, then helps you enable JustWatch's supported links. A home screen notice appears if JustWatch's link setup needs attention.

Android controls these choices, so you must confirm the changes yourself.

## Keep your social media blocks

I originally built JustWatch for my own use. I use DNS blocking on my phone and home network to keep social media out of reach, but I still wanted to watch the occasional video sent by a friend without turning those blocks off.

JustWatch uses its own connection to look up and fetch supported shared videos, so it can work even when your usual DNS settings block the platforms. Your phone and router settings stay as they are, and other apps and browsers keep using their existing DNS settings.

You can watch the video you were sent, then close it and move on. Playback still depends on the platform and network: login requirements, IP restrictions and other kinds of blocking can prevent it, and the webpage fallback may remain blocked.

## Playback and privacy

Videos are prepared on your phone, without a JustWatch account or an external extraction server. Temporary video files are removed when you close the video. Compatible TikTok videos can play while their local download continues; others finish downloading first.

The app contacts the relevant video platforms, DNS providers, and GitHub for extraction engine updates. An internet connection is required.

Private, deleted, login-required or restricted videos may not play. Support can also be affected by changes to the platforms.

## Stay up to date

JustWatch checks for a newer public app release when you open it, at most once a day. You can also use **Settings → Check for app updates**. If an update is available, choose **Download update**, then confirm installation in Android’s installer. Android may ask you to allow updates from JustWatch the first time.

Downloads are checked against the release checksum and the installed app’s signing key. App updates are separate from extraction engine updates. The updater is available in signed release builds; debug builds are for development.

## Share JustWatch

Watch the videos friends send you without getting lost in the feed. JustWatch opens shared Instagram, TikTok and Facebook videos, without needing their apps.

Download: https://github.com/Amine-AO/JustWatch

---

Created by [Amine-AO](https://github.com/Amine-AO).  
[Report an issue](https://github.com/Amine-AO/JustWatch/issues) · [Build instructions](docs/BUILDING.md)

