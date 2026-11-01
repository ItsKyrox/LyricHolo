# Changelog

## 1.0.1

- Raised the text-size limit to 128 px, including the slider and manual pixel entry.
- Kept sky holograms level while looking up or down and positioned regular 3D lyric spawns around the player's upper torso.
- Added macOS media detection for the Spotify desktop app and Apple Music app.
- Added guidance for macOS Automation permissions and clarified supported players.
- Fixed macOS playback-position parsing for locale-specific number formats and kept lyrics stable through brief media-query hiccups.

## 1.0.0

## 1.0.0

- Initial public release candidate for Fabric on Minecraft 1.21.11.
- Added Windows media-session playback detection and asynchronous timed-lyrics lookup.
- Added world and sky hologram rendering, corner display, configurable word synchronization, animations, themes, and persistent profiles.
- Hardened settings/profile saves with atomic replacement and made invalid lyric cache entries recover through a live lookup.

- Added Pacifico as a selectable, bundled lyric font with its SIL Open Font License.
- Added a high-resolution antialiased Noto Sans option as the default lyric font and improved Pacifico glyph oversampling.
