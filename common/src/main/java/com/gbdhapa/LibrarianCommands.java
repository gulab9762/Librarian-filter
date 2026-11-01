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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class LibrarianCommands {

    private static final Map<UUID, Set<UUID>> GLOWING_VILLAGERS_BY_PLAYER = new HashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("reroll")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("\u00a7a[Librarian Filter] Use \u00a7e/reroll setup\u00a7a to spawn a trapped villager booth, \u00a7e/reroll list\u00a7a for books, or \u00a7e/reroll find <enchantment>\u00a7a to locate villagers."), false);
                    return 1;
                })
                .then(Commands.literal("setup")
                        .requires(source -> source.isPlayer() && source.getPlayer() != null && source.getPlayer().isCreative())
                        .executes(context -> executeSetup(context, "eff"))
                        .then(Commands.argument("query", StringArgumentType.greedyString())
                                .executes(context -> executeSetup(context, StringArgumentType.getString(context, "query")))
                        )
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
                                            registry.listElements().map(h -> h.key().identifier().getPath()),
                                            builder
                                    );
                                })
                                .executes(context -> executeFind(context, StringArgumentType.getString(context, "enchantment")))
                        )
                )
        );
    }

    private static int executeSetup(CommandContext<CommandSourceStack> context, String query) {
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

        Block workstationBlock = Blocks.LECTERN;
        ResourceKey<VillagerProfession> profKey = VillagerProfession.LIBRARIAN;

        Item targetItem = LibrarianCore.findItem(source.getLevel(), query);
        if (targetItem != null) {
            String path = targetItem.builtInRegistryHolder().key().identifier().getPath();
            if (path.contains("wool") || path.contains("shear") || path.contains("carpet") || path.contains("dye")) {
                workstationBlock = Blocks.LOOM;
                profKey = VillagerProfession.SHEPHERD;
            } else if (path.contains("bread") || path.contains("wheat") || path.contains("carrot") || path.contains("potato") || path.contains("beetroot") || path.contains("melon") || path.contains("pumpkin") || path.contains("apple") || path.contains("cookie")) {
                workstationBlock = Blocks.COMPOSTER;
                profKey = VillagerProfession.FARMER;
            } else if (path.contains("pickaxe") || path.contains("shovel") || path.contains("hoe")) {
                workstationBlock = Blocks.SMITHING_TABLE;
                profKey = VillagerProfession.TOOLSMITH;
            } else if (path.contains("sword")) {
                workstationBlock = Blocks.GRINDSTONE;
                profKey = VillagerProfession.WEAPONSMITH;
            } else if (path.contains("helmet") || path.contains("chestplate") || path.contains("leggings") || path.contains("boots") || path.contains("shield")) {
                workstationBlock = Blocks.BLAST_FURNACE;
                profKey = VillagerProfession.ARMORER;
            } else if (path.contains("fish") || path.contains("salmon") || path.contains("cod") || path.contains("boat")) {
                workstationBlock = Blocks.BARREL;
                profKey = VillagerProfession.FISHERMAN;
            } else if (path.contains("beef") || path.contains("pork") || path.contains("chicken") || path.contains("mutton") || path.contains("rabbit")) {
                workstationBlock = Blocks.SMOKER;
                profKey = VillagerProfession.BUTCHER;
            } else if (path.contains("arrow") || path.contains("bow") || path.contains("crossbow") || path.contains("flint")) {
                workstationBlock = Blocks.FLETCHING_TABLE;
                profKey = VillagerProfession.FLETCHER;
            }
        }

        Villager villager = setupBooth(source, player, query, workstationBlock, profKey);
        if (villager == null) {
            return 0;
        }

        // Give player 3 stacks of Emeralds and 3 stacks of Books
        for (int i = 0; i < 3; i++) {
            giveItem(player, new ItemStack(Items.EMERALD, 64));
            giveItem(player, new ItemStack(Items.BOOK, 64));
        }

        source.sendSuccess(() -> Component.literal("\u00a7aSetup complete! Spawned an enclosed, immobile villager facing workstation with sign [\"" + query + "\"]. Right-click workstation to reroll trade!"), false);
        return 1;
    }

    private static Villager setupBooth(CommandSourceStack source, ServerPlayer player, String signText, Block workstationBlock, ResourceKey<VillagerProfession> profKey) {
        ServerLevel level = source.getLevel();
        BlockPos playerPos = player.blockPosition();
        Direction facing = player.getDirection();
        Direction opposite = facing.getOpposite();
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        BlockPos workstationPos = playerPos.relative(facing, 2);
        BlockPos villagerPos = playerPos.relative(facing, 3);
        BlockPos backPos = playerPos.relative(facing, 4);

        // 1. Floor (Y = -1): 3x3 stone
        for (int d = 2; d <= 4; d++) {
            for (int lat = -1; lat <= 1; lat++) {
                BlockPos floor = playerPos.relative(facing, d).relative(right, lat).below();
                level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            }
        }

        // 2. Ceiling (Y = +2): 3x3 glass so the villager cannot jump
        for (int d = 2; d <= 4; d++) {
            for (int lat = -1; lat <= 1; lat++) {
                BlockPos ceiling = playerPos.relative(facing, d).relative(right, lat).above(2);
                level.setBlock(ceiling, Blocks.GLASS.defaultBlockState(), 3);
            }
        }

        // 3. Back wall (d = 4): 3x2 glass (x x x)
        for (int lat = -1; lat <= 1; lat++) {
            BlockPos b0 = backPos.relative(right, lat);
            BlockPos b1 = b0.above();
            level.setBlock(b0, Blocks.GLASS.defaultBlockState(), 3);
            level.setBlock(b1, Blocks.GLASS.defaultBlockState(), 3);
        }

        // 4. Middle row (d = 3): left/right glass, center villager cell (x v x)
        level.setBlock(villagerPos.relative(left), Blocks.GLASS.defaultBlockState(), 3);
        level.setBlock(villagerPos.relative(left).above(), Blocks.GLASS.defaultBlockState(), 3);
        level.setBlock(villagerPos.relative(right), Blocks.GLASS.defaultBlockState(), 3);
        level.setBlock(villagerPos.relative(right).above(), Blocks.GLASS.defaultBlockState(), 3);
        level.setBlock(villagerPos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(villagerPos.above(), Blocks.AIR.defaultBlockState(), 3);

        // 5. Front row (d = 2): left/right glass, center workstation (x w x)
        level.setBlock(workstationPos.relative(left), Blocks.GLASS.defaultBlockState(), 3);
        level.setBlock(workstationPos.relative(left).above(), Blocks.GLASS.defaultBlockState(), 3);
        level.setBlock(workstationPos.relative(right), Blocks.GLASS.defaultBlockState(), 3);
        level.setBlock(workstationPos.relative(right).above(), Blocks.GLASS.defaultBlockState(), 3);

        if (workstationBlock == Blocks.LECTERN) {
            level.setBlock(workstationPos, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, opposite), 3);
        } else {
            level.setBlock(workstationPos, workstationBlock.defaultBlockState(), 3);
        }
        level.setBlock(workstationPos.above(), Blocks.AIR.defaultBlockState(), 3);

        // 6. Sign attached to front of workstation
        BlockPos signPos = workstationPos.relative(opposite, 1);
        level.setBlock(signPos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, opposite), 3);
        level.setBlock(signPos.above(), Blocks.AIR.defaultBlockState(), 3);

        if (level.getBlockEntity(signPos) instanceof SignBlockEntity signEntity) {
            signEntity.setText(signEntity.getText(SignTextSlot.FRONT).asMutable().setLine(0, Component.literal(signText)).asImmutable(), SignTextSlot.FRONT);
            signEntity.setChanged();
            level.sendBlockUpdated(signPos, signEntity.getBlockState(), signEntity.getBlockState(), 3);
        }

        // 7. Spawn enclosed Villager with NoAI so he cannot move
        Villager villager = new Villager(EntityTypes.VILLAGER, level);
        villager.setPos(villagerPos.getX() + 0.5, villagerPos.getY(), villagerPos.getZ() + 0.5);
        villager.setYRot(opposite.toYRot());
        villager.setYHeadRot(opposite.toYRot());
        villager.setNoAi(true);

        var professionHolder = level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(profKey);
        villager.setVillagerData(villager.getVillagerData().withProfession(professionHolder));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), workstationPos));
        level.addFreshEntity(villager);

        return villager;
    }

    private static void giveItem(ServerPlayer player, ItemStack stack) {
        if (!player.addItem(stack)) {
            player.drop(stack, false, Prediction.SERVER_ONLY);
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
                Entity entity = level.getEntityInAnyDimension(id);
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
                .sorted(Comparator.comparing(h -> h.key().identifier().getPath()))
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
            String path = holder.key().identifier().getPath();
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
                    .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/reroll list " + (page - 1))));
            nav.append(prevBtn);
        }
        nav.append(Component.literal("\u00a77Page " + page + " of " + totalPages + " "));
        if (page < totalPages) {
            MutableComponent nextBtn = Component.literal("\u00a7e[Next >>]")
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