package dev.lyricholo.common;

/** Persisted by HoloConfigStore in config/lyricholo/settings.json and profile snapshots. */
public final class HoloConfig {
    public enum Anchor { PLAYER, WORLD, JUKEBOX }
    public enum TextAnimation { NONE, FADE, GLITCH, POP, POP_FADE, TYPEWRITER }
    public enum FontStyle { DEFAULT, UNIFORM, PACIFICO, SMOOTH }
    public enum UiTheme { VIOLET, PINK, CYAN, EMERALD }
    public enum LyricsProvider { LRCLIB, YOUTUBE_MUSIC }
    public enum WordSyncStyle { HIGHLIGHT, POP_UP, STACK }
    public float offsetX = 0, offsetY = 0.2f, offsetZ = -2.8f;
    public float anchorFollow3D = 0.65f, sideSpread3D = 24.0f, speedLead3D = 0.08f;
    public long syncOffsetMs = 0;
    /** How long the previous floating lyric remains after a new one spawns. */
    public int oldTextHoldMs = 500;
    public int historyLines = 1, lookaheadLines = 1;
    public int color = 0xFFC7A1FF;
    public int textSizePx = 48;
    /** Number of words visible at a time; 0 shows the full lyric line. */
    public int wordsPerLine = 3;
    public int backplateOpacity = 54;
    public int strokeOpacity = 220;
    public float skyHeight = 8.0f;
    /** Bumped only when the default sky-plane height changes, for safe config migration. */
    public int skyAnchorVersion = 2;
    /** Radius of the world-space shell used to make sky lyrics track the celestial panorama. */
    public float skyShellRadius = 256.0f;
    public FontStyle fontStyle = FontStyle.SMOOTH;
    /** One-time migration marker for the improved antialiased lyric font default. */
    public int fontQualityVersion = 1;
    public UiTheme uiTheme = UiTheme.VIOLET;
    public LyricsProvider lyricsProvider = LyricsProvider.YOUTUBE_MUSIC;
    public WordSyncStyle wordSyncStyle = WordSyncStyle.HIGHLIGHT;
    public String language = "ENGLISH";
    public Anchor anchor = Anchor.PLAYER;
    public boolean billboard = true, shadow = true, textStroke = false, skyMode = true;
    public boolean worldBackplate = true, cornerBackplate = true, lyricsEnabled = true;
    public boolean keepOnScreen3D = true;
    public boolean screenSticky3D = false;
    public boolean keepAhead3D = true;
    public float maxOnScreenAngle = 34.0f;
    public boolean cornerLyrics = true;
    public boolean wordSync = true;
    public boolean glitch = true, holoGrid = true, combatDim = true;
    public boolean bloom = true, vectorOrbit = false, kineticScale = true;
    public float intensity = 0.65f;
    public TextAnimation textAnimation = TextAnimation.POP;
}
