package com.gbdhapa;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class LibrarianCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("reroll")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("§a[Librarian Filter] Use §e/reroll list§a to view supported book formats, or §e/reroll find <enchantment>§a to locate offering villagers."), false);
                    return 1;
                })
                .then(Commands.literal("list")
                        .executes(context -> executeList(context, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> executeList(context, IntegerArgumentType.getInteger(context, "page")))
                        )
                )
                .then(Commands.literal("find")
                        .then(Commands.argument("enchantment", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    var registry = context.getSource().getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                                    return SharedSuggestionProvider.suggest(
                                            registry.listElements().map(h -> h.key().identifier().getPath()),
                                            builder
                                    );
                                })
                                .executes(context -> executeFind(context, StringArgumentType.getString(context, "enchantment")))
                        )
                )
        );
    }

    private static int executeList(CommandContext<CommandSourceStack> context, int requestedPage) {
        CommandSourceStack source = context.getSource();
        var registry = source.getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        List<Holder.Reference<Enchantment>> enchantments = registry.listElements()
                .sorted(Comparator.comparing(h -> h.key().identifier().getPath()))
                .toList();

        if (enchantments.isEmpty()) {
            source.sendFailure(Component.literal("§cNo enchantments found in registry."));
            return 0;
        }

        int pageSize = 7;
        int totalPages = Math.max(1, (int) Math.ceil((double) enchantments.size() / pageSize));
        int page = Math.min(Math.max(1, requestedPage), totalPages);

        source.sendSuccess(() -> Component.literal("§6========================================"), false);
        source.sendSuccess(() -> Component.literal("§6   Librarian Books §e(Page " + page + "/" + totalPages + ")"), false);
        source.sendSuccess(() -> Component.literal("§7   Write any of these keywords on a sign:"), false);
        source.sendSuccess(() -> Component.literal("§6========================================"), false);

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, enchantments.size());

        for (int i = start; i < end; i++) {
            Holder.Reference<Enchantment> holder = enchantments.get(i);
            String path = holder.key().identifier().getPath();
            Enchantment ench = holder.value();
            int maxLevel = ench.getMaxLevel();
            String desc = ench.description().getString();
            int lowestCost = LibrarianCore.getLowestPrice(holder, maxLevel);

            String shortType = getSuggestedShortType(path);

            MutableComponent entry = Component.literal(" §e• Type: §f\"" + shortType + "\" §7(or \"" + path + "\") §8-> ")
                    .append(Component.literal("§a" + desc + " " + maxLevel))
                    .append(Component.literal(" §6[" + lowestCost + " Emeralds]"));

            source.sendSuccess(() -> entry, false);
        }

        // Navigation controls
        MutableComponent nav = Component.literal("§6----------------------------------------\n ");
        if (page > 1) {
            MutableComponent prevBtn = Component.literal("§e[<< Prev] ")
                    .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/reroll list " + (page - 1))));
            nav.append(prevBtn);
        }
        nav.append(Component.literal("§7Page " + page + " of " + totalPages + " "));
        if (page < totalPages) {
            MutableComponent nextBtn = Component.literal("§e[Next >>]")
                    .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/reroll list " + (page + 1))));
            nav.append(nextBtn);
        }

        source.sendSuccess(() -> nav, false);
        return 1;
    }

    private static int executeFind(CommandContext<CommandSourceStack> context, String query) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("§cThis command must be executed by a player."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        var registry = source.getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        Holder.Reference<Enchantment> matchedEnchantment = LibrarianCore.findEnchantmentHolder(registry.listElements().toList(), query);
        if (matchedEnchantment == null) {
            source.sendFailure(Component.literal("§cNo enchantment found matching: \"" + query + "\""));
            return 0;
        }

        String enchantName = matchedEnchantment.value().description().getString();
        AABB searchBox = player.getBoundingBox().inflate(96.0);
        List<Villager> nearbyVillagers = level.getEntitiesOfClass(Villager.class, searchBox);

        int count = 0;
        for (Villager villager : nearbyVillagers) {
            boolean offersTargetBook = false;
            for (MerchantOffer offer : villager.getOffers()) {
                ItemStack result = offer.getResult();
                if (result.is(Items.ENCHANTED_BOOK)) {
                    ItemEnchantments stored = result.get(DataComponents.STORED_ENCHANTMENTS);
                    if (stored != null) {
                        for (Holder<Enchantment> enchHolder : stored.keySet()) {
                            if (enchHolder.equals(matchedEnchantment) ||
                                    enchHolder.unwrapKey().equals(matchedEnchantment.unwrapKey())) {
                                offersTargetBook = true;
                                break;
                            }
                        }
                    }
                }
                if (offersTargetBook) {
                    break;
                }
            }

            if (offersTargetBook) {
                villager.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30 * 20, 0, false, false));
                count++;
            }
        }

        if (count > 0) {
            final int finalCount = count;
            source.sendSuccess(() -> Component.literal("§aFound " + finalCount + " librarian(s) offering " + enchantName + "! They are glowing for 30 seconds."), false);
        } else {
            source.sendFailure(Component.literal("§cNo nearby librarians found offering " + enchantName + " (searched 96-block radius)."));
        }

        return count;
    }

    private static String getSuggestedShortType(String path) {
        int underscore = path.indexOf('_');
        if (underscore > 0) {
            return path.substring(0, underscore);
        }
        if (path.length() > 4) {
            return path.substring(0, 4);
        }
        return path;
    }
}