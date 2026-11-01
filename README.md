# LyricHolo

LyricHolo brings your music into Minecraft as synced, animated lyric holograms. It reads the active track from the Windows media session, looks up timed lyrics, and displays them in the world or in a small HUD panel.

> **Current support:** Minecraft **1.21.11**, Fabric, Java 21+, Windows. This is a client-side mod. A build for Minecraft 26.1.2 is not included in this repository.

## Features

- Detects track title, artist, album, playback position, and play state from Windows media sessions.
- Looks up synchronized lyrics using **YouTube Music** or **LRCLIB**. Choose the source in the in-game menu.
- Renders lyrics as floating 3D text or as a corner HUD display.
- Offers word-sync modes, including highlight, pop-up, and separately stacked words. Word-level timing depends on the lyric source.
- Customizes text color, size, font, shadow, stroke, backplate, sky position, distance, animation, and theme.
- Saves named settings profiles and includes **One Word** and **Sky Hologram** presets.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.11.
2. Install the matching [Fabric API](https://modrinth.com/mod/fabric-api) version.
3. Download the LyricHolo JAR from the project's Releases page and put it in your Minecraft instance's `mods` folder.
4. Launch the Fabric profile.

## Use

1. Start audio in an app that exposes its track metadata through Windows media controls. Spotify, YouTube Music, and supported browsers are examples; browser behavior can vary.
2. Press **H** to open LyricHolo's control deck. You can change the key in Minecraft's **Options → Controls** menu.
3. Select a lyrics source, enable the display you want, and adjust the appearance in **Text / Theme**.

The corner lyric panel can be enabled independently of the 3D hologram. Track and lyric status are shown in the control deck. Settings and profiles are saved under `.minecraft/config/lyricholo/`.

### Lyrics sources and timing

- **YouTube Music API** is the default source. It uses an unofficial, unauthenticated YouTube Music endpoint; no API key or account login is needed. The endpoint can change, and some tracks may not have accessible timed lyrics.
- **LRCLIB** provides timed LRC lyrics when a matching entry is available. Lyric files are cached locally.
- Line timing is available more often than word timing. Word-sync modes use word timestamps when the selected source supplies them; otherwise LyricHolo estimates word progression from line timing. Results therefore vary by song and source.
- The sync offset in the control deck can compensate for a consistent delay between the media session and the lyric data.

## Settings at a glance

Open the control deck with **H**. The menu includes controls for:

- 3D lyrics, corner lyrics, sky mode, display distance, and screen-facing text.
- Lyrics source, word-sync mode, animation, and sync offset.
- Font (**Default**, **Uniform**, **Smooth**, or **Pacifico**), text color, size, shadow, stroke, and backplates.
- UI theme and language.
- Saved profiles, including reset-to-defaults and built-in presets.

## Compatibility and privacy

- Media detection currently works on **Windows** through the system's Global System Media Transport Controls (GSMTC). Other operating systems are not supported by the current media provider.
- Lyric lookup requires an internet connection. Depending on the selected source, LyricHolo sends track metadata such as title, artist, album, and duration to that source to find lyrics.
- LyricHolo reads media metadata and playback state; it does not capture or transmit audio.
- YouTube Music lookup relies on an unofficial endpoint and may stop working if YouTube changes its protocol.

## Build from source

Requirements: **Java 21** and an internet connection for Gradle dependencies.

From PowerShell in the project directory:

```powershell
.\gradlew.bat build
```

The remapped mod JAR is produced in `build/libs/` (currently `lyricholo-1.21.11-1.0.0.jar`).

## Credits and license

Created by **ItsKyrox_MC**. Pacifico and Smooth font files are bundled with their respective SIL Open Font License notices in `src/main/resources/assets/lyricholo/font/`. A license for LyricHolo's own source code has not yet been declared.
