package com.gbdhapa;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class LibrarianCommands {

    private static final Map<UUID, Set<UUID>> GLOWING_VILLAGERS_BY_PLAYER = new HashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("reroll")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("\u00a7a[Librarian Filter] Use \u00a7e/reroll list\u00a7a to view supported book formats, \u00a7e/reroll find <enchantment>\u00a7a to locate offering villagers, \u00a7e/reroll setup\u00a7a to spawn a test booth (creative only), or \u00a7e/reroll clear\u00a7a to remove glow."), false);
                    return 1;
                })
                .then(Commands.literal("setup")
                        .executes(LibrarianCommands::executeSetup)
                )
                .then(Commands.literal("list")
                        .executes(context -> executeList(context, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> executeList(context, IntegerArgumentType.getInteger(context, "page")))
                        )
                )
                .then(Commands.literal("clear")
                        .executes(LibrarianCommands::executeClear)
                )
                .then(Commands.literal("find")
                        .then(Commands.argument("enchantment", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    var registry = context.getSource().getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                                    return SharedSuggestionProvider.suggest(
                                            registry.listElements().map(h -> h.key().location().getPath()),
                                            builder
                                    );
                                })
                                .executes(context -> executeFind(context, StringArgumentType.getString(context, "enchantment")))
                        )
                )
        );
    }

    private static int executeSetup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("\u00a7cThis command must be executed by a player."));
            return 0;
        }

        if (!player.isCreative()) {
            source.sendFailure(Component.literal("\u00a7cThis command can only be used in Creative mode."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        BlockPos playerPos = player.blockPosition();
        Direction facing = player.getDirection();
        Direction opposite = facing.getOpposite();

        BlockPos center = playerPos.relative(facing, 3); // 3 blocks in front of the player

        // Build a small 3x3 interior glass room (5x5 outer)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (dy == -1) {
                        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3); // Floor
                    } else if (dx == -2 || dx == 2 || dz == -2 || dz == 2 || dy == 3) {
                        level.setBlock(pos, Blocks.GLASS.defaultBlockState(), 3); // Walls & Roof
                    } else {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); // Interior
                    }
                }
            }
        }

        // Place Lectern inside the room, in front of the villager
        BlockPos lecternPos = center.relative(opposite, 1);
        level.setBlock(lecternPos, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, opposite), 3);

        // Place Sign attached to the front of the Lectern (on the front wall facing player)
        BlockPos signPos = lecternPos.relative(opposite, 1);
        level.setBlock(signPos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, opposite), 3);

        // Clear the block directly above the sign so the player can reach and right-click the lectern directly
        level.setBlock(signPos.above(), Blocks.AIR.defaultBlockState(), 3);

        // Set default sign text "eff"
        if (level.getBlockEntity(signPos) instanceof SignBlockEntity signEntity) {
            signEntity.setText(signEntity.getFrontText().setMessage(0, Component.literal("eff")), true);
            signEntity.setChanged();
            level.sendBlockUpdated(signPos, signEntity.getBlockState(), signEntity.getBlockState(), 3);
        }

        // Spawn Villager at center
        Villager villager = new Villager(EntityType.VILLAGER, level);
        villager.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);

        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), lecternPos));
        level.addFreshEntity(villager);

        // Give player 3 stacks of Emeralds and 3 stacks of Books
        for (int i = 0; i < 3; i++) {
            giveItem(player, new ItemStack(Items.EMERALD, 64));
            giveItem(player, new ItemStack(Items.BOOK, 64));
        }

        source.sendSuccess(() -> Component.literal("\u00a7aSetup complete! Spawned a librarian in a glass booth and gave 3 stacks of emeralds and books. Right-click the lectern to test rerolling!"), false);
        return 1;
    }

    private static void giveItem(ServerPlayer player, ItemStack stack) {
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }

    private static int executeClear(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("\u00a7cThis command must be executed by a player."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        int removed = clearPreviousGlow(level, player.getUUID());
        source.sendSuccess(() -> Component.literal("\u00a7aRemoved glowing effect from " + removed + " previously highlighted villager(s)."), false);
        return removed;
    }

    private static int clearPreviousGlow(ServerLevel level, UUID playerUuid) {
        Set<UUID> previous = GLOWING_VILLAGERS_BY_PLAYER.remove(playerUuid);
        int count = 0;
        if (previous != null) {
            for (UUID id : previous) {
                Entity entity = level.getEntity(id);
            if (entity == null) {
                for (ServerLevel sl : level.getServer().getAllLevels()) {
                    entity = sl.getEntity(id);
                    if (entity != null) break;
                }
            }
                if (entity instanceof LivingEntity living) {
                    living.removeEffect(MobEffects.GLOWING);
                    count++;
                }
            }
        }
        return count;
    }

    private static int executeList(CommandContext<CommandSourceStack> context, int requestedPage) {
        CommandSourceStack source = context.getSource();
        var registry = source.getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        List<Holder.Reference<Enchantment>> enchantments = registry.listElements()
                .sorted(Comparator.comparing(h -> h.key().location().getPath()))
                .toList();

        if (enchantments.isEmpty()) {
            source.sendFailure(Component.literal("\u00a7cNo enchantments found in registry."));
            return 0;
        }

        int pageSize = 7;
        int totalPages = Math.max(1, (int) Math.ceil((double) enchantments.size() / pageSize));
        int page = Math.min(Math.max(1, requestedPage), totalPages);

        source.sendSuccess(() -> Component.literal("\u00a76========================================"), false);
        source.sendSuccess(() -> Component.literal("\u00a76   Librarian Books \u00a7e(Page " + page + "/" + totalPages + ")"), false);
        source.sendSuccess(() -> Component.literal("\u00a77   Write any of these keywords on a sign:"), false);
        source.sendSuccess(() -> Component.literal("\u00a76========================================"), false);

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, enchantments.size());

        for (int i = start; i < end; i++) {
            Holder.Reference<Enchantment> holder = enchantments.get(i);
            String path = holder.key().location().getPath();
            Enchantment ench = holder.value();
            int maxLevel = ench.getMaxLevel();
            String desc = ench.description().getString();
            int lowestCost = LibrarianCore.getLowestPrice(holder, maxLevel);

            String shortType = getSuggestedShortType(path);

            MutableComponent entry = Component.literal(" \u00a7e* Type: \u00a7f\"" + shortType + "\" \u00a77(or \"" + path + "\") \u00a78-> ")
                    .append(Component.literal("\u00a7a" + desc + " " + maxLevel))
                    .append(Component.literal(" \u00a76[" + lowestCost + " Emeralds]"));

            source.sendSuccess(() -> entry, false);
        }

        // Navigation controls
        MutableComponent nav = Component.literal("\u00a76----------------------------------------\n ");
        if (page > 1) {
            MutableComponent prevBtn = Component.literal("\u00a7e[<< Prev] ")
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reroll list " + (page - 1))));
            nav.append(prevBtn);
        }
        nav.append(Component.literal("\u00a77Page " + page + " of " + totalPages + " "));
        if (page < totalPages) {
            MutableComponent nextBtn = Component.literal("\u00a7e[Next >>]")
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reroll list " + (page + 1))));
            nav.append(nextBtn);
        }

        source.sendSuccess(() -> nav, false);
        return 1;
    }

    private static int executeFind(CommandContext<CommandSourceStack> context, String query) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("\u00a7cThis command must be executed by a player."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        UUID playerUuid = player.getUUID();

        // 1. Remove glow from previously highlighted villagers
        clearPreviousGlow(level, playerUuid);

        // 2. Find matching enchantment
        var registry = source.getServer().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder.Reference<Enchantment> matchedEnchantment = LibrarianCore.findEnchantmentHolder(registry.listElements().toList(), query);
        if (matchedEnchantment == null) {
            source.sendFailure(Component.literal("\u00a7cNo enchantment found matching: \"" + query + "\""));
            return 0;
        }

        String enchantName = matchedEnchantment.value().description().getString();
        AABB searchBox = player.getBoundingBox().inflate(96.0);
        List<Villager> nearbyVillagers = level.getEntitiesOfClass(Villager.class, searchBox);

        Set<UUID> newlyFoundVillagers = new HashSet<>();
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
                newlyFoundVillagers.add(villager.getUUID());
                count++;
            }
        }

        if (!newlyFoundVillagers.isEmpty()) {
            GLOWING_VILLAGERS_BY_PLAYER.put(playerUuid, newlyFoundVillagers);
            final int finalCount = count;
            source.sendSuccess(() -> Component.literal("\u00a7aFound " + finalCount + " librarian(s) offering " + enchantName + "! Previous glow cleared; new ones glowing for 30s."), false);
        } else {
            source.sendFailure(Component.literal("\u00a7cNo nearby librarians found offering " + enchantName + " (searched 96-block radius)."));
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