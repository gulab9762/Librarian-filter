package com.gbdhapa.forge;

import com.gbdhapa.RerollLogic;
import com.gbdhapa.config.TradeConfig;
import com.gbdhapa.network.*;
import com.gbdhapa.forge.client.LibrarianfilterForgeClient;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;

@Mod("librarian_filter")
public class LibrarianfilterForge {

    public static Channel<CustomPacketPayload> CHANNEL = createChannel();

    public static void send(CustomPacketPayload payload, PacketDistributor.PacketTarget target) {
        if (CHANNEL != null) {
            CHANNEL.send(payload, target);
        }
    }

    private static Channel<CustomPacketPayload> createChannel() {
        return ChannelBuilder
            .named(Identifier.fromNamespaceAndPath("librarian_filter", "main"))
            .networkProtocolVersion(1)
            .payloadChannel()
            .play()
                .clientbound()
                    .addMain(TradeConfigSyncPayload.ID, TradeConfigSyncPayload.CODEC, (payload, context) -> {
                        TradeConfig.INSTANCE.enableReroll = payload.enableReroll();
                        TradeConfig.INSTANCE.enableEachLevelReroll = payload.enableEachLevelReroll();
                        TradeConfig.INSTANCE.disableTradeRebalance = payload.disableTradeRebalance();
                        TradeConfig.INSTANCE.enableSignSuggestions = payload.enableSignSuggestions();
                        TradeConfig.INSTANCE.allowTreasureEnchantments = payload.allowTreasureEnchantments();
                    })
                    .addMain(OpenConfigScreenPayload.ID, OpenConfigScreenPayload.CODEC, (payload, context) -> {
                        if (FMLEnvironment.dist == Dist.CLIENT) {
                            LibrarianfilterForgeClient.openConfigScreen(payload);
                        }
                    })
                .serverbound()
                    .addMain(TradeConfigUpdatePayload.ID, TradeConfigUpdatePayload.CODEC, (payload, context) -> {
                        ServerPlayer player = context.getSender();
                        var server = player.level().getServer();
                        NameAndId nameAndId = new NameAndId(player.getGameProfile());
                        if (server.getPlayerList().isOp(nameAndId) || server.isSingleplayerOwner(nameAndId)) {
                            TradeConfig.INSTANCE.enableReroll = payload.enableReroll();
                            TradeConfig.INSTANCE.enableEachLevelReroll = payload.enableEachLevelReroll();
                            TradeConfig.INSTANCE.disableTradeRebalance = payload.disableTradeRebalance();
                            TradeConfig.INSTANCE.enableSignSuggestions = payload.enableSignSuggestions();
                            TradeConfig.INSTANCE.allowTreasureEnchantments = payload.allowTreasureEnchantments();
                            TradeConfig.save();
                            TradeConfig.applyTradeRebalanceOverride(server);

                            send(new TradeConfigSyncPayload(
                                    TradeConfig.INSTANCE.enableReroll,
                                    TradeConfig.INSTANCE.enableEachLevelReroll,
                                    TradeConfig.INSTANCE.disableTradeRebalance,
                                    TradeConfig.INSTANCE.enableSignSuggestions,
                                    TradeConfig.INSTANCE.allowTreasureEnchantments
                            ), PacketDistributor.ALL.noArg());
                        }
                    })
                    .addMain(ConfigRequestPayload.ID, ConfigRequestPayload.CODEC, (payload, context) -> {
                        ServerPlayer player = context.getSender();
                        var server = player.level().getServer();
                        NameAndId nameAndId = new NameAndId(player.getGameProfile());
                        if (server.getPlayerList().isOp(nameAndId) || server.isSingleplayerOwner(nameAndId)) {
                            send(new OpenConfigScreenPayload(
                                    TradeConfig.INSTANCE.enableReroll,
                                    TradeConfig.INSTANCE.enableEachLevelReroll,
                                    TradeConfig.INSTANCE.disableTradeRebalance,
                                    TradeConfig.INSTANCE.enableSignSuggestions,
                                    TradeConfig.INSTANCE.allowTreasureEnchantments
                            ), PacketDistributor.PLAYER.with(player));
                        }
                    })
            .build();
    }

