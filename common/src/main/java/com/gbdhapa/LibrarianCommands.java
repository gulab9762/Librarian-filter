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
import net.minecraft.core.Registry;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
                    context.getSource().sendSuccess(new TextComponent("§a[Librarian Filter] Use §e/reroll list§a to view supported book formats, §e/reroll find <enchantment>§a to locate offering villagers, §e/reroll setup§a to spawn a test booth (creative only), or §e/reroll clear§a to remove glow."), false);
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
                                    return SharedSuggestionProvider.suggest(
                                            Registry.ENCHANTMENT.keySet().stream().map(ResourceLocation::getPath),
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
        ServerPlayer player = source.getEntity() instanceof ServerPlayer ? (ServerPlayer) source.getEntity() : null;
        if (player == null) {
            source.sendFailure(new TextComponent("§cThis command must be executed by a player."));
            return 0;
        }

        if (!player.isCreative()) {
            source.sendFailure(new TextComponent("§cThis command can only be used in Creative mode."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        BlockPos playerPos = new BlockPos(player.x, player.y, player.z);
        Direction facing = player.getDirection();
        Direction opposite = facing.getOpposite();

        BlockPos center = playerPos.relative(facing, 3);

        // Build 3x3 interior glass room
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (dy == -1) {
                        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                    } else if (dx == -2 || dx == 2 || dz == -2 || dz == 2 || dy == 3) {
                        level.setBlock(pos, Blocks.GLASS.defaultBlockState(), 3);
                    } else {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }

        BlockPos lecternPos = center.relative(opposite, 1);
        level.setBlock(lecternPos, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, opposite), 3);

        BlockPos signPos = lecternPos.relative(opposite, 1);
        level.setBlock(signPos, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, opposite), 3);
        level.setBlock(signPos.above(), Blocks.AIR.defaultBlockState(), 3);

        if (level.getBlockEntity(signPos) instanceof SignBlockEntity signEntity) {
            signEntity.setMessage(0, new TextComponent("eff"));
            signEntity.setChanged();
            level.sendBlockUpdated(signPos, signEntity.getBlockState(), signEntity.getBlockState(), 3);
        }

        Villager villager = new Villager(EntityType.VILLAGER, level);
        villager.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.getDimension().getType(), lecternPos));
        level.addFreshEntity(villager);

        for (int i = 0; i < 3; i++) {
            giveItem(player, new ItemStack(Items.EMERALD, 64));
            giveItem(player, new ItemStack(Items.BOOK, 64));
        }

        source.sendSuccess(new TextComponent("§aSetup complete! Spawned a librarian in a glass booth and gave 3 stacks of emeralds and books. Right-click the lectern to test rerolling!"), false);
        return 1;
    }

    private static void giveItem(ServerPlayer player, ItemStack stack) {
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
    }

    private static int executeClear(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getEntity() instanceof ServerPlayer ? (ServerPlayer) source.getEntity() : null;
        if (player == null) {
            source.sendFailure(new TextComponent("§cThis command must be executed by a player."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        int removed = clearPreviousGlow(level, player.getUUID());
        source.sendSuccess(new TextComponent("§aRemoved glowing effect from " + removed + " previously highlighted villager(s)."), false);
        return removed;
    }

    private static int clearPreviousGlow(ServerLevel level, UUID playerUuid) {
        Set<UUID> previous = GLOWING_VILLAGERS_BY_PLAYER.remove(playerUuid);
        int count = 0;
        if (previous != null) {
            for (UUID id : previous) {
                Entity entity = level.getEntity(id);
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

        List<Enchantment> enchantments = new ArrayList<>();
        for (Enchantment ench : Registry.ENCHANTMENT) {
            enchantments.add(ench);
        }
        enchantments.sort(Comparator.comparing(e -> {
            ResourceLocation loc = Registry.ENCHANTMENT.getKey(e);
            return loc != null ? loc.getPath() : "";
        }));

        if (enchantments.isEmpty()) {
            source.sendFailure(new TextComponent("§cNo enchantments found in registry."));
            return 0;
        }

        int pageSize = 7;
        int totalPages = (int) Math.ceil((double) enchantments.size() / pageSize);
        int page = Math.max(1, Math.min(requestedPage, totalPages));

        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, enchantments.size());

        source.sendSuccess(new TextComponent("§6=== Librarian Filter: Available Enchantments (Page " + page + "/" + totalPages + ") ==="), false);

        for (int i = startIndex; i < endIndex; i++) {
            Enchantment ench = enchantments.get(i);
            ResourceLocation id = Registry.ENCHANTMENT.getKey(ench);
            String path = id != null ? id.getPath() : "";
            int maxLevel = ench.getMaxLevel();
            String desc = new TranslatableComponent(ench.getDescriptionId()).getString();
            int lowestCost = LibrarianCore.getLowestPrice(ench, maxLevel);

            String shortType = getSuggestedShortType(path);

            Component entry = new TextComponent(" §e* Type: §f\"" + shortType + "\" §7(or \"" + path + "\") §8-> ")
                    .append(new TextComponent("§a" + desc + " " + maxLevel))
                    .append(new TextComponent(" §6[" + lowestCost + " Emeralds]"));

            source.sendSuccess(entry, false);
        }

        TextComponent nav = new TextComponent("§6----------------------------------------\n ");
        if (page > 1) {
            Component prevBtn = new TextComponent("§e[<< Prev] ")
                    .setStyle(new Style().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reroll list " + (page - 1))));
            nav.append(prevBtn);
        }
        nav.append(new TextComponent("§7Page " + page + " of " + totalPages + " "));
        if (page < totalPages) {
            Component nextBtn = new TextComponent("§e[Next >>]")
                    .setStyle(new Style().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/reroll list " + (page + 1))));
            nav.append(nextBtn);
        }

        source.sendSuccess(nav, false);
        return 1;
    }

    private static int executeFind(CommandContext<CommandSourceStack> context, String query) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getEntity() instanceof ServerPlayer ? (ServerPlayer) source.getEntity() : null;
        if (player == null) {
            source.sendFailure(new TextComponent("§cThis command must be executed by a player."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        UUID playerUuid = player.getUUID();

        clearPreviousGlow(level, playerUuid);

        List<Enchantment> allEnchantments = new ArrayList<>();
        for (Enchantment e : Registry.ENCHANTMENT) {
            allEnchantments.add(e);
        }

        Enchantment matchedEnchantment = LibrarianCore.findEnchantment(allEnchantments, query);
        if (matchedEnchantment == null) {
            source.sendFailure(new TextComponent("§cNo enchantment found matching: \"" + query + "\""));
            return 0;
        }

        String enchantName = new TranslatableComponent(matchedEnchantment.getDescriptionId()).getString();
        AABB searchBox = player.getBoundingBox().inflate(96.0);
        List<Villager> nearbyVillagers = level.getEntitiesOfClass(Villager.class, searchBox);

        Set<UUID> newlyFoundVillagers = new HashSet<>();
        int count = 0;
        for (Villager villager : nearbyVillagers) {
            boolean offersTargetBook = false;
            for (MerchantOffer offer : villager.getOffers()) {
                ItemStack result = offer.getResult();
                if (result.getItem() == Items.ENCHANTED_BOOK) {
                    Map<Enchantment, Integer> map = EnchantmentHelper.getEnchantments(result);
                    if (map.containsKey(matchedEnchantment)) {
                        offersTargetBook = true;
                        break;
                    }
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
            source.sendSuccess(new TextComponent("§aFound " + finalCount + " librarian(s) offering " + enchantName + "! Previous glow cleared; new ones glowing for 30s."), false);
        } else {
            source.sendFailure(new TextComponent("§cNo nearby librarians found offering " + enchantName + " (searched 96-block radius)."));
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
