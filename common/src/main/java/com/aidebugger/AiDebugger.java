package com.aidebugger;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Universal AI Debugger engine for Minecraft.
 * Coordinates local LLM interaction, diagnostic prompt construction,
 * autocomplete suggestions, and interactive player chat output.
 */
public class AiDebugger {
    private static final Logger LOGGER = LoggerFactory.getLogger("AiDebugger");

    public static int handleCommand(CommandSourceStack source, String userPrompt, DiagnosticProvider provider,
                                    String ollamaUrl, String defaultModel) {
        ServerPlayer player = null;
        try {
            player = source.getPlayer();
        } catch (Exception ignored) {}

        final ServerPlayer targetPlayer = player;
        final String modName = provider != null ? provider.getModName() : "AI Helper";
        final DiagnosticReport report = provider != null ? provider.createReport(source, userPrompt) : null;
        final String targetModel = (defaultModel != null && !defaultModel.trim().isEmpty()) ? defaultModel : "qwen2.5-coder:7b";

        String query = userPrompt;
        if (query == null || query.trim().isEmpty()) {
            query = (report != null && report.defaultQuery() != null)
                    ? report.defaultQuery()
                    : "Why is the action not working? Please check my recent attempts.";
        }
        final String finalQuery = query.trim();

        // Check if player requested to clear diagnostic context
        if ("clear".equalsIgnoreCase(finalQuery) || "clear context".equalsIgnoreCase(finalQuery)
                || "clear-context".equalsIgnoreCase(finalQuery) || "reset".equalsIgnoreCase(finalQuery)) {
            return clearContext(source, provider);
        }

        // 1. Log prompt to console/latest.log and display in in-game chat
        LOGGER.info("[{} AI] Player '{}' requested AI prompt: \"{}\"",
                modName, (targetPlayer != null ? targetPlayer.getScoreboardName() : "Server"), finalQuery);

        sendMessage(source, targetPlayer,
                Component.literal("[" + modName + " AI] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Prompt: ").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("\"" + finalQuery + "\"").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)));
        sendMessage(source, targetPlayer,
                Component.literal("[" + modName + " AI] Thinking with local AI...").withStyle(ChatFormatting.GRAY));

        // 2. Check if Ollama is running
        OllamaClient.checkStatus(ollamaUrl).thenAccept(status -> {
            if (!status.isRunning()) {
                LOGGER.warn("[{} AI] Local Ollama is not running at {} (cause: {})", modName, ollamaUrl, status.getErrorMessage());
                sendOllamaNotRunningMessage(source, targetPlayer, modName, targetModel);
                return;
            }

            final String workingUrl = status.getWorkingUrl() != null ? status.getWorkingUrl() : ollamaUrl;

            // Resolve installed model
            String chosenModel = targetModel;
            List<String> availableModels = status.getAvailableModels();

            if (availableModels.isEmpty()) {
                MutableComponent noModelMsg = Component.literal("[" + modName + " AI] Ollama is running, but no models are downloaded yet!\n")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("Run this command in terminal to download the recommended model:\n  ").withStyle(ChatFormatting.WHITE))
                        .append(copyableTerminalCommand("ollama run " + targetModel))
                        .append(Component.literal("\n  (Or click to copy alternative: ").withStyle(ChatFormatting.GRAY))
                        .append(copyableTerminalCommand("ollama run llama3.2"))
                        .append(Component.literal(")").withStyle(ChatFormatting.GRAY));
                sendMessage(source, targetPlayer, noModelMsg);
                return;
            }

            boolean modelFound = false;
            for (String m : availableModels) {
                if (m.equalsIgnoreCase(chosenModel) || m.startsWith(chosenModel + ":") || chosenModel.startsWith(m + ":")) {
                    chosenModel = m;
                    modelFound = true;
                    break;
                }
            }

            if (!modelFound) {
                String fallback = availableModels.stream()
                        .filter(m -> m.contains("coder") || m.contains("qwen") || m.contains("llama") || m.contains("gemma") || m.contains("mistral"))
                        .findFirst()
                        .orElse(availableModels.getFirst());
                chosenModel = fallback;
            }

            final String activeModel = chosenModel;

            // 3. Build diagnostic query with tools and recent conversation history
            String systemPrompt = (report != null && report.systemPrompt() != null)
                    ? report.systemPrompt()
                    : DiagnosticReport.DEFAULT_ONE_LINER_SYSTEM_PROMPT;

            String context = (report != null && report.diagnosticContext() != null)
                    ? report.diagnosticContext()
                    : "";

            List<AiTool> tools = provider != null ? provider.getTools() : List.of();
            StringBuilder toolsSection = new StringBuilder();
            if (!tools.isEmpty()) {
                toolsSection.append("--- MOD TOOLS & LIVE DATA ---\n");
                for (AiTool tool : tools) {
                    toolsSection.append("[Tool: ").append(tool.getName()).append("]\n");
                    toolsSection.append("Description: ").append(tool.getDescription()).append("\n");
                    try {
                        String result = tool.execute(java.util.Map.of());
                        if (result != null && !result.isBlank()) {
                            toolsSection.append("Data:\n").append(result.trim()).append("\n");
                        }
                    } catch (Exception e) {
                        LOGGER.warn("[{} AI] Error executing tool '{}': {}", modName, tool.getName(), e.getMessage());
                    }
                    toolsSection.append("\n");
                }
            }

            String history = ChatHistoryManager.getRecentHistoryFormatted(6);
            String fullUserQuery = (history.isEmpty() ? "" : history + "\n\n")
                    + (toolsSection.isEmpty() ? "" : toolsSection + "\n")
                    + "Player Question:\n" + finalQuery
                    + "\n\n(Context & In-Game Reference):\n" + context;

            LOGGER.info("[{} AI] Querying Ollama (model: '{}'):\nSystem: {}\nQuery: {}",
                    modName, activeModel, systemPrompt, fullUserQuery);

            // 4. Send request to Ollama
            OllamaClient.generate(workingUrl, activeModel, systemPrompt, fullUserQuery)
                    .thenAccept(response -> {
                        ChatHistoryManager.record(
                                targetPlayer != null ? targetPlayer.getScoreboardName() : "Player",
                                finalQuery,
                                response
                        );
                        sendAiResponse(source, targetPlayer, modName, finalQuery, response);
                    });
        });

        return 1;
    }

    public static int clearContext(CommandSourceStack source, DiagnosticProvider provider) {
        ServerPlayer player = null;
        try {
            player = source.getPlayer();
        } catch (Exception ignored) {}

        final String modName = provider != null ? provider.getModName() : "AI Helper";
        if (provider != null) {
            provider.clearContext();
        }
        ChatHistoryManager.clear();

        LOGGER.info("[{} AI] Player '{}' cleared AI diagnostic context, reroll history, and chat history.",
                modName, (player != null ? player.getScoreboardName() : "Server"));

        MutableComponent prefix = Component.literal("[" + modName + " AI] ").withStyle(ChatFormatting.GOLD);
        MutableComponent msg = Component.literal("Diagnostic context and chat history have been cleared! AI is now reset to a clean slate.")
                .withStyle(ChatFormatting.GREEN);

        sendMessage(source, player, prefix.append(msg));
        return 1;
    }

    public static CompletableFuture<Suggestions> suggestPrompts(SuggestionsBuilder builder, DiagnosticProvider provider) {
        String remaining = builder.getRemaining().toLowerCase();
        List<String> suggestions = (provider != null) ? provider.getSuggestedPrompts() : List.of();
        for (String s : suggestions) {
            if (remaining.isEmpty() || s.toLowerCase().startsWith(remaining) || s.toLowerCase().contains(remaining)) {
                builder.suggest(s);
            }
        }
        return builder.buildFuture();
    }

    private static void sendOllamaNotRunningMessage(CommandSourceStack source, ServerPlayer player, String modName, String model) {
        MutableComponent header = Component.literal("\n=== [" + modName + " AI Helper] ===").withStyle(ChatFormatting.GOLD);
        MutableComponent line1 = Component.literal("\nLocal LLM is NOT running!").withStyle(ChatFormatting.RED);
        MutableComponent line2 = Component.literal("\nTo enable the free in-game AI diagnostic helper:\n").withStyle(ChatFormatting.YELLOW);

        MutableComponent dlStep = Component.literal("1. Download Ollama: ").withStyle(ChatFormatting.WHITE);
        MutableComponent dlLink;
        try {
            dlLink = Component.literal("https://ollama.com")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://ollama.com")))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to open Ollama website"))));
        } catch (Exception e) {
            dlLink = Component.literal("https://ollama.com").withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE);
        }
        dlStep.append(dlLink);

        MutableComponent cmdStep = Component.literal("\n2. In Terminal or Command Prompt, run:\n   ").withStyle(ChatFormatting.WHITE)
                .append(copyableTerminalCommand("ollama run " + model))
                .append(Component.literal("\n   (Click above to copy command! Free local AI for troubleshooting)\n").withStyle(ChatFormatting.DARK_GRAY));

        MutableComponent askStep = Component.literal("3. Keep Ollama running and ask your question again!\n==============================\n").withStyle(ChatFormatting.GOLD);

        MutableComponent full = header.append(line1).append(line2).append(dlStep).append(cmdStep).append(askStep);
        sendMessage(source, player, full);
    }

    private static void sendAiResponse(CommandSourceStack source, ServerPlayer player, String modName, String originalQuery, String response) {
        if (response == null || response.trim().isEmpty()) {
            LOGGER.warn("[{} AI] Received empty diagnosis from LLM for query: \"{}\"", modName, originalQuery);
            sendMessage(source, player, Component.literal("[" + modName + " AI] Received empty diagnosis from LLM.").withStyle(ChatFormatting.RED));
            return;
        }

        // Clean response: remove <think> tags if any
        String cleaned = response.replaceAll("(?s)<think>.*?</think>", "").trim();

        // Strip enclosing quotes if model wrapped output in quotes
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() > 2) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }

        // Remove bullet markers at the beginning of lines
        cleaned = cleaned.replaceAll("^[#\\*\\-\\s]+", "");

        LOGGER.info("[{} AI] Received AI response for query \"{}\":\n{}", modName, originalQuery, cleaned);

        MutableComponent prefix = Component.literal("[" + modName + " AI] ").withStyle(ChatFormatting.GOLD);
        MutableComponent answer = formatInteractiveComponent(cleaned);

        sendMessage(source, player, prefix.append(answer));
    }

    /**
     * Converts raw text, commands, and markdown into an interactive Minecraft component
     * using the ResponseParser utility.
     */
    public static MutableComponent formatInteractiveComponent(String text) {
        return ResponseParser.parse(text);
    }

    /**
     * Creates a text component that copies the terminal command to clipboard when clicked.
     */
    public static MutableComponent copyableTerminalCommand(String text) {
        return ResponseParser.createTerminalCommandComponent(text);
    }

    /**
     * Creates a text component that runs the in-game command immediately when clicked.
     */
    public static MutableComponent clickableRunCommand(String command) {
        return ResponseParser.createInGameCommandComponent(command);
    }

    private static void sendMessage(CommandSourceStack source, ServerPlayer player, Component component) {
        if (player != null) {
            player.sendSystemMessage(component);
        } else {
            source.sendSuccess(() -> component, false);
        }
    }
}
