package com.aidebugger;

import java.util.Map;

/**
 * Interface for tools provided by mods to the AI Debugger.
 * Allows the AI to query live mod state, settings, registries, or calculations dynamically.
 */
public interface AiTool {
    /**
     * Unique identifier for the tool (e.g. "config_info", "enchantment_lookup").
     */
    String getName();

    /**
     * Description explaining what the tool does and what data it provides.
     */
    String getDescription();

    /**
     * Executes the tool with the given parameters and returns a formatted result.
     *
     * @param parameters key-value parameters passed to the tool (can be empty if no arguments needed).
     * @return string representation of the tool's result.
     */
    String execute(Map<String, String> parameters);
}
