package com.gbdhapa.neoforge;

import com.gbdhapa.RerollLogic;
import com.gbdhapa.config.TradeConfig;
import com.gbdhapa.network.*;
import com.gbdhapa.neoforge.client.LibrarianfilterNeoForgeClient;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("librarian_filter")
@EventBusSubscriber(modid = "librarian_filter")
public class LibrarianfilterNeoForge {
    private static final Logger LOGGER = LoggerFactory.getLogger(LibrarianfilterNeoForge.class);

    public LibrarianfilterNeoForge(IEventBus modEventBus) {
        TradeConfig.load();

        modEventBus.addListener(this::registerPayloads);

        LibrarianfilterNeoForgeClient.init(modEventBus);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1.0.1");

        registrar.playToClient(TradeConfigSyncPayload.ID, TradeConfigSyncPayload.CODEC, (payload, context) -> {
            TradeConfig.INSTANCE.enableReroll = payload.enableReroll();
            TradeConfig.INSTANCE.enableEachLevelReroll = payload.enableEachLevelReroll();
            TradeConfig.INSTANCE.disableTradeRebalance = payload.disableTradeRebalance();
            TradeConfig.INSTANCE.enableSignSuggestions = payload.enableSignSuggestions();
            TradeConfig.INSTANCE.allowTreasureEnchantments = payload.allowTreasureEnchantments();
        });

        registrar.playToServer(TradeConfigUpdatePayload.ID, TradeConfigUpdatePayload.CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
                ServerPlayer player = (ServerPlayer) context.player();
                var server = player.level().getServer();
                com.mojang.authlib.GameProfile profile = player.getGameProfile();
                if (server.getPlayerList().isOp(profile) || server.isSingleplayerOwner(profile)) {
                    TradeConfig.INSTANCE.enableReroll = payload.enableReroll();
                    TradeConfig.INSTANCE.enableEachLevelReroll = payload.enableEachLevelReroll();
                    TradeConfig.INSTANCE.disableTradeRebalance = payload.disableTradeRebalance();
                    TradeConfig.INSTANCE.enableSignSuggestions = payload.enableSignSuggestions();
                    TradeConfig.INSTANCE.allowTreasureEnchantments = payload.allowTreasureEnchantments();
                    TradeConfig.save();

                    TradeConfig.applyTradeRebalanceOverride(server);

                    PacketDistributor.sendToAllPlayers(new TradeConfigSyncPayload(payload.enableReroll(), payload.enableEachLevelReroll(), payload.disableTradeRebalance(), payload.enableSignSuggestions(), payload.allowTreasureEnchantments()));
                }
            });
        });

        registrar.playToServer(ConfigRequestPayload.ID, ConfigRequestPayload.CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
                ServerPlayer player = (ServerPlayer) context.player();
                com.mojang.authlib.GameProfile profile = player.getGameProfile();
                if (player.level().getServer().getPlayerList().isOp(profile) || player.level().getServer().isSingleplayerOwner(profile)) {
                    PacketDistributor.sendToPlayer(player, new OpenConfigScreenPayload(
                            TradeConfig.INSTANCE.enableReroll,
                            TradeConfig.INSTANCE.enableEachLevelReroll,
                            TradeConfig.INSTANCE.disableTradeRebalance,
                            TradeConfig.INSTANCE.enableSignSuggestions,
                            TradeConfig.INSTANCE.allowTreasureEnchantments
                    ));
                }
            });
        });
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        event.setCancellationResult(RerollLogic.handleBlockUse(event.getEntity(), event.getLevel(), event.getPos()));
        if (event.getCancellationResult().consumesAction()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("reroll")
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
                            PacketDistributor.sendToPlayer(player, new OpenConfigScreenPayload(
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
                                            PacketDistributor.sendToAllPlayers(new TradeConfigSyncPayload(
                                                    TradeConfig.INSTANCE.enableReroll,
                                                    TradeConfig.INSTANCE.enableEachLevelReroll,
                                                    TradeConfig.INSTANCE.disableTradeRebalance,
                                                    TradeConfig.INSTANCE.enableSignSuggestions,
                                                    TradeConfig.INSTANCE.allowTreasureEnchantments
                                            ));
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
    }

    @SubscribeEvent
    public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        TradeConfig.applyTradeRebalanceOverride(event.getServer());
    }
}
