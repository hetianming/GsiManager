package com.example.gsimanager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

public final class Shell {

    public static final String GSI_TOOL = "/system/bin/gsi_tool";

    private Shell() {
    }

    public static boolean isRooted() {
        String[] paths = {
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/vendor/bin/su",
                "/system/bin/magisk",
                "/data/adb/magisk/magisk"
        };
        for (String p : paths) {
            if (new File(p).exists()) {
                return true;
            }
        }
        String out = exec("id", 8);
        return out != null && out.contains("uid=0");
    }

    public static String exec(String command) {
        return exec(command, 60);
    }

    public static String exec(String command, int timeoutSeconds) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroy();
                return null;
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return null;
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static String execWrite(String command, InputStream input, int timeoutSeconds) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            OutputStream os = process.getOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = input.read(buf)) != -1) {
                os.write(buf, 0, n);
            }
            os.flush();
            os.close();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder sb = new StringBuilder();
            char[] cbuf = new char[8192];
            int c;
            while ((c = reader.read(cbuf)) != -1) {
                sb.append(cbuf, 0, c);
            }
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroy();
                return null;
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception ignored) {
                }
            }
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
