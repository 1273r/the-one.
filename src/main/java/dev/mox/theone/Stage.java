package dev.mox.theone;

import org.bukkit.Color;
import org.bukkit.potion.PotionEffectType;

public enum Stage {
    AWAKENING("AWAKENING", 0, 2, Color.fromRGB(245, 245, 255), 2, PotionEffectType.SPEED, 0),
    WITNESS("WITNESS", 2, 5, Color.fromRGB(65, 235, 255), 3, PotionEffectType.RESISTANCE, 0),
    ASCENDED("ASCENDED", 5, 10, Color.fromRGB(65, 135, 255), 4, PotionEffectType.STRENGTH, 0),
    EXALTED("EXALTED", 10, 15, Color.fromRGB(175, 80, 255), 5, PotionEffectType.SPEED, 1),
    DIVINE("DIVINE", 15, 20, Color.fromRGB(255, 105, 215), 6, PotionEffectType.RESISTANCE, 1),
    UNBOUND("UNBOUND", 20, 30, Color.fromRGB(255, 55, 70), 8, PotionEffectType.STRENGTH, 1),
    THE_ONE("THE ONE", 30, Integer.MAX_VALUE, Color.fromRGB(255, 205, 105), 10, PotionEffectType.STRENGTH, 1);

    private static final Color[] ONE_PALETTE = {
            Color.fromRGB(255, 205, 105),
            Color.fromRGB(255, 65, 85),
            Color.fromRGB(75, 235, 255),
            Color.fromRGB(185, 90, 255),
            Color.fromRGB(255, 245, 225)
    };

    private final String displayName;
    private final int minimumKills;
    private final int nextThreshold;
    private final Color color;
    private final int rings;
    private final PotionEffectType buff;
    private final int amplifier;

    Stage(String displayName, int minimumKills, int nextThreshold, Color color,
          int rings, PotionEffectType buff, int amplifier) {
        this.displayName = displayName;
        this.minimumKills = minimumKills;
        this.nextThreshold = nextThreshold;
        this.color = color;
        this.rings = rings;
        this.buff = buff;
        this.amplifier = amplifier;
    }

    public static Stage fromKills(int kills) {
        Stage selected = AWAKENING;
        for (Stage stage : values()) {
            if (kills >= stage.minimumKills) selected = stage;
        }
        return selected;
    }

    public String displayName() { return displayName; }
    public int minimumKills() { return minimumKills; }
    public int nextThreshold() { return nextThreshold; }
    public Color color() { return color; }
    public int rings() { return rings; }
    public PotionEffectType buff() { return buff; }
    public int amplifier() { return amplifier; }

    public Color ringColor(int ringIndex, int pointIndex, int pointCount) {
        if (this != THE_ONE) return color;
        int paletteIndex = Math.floorMod(ringIndex * 2 + (pointIndex * ONE_PALETTE.length / Math.max(1, pointCount)),
                ONE_PALETTE.length);
        return ONE_PALETTE[paletteIndex];
    }

    public int progressPercent(int kills) {
        if (this == THE_ONE) return 100;
        int span = Math.max(1, nextThreshold - minimumKills);
        return Math.max(0, Math.min(100, (kills - minimumKills) * 100 / span));
    }

    public String nextStageName() {
        int index = ordinal() + 1;
        return index >= values().length ? "MAXIMUM EVOLUTION" : values()[index].displayName;
    }
}
