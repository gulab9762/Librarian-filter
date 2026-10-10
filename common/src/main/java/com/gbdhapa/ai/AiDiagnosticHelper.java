package com.gbdhapa.ai;

import com.aidebugger.AiDebugger;
import com.gbdhapa.config.TradeConfig;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Mod-side bridge that delegates command execution and suggestions
 * to the independent com.aidebugger engine.
 */
public class AiDiagnosticHelper {

    public static final List<String> SUGGESTED_PROMPTS = LibrarianDiagnosticProvider.SUGGESTED_PROMPTS;

    public static int handleCommand(CommandSourceStack source, String userPrompt) {
        return AiDebugger.handleCommand(
                source,
                userPrompt,
                LibrarianDiagnosticProvider.INSTANCE,
                TradeConfig.INSTANCE.ollamaUrl,
                TradeConfig.INSTANCE.ollamaModel
        );
    }

    public static int clearContext(CommandSourceStack source) {
        return AiDebugger.clearContext(source, LibrarianDiagnosticProvider.INSTANCE);
    }

    public static CompletableFuture<Suggestions> suggestPrompts(SuggestionsBuilder builder) {
        return AiDebugger.suggestPrompts(builder, LibrarianDiagnosticProvider.INSTANCE);
    }
}
