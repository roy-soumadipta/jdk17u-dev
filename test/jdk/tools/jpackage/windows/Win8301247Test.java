/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.HelloApp;
import jdk.jpackage.test.TKit;

/**
 * Test that terminating of the parent app launcher process automatically
 * terminates child app launcher process.
 */

/*
 * @test
 * @summary Test case for JDK-8301247
 * @library ../helpers
 * @build jdk.jpackage.test.*
 * @build Win8301247Test
 * @requires (os.family == "windows")
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @run main/othervm/timeout=360 -Xmx512m  jdk.jpackage.test.Main
 *  --jpt-run=Win8301247Test
 */
public class Win8301247Test {

    @Test
    public void test() throws IOException, InterruptedException {
        JPackageCommand cmd = JPackageCommand.helloAppImage();

        // Launch the app in a way it doesn't exit to let us trap app laucnher
        // processes in the process list
        cmd.addArguments("--java-options", "-Djpackage.test.noexit=true");
        cmd.executeAndAssertImageCreated();

        if (!cmd.canRunLauncher("Not running the test")) {
            return;
        }

        ExecutorService exec = null;
        try {
            // Launch the app in a separate thread
            exec = Executors.newSingleThreadExecutor();
            exec.execute(() -> {
                HelloApp.executeLauncher(cmd);
            });

            // Wait a bit to let the app start
            Thread.sleep(10000);

            // Get PID of the main app launcher process
            final long pid = findMainAppLauncherPID(cmd, 2).get();

            // Kill the main app launcher process
            Executor.of("taskkill", "/F", "/PID", Long.toString(pid)).
                    dumpOutput(true).execute();

            // Wait a bit and check if child app launcher process is still running (it must NOT)
            Thread.sleep(5000);

            findMainAppLauncherPID(cmd, 0);
        } finally {
            if (exec != null) {
                exec.shutdown();
            }
        }
    }

    private static Optional<Long> findMainAppLauncherPID(JPackageCommand cmd,
            int expectedCount) {
        // Get the list of PIDs and PPIDs of app launcher processes. Run setWinRunWithEnglishOutput(true) for JDK-8344275.
        // powershell -NoLogo -NoProfile -NonInteractive -Command
        //   "Get-CimInstance Win32_Process -Filter \"Name = 'foo.exe'\" | select ProcessID,ParentProcessID"
        String command = "Get-CimInstance Win32_Process -Filter \\\"Name = '"
                + cmd.appLauncherPath().getFileName().toString()
                + "'\\\" | select ProcessID,ParentProcessID";
        List<String> output = Executor.of("powershell", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", command)
                .dumpOutput(true).saveOutput().executeAndGetOutput();

        if (expectedCount == 0) {
            if (output.size() < 1) {
                return Optional.empty();
            }
        }

        String[] headers = Stream.of(output.get(1).split("\\s+", 2)).map(
                String::trim).map(String::toLowerCase).toArray(String[]::new);
        Pattern pattern;
        if (headers[0].equals("parentprocessid") && headers[1].equals(
                "processid")) {
            pattern = Pattern.compile("^\\s+(?<ppid>\\d+)\\s+(?<pid>\\d+)$");
        } else if (headers[1].equals("parentprocessid") && headers[0].equals(
                "processid")) {
            pattern = Pattern.compile("^\\s+(?<pid>\\d+)\\s+(?<ppid>\\d+)$");
        } else {
            throw new RuntimeException(
                    "Unrecognizable output of \'Get-CimInstance Win32_Process\' command");
        }

        List<long[]> processes = output.stream().skip(3).map(line -> {
            Matcher m = pattern.matcher(line);
            long[] pids = null;
            if (m.matches()) {
                pids = new long[]{Long.parseLong(m.group("pid")), Long.
                    parseLong(m.group("ppid"))};
            }
            return pids;
        }).filter(Objects::nonNull).toList();

        TKit.assertEquals(expectedCount, processes.size(), String.format(
                "Check [%d] app launcher processes found running", expectedCount));

        switch (expectedCount) {
            case 2 -> {
                if (processes.get(0)[0] == processes.get(1)[1]) {
                    return Optional.of(processes.get(0)[0]);
                } else if (processes.get(1)[0] == processes.get(0)[1]) {
                    return Optional.of(processes.get(1)[0]);
                } else {
                    throw new RuntimeException(
                            "App launcher processes unrelated");
                }
            }
            default ->
                throw new IllegalArgumentException();
        }
    }
}
