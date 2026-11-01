package com.gbdhapa;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LibrarianCore {

    public record MatchResult(Enchantment enchantment, int level) {}

    private static final Pattern LEVEL_PATTERN = Pattern.compile("^(.*?)[\\s\\-_:]+(\\d+)$");

    public static final Map<String, Integer> LOWEST_PRICES_FOR_MAX_LEVEL = Map.ofEntries(
            // Level 5 (min: 2 + 3*5 = 17 emeralds)
            Map.entry("efficiency", 17),
            Map.entry("sharpness", 17),
            Map.entry("smite", 17),
            Map.entry("bane_of_arthropods", 17),
            Map.entry("power", 17),
            Map.entry("impaling", 17),
            Map.entry("density", 17),

            // Level 4 (min: 2 + 3*4 = 14 emeralds)
            Map.entry("protection", 14),
            Map.entry("fire_protection", 14),
            Map.entry("feather_falling", 14),
            Map.entry("blast_protection", 14),
            Map.entry("projectile_protection", 14),
            Map.entry("piercing", 14),
            Map.entry("breach", 14),

            // Level 3 (min: 2 + 3*3 = 11 emeralds; treasure/double: 22 emeralds)
            Map.entry("unbreaking", 11),
            Map.entry("fortune", 11),
            Map.entry("looting", 11),
            Map.entry("respiration", 11),
            Map.entry("quick_charge", 11),
            Map.entry("depth_strider", 11),
            Map.entry("thorns", 11),
            Map.entry("sweeping_edge", 11),
            Map.entry("loyalty", 11),
            Map.entry("riptide", 11),
            Map.entry("luck_of_the_sea", 11),
            Map.entry("lure", 11),
            Map.entry("soul_speed", 22),
            Map.entry("swift_sneak", 22),
            Map.entry("wind_burst", 22),

            // Level 2 (min: 2 + 3*2 = 8 emeralds; treasure/double: 16 emeralds)
            Map.entry("fire_aspect", 8),
            Map.entry("knockback", 8),
            Map.entry("punch", 8),
            Map.entry("frost_walker", 16),

            // Level 1 (min: 2 + 3*1 = 5 emeralds; treasure/double: 10 emeralds)
            Map.entry("silk_touch", 5),
            Map.entry("infinity", 5),
            Map.entry("flame", 5),
            Map.entry("aqua_affinity", 5),
            Map.entry("multishot", 5),
            Map.entry("channeling", 5),
            Map.entry("mending", 10),
            Map.entry("binding_curse", 10),
            Map.entry("vanishing_curse", 10)
    );

    public static int getLowestPrice(Enchantment ench, int level) {
        ResourceLocation id = Registry.ENCHANTMENT.getKey(ench);
        String path = id != null ? id.getPath().toLowerCase(Locale.ROOT) : "";

        if (level == ench.getMaxLevel() && LOWEST_PRICES_FOR_MAX_LEVEL.containsKey(path)) {
            return LOWEST_PRICES_FOR_MAX_LEVEL.get(path);
        }

        boolean isDoublePrice = ench.isTreasureOnly();
        int cost = 2 + 3 * level;
        if (isDoublePrice) {
            cost *= 2;
        }
        return Math.max(1, Math.min(64, cost));
    }

    public static InteractionResult handleRightClick(Player player, Level level, BlockPos clickedPos, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        BlockState clickedState = level.getBlockState(clickedPos);

        // Only handle clicks on the Lectern workstation itself; allow vanilla sign editing when clicking signs
        if (!clickedState.is(Blocks.LECTERN)) {
            return InteractionResult.PASS;
        }

        SignBlockEntity signEntity = findAttachedSign(level, clickedPos, clickedState);
        if (signEntity == null) {
            return InteractionResult.PASS;
        }

        List<String> rawLines = getSignLines(signEntity);
        boolean hasText = rawLines.stream().anyMatch(s -> !s.isBlank());
        if (!hasText) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;

        Villager villager = findAssignedVillager(serverLevel, clickedPos);
        if (villager == null) {
            sendFailure(serverLevel, player, null, clickedPos, "No villager found with this lectern as their job station.");
            return InteractionResult.SUCCESS;
        }

        // If villager has no profession, assign Librarian
        VillagerProfession prof = villager.getVillagerData().getProfession();
        if (prof == VillagerProfession.NONE) {
            prof = VillagerProfession.LIBRARIAN;
            villager.setVillagerData(villager.getVillagerData().setProfession(prof));
        }

        if (prof != VillagerProfession.LIBRARIAN) {
            sendFailure(serverLevel, player, villager, clickedPos, "Workstation must belong to a Librarian.");
            return InteractionResult.SUCCESS;
        }

        if (hasTradedForCurrentLevel(villager)) {
            sendFailure(serverLevel, player, villager, clickedPos, "Villager has already been traded with at their current level.");
            return InteractionResult.SUCCESS;
        }

        MatchResult match = findMatchingEnchantment(rawLines);
        if (match == null) {
            String filterSummary = String.join(", ", rawLines.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
            sendFailure(serverLevel, player, villager, clickedPos, "No matching enchantment found for: " + filterSummary);
            return InteractionResult.SUCCESS;
        }

        boolean updated = applyEnchantedBookTrade(villager, match.enchantment(), match.level());
        if (!updated) {
            sendFailure(serverLevel, player, villager, clickedPos, "Failed to update villager trades.");
            return InteractionResult.SUCCESS;
        }

        sendSuccess(serverLevel, player, villager, match);
        return InteractionResult.SUCCESS;
    }

    public static SignBlockEntity findAttachedSign(Level level, BlockPos lecternPos, BlockState lecternState) {
        List<BlockPos> candidates = new ArrayList<>();

        if (lecternState.hasProperty(LecternBlock.FACING)) {
            Direction lecternFacing = lecternState.getValue(LecternBlock.FACING);
            candidates.add(lecternPos.relative(lecternFacing));
            candidates.add(lecternPos.relative(lecternFacing.getOpposite()));
            candidates.add(lecternPos.relative(lecternFacing.getClockWise()));
            candidates.add(lecternPos.relative(lecternFacing.getCounterClockWise()));
        } else {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                candidates.add(lecternPos.relative(dir));
            }
        }

        candidates.add(lecternPos.above());
        candidates.add(lecternPos.below());

        SignBlockEntity fallbackSign = null;
        for (BlockPos pos : candidates) {
            if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                List<String> lines = getSignLines(sign);
                boolean hasText = lines.stream().anyMatch(s -> !s.isBlank());
                if (hasText) {
                    return sign;
                }
                if (fallbackSign == null) {
                    fallbackSign = sign;
                }
            }
        }
        return fallbackSign;
    }

    public static List<String> getSignLines(SignBlockEntity signEntity) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Component c = signEntity.getMessage(i, false);
            if (c != null) {
                String s = c.getString().trim();
                if (!s.isEmpty()) {
                    lines.add(s);
                }
            }
        }
        return lines;
    }

    public static Villager findAssignedVillager(ServerLevel level, BlockPos lecternPos) {
        AABB box = new AABB(lecternPos).inflate(16);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive());

        for (Villager v : villagers) {
            Optional<GlobalPos> jobSite = v.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            if (jobSite.isPresent()) {
                GlobalPos pos = jobSite.get();
                if (pos.dimension().equals(level.dimension()) && pos.pos().equals(lecternPos)) {
                    return v;
                }
            }
        }

        if (villagers.size() == 1) {
            return villagers.get(0);
        }

        Villager closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Villager v : villagers) {
            double d = v.distanceToSqr(lecternPos.getX() + 0.5, lecternPos.getY() + 0.5, lecternPos.getZ() + 0.5);
            if (d < closestDist && d <= 16.0) {
                closestDist = d;
                closest = v;
            }
        }

        return closest;
    }

    public static boolean hasTradedForCurrentLevel(Villager villager) {
        MerchantOffers offers = villager.getOffers();
        if (offers == null || offers.isEmpty()) {
            return false;
        }
        for (MerchantOffer offer : offers) {
            if (offer.getUses() > 0) {
                return true;
            }
        }
        return false;
    }

    public static MatchResult findMatchingEnchantment(List<String> lines) {
        List<Enchantment> allEnchantments = new ArrayList<>();
        for (Enchantment ench : Registry.ENCHANTMENT) {
            allEnchantments.add(ench);
        }

        for (String line : lines) {
            String cleaned = line.trim();
            if (cleaned.isEmpty()) continue;

            Matcher matcher = LEVEL_PATTERN.matcher(cleaned);
            String textQuery;
            int requestedLevel = -1;

            if (matcher.matches()) {
                textQuery = matcher.group(1).trim();
                try {
                    requestedLevel = Integer.parseInt(matcher.group(2));
                } catch (NumberFormatException ignored) {}
            } else {
                textQuery = cleaned;
            }

            if (textQuery.isEmpty()) {
                continue;
            }

            Enchantment bestMatch = findEnchantment(allEnchantments, textQuery);
            if (bestMatch != null) {
                int maxLevel = bestMatch.getMaxLevel();
                int minLevel = bestMatch.getMinLevel();
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

    public static Enchantment findEnchantment(List<Enchantment> allEnchantments, String textQuery) {
        String q = textQuery.trim().toLowerCase(Locale.ROOT);
        String qClean = q.replaceAll("[^a-z0-9]", "");
        String qSpaced = q.replace("_", " ");

        Enchantment bestMatch = null;
        int bestPriority = Integer.MAX_VALUE;
        int bestLength = Integer.MAX_VALUE;

        for (Enchantment ench : allEnchantments) {
            ResourceLocation id = Registry.ENCHANTMENT.getKey(ench);
            if (id == null) continue;
            String path = id.getPath().toLowerCase(Locale.ROOT);
            String fullId = id.toString().toLowerCase(Locale.ROOT);
            String desc = new TranslatableComponent(ench.getDescriptionId()).getString().toLowerCase(Locale.ROOT);

            String pathClean = path.replaceAll("[^a-z0-9]", "");
            String pathSpaced = path.replace("_", " ");
            String descClean = desc.replaceAll("[^a-z0-9]", "");

            int priority;
            if (path.equals(q) || pathSpaced.equals(qSpaced) || pathClean.equals(qClean) || desc.equals(q) || fullId.equals(q)) {
                priority = 1;
            } else if (path.startsWith(q) || pathSpaced.startsWith(qSpaced) || desc.startsWith(q) || fullId.startsWith(q)) {
                priority = 2;
            } else if (!qClean.isEmpty() && (pathClean.startsWith(qClean) || descClean.startsWith(qClean))) {
                priority = 3;
            } else if (path.contains(q) || pathSpaced.contains(qSpaced) || desc.contains(q) || pathClean.contains(qClean)) {
                priority = 4;
            } else {
                continue;
            }

            if (priority < bestPriority || (priority == bestPriority && path.length() < bestLength)) {
                bestPriority = priority;
                bestLength = path.length();
                bestMatch = ench;
            }
        }

        return bestMatch;
    }

    public static boolean applyEnchantedBookTrade(Villager villager, Enchantment ench, int level) {
        MerchantOffers offers = villager.getOffers();
        if (offers == null) {
            offers = new MerchantOffers();
        }

        ItemStack bookStack = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(ench, level));
        int emeraldCost = getLowestPrice(ench, level);

        int targetIndex = -1;
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
        }

        MerchantOffer newOffer = new MerchantOffer(
                new ItemStack(Items.EMERALD, emeraldCost),
                new ItemStack(Items.BOOK),
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

        villager.addEffect(new MobEffectInstance(MobEffects.GLOWING, 10 * 20, 0, false, false));

        String enchName = new TranslatableComponent(match.enchantment().getDescriptionId()).getString();
        int lowestCost = getLowestPrice(match.enchantment(), match.level());
        player.displayClientMessage(new TextComponent("§a[Librarian Filter] Updated trade to: " + enchName + " " + match.level() + " (" + lowestCost + " Emeralds - Lowest Price)"), false);
    }

    private static void sendFailure(ServerLevel serverLevel, Player player, Villager villager, BlockPos fallbackPos, String reason) {
        if (villager != null) {
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER, villager.getX(), villager.getY() + 1.0, villager.getZ(), 8, 0.4, 0.4, 0.4, 0.02);
            serverLevel.playSound(null, villager.getX(), villager.getY() + 1.0, villager.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
        } else if (fallbackPos != null) {
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER, fallbackPos.getX() + 0.5, fallbackPos.getY() + 1.0, fallbackPos.getZ() + 0.5, 5, 0.3, 0.3, 0.3, 0.02);
            serverLevel.playSound(null, fallbackPos.getX() + 0.5, fallbackPos.getY() + 1.0, fallbackPos.getZ() + 0.5, SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }

        player.displayClientMessage(new TextComponent("§c[Librarian Filter] " + reason), false);
    }
}
