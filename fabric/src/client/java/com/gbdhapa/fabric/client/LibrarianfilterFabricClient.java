package com.gbdhapa.fabric.client;

import com.gbdhapa.config.TradeConfig;
import com.gbdhapa.fabric.client.gui.FabricTradeConfigScreen;
import com.gbdhapa.network.OpenConfigScreenPayload;
import com.gbdhapa.network.TradeConfigSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;

public class LibrarianfilterFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientInit.init();
        
        ClientPlayNetworking.registerGlobalReceiver(TradeConfigSyncPayload.ID, (payload, context) -> {
            TradeConfig.INSTANCE.enableReroll = payload.enableReroll();
            TradeConfig.INSTANCE.enableEachLevelReroll = payload.enableEachLevelReroll();
            TradeConfig.INSTANCE.disableTradeRebalance = payload.disableTradeRebalance();
            TradeConfig.INSTANCE.enableSignSuggestions = payload.enableSignSuggestions();
            TradeConfig.INSTANCE.allowTreasureEnchantments = payload.allowTreasureEnchantments();
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenConfigScreenPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreen(new FabricTradeConfigScreen(payload.enableReroll(), payload.enableEachLevelReroll(), payload.disableTradeRebalance(), payload.enableSignSuggestions(), payload.allowTreasureEnchantments()));
            });
        });

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (handler.enabledFeatures().contains(net.minecraft.world.flag.FeatureFlags.TRADE_REBALANCE)) {
                client.execute(() -> {
                    if (client.player != null) {
                        net.minecraft.network.chat.Component warning = net.minecraft.network.chat.Component.literal("[Librarian Filter] Warning: Villager Trade Rebalance experimental feature is enabled in this world! Villager book trades are biome-dependent. ")
                                .withStyle(net.minecraft.ChatFormatting.YELLOW)
                                .append(
                                        net.minecraft.network.chat.Component.literal("[Click here to disable]")
                                                .withStyle(style -> style
                                                        .withColor(net.minecraft.ChatFormatting.RED)
                                                        .withUnderlined(true)
                                                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reroll config toggle disableTradeRebalance"))
                                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, net.minecraft.network.chat.Component.literal("Click to disable Trade Rebalance")))
                                                )
                                );
                        client.player.displayClientMessage(warning, false);
                    }
                });
            }
        });
    }
}
