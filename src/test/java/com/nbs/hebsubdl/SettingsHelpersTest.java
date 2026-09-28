package com.nbs.hebsubdl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettingsHelpersTest {

    @Test
    void logLevelAliasesMapToTheComboEntries() {
        assertEquals("finest", SettingsDialog.normalizeLogLevel("DEBUG"));
        assertEquals("severe", SettingsDialog.normalizeLogLevel("error"));
        assertEquals("fine", SettingsDialog.normalizeLogLevel(" Fine "));
        assertEquals("info", SettingsDialog.normalizeLogLevel(null));
        assertEquals("info", SettingsDialog.normalizeLogLevel("verbose"));
    }

    @Test
    void watchDirectoriesAreSplitAndTrimmed() {
        assertEquals(List.of("D:\\Movies", "D:\\TV Shows"), WatchDirsDialog.parse("D:\\Movies, D:\\TV Shows,"));
        assertTrue(WatchDirsDialog.parse(null).isEmpty());
        assertTrue(WatchDirsDialog.parse("  ").isEmpty());
    }

    @Test
    void durationsReadNaturally() {
        assertEquals("0s", MainGUI.formatDuration(0));
        assertEquals("45s", MainGUI.formatDuration(44_600));
        assertEquals("1m 05s", MainGUI.formatDuration(65_000));
        assertEquals("2h 03m", MainGUI.formatDuration((2 * 3600 + 3 * 60 + 10) * 1000L));
    }
}
