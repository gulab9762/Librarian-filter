package com.aidebugger;

import net.minecraft.commands.CommandSourceStack;

import java.util.List;

/**
 * Interface implemented by mods/modpacks to feed diagnostic data into the AI debugger.
 */
public interface DiagnosticProvider {
    /**
     * Name of the mod or diagnostic feature (e.g. "Librarian Filter").
     */
    String getModName();

    /**
     * Prepares the diagnostic report containing system instructions, mod state, and logs.
     */
    DiagnosticReport createReport(CommandSourceStack source, String userQuery);

    /**
     * Pre-configured prompts suggested to the player in chat autocomplete.
     */
    List<String> getSuggestedPrompts();

    /**
     * Clears cached diagnostic state, recent failure snapshots, and log history.
     */
    default void clearContext() {}

    /**
     * Tools provided by this mod for the AI to query dynamically or execute.
     */
    default List<AiTool> getTools() {
        return List.of();
    }
}