    public LibrarianfilterForge(FMLJavaModLoadingContext context) {
        TradeConfig.load();

        var modBusGroup = context.getModBusGroup();
        FMLCommonSetupEvent.getBus(modBusGroup).addListener(this::commonSetup);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            LibrarianfilterForgeClient.init();
        }

        PlayerInteractEvent.RightClickBlock.BUS.addListener(this::onRightClickBlock);
        RegisterCommandsEvent.BUS.addListener(this::registerCommands);
        ServerStartedEvent.BUS.addListener(this::onServerStarted);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Common initialization if needed
    }

    private boolean onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        InteractionResult result = RerollLogic.handleBlockUse(event.getEntity(), event.getLevel(), event.getPos());
        event.setCancellationResult(result);
        return result.consumesAction();
    }

    private void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("reroll")
                .then(Commands.literal("config")
                        .requires(source -> {
                            try {
                                NameAndId nameAndId = new NameAndId(source.getPlayerOrException().getGameProfile());
                                return source.getServer().getPlayerList().isOp(nameAndId) || source.getServer().isSingleplayerOwner(nameAndId);
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            CHANNEL.send(new OpenConfigScreenPayload(
                                    TradeConfig.INSTANCE.enableReroll,
                                    TradeConfig.INSTANCE.enableEachLevelReroll,
                                    TradeConfig.INSTANCE.disableTradeRebalance,
                                    TradeConfig.INSTANCE.enableSignSuggestions,
                                    TradeConfig.INSTANCE.allowTreasureEnchantments
                            ), PacketDistributor.PLAYER.with(player));
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
                                                     context.getSource().sendFailure(Component.literal("Unknown config option: " + option));
                                                     return 0;
                                             }
                                             TradeConfig.save();
                                             CHANNEL.send(new TradeConfigSyncPayload(
                                                     TradeConfig.INSTANCE.enableReroll,
                                                     TradeConfig.INSTANCE.enableEachLevelReroll,
                                                     TradeConfig.INSTANCE.disableTradeRebalance,
                                                     TradeConfig.INSTANCE.enableSignSuggestions,
                                                     TradeConfig.INSTANCE.allowTreasureEnchantments
                                             ), PacketDistributor.ALL.noArg());
                                             if (option.equals("disableTradeRebalance")) {
                                                 TradeConfig.applyTradeRebalanceOverride(context.getSource().getServer());
                                             }
                                             final Component msg = Component.literal("Toggled " + option + " to " + newValue);
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
                                                .map(key -> key.identifier().getPath())
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
                .then(Commands.literal("clear")
                        .executes(context -> {
                            return com.gbdhapa.ai.AiDiagnosticHelper.clearContext(context.getSource());
                        })
                )
                .then(Commands.literal("ai")
                        .executes(context -> {
                            return com.gbdhapa.ai.AiDiagnosticHelper.handleCommand(context.getSource(), null);
                        })
                        .then(Commands.literal("clear")
                                .executes(context -> {
                                    return com.gbdhapa.ai.AiDiagnosticHelper.clearContext(context.getSource());
                                })
                        )
                        .then(Commands.argument("prompt", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .suggests((context, builder) -> com.gbdhapa.ai.AiDiagnosticHelper.suggestPrompts(builder))
                                .executes(context -> {
                                    String prompt = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "prompt");
                                    return com.gbdhapa.ai.AiDiagnosticHelper.handleCommand(context.getSource(), prompt);
                                })
                        )
                )
                .then(Commands.literal("debug")
                        .executes(context -> {
                            return com.gbdhapa.ai.AiDiagnosticHelper.handleCommand(context.getSource(), "Diagnose why reroll is not working based on recent logs and workstation state.");
                        })
                        .then(Commands.literal("clear")
                                .executes(context -> {
                                    return com.gbdhapa.ai.AiDiagnosticHelper.clearContext(context.getSource());
                                })
                        )
                        .then(Commands.argument("prompt", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .suggests((context, builder) -> com.gbdhapa.ai.AiDiagnosticHelper.suggestPrompts(builder))
                                .executes(context -> {
                                    String prompt = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "prompt");
                                    return com.gbdhapa.ai.AiDiagnosticHelper.handleCommand(context.getSource(), prompt);
                                })
                        )
                )
        );
    }

    private void onServerStarted(ServerStartedEvent event) {
        TradeConfig.applyTradeRebalanceOverride(event.getServer());
    }
}
