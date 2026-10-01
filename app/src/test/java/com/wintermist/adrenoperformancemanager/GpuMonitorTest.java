package com.wintermist.adrenoperformancemanager;

import com.wintermist.adrenoperformancemanager.monitoring.CsvSessionExporter;
import com.wintermist.adrenoperformancemanager.monitoring.GpuMonitor;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class GpuMonitorTest {

    @Test
    public void testParseGfxInfoFramestats() {
        String mockOutput = "---PROFileData---\n" +
                "FLAGS,INTENDED_VSYNC,VSYNC,NEW_VSYNC,HANDLE_INPUT_START,ANIMATION_START,PERFORM_TRAVERSALS_START,DRAW_START,SYNC_QUEUED,SYNC_START,ISSUE_DRAW_COMMANDS_START,SWAP_BUFFERS,FRAME_COMPLETED,DEQUEUE_BUFFER_DURATION,QUEUE_BUFFER_DURATION,GPUTIME\n" +
                "0,1000000000,1000000000,1000000000,1000000000,1000000000,1000000000,1000000000,1000000000,1000000000,1000000000,1000000000,1016666666,0,0,0\n" +
                "0,2000000000,2000000000,2000000000,2000000000,2000000000,2000000000,2000000000,2000000000,2000000000,2000000000,2000000000,2033333333,0,0,0\n" +
                "---def---";

        List<Float> frameTimes = GpuMonitor.parseGfxInfoFramestats(mockOutput);
        assertEquals(2, frameTimes.size());
        assertEquals(16.666666f, frameTimes.get(0), 0.1f);
        assertEquals(33.333333f, frameTimes.get(1), 0.1f);
    }

    @Test
    public void testCalculateP95FrameTime() {
        List<Float> times = Arrays.asList(8.0f, 10.0f, 12.0f, 15.0f, 16.6f, 18.0f, 20.0f, 25.0f, 30.0f, 50.0f);
        double p95 = GpuMonitor.calculateP95FrameTime(times);
        assertEquals(50.0, p95, 0.1);
    }

    @Test
    public void testCsvExporter() throws IOException {
        File tempDir = Files.createTempDirectory("apm_test").toFile();
        GpuMonitor.MetricsSnapshot snap = new GpuMonitor.MetricsSnapshot(42.5, 600, 75.0, 16.6);

        String path = CsvSessionExporter.exportToCsv(Arrays.asList(snap), tempDir);
        assertNotNull(path);
        File csvFile = new File(path);
        assertTrue(csvFile.exists());

        List<String> lines = Files.readAllLines(csvFile.toPath());
        assertTrue(lines.size() >= 2);
        assertTrue(lines.get(0).startsWith("TimestampMs"));
        assertTrue(lines.get(1).contains("42.5,600,75.0,16.60"));
    }
}
