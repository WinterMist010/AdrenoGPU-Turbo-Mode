package com.wintermist.adrenoperformancemanager.monitoring;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CsvSessionExporter {

    public static String exportToCsv(List<GpuMonitor.MetricsSnapshot> snapshots, File outputDir) throws IOException {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File csvFile = new File(outputDir, "apm_session_" + timestamp + ".csv");

        try (FileWriter writer = new FileWriter(csvFile)) {
            writer.write("TimestampMs,GpuTempC,GpuFreqMHz,GpuBusyPercent,P95FrameTimeMs\n");
            if (snapshots != null) {
                for (GpuMonitor.MetricsSnapshot snap : snapshots) {
                    writer.write(String.format(Locale.US, "%d,%.1f,%d,%.1f,%.2f\n",
                            snap.timestampMs, snap.temperatureC, snap.frequencyMhz, snap.busyPercent, snap.p95FrameTimeMs));
                }
            }
        }

        return csvFile.getAbsolutePath();
    }
}
