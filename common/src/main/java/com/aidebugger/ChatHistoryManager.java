package com.aidebugger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages in-memory and local disk persistence for AI chat history.
 * Allows the LLM to maintain conversational memory across follow-up queries.
 */
public class ChatHistoryManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatHistoryManager.class);
    private static final File HISTORY_FILE = new File("config/ai_chat_history.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_HISTORY_ENTRIES = 12;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public record ChatTurn(String timestamp, String player, String query, String response) {}

    private static final List<ChatTurn> TURNS = new ArrayList<>();
    private static boolean loaded = false;

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!HISTORY_FILE.exists()) return;

        try (FileReader reader = new FileReader(HISTORY_FILE)) {
            Type listType = new TypeToken<ArrayList<ChatTurn>>() {}.getType();
            List<ChatTurn> saved = GSON.fromJson(reader, listType);
            if (saved != null) {
                TURNS.addAll(saved);
            }
        } catch (Exception e) {
            LOGGER.warn("[AI Chat History] Failed to load previous chat history: {}", e.getMessage());
        }
    }

    public static synchronized void record(String player, String query, String response) {
        ensureLoaded();
        String time = LocalDateTime.now().format(TIME_FORMAT);
        TURNS.add(new ChatTurn(time, player, query, response));

        while (TURNS.size() > MAX_HISTORY_ENTRIES) {
            TURNS.remove(0);
        }

        saveToDisk();
    }

    public static synchronized String getRecentHistoryFormatted(int maxTurns) {
        ensureLoaded();
        if (TURNS.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("--- RECENT CONVERSATION HISTORY ---\n");
        int start = Math.max(0, TURNS.size() - maxTurns);
        for (int i = start; i < TURNS.size(); i++) {
            ChatTurn turn = TURNS.get(i);
            sb.append("Player (").append(turn.player()).append("): ").append(turn.query()).append("\n");
            sb.append("AI: ").append(turn.response()).append("\n\n");
        }
        return sb.toString().trim();
    }

    public static synchronized void clear() {
        TURNS.clear();
        loaded = true;
        try {
            if (HISTORY_FILE.exists()) {
                HISTORY_FILE.delete();
            }
        } catch (Exception e) {
            LOGGER.warn("[AI Chat History] Failed to delete history file: {}", e.getMessage());
        }
    }

    private static void saveToDisk() {
        try {
            File parent = HISTORY_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(HISTORY_FILE)) {
                GSON.toJson(TURNS, writer);
            }
        } catch (Exception e) {
            LOGGER.warn("[AI Chat History] Failed to save chat history: {}", e.getMessage());
        }
    }
}
