package dev.mox.theone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StageTest {
    @Test
    void progressionUsesOriginalSevenThresholds() {
        assertEquals(Stage.AWAKENING, Stage.fromKills(0));
        assertEquals(Stage.AWAKENING, Stage.fromKills(1));
        assertEquals(Stage.WITNESS, Stage.fromKills(2));
        assertEquals(Stage.ASCENDED, Stage.fromKills(5));
        assertEquals(Stage.EXALTED, Stage.fromKills(10));
        assertEquals(Stage.DIVINE, Stage.fromKills(15));
        assertEquals(Stage.UNBOUND, Stage.fromKills(20));
        assertEquals(Stage.THE_ONE, Stage.fromKills(30));
        assertEquals(Stage.THE_ONE, Stage.fromKills(999));
    }

    @Test
    void progressBarPercentageIsBoundedAndStageAware() {
        assertEquals(0, Stage.AWAKENING.progressPercent(0));
        assertEquals(50, Stage.AWAKENING.progressPercent(1));
        assertEquals(100, Stage.THE_ONE.progressPercent(30));
        assertEquals(100, Stage.THE_ONE.progressPercent(500));
    }

    @Test
    void stagesWithoutListedBuffsDoNotInventOne() {
        assertNull(Stage.DIVINE.buffKey());
        assertNull(Stage.THE_ONE.buffKey());
    }

    @Test
    void ringCountsMatchTheOriginalDesign() {
        assertEquals(2, Stage.AWAKENING.rings());
        assertEquals(3, Stage.WITNESS.rings());
        assertEquals(4, Stage.ASCENDED.rings());
        assertEquals(5, Stage.EXALTED.rings());
        assertEquals(6, Stage.DIVINE.rings());
        assertEquals(8, Stage.UNBOUND.rings());
        assertEquals(10, Stage.THE_ONE.rings());
    }
}
