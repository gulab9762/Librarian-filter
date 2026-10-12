package com.gbdhapa;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;

public class LibrarianCore {

    public record MatchResult(Holder.Reference<Enchantment> holder, int level) {}

    public static InteractionResult handleRightClick(Player player, Level level, BlockPos clickedPos, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        BlockEntity blockEntity = level.getBlockEntity(clickedPos);
        if (!(blockEntity instanceof SignBlockEntity signEntity)) {
            return InteractionResult.PASS;
        }

        List<String> rawLines = getSignLines(signEntity);
        boolean hasText = rawLines.stream().anyMatch(s -> !s.isBlank());
        if (!hasText) {
            return InteractionResult.PASS;
        }

        BlockPos lecternPos = findAttachedLectern(level, clickedPos);
        if (lecternPos == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;

        Villager villager = findAssignedVillager(serverLevel, lecternPos);
        if (villager == null) {
            sendFailure(serverLevel, player, null, lecternPos, "No villager found with this lectern as their job station.");
            return InteractionResult.SUCCESS;
        }

        if (hasTradedForCurrentLevel(villager)) {
            sendFailure(serverLevel, player, villager, lecternPos, "Villager has already been traded with at their current level.");
            return InteractionResult.SUCCESS;
        }

        MatchResult match = findMatchingEnchantment(serverLevel, rawLines);
        if (match == null) {
            String filterSummary = String.join(", ", rawLines.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
            sendFailure(serverLevel, player, villager, lecternPos, "No matching enchantment found in game registry for: " + filterSummary);
            return InteractionResult.SUCCESS;
        }

        boolean updated = applyEnchantedBookTrade(villager, match.holder(), match.level());
        if (!updated) {
            sendFailure(serverLevel, player, villager, lecternPos, "Failed to update villager trades.");
            return InteractionResult.SUCCESS;
        }

        sendSuccess(serverLevel, player, villager, match);
        return InteractionResult.SUCCESS;
    }

    public static List<String> getSignLines(SignBlockEntity signEntity) {
        List<String> lines = new ArrayList<>();
        for (Component c : signEntity.getFrontText().getMessages(false)) {
            String s = c.getString().trim();
            if (!s.isEmpty()) {
                lines.add(s);
            }
        }
        if (lines.isEmpty()) {
            for (Component c : signEntity.getBackText().getMessages(false)) {
                String s = c.getString().trim();
                if (!s.isEmpty()) {
                    lines.add(s);
                }
            }
        }
        return lines;
    }

    public static BlockPos findAttachedLectern(Level level, BlockPos signPos) {
        BlockState state = level.getBlockState(signPos);
        Block block = state.getBlock();

        if (state.hasProperty(WallSignBlock.FACING)) {
            Direction facing = state.getValue(WallSignBlock.FACING);
            BlockPos attached = signPos.relative(facing.getOpposite());
            if (level.getBlockState(attached).is(Blocks.LECTERN)) {
                return attached;
            }
        }

        if (block instanceof SignBlock) {
            BlockPos below = signPos.below();
            if (level.getBlockState(below).is(Blocks.LECTERN)) {
                return below;
            }
        }

        if (block instanceof CeilingHangingSignBlock) {
            BlockPos above = signPos.above();
            if (level.getBlockState(above).is(Blocks.LECTERN)) {
                return above;
            }
        }

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = signPos.relative(dir);
            if (level.getBlockState(neighbor).is(Blocks.LECTERN)) {
                return neighbor;
            }
        }

        return null;
    }

    public static Villager findAssignedVillager(ServerLevel serverLevel, BlockPos lecternPos) {
        AABB searchBox = new AABB(lecternPos).inflate(48.0);
        List<Villager> villagers = serverLevel.getEntitiesOfClass(Villager.class, searchBox);
        for (Villager candidate : villagers) {
            Optional<GlobalPos> jobSiteOpt = candidate.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            if (jobSiteOpt.isPresent()) {
                GlobalPos globalPos = jobSiteOpt.get();
                if (globalPos.dimension().equals(serverLevel.dimension()) && globalPos.pos().equals(lecternPos)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    public static boolean hasTradedForCurrentLevel(Villager villager) {
        MerchantOffers offers = villager.getOffers();
        if (offers == null || offers.isEmpty()) {
            return false;
        }

        int level = villager.getVillagerData().level();
        if (level == 1 && villager.getVillagerXp() == 0) {
            for (MerchantOffer offer : offers) {
                if (offer.getUses() > 0) {
                    return true;
                }
            }
            return false;
        }

        int size = offers.size();
        if (size % 2 == 0 && size >= 2) {
            return offers.get(size - 1).getUses() > 0 || offers.get(size - 2).getUses() > 0;
        }
        return offers.get(size - 1).getUses() > 0;
    }

    public static MatchResult findMatchingEnchantment(ServerLevel serverLevel, List<String> signLines) {
        var registry = serverLevel.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<Holder.Reference<Enchantment>> allEnchantments = registry.listElements().toList();

        for (String rawLine : signLines) {
            String cleaned = rawLine.trim();
            if (cleaned.contains(">")) {
                cleaned = cleaned.split(">")[0].trim();
            }

            String[] tokens = cleaned.split("[\\s\\-_:]+");
            if (tokens.length == 0 || tokens[0].isEmpty()) {
                continue;
            }

            String query = tokens[0].toLowerCase(Locale.ROOT);
            int requestedLevel = 0;
            if (tokens.length >= 2) {
                try {
                    requestedLevel = Integer.parseInt(tokens[1]);
                } catch (NumberFormatException ignored) {}
            }

            Holder.Reference<Enchantment> bestMatch = null;
            int bestPriority = Integer.MAX_VALUE;
            int bestLength = Integer.MAX_VALUE;

            for (Holder.Reference<Enchantment> holder : allEnchantments) {
                Identifier id = holder.key().identifier();
                String path = id.getPath().toLowerCase(Locale.ROOT);
                String fullId = id.toString().toLowerCase(Locale.ROOT);
                String desc = holder.value().description().getString().toLowerCase(Locale.ROOT);

                int priority;
                if (path.equals(query) || desc.equals(query) || fullId.equals(query)) {
                    priority = 1;
                } else if (path.startsWith(query) || desc.startsWith(query) || fullId.startsWith(query)) {
                    priority = 2;
                } else if (path.replace("_", "").startsWith(query.replace("_", ""))) {
                    priority = 3;
                } else if (path.contains(query) || desc.contains(query)) {
                    priority = 4;
                } else {
                    continue;
                }

                if (priority < bestPriority || (priority == bestPriority && path.length() < bestLength)) {
                    bestPriority = priority;
                    bestLength = path.length();
                    bestMatch = holder;
                }
            }

            if (bestMatch != null) {
                Enchantment ench = bestMatch.value();
                int maxLevel = ench.getMaxLevel();
                int minLevel = ench.getMinLevel();
                int selectedLevel;
                if (requestedLevel >= minLevel && requestedLevel <= maxLevel) {
                    selectedLevel = requestedLevel;
                } else {
                    selectedLevel = maxLevel;
                }
                return new MatchResult(bestMatch, selectedLevel);
            }
        }

        return null;
    }

    public static boolean applyEnchantedBookTrade(Villager villager, Holder.Reference<Enchantment> holder, int level) {
        MerchantOffers offers = villager.getOffers();
        if (offers == null) {
            offers = new MerchantOffers();
        }

        ItemStack bookStack = EnchantmentHelper.createBook(new EnchantmentInstance(holder, level));

        int targetIndex = -1;
        int emeraldCost = Math.min(64, Math.max(5, 5 + level * 3));

        if (!offers.isEmpty()) {
            int size = offers.size();
            int startIndex = Math.max(0, size - 2);
            for (int i = startIndex; i < size; i++) {
                if (offers.get(i).getResult().is(Items.ENCHANTED_BOOK)) {
                    targetIndex = i;
                    break;
                }
            }
            if (targetIndex == -1) {
                targetIndex = size - 1;
            }

            MerchantOffer oldOffer = offers.get(targetIndex);
            if (oldOffer.getBaseCostA().is(Items.EMERALD) && oldOffer.getBaseCostA().getCount() > 0) {
                emeraldCost = oldOffer.getBaseCostA().getCount();
            }
        }

        MerchantOffer newOffer = new MerchantOffer(
                new ItemCost(Items.EMERALD, emeraldCost),
                Optional.of(new ItemCost(Items.BOOK)),
                bookStack,
                12,
                1,
                0.05F
        );

        MerchantOffers updatedOffers = new MerchantOffers();
        updatedOffers.addAll(offers);
        if (targetIndex >= 0 && targetIndex < updatedOffers.size()) {
            updatedOffers.set(targetIndex, newOffer);
        } else {
            updatedOffers.add(newOffer);
        }

        villager.setOffers(updatedOffers);
        return true;
    }

    private static void sendSuccess(ServerLevel serverLevel, Player player, Villager villager, MatchResult match) {
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.0, villager.getZ(), 15, 0.5, 0.5, 0.5, 0.02);
        serverLevel.playSound(null, villager.getX(), villager.getY() + 1.0, villager.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);

        String enchName = match.holder().value().description().getString();
        player.sendSystemMessage(Component.literal("§a[Librarian Filter] Updated trade to: " + enchName + " " + match.level()));
    }

    private static void sendFailure(ServerLevel serverLevel, Player player, Villager villager, BlockPos fallbackPos, String reason) {
        if (villager != null) {
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER, villager.getX(), villager.getY() + 1.0, villager.getZ(), 8, 0.4, 0.4, 0.4, 0.02);
            serverLevel.playSound(null, villager.getX(), villager.getY() + 1.0, villager.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
        } else if (fallbackPos != null) {
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER, fallbackPos.getX() + 0.5, fallbackPos.getY() + 1.0, fallbackPos.getZ() + 0.5, 5, 0.3, 0.3, 0.3, 0.02);
            serverLevel.playSound(null, fallbackPos.getX() + 0.5, fallbackPos.getY() + 1.0, fallbackPos.getZ() + 0.5, SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }

        player.sendSystemMessage(Component.literal("§c[Librarian Filter] " + reason));
    }
}