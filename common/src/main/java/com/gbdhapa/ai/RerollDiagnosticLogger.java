package com.gbdhapa.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

public class RerollDiagnosticLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("LibrarianFilter");
    private static final int MAX_LOG_HISTORY = 50;
    private static final ConcurrentLinkedDeque<String> LOG_HISTORY = new ConcurrentLinkedDeque<>();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static volatile DiagnosticSnapshot latestSnapshot = null;

    public enum Outcome {
        SUCCESS,
        FAILED_NO_MATCH,
        NO_WORKSTATION,
        NO_SIGN,
        EMPTY_SIGN,
        INVALID_FILTER,
        NO_VILLAGER,
        VILLAGER_LOCKED,
        DISABLED,
        COOLDOWN
    }

    public record DiagnosticSnapshot(
            String timestamp,
            String playerName,
            BlockPos playerPos,
            BlockPos clickedPos,
            String clickedBlock,
            String currentBiome,
            BlockPos signPos,
            List<String> rawSignLines,
            List<String> parsedFilters,
            int nearbyVillagersCount,
            boolean villagerFound,
            String villagerProfession,
            String villagerType,
            int villagerXp,
            int villagerLevel,
            boolean tradesLocked,
            int existingOffersCount,
            boolean treasureEnchantmentsAllowed,
            boolean tradeRebalanceActive,
            Outcome outcome,
            String reason
    ) {}

    public static void info(String message, Object... args) {
        String formatted = formatMessage(message, args);
        LOGGER.info(formatted);
        recordLogEntry("INFO", formatted);
    }

    public static void warn(String message, Object... args) {
        String formatted = formatMessage(message, args);
        LOGGER.warn(formatted);
        recordLogEntry("WARN", formatted);
    }

    public static void error(String message, Object... args) {
        String formatted = formatMessage(message, args);
        LOGGER.error(formatted);
        recordLogEntry("ERROR", formatted);
    }

    private static void recordLogEntry(String level, String msg) {
        String time = LocalTime.now().format(TIME_FORMAT);
        String entry = "[" + time + " " + level + "] " + msg;
        LOG_HISTORY.addLast(entry);
        while (LOG_HISTORY.size() > MAX_LOG_HISTORY) {
            LOG_HISTORY.pollFirst();
        }
    }

    public static void recordSnapshot(DiagnosticSnapshot snapshot) {
        latestSnapshot = snapshot;
    }

    public static DiagnosticSnapshot getLatestSnapshot() {
        return latestSnapshot;
    }

    public static List<String> getRecentLogs() {
        return new ArrayList<>(LOG_HISTORY);
    }

    public static void clearContext() {
        latestSnapshot = null;
        LOG_HISTORY.clear();
        LOGGER.info("[LibrarianFilter] Cleared AI diagnostic context and reroll history.");
    }

    public static String getRecentLatestLogFileTail(int maxLines) {
        File logFile = new File("logs/latest.log");
        if (!logFile.exists() || !logFile.canRead()) {
            return "(logs/latest.log not found or not readable)";
        }

        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            long length = raf.length();
            if (length == 0) return "(logs/latest.log is empty)";

            // Read up to last 32KB
            long readLength = Math.min(length, 32768);
            long startPos = length - readLength;
            raf.seek(startPos);
            byte[] bytes = new byte[(int) readLength];
            raf.readFully(bytes);

            String text = new String(bytes, StandardCharsets.UTF_8);
            String[] lines = text.split("\r?\n");
            List<String> relevant = new ArrayList<>();

            for (int i = Math.max(0, lines.length - maxLines); i < lines.length; i++) {
                String line = lines[i];
                if (line.toLowerCase().contains("librarian") ||
                    line.toLowerCase().contains("reroll") ||
                    line.toLowerCase().contains("villager") ||
                    line.contains("WARN") ||
                    line.contains("ERROR")) {
                    relevant.add(line);
                }
            }

            if (relevant.isEmpty()) {
                // If no filtered lines matched, return last 15 raw lines
                int start = Math.max(0, lines.length - 15);
                for (int i = start; i < lines.length; i++) {
                    relevant.add(lines[i]);
                }
            }

            return String.join("\n", relevant);
        } catch (Exception e) {
            return "(Failed to read logs/latest.log: " + e.getMessage() + ")";
        }
    }

    private static String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        for (Object arg : args) {
            int idx = message.indexOf("{}");
            if (idx != -1) {
                message = message.substring(0, idx) + (arg == null ? "null" : arg.toString()) + message.substring(idx + 2);
            }
        }
        return message;
    }
}
