package com.gbdhapa.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class TradeConfig {
    private static final File CONFIG_FILE = new File("config/librarian-filter.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static TradeConfig INSTANCE = new TradeConfig();

    public boolean enableReroll = true;
    public boolean enableEachLevelReroll = false;
    public boolean disableTradeRebalance = false;
    public boolean enableSignSuggestions = true;
    public boolean allowTreasureEnchantments = false;

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, TradeConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    public static void save() {
        CONFIG_FILE.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void applyTradeRebalanceOverride(net.minecraft.server.MinecraftServer server) {
        if (server != null) {
            try {
                var packRepo = server.getPackRepository();
                var worldData = server.getWorldData();
                var currentConfig = worldData.getDataConfiguration();
                var currentFeatures = currentConfig.enabledFeatures();
                
                java.util.List<String> activePacks = new java.util.ArrayList<>(packRepo.getSelectedIds());
                
                boolean hasRebalanceFlag = currentFeatures.contains(net.minecraft.world.flag.FeatureFlags.TRADE_REBALANCE);
                boolean shouldDisable = INSTANCE.disableTradeRebalance;
                
                boolean changed = false;
                net.minecraft.world.flag.FeatureFlagSet newFeatures = currentFeatures;
                
                if (shouldDisable && hasRebalanceFlag) {
                    newFeatures = currentFeatures.subtract(net.minecraft.world.flag.FeatureFlagSet.of(net.minecraft.world.flag.FeatureFlags.TRADE_REBALANCE));
                    activePacks.remove("trade_rebalance");
                    activePacks.remove("minecraft:trade_rebalance");
                    activePacks.remove("builtin/trade_rebalance");
                    changed = true;
                } else if (!shouldDisable && !hasRebalanceFlag) {
                    newFeatures = currentFeatures.join(net.minecraft.world.flag.FeatureFlagSet.of(net.minecraft.world.flag.FeatureFlags.TRADE_REBALANCE));
                    if (packRepo.isAvailable("trade_rebalance")) {
                        activePacks.add("trade_rebalance");
                    } else if (packRepo.isAvailable("minecraft:trade_rebalance")) {
                        activePacks.add("minecraft:trade_rebalance");
                    }
                    changed = true;
                }

                if (changed) {
                    var newPackConfig = new net.minecraft.world.level.DataPackConfig(activePacks, currentConfig.dataPacks().getDisabled());
                    var newWorldConfig = new net.minecraft.world.level.WorldDataConfiguration(newPackConfig, newFeatures);
                    worldData.setDataConfiguration(newWorldConfig);
                    server.reloadResources(activePacks);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
