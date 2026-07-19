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
                var worldData = server.getWorldData();
                var currentConfig = worldData.getDataConfiguration();
                var enabledFeatures = currentConfig.enabledFeatures();
                
                boolean hasRebalance = enabledFeatures.contains(net.minecraft.world.flag.FeatureFlags.TRADE_REBALANCE);
                boolean shouldDisable = INSTANCE.disableTradeRebalance;
                
                if (shouldDisable && hasRebalance) {
                    var newFeatures = enabledFeatures.subtract(net.minecraft.world.flag.FeatureFlagSet.of(net.minecraft.world.flag.FeatureFlags.TRADE_REBALANCE));
                    var newConfig = new net.minecraft.world.level.WorldDataConfiguration(currentConfig.dataPacks(), newFeatures);
                    if (worldData instanceof net.minecraft.world.level.storage.PrimaryLevelData) {
                        ((net.minecraft.world.level.storage.PrimaryLevelData) worldData).setDataConfiguration(newConfig);
                    }
                } else if (!shouldDisable && !hasRebalance) {
                    var newFeatures = enabledFeatures.join(net.minecraft.world.flag.FeatureFlagSet.of(net.minecraft.world.flag.FeatureFlags.TRADE_REBALANCE));
                    var newConfig = new net.minecraft.world.level.WorldDataConfiguration(currentConfig.dataPacks(), newFeatures);
                    if (worldData instanceof net.minecraft.world.level.storage.PrimaryLevelData) {
                        ((net.minecraft.world.level.storage.PrimaryLevelData) worldData).setDataConfiguration(newConfig);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
