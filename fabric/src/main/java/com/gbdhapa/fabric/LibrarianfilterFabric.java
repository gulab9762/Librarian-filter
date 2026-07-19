package com.gbdhapa.fabric;

import com.gbdhapa.RerollLogic;
import com.gbdhapa.config.TradeConfig;
import com.gbdhapa.network.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;


public class LibrarianfilterFabric implements ModInitializer {
    public static final String MOD_ID = "librarian-filter";

    @Override
    public void onInitialize() {
        TradeConfig.load();

        // Register Payloads
        PayloadTypeRegistry.playS2C().register(TradeConfigSyncPayload.ID, TradeConfigSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenConfigScreenPayload.ID, OpenConfigScreenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TradeConfigUpdatePayload.ID, TradeConfigUpdatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigRequestPayload.ID, ConfigRequestPayload.CODEC);

        // Register Receivers
        ServerPlayNetworking.registerGlobalReceiver(TradeConfigUpdatePayload.ID, (payload, context) -> {
            var server = context.player().level().getServer();
            com.mojang.authlib.GameProfile profile = context.player().getGameProfile();
            if (server.getPlayerList().isOp(profile) || server.isSingleplayerOwner(profile)) {
                TradeConfig.INSTANCE.enableReroll = payload.enableReroll();
                TradeConfig.INSTANCE.enableEachLevelReroll = payload.enableEachLevelReroll();
                TradeConfig.INSTANCE.disableTradeRebalance = payload.disableTradeRebalance();
                TradeConfig.INSTANCE.enableSignSuggestions = payload.enableSignSuggestions();
                TradeConfig.INSTANCE.allowTreasureEnchantments = payload.allowTreasureEnchantments();
                TradeConfig.save();

                TradeConfig.applyTradeRebalanceOverride(server);

                TradeConfigSyncPayload syncPayload = new TradeConfigSyncPayload(payload.enableReroll(), payload.enableEachLevelReroll(), payload.disableTradeRebalance(), payload.enableSignSuggestions(), payload.allowTreasureEnchantments());
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(player, syncPayload);
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(ConfigRequestPayload.ID, (payload, context) -> {
            var server = context.player().level().getServer();
            com.mojang.authlib.GameProfile profile = context.player().getGameProfile();
            if (server.getPlayerList().isOp(profile) || server.isSingleplayerOwner(profile)) {
                ServerPlayNetworking.send(context.player(), new OpenConfigScreenPayload(
                        TradeConfig.INSTANCE.enableReroll,
                        TradeConfig.INSTANCE.enableEachLevelReroll,
                        TradeConfig.INSTANCE.disableTradeRebalance,
                        TradeConfig.INSTANCE.enableSignSuggestions,
                        TradeConfig.INSTANCE.allowTreasureEnchantments
                ));
            }
        });

        // Register Events
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            return RerollLogic.handleBlockUse(player, world, hitResult.getBlockPos());
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            TradeConfig.applyTradeRebalanceOverride(server);
        });

        // Register Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("reroll")
                    .then(Commands.literal("config")
                            .requires(source -> {
                                try {
                                    com.mojang.authlib.GameProfile profile = source.getPlayerOrException().getGameProfile();
                                    return source.getServer().getPlayerList().isOp(profile) || source.getServer().isSingleplayerOwner(profile);
                                } catch (Exception e) {
                                    return false;
                                }
                            })
                            .executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                ServerPlayNetworking.send(player, new OpenConfigScreenPayload(
                                        TradeConfig.INSTANCE.enableReroll,
                                        TradeConfig.INSTANCE.enableEachLevelReroll,
                                        TradeConfig.INSTANCE.disableTradeRebalance,
                                        TradeConfig.INSTANCE.enableSignSuggestions,
                        TradeConfig.INSTANCE.allowTreasureEnchantments
                                ));
                                return 1;
                            })
                            .then(Commands.literal("toggle")
                                    .then(Commands.argument("option", com.mojang.brigadier.arguments.StringArgumentType.word())
                                            .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"enableReroll", "enableEachLevelReroll", "disableTradeRebalance", "enableSignSuggestions", "allowTreasureEnchantments"}, builder))
                                            .executes(context -> {
                                                String option = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "option");
                                                boolean newValue = false;
                                                switch (option) {
                                                    case "enableReroll":
                                                        TradeConfig.INSTANCE.enableReroll = !TradeConfig.INSTANCE.enableReroll;
                                                        newValue = TradeConfig.INSTANCE.enableReroll;
                                                        break;
                                                    case "enableEachLevelReroll":
                                                        TradeConfig.INSTANCE.enableEachLevelReroll = !TradeConfig.INSTANCE.enableEachLevelReroll;
                                                        newValue = TradeConfig.INSTANCE.enableEachLevelReroll;
                                                        break;
                                                    case "disableTradeRebalance":
                                                        TradeConfig.INSTANCE.disableTradeRebalance = !TradeConfig.INSTANCE.disableTradeRebalance;
                                                        newValue = TradeConfig.INSTANCE.disableTradeRebalance;
                                                        break;
                                                    case "enableSignSuggestions":
                                                        TradeConfig.INSTANCE.enableSignSuggestions = !TradeConfig.INSTANCE.enableSignSuggestions;
                                                        newValue = TradeConfig.INSTANCE.enableSignSuggestions;
                                                        break;
                                                    case "allowTreasureEnchantments":
                                                        TradeConfig.INSTANCE.allowTreasureEnchantments = !TradeConfig.INSTANCE.allowTreasureEnchantments;
                                                        newValue = TradeConfig.INSTANCE.allowTreasureEnchantments;
                                                        break;
                                                    default:
                                                        context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Unknown config option: " + option));
                                                        return 0;
                                                }
                                                TradeConfig.save();
                                                context.getSource().getServer().getPlayerList().getPlayers().forEach(p -> {
                                                    ServerPlayNetworking.send(p, new TradeConfigSyncPayload(
                                                            TradeConfig.INSTANCE.enableReroll,
                                                            TradeConfig.INSTANCE.enableEachLevelReroll,
                                                            TradeConfig.INSTANCE.disableTradeRebalance,
                                                            TradeConfig.INSTANCE.enableSignSuggestions,
                                                            TradeConfig.INSTANCE.allowTreasureEnchantments
                                                    ));
                                                });
                                                if (option.equals("disableTradeRebalance")) {
                                                    TradeConfig.applyTradeRebalanceOverride(context.getSource().getServer());
                                                }
                                                final net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal("Toggled " + option + " to " + newValue);
                                                context.getSource().sendSuccess(() -> msg, true);
                                                return 1;
                                            })
                                    )
                            )
                    )
                    .then(Commands.literal("find")
                            .then(Commands.argument("query", com.mojang.brigadier.arguments.StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        net.minecraft.commands.CommandSourceStack source = context.getSource();
                                        try {
                                            var registry = source.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                                            java.util.List<String> paths = registry.listElementIds().toList().stream()
                                                    .map(key -> key.location().getPath())
                                                    .filter(path -> TradeConfig.INSTANCE.allowTreasureEnchantments || (!path.equals("soul_speed") && !path.equals("swift_sneak") && !path.equals("wind_burst")))
                                                    .toList();
                                            return net.minecraft.commands.SharedSuggestionProvider.suggest(paths, builder);
                                        } catch (Exception e) {
                                            java.util.List<String> paths = com.gbdhapa.EnchantmentDescriptions.DESCRIPTIONS.keySet().stream()
                                                    .filter(path -> TradeConfig.INSTANCE.allowTreasureEnchantments || (!path.equals("soul_speed") && !path.equals("swift_sneak") && !path.equals("wind_burst")))
                                                    .toList();
                                            return net.minecraft.commands.SharedSuggestionProvider.suggest(paths, builder);
                                        }
                                    })
                                    .executes(context -> {
                                        String query = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "query");
                                        try {
                                            return RerollLogic.executeFind(context.getSource(), query);
                                        } catch (Exception e) {
                                            return 0;
                                        }
                                    })
                            )
                    )
                    .then(Commands.literal("setup")
                            .executes(context -> {
                                try {
                                    return RerollLogic.executeSetup(context.getSource());
                                } catch (Exception e) {
                                    return 0;
                                }
                            })
                    )
            );
        });
    }
}
