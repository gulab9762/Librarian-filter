package com.gbdhapa.forge.client;

import com.gbdhapa.forge.LibrarianfilterForge;
import com.gbdhapa.network.ConfigRequestPayload;
import com.gbdhapa.network.OpenConfigScreenPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.PacketDistributor;

public class LibrarianfilterForgeClient {
    public static KeyMapping OPEN_CONFIG_KEY;

    public static void init() {
        RegisterKeyMappingsEvent.BUS.addListener(LibrarianfilterForgeClient::registerKeyMappings);
        TickEvent.ClientTickEvent.Post.BUS.addListener(LibrarianfilterForgeClient::onClientTick);
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(LibrarianfilterForgeClient::onClientLogin);
    }

    public static void openConfigScreen(OpenConfigScreenPayload payload) {
        Minecraft.getInstance().setScreenAndShow(new ForgeTradeConfigScreen(
                payload.enableReroll(),
                payload.enableEachLevelReroll(),
                payload.disableTradeRebalance(),
                payload.enableSignSuggestions(),
                payload.allowTreasureEnchantments()
        ));
    }

    private static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
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
                                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/reroll config toggle disableTradeRebalance"))
                                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(net.minecraft.network.chat.Component.literal("Click to disable Trade Rebalance")))
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
                new Category(Identifier.fromNamespaceAndPath("minecraft", "gameplay"))
        );
        event.register(OPEN_CONFIG_KEY);
    }

    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level != null) {
            if (OPEN_CONFIG_KEY != null) {
                while (OPEN_CONFIG_KEY.consumeClick()) {
                    LibrarianfilterForge.CHANNEL.send(new ConfigRequestPayload(), PacketDistributor.SERVER.noArg());
                }
            }
        }
    }
}
