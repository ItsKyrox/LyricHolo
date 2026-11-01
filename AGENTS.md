# LyricHolo — Agent and Developer Guide

## Overview & Application Purpose

LyricHolo is a client-side Minecraft Fabric mod that detects the active desktop media session, retrieves time-synced lyrics from the user's selected provider, and presents them as readable, animated holographic text in the game world. Its control experience should feel like a polished sci-fi instrument rather than a collection of default Minecraft menus. The visual direction draws on the bold, game-centric presentation of the KoHs Death Effects mod while retaining LyricHolo's violet hologram identity.

The product has two connected surfaces:

- **In-world lyric presentation:** compact, legible 3D lyric text anchored in the world, with optional HUD presentation and configurable placement, timing, color, animation, and visibility.
- **Control deck:** an animated, themeable glass interface for media status and lyric/render settings, with a live preview and a safe place to try presentation options.

LyricHolo is a client-side experience. Do not add server requirements or assume that servers should receive media metadata. Media detection uses supported operating-system media-session interfaces. The optional YouTube Music mode uses its undocumented InnerTube web endpoints directly, inspired by ytmusicapi; it is unofficial and may break when Google changes the protocol. Do not describe it as an official Google API. Avoid storing browser cookies or account tokens in plain text configuration.

## System Architecture Decisions (Why custom UI over owo-lib)

### Stack and compatibility

