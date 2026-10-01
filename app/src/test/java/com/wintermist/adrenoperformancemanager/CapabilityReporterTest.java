package com.wintermist.adrenoperformancemanager;

import com.wintermist.adrenoperformancemanager.capability.CapabilityReporter;
import com.wintermist.adrenoperformancemanager.privilege.NoneBackend;
import com.wintermist.adrenoperformancemanager.privilege.PrivilegeBackend;
import org.junit.Test;
import static org.junit.Assert.*;

public class CapabilityReporterTest {

    @Test
    public void testGenerateReport() {
        CapabilityReporter reporter = new CapabilityReporter();
        PrivilegeBackend backend = new NoneBackend();
        String report = reporter.generateReport(backend, "Adreno 740");

        assertNotNull(report);
        assertTrue(report.contains("Adreno 740"));
        assertTrue(report.contains("Tier 3"));
        assertTrue(report.contains("SYSFS PROBE RESULTS"));
    }
}
