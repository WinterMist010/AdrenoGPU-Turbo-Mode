package com.wintermist.adrenoperformancemanager;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

public class MainActivityTest {

    @Test
    public void testParseToMHz_HzValues() {
        assertEquals(Long.valueOf(800), MainActivity.parseToMHz("800000000"));
        assertEquals(Long.valueOf(600), MainActivity.parseToMHz("600000000"));
    }

    @Test
    public void testParseToMHz_KHzValues() {
        assertEquals(Long.valueOf(500), MainActivity.parseToMHz("500000"));
    }

    @Test
    public void testParseToMHz_MHzValues() {
        assertEquals(Long.valueOf(400), MainActivity.parseToMHz("400"));
    }

    @Test
    public void testParseToMHz_InvalidOrNull() {
        assertNull(MainActivity.parseToMHz(null));
        assertNull(MainActivity.parseToMHz(""));
        assertNull(MainActivity.parseToMHz("invalid"));
        assertNull(MainActivity.parseToMHz("0"));
        assertNull(MainActivity.parseToMHz("-100"));
    }

    @Test
    public void testGetFrequencyList() {
        String input = "800000000 300000000 500000000 300000000";
        List<Long> result = MainActivity.getFrequencyList(input);
        assertEquals(Arrays.asList(300L, 500L, 800L), result);
    }

    @Test
    public void testFormatFrequenciesToMHz() {
        String input = "800000000 300000000 500000000";
        String formatted = MainActivity.formatFrequenciesToMHz(input);
        assertEquals("300, 500, 800", formatted);
    }
}