- **Primary implementation:** Java, Fabric Loader and Fabric API, targeting Minecraft 1.21.11 in the current project.
- **Future 26.1.2 port:** treat Minecraft 26.1.2 as a separate compatibility target with its own build/source adapter where Minecraft, mappings, Java, and rendering APIs differ. Share only genuinely version-independent logic such as lyric parsing, track models, and configuration serialization. Never claim one jar supports both versions unless the toolchain proves it.
- Follow the versions and mappings declared in the relevant Gradle build. Check official Fabric/Minecraft APIs and the existing project source before using APIs from another version.
- Keep platform media providers isolated behind a small provider interface. Keep lyric sources selectable (LRCLIB and a direct, unofficial YouTube Music InnerTube client modeled on ytmusicapi's search -> next -> timed lyrics browse flow). The YouTube Music mode must not silently fall back to unrelated lyric catalogs. Keep network lookup, cache I/O, parsing, timing, configuration, UI, and rendering in separate modules. The InnerTube protocol is undocumented and can change; bound requests, validate search result identity, and report when timed lyrics are unavailable. Do not store browser cookies or Google auth tokens in plain config.
- Use YouTube Music API as the default lyric source for new configurations. Present YouTube Music API and LRCLIB in one explicit selector; never use two competing buttons or require repeated clicks to cycle through choices. Keep selector controls visually consistent with the custom glass UI.

### Native, purpose-built UI

Use lightweight custom screens and widgets built on Minecraft's native screen, draw, matrix, and render-command contexts. Do **not** introduce owo-lib or another generic UI framework for the control deck. Custom rendering is a product requirement: it allows the layout, transitions, glass treatment, and live 3D preview to share the same rendering context without bulky wrappers or extra runtime dependencies.

Use native matrix/rendering contexts and the supported world-render submission APIs for all 3D content. Keep render work on the render thread; never block rendering on network, disk, or media-session calls. Use bounded caches and asynchronous workers where needed. Do not mutate global graphics state without restoring it, and balance every matrix push with a pop on every control path.

Treat brief missing Windows media-session snapshots as transient: preserve the last track through a short grace period so lookup state does not reset. Deduplicate successful lyric lookups in a bounded cache and use capped exponential retry backoff; do not retry a no-result lookup every media poll.

The control interface is a settings and preview surface; the floating lyrics remain the primary in-game feature. Preserve readable gameplay and avoid large overlays or visual effects that obscure the player’s view.

## Strict UI/UX Style Guide (Layout specs, colors, typography, glassmorphism filters)

All new screens and substantial revisions must follow these standards.

### Violet Glass theme

- Use a restrained dark-purple foundation with translucent violet surfaces, luminous purple borders, and sparing lilac/cyan highlights. Suggested palette: near-black violet `#0D0A1A`, panel `#171126`, surface `#241832`, primary violet `#B968FF`, bright accent `#DCC5FF`, and muted text `#9E8CB5`.
- The default accent is violet, but users must be able to select alternate coherent UI palettes (including electric pink and cyan). Theme choice affects chrome and controls; lyric text color remains independently configurable.
- Create depth through layered translucent fills, narrow edge highlights, soft shadows, restrained bloom/glow, and subtle scanlines or grid marks. Avoid flat default-Minecraft button styling.
- Use rounded corners selectively on cards, panels, toggles, chips, and preview frames. Maintain consistent radii, padding, alignment, and visual hierarchy.
- Use backdrop blur where it can be implemented through a small native shader/render pass without destabilizing rendering or degrading frame time. Provide a layered translucent fallback for unsupported graphics settings. Never add a heavyweight UI or post-processing dependency solely for blur.
- Hover, focus, pressed, and disabled states must be visually distinct. Use smooth time-based easing for panel entrance, hover glow, and small state transitions. Animations should be brief, subtle, frame-rate independent, and must not delay input or lyric timing.
- Keep screens focused and uncluttered. Group related controls into pages and use explicit choice lists for enums (source, language, font, theme, animation) rather than click-to-cycle controls when the available choices are not obvious.
- Provide readable contrast and a reduced-motion or effects-off path when practical. Avoid glow that blooms over text or makes controls difficult to distinguish.

### Required control-deck layout

The main settings/preview screen must use a clear three-part composition:

1. **Left column — Selection Panel:** a scrollable grid/list of grouped settings and options. Keep labels concise, show current values inline, and provide clear selected/hover/focus states.
2. **Right column — 3D Viewport:** an unobstructed live preview area for the player/model and the lyric hologram. The preview must not be covered by option cards, decorative effects, or controls. It should use Minecraft's native model and world rendering facilities where available.
3. **Bottom panel — Testing Sandbox:** interactive controls to preview animation, color, placement, visibility, and other presentation settings without needing to leave the screen or wait for a song. Sandbox actions must not change live playback state unless explicitly labeled and requested.

Adapt responsively to window size: preserve the three functions and hierarchy, reduce spacing or use a deliberate compact layout at small resolutions, and allow scrolling instead of clipping controls. Do not let ornamental chrome consume the usable viewport.

### Typography and controls

- Prefer Minecraft's bundled font and asset/resource font definitions. Use typography with clear hierarchy: section labels, control labels, values, and helper text.
- Keep control-deck labels on Minecraft's standard UI font unless the user selects otherwise; font-family controls apply to lyric text and must not force the entire interface into the narrower uniform face.
- Keep lyric text readable at its actual in-world scale. Avoid shrinking lyric size globally to fit a decorative panel; wrap long lines and keep alignment stable during animation.
- Buttons and toggles should be custom-drawn components with rounded shapes, subtle violet edge glow, smooth hover/focus transitions, and meaningful ON/OFF or selected states. Preserve keyboard and controller navigation where supported.
- Color selection must include a usable hex input/picker with validation and a visible swatch. Invalid values should not crash or silently overwrite the previous valid setting.
- Expose font choices from available Minecraft font assets, text-size controls with both a slider and exact pixel entry, optional text shadow and stroke, and independent 3D/HUD backplate toggles with opacity control. Save the active configuration and named profile snapshots to the config directory; ship useful built-in profiles without overwriting user-edited copies, and provide an explicit reset-to-default action.
- Provide a words-per-display setting: `0` means show the whole line; positive values show that many words at a time, advancing with the lyric word timing.
- In 3D mode, every words-per-display group is a separate lyric spawn event with its own world anchor and entry animation. Do not mutate the displayed phrase in place at the previous group's anchor.
- Preserve each replaced 3D lyric as a fading world-anchored ghost for the configured hold duration (default 0.5 seconds); a zero duration disables lingering. Keep retained text, anchors, and render submissions bounded.
- Provide sky text on a distant spherical world-space shell. Place each lyric along the camera's forward direction when its segment begins, then translate its anchor horizontally with player movement while keeping both its Y and its direction fixed as the camera turns. Sky text must use normal depth-tested text layers so solid blocks occlude it; never submit a see-through pass for sky lyrics or their ghosts. Do not describe this as a true skybox shader.
- Word sync offers active-word highlighting or a pop-up segment animation. In Pop Up mode, `wordsPerLine=0` means one timed word at a time (never the full line); positive values reveal that many timed words per segment. Persist that choice in settings and every saved profile.
- The `POP_FADE` text animation rises from below while fading in, then the outgoing world-anchored ghost rises slightly and fades out during its hold interval.
- Default the word window to three words. Reveal the next group when playback reaches that group, creating a new lyric display segment at the same lyric anchor lifecycle used for other word windows. Retain `0` as the option for displaying a complete line.
- Keep text-size and word-count exact-entry apply actions on a separate full-width row beneath their fields; do not crowd them beside the numeric inputs.
- Expose English, Russian, Bulgarian, German, and Dutch UI language choices from the main settings screen. Keep media metadata (such as artist and song titles) unchanged.
- Respect configured color, shadow, backplate, billboard, animation, and timing options consistently across preview, HUD, and 3D rendering where those options apply.

## Core Component Specifications (Selection Panel, 3D Viewport, Testing Sandbox, The Soul Vault)

### Selection Panel

The left column is the primary route to settings. Group related options (media/lyrics, timing, 3D placement, appearance, HUD/visibility) and make the list scrollable. Each control must have an explicit label, current value/state, sensible bounds, and immediate preview feedback when safe. Avoid destructive reset behavior on ordinary clicks. Persist settings using the project's configuration approach when persistence is implemented, and migrate older config values safely.

The appearance controls must include sky placement and height, 3D distance, previous-text hold duration, font family, theme palette, text color, pixel-size slider and numeric entry, word-window size and numeric entry, shadow, stroke, separate HUD/3D backplate switches, and backplate opacity. Exact-entry apply buttons sit below their associated fields on their own row.

### 3D Viewport

The right column is a live, unobstructed preview of the player's model and holographic lyric treatment. Keep it isolated from settings hit targets. Preview rendering must use the same relevant layout, color, scale, and animation rules as the in-world renderer, while avoiding side effects such as changing the real anchor, playback position, or world state. Ensure matrix and render state are restored even if preview content is absent or an exception occurs.

### Testing Sandbox

The bottom panel is a small interactive preview/test suite. It should support representative sample lyrics and controls for animation, placement, text color, backplate, and visibility. Clearly distinguish simulated sample content from detected track data. Tests and preview playback must be deterministic enough to compare settings and must not start network requests or alter media playback.

### The Soul Vault

Provide **The Soul Vault** as a distinct tab or button in the control deck. It is the premium-feeling showcase for available visual treatments, presets, and effect styles; it is a presentation feature, not an assumption of real-money sales or entitlement enforcement. On interaction, use restrained drifting particles or smoke-like aura effects that remain behind readable content, respect reduced-effects settings, and stay within a strict particle/render budget. Do not make effects obscure the preview or interfere with settings navigation.

## Implementation Guidelines for AI Agents (Step-by-step logic rules an agent must follow when writing code for this app)

1. **Inspect before editing.** Read this file, the relevant source files, Gradle configuration, and existing UI/render conventions. Check for nested `AGENTS.md` instructions. Determine the actual target version and avoid assuming code from another Minecraft release is compatible.
2. **State the behavior being changed.** Identify the owning module (media, lyrics, timing, configuration, UI, HUD, or world rendering) and trace its data flow before making changes. Keep each change focused and avoid unrelated rewrites.
3. **Preserve architecture boundaries.** Network requests and cache I/O belong off the render thread; media polling belongs in the media provider/manager; timing calculations should consume stable track snapshots; rendering should consume prepared state and submit native draw commands. Share common code across versions only where APIs and semantics are genuinely independent.
4. **Design for stable lyric timing.** Use the media sample's playback position and receipt timestamp as the authoritative playhead sample, then interpolate with a monotonic clock between samples. Avoid a second low-pass/slew layer that adds lag or cumulative drift. Keep media polling bounded, avoid metadata waits for unrelated sessions, and never do network/disk work per render frame. Apply the configured sync offset once per line/word lookup from the same adjusted playhead.
5. **Design 3D anchors deliberately.** Distinguish world-static anchors from camera-relative presentation. A line intended to remain in the world must capture a world-space `Vec3d` at spawn and must not be rebuilt from the current camera every frame unless the selected mode explicitly requests that behavior. Keep movement, speed lead, follow thresholds, offsets, and collision behavior bounded and predictable. Never let a display-only feature push or constrain the player entity.
6. **Build UI from reusable native pieces.** Reuse custom panel, button, toggle, slider, color-picker, tooltip, and animation helpers rather than duplicating drawing logic. Keep components lightweight and avoid generic UI libraries. Every interactive control needs mouse and keyboard semantics, clear state feedback, and sensible focus behavior.
7. **Honor the three-column composition.** Keep the scrollable settings selection on the left, unobstructed live 3D viewport on the right, and sandbox at the bottom. At small sizes, adapt or scroll; do not silently omit a required region or let decorative layers cover it.
8. **Keep animation non-blocking.** Derive animation progress from monotonic time or frame delta, clamp it, and use eased interpolation. Animation must never add intentional pauses to lyric reveal, media polling, user input, or rendering. Respect effects/reduced-motion preferences and cap particle counts.
9. **Protect rendering state.** Balance matrix push/pop on all paths, restore shader/blend/depth/scissor state after custom drawing, guard absent world/player/camera/render context, and use supported native render submission APIs. Avoid allocations in per-frame hot paths where practical.
10. **Make configuration safe.** Clamp numeric values to documented ranges, validate enums and hex colors, provide defaults for missing/invalid fields, and keep old configuration readable when adding a setting. Save user profiles separately from active settings, never overwrite customized profiles on startup, and keep reset-to-default behavior explicit. Do not reset unrelated settings as a side effect of opening or changing a screen.
11. **Handle external data defensively.** Treat media metadata, remote lyric responses, cached files, and external text as untrusted input. Bound response sizes and timeouts, normalize track matching, handle missing or unsynced lyrics gracefully, and do not log private lyric/media data unnecessarily.
12. **Keep the UI honest.** Clearly distinguish loading, unavailable track, unavailable synced lyrics, and active lyric states. Do not imply that browser scraping, audio-frequency analysis, BPM detection, jukebox tethering, or other planned capability exists unless it is actually implemented and verified.
13. **Verify the relevant target.** Build with the repository's Gradle wrapper after code changes when practical. For a version port, build each target independently and confirm the correct output artifact. Do not claim runtime behavior has been visually verified unless it was run in Minecraft. Add or run tests only when requested by the user or required by applicable project instructions.
14. **Document layout intent.** Add short comments where screen geometry is calculated, explaining column/row placement and responsive constraints; do not litter obvious drawing code with redundant comments.
15. **Report concisely and accurately.** Summarize changed behavior, list key files, state build/runtime verification and limitations, and provide the produced artifact path when applicable. Call out unimplemented vision items rather than presenting them as completed features.
