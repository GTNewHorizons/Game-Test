package com.gtnewhorizons.horizonqa.report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import org.apache.logging.log4j.LogManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RunReportWriterTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void junitFailureIsIncludedInLaterStatusReport() throws Exception {
        File junitDirectory = temporaryFolder.newFolder("junit-target");
        File statusFile = new File(temporaryFolder.getRoot(), "status.json");

        RunResult written = RunReportWriter.write(result(), junitDirectory, statusFile, LogManager.getLogger());

        assertEquals(2, written.exitCode());
        assertEquals(
            "REPORT_WRITE_ERROR",
            written.issues()
                .get(0)
                .kind());
        assertTrue(statusFile.isFile());
        String status = new String(Files.readAllBytes(statusFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(status.contains("REPORT_WRITE_ERROR"));
    }

    @Test
    public void runtimeFailureInOneSinkDoesNotSkipLaterSinks() throws Exception {
        File statusFile = new File(temporaryFolder.getRoot(), "status.json");

        RunResult written = RunReportWriter.write(result(), null, statusFile, LogManager.getLogger());

        assertEquals(2, written.exitCode());
        assertEquals(
            "reporting:junit",
            written.issues()
                .get(0)
                .id());
        assertTrue(statusFile.isFile());
    }

    private static RunResult result() {
        return RunResult.completedCases("ci", Collections.emptyList(), Collections.emptyList(), "TEST.xml");
    }
}
