package com.gbdhapa.neoforge.client;

import com.gbdhapa.network.ConfigRequestPayload;
import com.gbdhapa.network.OpenConfigScreenPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class LibrarianfilterNeoForgeClient {
    public static KeyMapping OPEN_CONFIG_KEY;

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(LibrarianfilterNeoForgeClient::registerKeyMappings);
        modEventBus.addListener(LibrarianfilterNeoForgeClient::registerClientPayloads);
        NeoForge.EVENT_BUS.addListener(LibrarianfilterNeoForgeClient::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(LibrarianfilterNeoForgeClient::onClientLogin);
    }

    private static void onClientLogin(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        if (event.getPlayer() != null && event.getPlayer().level().enabledFeatures().contains(net.minecraft.world.flag.FeatureFlags.TRADE_REBALANCE)) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    net.minecraft.network.chat.Component warning = net.minecraft.network.chat.Component.literal("[Librarian Filter] Warning: Villager Trade Rebalance experimental feature is enabled in this world! Villager book trades are biome-dependent. ")
                            .withStyle(net.minecraft.ChatFormatting.YELLOW)
                            .append(
                                    net.minecraft.network.chat.Component.literal("[Click here to disable]")
                                            .withStyle(style -> style
                                                    .withColor(net.minecraft.ChatFormatting.RED)
                                                    .withUnderlined(true)
                                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/reroll config toggle disableTradeRebalance"))
                                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, net.minecraft.network.chat.Component.literal("Click to disable Trade Rebalance")))
                                            )
                            );
                    Minecraft.getInstance().player.sendSystemMessage(warning);
                }
            });
        }
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        OPEN_CONFIG_KEY = new KeyMapping(
                "key.librarian-filter.open_trade_config",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_O,
                "key.categories.gameplay"
        );
        event.register(OPEN_CONFIG_KEY);
    }

    public static void registerClientPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1.0.1").playToClient(OpenConfigScreenPayload.ID, OpenConfigScreenPayload.CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
                Minecraft.getInstance().setScreen(new NeoForgeTradeConfigScreen(payload.enableReroll(), payload.enableEachLevelReroll(), payload.disableTradeRebalance(), payload.enableSignSuggestions(), payload.allowTreasureEnchantments()));
            });
        });
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) {
            if (OPEN_CONFIG_KEY != null) {
                while (OPEN_CONFIG_KEY.consumeClick()) {
                    PacketDistributor.sendToServer(new ConfigRequestPayload());
                }
            }
        }
    }
}
