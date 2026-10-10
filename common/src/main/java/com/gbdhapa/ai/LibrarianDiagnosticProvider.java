package com.gbdhapa.ai;

import com.aidebugger.DiagnosticProvider;
import com.aidebugger.DiagnosticReport;
import com.gbdhapa.config.TradeConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.flag.FeatureFlags;

import java.util.List;

/**
 * Librarian Filter mod's implementation of DiagnosticProvider.
 * Supplies mod telemetry context: latest reroll attempt, mod settings, and recent logs.
 * No hardcoded biases; allows the LLM to deduce solutions naturally.
 */
public class LibrarianDiagnosticProvider implements DiagnosticProvider {

    public static final LibrarianDiagnosticProvider INSTANCE = new LibrarianDiagnosticProvider();

    public static final List<String> SUGGESTED_PROMPTS = List.of(
            "why is reroll not working",
            "why did price filter fail",
            "give command to generate reroll setup",
            "clear",
            "mod not working",
            "mending not showing",
            "reroll book not working",
            "why is villager angry",
            "how to write on sign",
            "why are trades locked"
    );

    @Override
    public String getModName() {
        return "Librarian Filter";
    }

    @Override
    public List<String> getSuggestedPrompts() {
        return SUGGESTED_PROMPTS;
    }

    @Override
    public DiagnosticReport createReport(CommandSourceStack source, String userQuery) {
        ServerLevel level = source.getLevel();
        boolean tradeRebalanceActive = level.enabledFeatures().contains(FeatureFlags.TRADE_REBALANCE);

        StringBuilder sb = new StringBuilder();

        // 1. Mod Overview / Wiki reference
        sb.append("--- MOD: LIBRARIAN FILTER ---\n");
        sb.append("Overview: Allows players to reroll villager trades by placing an attached sign with a filter on a workstation (e.g. lectern) and right-clicking it.\n");
        sb.append("Sign Filter Syntax: '<enchantment> [level] [max_price]' (e.g. 'mending 1 10' or 'unbreaking 3 14').\n");
        sb.append("- Price filter: When max_price is specified, offers exceeding that emerald cost are rejected. If no rolls meet the price limit, reroll fails with FAILED_PRICE_FILTER.\n");
        sb.append("Commands:\n");
        sb.append("- '/reroll setup': Spawns an unlinked librarian villager, lectern workstation, and attached sign.\n");
        sb.append("- '/reroll config': Opens mod configuration settings.\n");
        sb.append("- '/reroll find <enchantment>': Checks which biomes/villagers can offer a specific trade.\n");
        sb.append("- '/reroll clear': Resets AI diagnostic history.\n");
        sb.append("Config: enableReroll=").append(TradeConfig.INSTANCE.enableReroll)
          .append(", enableEachLevelReroll=").append(TradeConfig.INSTANCE.enableEachLevelReroll)
          .append(", disableTradeRebalance=").append(TradeConfig.INSTANCE.disableTradeRebalance)
          .append(", allowTreasureEnchantments=").append(TradeConfig.INSTANCE.allowTreasureEnchantments)
          .append(", tradeRebalanceActive=").append(tradeRebalanceActive).append("\n\n");

        // 2. Recent in-game reroll attempt (Telemetry snapshot)
        RerollDiagnosticLogger.DiagnosticSnapshot snapshot = RerollDiagnosticLogger.getLatestSnapshot();
        if (snapshot != null) {
            sb.append("--- RECENT IN-GAME REROLL ATTEMPT ---\n");
            sb.append("Timestamp: ").append(snapshot.timestamp()).append("\n");
            sb.append("Player: ").append(snapshot.playerName()).append("\n");
            sb.append("Biome: ").append(snapshot.currentBiome()).append("\n");
            sb.append("Clicked Block: ").append(snapshot.clickedBlock()).append(" at ").append(snapshot.clickedPos() != null ? snapshot.clickedPos().toShortString() : "null").append("\n");
            sb.append("Attached Sign: ").append(snapshot.signPos() != null ? "at " + snapshot.signPos().toShortString() : "none").append("\n");
            sb.append("Sign Lines: ").append(snapshot.rawSignLines()).append("\n");
            sb.append("Parsed Filters: ").append(snapshot.parsedFilters()).append("\n");
            sb.append("Villager Linked: ").append(snapshot.villagerFound()).append("\n");
            if (snapshot.villagerFound()) {
                sb.append("  Profession: ").append(snapshot.villagerProfession()).append("\n");
                sb.append("  Type: ").append(snapshot.villagerType()).append("\n");
                sb.append("  Level: ").append(snapshot.villagerLevel()).append(", XP: ").append(snapshot.villagerXp()).append("\n");
                sb.append("  Trades Locked (XP > 0): ").append(snapshot.tradesLocked()).append("\n");
                sb.append("  Existing Offers Count: ").append(snapshot.existingOffersCount()).append("\n");
            }
            if (snapshot.lowestAmountFound() != null) {
                sb.append("Lowest Emerald Cost/Amount Found: ").append(snapshot.lowestAmountFound()).append(" emeralds\n");
            }
            sb.append("Result: ").append(snapshot.outcome()).append("\n");
            sb.append("Reason: ").append(snapshot.reason()).append("\n\n");
        }

        // 3. Recent Mod Logs
        List<String> logs = RerollDiagnosticLogger.getRecentLogs();
        if (!logs.isEmpty()) {
            sb.append("--- RECENT MOD LOGS ---\n");
            int start = Math.max(0, logs.size() - 6);
            for (int i = start; i < logs.size(); i++) {
                sb.append(logs.get(i)).append("\n");
            }
            sb.append("\n");
        }

        return new DiagnosticReport(
                DiagnosticReport.DEFAULT_ONE_LINER_SYSTEM_PROMPT,
                sb.toString(),
                "Why is reroll not working? Please check my recent reroll attempts and tell me what is wrong.",
                SUGGESTED_PROMPTS
        );
    }

    @Override
    public void clearContext() {
        RerollDiagnosticLogger.clearContext();
    }

    @Override
    public List<com.aidebugger.AiTool> getTools() {
        return List.of(
                new com.aidebugger.AiTool() {
                    @Override
                    public String getName() {
                        return "config_info";
                    }

                    @Override
                    public String getDescription() {
                        return "Returns all active Librarian Filter mod configuration settings and what each setting controls.";
                    }

                    @Override
                    public String execute(java.util.Map<String, String> parameters) {
                        return "Librarian Filter Mod Configuration:\n" +
                                "- enableReroll: " + TradeConfig.INSTANCE.enableReroll + " (Master toggle: whether villager rerolling via sign is enabled)\n" +
                                "- enableEachLevelReroll: " + TradeConfig.INSTANCE.enableEachLevelReroll + " (Allows rerolling leveled villagers with existing XP)\n" +
                                "- disableTradeRebalance: " + TradeConfig.INSTANCE.disableTradeRebalance + " (Disables experimental trade rebalance biome restrictions)\n" +
                                "- enableSignSuggestions: " + TradeConfig.INSTANCE.enableSignSuggestions + " (Shows visual autocomplete overlay of enchantment books while player types on signs)\n" +
                                "- allowTreasureEnchantments: " + TradeConfig.INSTANCE.allowTreasureEnchantments + " (Allows rolling treasure enchantments like Mending, Soul Speed, Frost Walker, Swift Sneak)\n" +
                                "- ollamaModel: " + TradeConfig.INSTANCE.ollamaModel;
                    }
                }
        );
    }
}
