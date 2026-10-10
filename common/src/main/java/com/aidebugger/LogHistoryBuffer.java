package com.aidebugger;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Universal thread-safe circular log buffer and latest.log file reader.
 * Can be used by any mod/modpack to capture diagnostic history.
 */
public class LogHistoryBuffer {
    private final int maxEntries;
    private final ConcurrentLinkedDeque<String> entries = new ConcurrentLinkedDeque<>();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    public LogHistoryBuffer(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public void append(String level, String message) {
        String time = LocalTime.now().format(timeFormatter);
        entries.addLast("[" + time + " " + level + "] " + message);
        while (entries.size() > maxEntries) {
            entries.pollFirst();
        }
    }

    public List<String> getRecentEntries() {
        return new ArrayList<>(entries);
    }

    /**
     * Reads recent lines from the game's logs/latest.log file.
     */
    public static String readLatestLogTail(int maxLines, String... keywords) {
        File logFile = new File("logs/latest.log");
        if (!logFile.exists() || !logFile.canRead()) {
            return "(logs/latest.log not found)";
        }

        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            long length = raf.length();
            if (length == 0) return "(logs/latest.log is empty)";

            long readLength = Math.min(length, 32768);
            raf.seek(length - readLength);
            byte[] bytes = new byte[(int) readLength];
            raf.readFully(bytes);

            String text = new String(bytes, StandardCharsets.UTF_8);
            String[] lines = text.split("\r?\n");
            List<String> result = new ArrayList<>();

            for (int i = Math.max(0, lines.length - maxLines); i < lines.length; i++) {
                String line = lines[i];
                if (keywords == null || keywords.length == 0) {
                    result.add(line);
                } else {
                    boolean match = false;
                    for (String kw : keywords) {
                        if (line.toLowerCase().contains(kw.toLowerCase())) {
                            match = true;
                            break;
                        }
                    }
                    if (match || line.contains("WARN") || line.contains("ERROR")) {
                        result.add(line);
                    }
                }
            }

            if (result.isEmpty()) {
                int start = Math.max(0, lines.length - 15);
                for (int i = start; i < lines.length; i++) {
                    result.add(lines[i]);
                }
            }

            return String.join("\n", result);
        } catch (Exception e) {
            return "(Failed to read logs/latest.log: " + e.getMessage() + ")";
        }
    }
}
