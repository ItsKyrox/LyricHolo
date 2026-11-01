package dev.lyricholo.common;

/** Persist these values as JSON in config/lyricholo.json from each version adapter. */
public final class HoloConfig {
    public enum Anchor { PLAYER, WORLD, JUKEBOX }
    public float offsetX = 0, offsetY = 0.35f, offsetZ = -2.4f;
    public long syncOffsetMs = 0;
    public int historyLines = 1, lookaheadLines = 1;
    public int color = 0xFFC7A1FF;
    public Anchor anchor = Anchor.PLAYER;
    public boolean billboard = true, shadow = true, backplate = true;
    public boolean glitch = true, holoGrid = true, combatDim = true;
    public boolean bloom = true, vectorOrbit = false, kineticScale = true;
    public float intensity = 0.65f;
}
