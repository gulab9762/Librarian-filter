package com.gbdhapa;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.EnchantedBookItem;
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

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.StandingSignBlock;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LibrarianCore {

    public record MatchResult(Holder.Reference<Enchantment> holder, int level) {}

    private static final Pattern LEVEL_PATTERN = Pattern.compile("^(.*?)[\\s\\-_:]+(\\d+)$");

    /**
     * Researched map of vanilla enchantments at their MAXIMUM level with their absolute
     * LOWEST possible emerald price in vanilla Minecraft.
     */
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

    public static int getLowestPrice(Holder.Reference<Enchantment> holder, int level) {
        String path = holder.key().location().getPath().toLowerCase(Locale.ROOT);
        Enchantment enchantment = holder.value();

        if (level == enchantment.getMaxLevel() && LOWEST_PRICES_FOR_MAX_LEVEL.containsKey(path)) {
            return LOWEST_PRICES_FOR_MAX_LEVEL.get(path);
        }

        boolean isDoublePrice = holder.value().isTreasureOnly();
        int cost = 2 + 3 * level;
        if (isDoublePrice) {
            cost *= 2;
        }
        return Math.max(1, Math.min(64, cost));
    }

    public static final Set<net.minecraft.world.level.block.Block> WORKSTATIONS = Set.of(
            Blocks.LECTERN,
            Blocks.COMPOSTER,
            Blocks.FLETCHING_TABLE,
            Blocks.BLAST_FURNACE,
            Blocks.SMOKER,
            Blocks.CARTOGRAPHY_TABLE,
            Blocks.BREWING_STAND,
            Blocks.BARREL,
            Blocks.CAULDRON,
            Blocks.WATER_CAULDRON,
            Blocks.LAVA_CAULDRON,
            Blocks.POWDER_SNOW_CAULDRON,
            Blocks.STONECUTTER,
            Blocks.LOOM,
            Blocks.SMITHING_TABLE,
            Blocks.GRINDSTONE
    );

    public static boolean isWorkstation(BlockState state) {
        return state != null && WORKSTATIONS.contains(state.getBlock());
    }

    public static InteractionResult handleRightClick(Player player, Level level, BlockPos clickedPos, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        BlockState clickedState = level.getBlockState(clickedPos);
        BlockPos workstationPos = clickedPos;

        if (clickedState.getBlock() instanceof WallSignBlock) {
            Direction attachedDir = clickedState.getValue(WallSignBlock.FACING).getOpposite();
            BlockPos attachedPos = clickedPos.relative(attachedDir);
            BlockState attachedState = level.getBlockState(attachedPos);
            if (isWorkstation(attachedState)) {
                workstationPos = attachedPos;
                clickedState = attachedState;
            }
        } else if (clickedState.getBlock() instanceof StandingSignBlock) {
            BlockPos belowPos = clickedPos.below();
            BlockState belowState = level.getBlockState(belowPos);
            if (isWorkstation(belowState)) {
                workstationPos = belowPos;
                clickedState = belowState;
            }
        }

        if (!isWorkstation(clickedState)) {
            return InteractionResult.PASS;
        }

        SignBlockEntity signEntity = findAttachedSign(level, workstationPos, clickedState);
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

        Villager villager = findAssignedVillager(serverLevel, workstationPos);
        if (villager == null) {
            sendFailure(serverLevel, player, null, workstationPos, "No villager found with this workstation as their job station.");
            return InteractionResult.SUCCESS;
        }

        if (hasTradedForCurrentLevel(villager)) {
            sendFailure(serverLevel, player, villager, workstationPos, "Villager has already been traded with at their current level.");
            return InteractionResult.SUCCESS;
        }

        VillagerData villagerData = villager.getVillagerData();
        VillagerProfession profession = villagerData.getProfession();

        // 1. If Librarian, try matching an enchantment book first
        if (profession == VillagerProfession.LIBRARIAN) {
            MatchResult match = findMatchingEnchantment(serverLevel, rawLines);
            if (match != null) {
                boolean updated = applyEnchantedBookTrade(villager, match.holder(), match.level());
                if (!updated) {
                    sendFailure(serverLevel, player, villager, workstationPos, "Failed to update villager trades.");
                    return InteractionResult.SUCCESS;
                }
                sendSuccess(serverLevel, player, villager, match);
                return InteractionResult.SUCCESS;
            }
        }

        // 2. Non-librarian (or librarian general item trades)
        List<String> queries = cleanSignQueries(rawLines);
        if (queries.isEmpty()) {
            return InteractionResult.PASS;
        }

        MerchantOffer matchedOffer = findMatchingTradeOffer(villager, queries);
        if (matchedOffer == null) {
            String filterSummary = String.join(", ", queries);
            String profName = profession.name().toLowerCase(Locale.ROOT);
            sendFailure(serverLevel, player, villager, workstationPos,
                    "No matching trade found for " + profName + " (level " + villagerData.getLevel() + ") for: " + filterSummary);
            return InteractionResult.SUCCESS;
        }

        boolean updated = applyTradeOffer(villager, matchedOffer);
        if (!updated) {
            sendFailure(serverLevel, player, villager, workstationPos, "Failed to update villager trades.");
            return InteractionResult.SUCCESS;
        }

        sendTradeSuccess(serverLevel, player, villager, matchedOffer);
        return InteractionResult.SUCCESS;
    }

    public static SignBlockEntity findAttachedSign(Level level, BlockPos lecternPos, BlockState lecternState) {
        List<BlockPos> candidates = new ArrayList<>();

        if (lecternState.hasProperty(LecternBlock.FACING)) {
            Direction facing = lecternState.getValue(LecternBlock.FACING);
            candidates.add(lecternPos.relative(facing));
            candidates.add(lecternPos.relative(facing.getOpposite()));
        }

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = lecternPos.relative(dir);
            if (!candidates.contains(p)) {
                candidates.add(p);
            }
        }

        candidates.add(lecternPos.above());
        candidates.add(lecternPos.below());

        SignBlockEntity fallbackSign = null;
        for (BlockPos pos : candidates) {
            if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                List<String> lines = getSignLines(sign);
                if (lines.stream().anyMatch(s -> !s.isBlank())) {
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

        int level = villager.getVillagerData().getLevel();
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
        List<Holder.Reference<Enchantment>> allEnchantments = BuiltInRegistries.ENCHANTMENT.holders().toList();

        for (String rawLine : signLines) {
            String cleaned = rawLine.trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            if (cleaned.contains(">")) {
                cleaned = cleaned.split(">")[0].trim();
            }

            cleaned = cleaned.replaceAll("[-:]+$", "").trim();
            if (cleaned.isEmpty()) {
                continue;
            }

            int requestedLevel = 0;
            String textQuery;
            Matcher levelMatcher = LEVEL_PATTERN.matcher(cleaned);
            if (levelMatcher.matches()) {
                textQuery = levelMatcher.group(1).trim();
                try {
                    requestedLevel = Integer.parseInt(levelMatcher.group(2));
                } catch (NumberFormatException ignored) {}
            } else {
                textQuery = cleaned;
            }

            if (textQuery.isEmpty()) {
                continue;
            }

            Holder.Reference<Enchantment> bestMatch = findEnchantmentHolder(allEnchantments, textQuery);
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

    public static Holder.Reference<Enchantment> findEnchantmentHolder(List<Holder.Reference<Enchantment>> allEnchantments, String textQuery) {
        String q = textQuery.trim().toLowerCase(Locale.ROOT);
        String qClean = q.replaceAll("[^a-z0-9]", "");
        String qSpaced = q.replace("_", " ");

        Holder.Reference<Enchantment> bestMatch = null;
        int bestPriority = Integer.MAX_VALUE;
        int bestLength = Integer.MAX_VALUE;

        for (Holder.Reference<Enchantment> holder : allEnchantments) {
            ResourceLocation id = holder.key().location();
            String path = id.getPath().toLowerCase(Locale.ROOT);
            String fullId = id.toString().toLowerCase(Locale.ROOT);
            String desc = Component.translatable(holder.value().getDescriptionId()).getString().toLowerCase(Locale.ROOT);

            String pathClean = path.replaceAll("[^a-z0-9]", "");
            String pathSpaced = path.replace("_", " ");
            String descClean = desc.replaceAll("[^a-z0-9]", "");

            int priority;
            // Priority 1: Exact matches (path, spaced, clean alphanumeric, or description)
            if (path.equals(q) || pathSpaced.equals(qSpaced) || pathClean.equals(qClean) || desc.equals(q) || fullId.equals(q)) {
                priority = 1;
            }
            // Priority 2: Direct prefix match
            else if (path.startsWith(q) || pathSpaced.startsWith(qSpaced) || desc.startsWith(q) || fullId.startsWith(q)) {
                priority = 2;
            }
            // Priority 3: Clean alphanumeric prefix match
            else if (!qClean.isEmpty() && (pathClean.startsWith(qClean) || descClean.startsWith(qClean))) {
                priority = 3;
            }
            // Priority 4: Contains substring match as fallback
            else if (path.contains(q) || pathSpaced.contains(qSpaced) || desc.contains(q) || pathClean.contains(qClean)) {
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

        return bestMatch;
    }

    public static boolean applyEnchantedBookTrade(Villager villager, Holder.Reference<Enchantment> holder, int level) {
        MerchantOffers offers = villager.getOffers();
        if (offers == null) {
            offers = new MerchantOffers();
        }

        ItemStack bookStack = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder.value(), level));
        int emeraldCost = getLowestPrice(holder, level);

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

        String enchName = Component.translatable(match.holder().value().getDescriptionId()).getString();
        int lowestCost = getLowestPrice(match.holder(), match.level());
        player.displayClientMessage(Component.literal("§a[Librarian Filter] Updated trade to: " + enchName + " " + match.level() + " (" + lowestCost + " Emeralds - Lowest Price)"), false);
    }

    private static void sendFailure(ServerLevel serverLevel, Player player, Villager villager, BlockPos fallbackPos, String reason) {
        if (villager != null) {
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER, villager.getX(), villager.getY() + 1.0, villager.getZ(), 8, 0.4, 0.4, 0.4, 0.02);
            serverLevel.playSound(null, villager.getX(), villager.getY() + 1.0, villager.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
        } else if (fallbackPos != null) {
            serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER, fallbackPos.getX() + 0.5, fallbackPos.getY() + 1.0, fallbackPos.getZ() + 0.5, 5, 0.3, 0.3, 0.3, 0.02);
            serverLevel.playSound(null, fallbackPos.getX() + 0.5, fallbackPos.getY() + 1.0, fallbackPos.getZ() + 0.5, SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }

        player.displayClientMessage(Component.literal("§c[Librarian Filter] " + reason), false);
    }

    public static List<String> cleanSignQueries(List<String> rawLines) {
        List<String> queries = new ArrayList<>();
        for (String line : rawLines) {
            String cleaned = line.trim();
            if (cleaned.contains(">")) {
                cleaned = cleaned.split(">")[0].trim();
            }
            cleaned = cleaned.replaceAll("[-:]+$", "").trim();
            if (!cleaned.isEmpty()) {
                queries.add(cleaned);
            }
        }
        return queries;
    }

    public static MerchantOffer findMatchingTradeOffer(Villager villager, List<String> queries) {
        VillagerData villagerData = villager.getVillagerData();
        VillagerProfession profession = villagerData.getProfession();
        int level = villagerData.getLevel();

        Int2ObjectMap<VillagerTrades.ItemListing[]> tradesMap = VillagerTrades.TRADES.get(profession);
        if (tradesMap == null || tradesMap.isEmpty()) {
            return null;
        }

        VillagerTrades.ItemListing[] listings = tradesMap.get(level);
        if (listings == null || listings.length == 0) {
            return null;
        }

        for (String query : queries) {
            // 1. First check existing offers on the villager
            MerchantOffers existingOffers = villager.getOffers();
            if (existingOffers != null) {
                for (MerchantOffer existing : existingOffers) {
                    if (offerMatchesQuery(existing, query)) {
                        return existing;
                    }
                }
            }

            // 2. Next sample possible trade listings for this profession and level
            for (VillagerTrades.ItemListing listing : listings) {
                // Sample multiple times to catch randomized trades like tipped arrows
                for (int attempt = 0; attempt < 64; attempt++) {
                    MerchantOffer offer = listing.getOffer(villager, villager.getRandom());
                    if (offer != null && offerMatchesQuery(offer, query)) {
                        return offer;
                    }
                }
            }
        }

        return null;
    }

    public static boolean offerMatchesQuery(MerchantOffer offer, String query) {
        if (offer == null) {
            return false;
        }
        if (itemMatchesQuery(offer.getResult(), query)) {
            return true;
        }
        if (itemMatchesQuery(offer.getCostA(), query)) {
            return true;
        }
        if (!offer.getCostB().isEmpty() && itemMatchesQuery(offer.getCostB(), query)) {
            return true;
        }
        return false;
    }

    public static boolean itemMatchesQuery(ItemStack stack, String rawQuery) {
        if (stack == null || stack.isEmpty() || rawQuery == null || rawQuery.trim().isEmpty()) {
            return false;
        }

        String q = rawQuery.trim().toLowerCase(Locale.ROOT);
        String qClean = q.replaceAll("[^a-z0-9]", "");
        String qSpaced = q.replace("_", " ");

        // 1. Check item hover name (e.g. "Potato", "Arrow of Weakness", "Bread")
        String hoverName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        if (hoverName.startsWith(q) || hoverName.startsWith(qSpaced) || hoverName.contains(q) || hoverName.contains(qSpaced)) {
            return true;
        }
        for (String word : hoverName.split("\\s+")) {
            if (word.startsWith(q) || word.startsWith(qClean)) {
                return true;
            }
        }

        // 2. Check item registry path (e.g. "potato", "tipped_arrow", "bread")
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId != null) {
            String path = itemId.getPath().toLowerCase(Locale.ROOT);
            String fullId = itemId.toString().toLowerCase(Locale.ROOT);
            if (path.startsWith(q) || path.replace("_", " ").startsWith(qSpaced) || path.contains(q) || fullId.startsWith(q)) {
                return true;
            }
        }

        // 3. Check item translation key description (e.g. "item.minecraft.potato")
        String descName = Component.translatable(stack.getDescriptionId()).getString().toLowerCase(Locale.ROOT);
        if (descName.startsWith(q) || descName.contains(q)) {
            return true;
        }
        for (String word : descName.split("\\s+")) {
            if (word.startsWith(q) || word.startsWith(qClean)) {
                return true;
            }
        }

        // 4. Check Potion & Mob Effects (crucial for Tipped Arrows, Potions)
        Potion potion = PotionUtils.getPotion(stack);
        if (potion != null && potion != Potions.EMPTY) {
            ResourceLocation potionId = BuiltInRegistries.POTION.getKey(potion);
            if (potionId != null) {
                String potionPath = potionId.getPath().toLowerCase(Locale.ROOT);
                if (potionPath.startsWith(q) || potionPath.contains(q) || potionId.toString().startsWith(q)) {
                    return true;
                }
                String strippedPotion = potionPath.replaceFirst("^(long_|strong_)", "");
                if (strippedPotion.startsWith(q) || strippedPotion.contains(q)) {
                    return true;
                }
            }

            for (MobEffectInstance effectInstance : potion.getEffects()) {
                ResourceLocation effectId = BuiltInRegistries.MOB_EFFECT.getKey(effectInstance.getEffect());
                if (effectId != null) {
                    String effectPath = effectId.getPath().toLowerCase(Locale.ROOT);
                    if (effectPath.startsWith(q) || effectPath.contains(q)) {
                        return true;
                    }
                }
                String effectName = Component.translatable(effectInstance.getEffect().getDescriptionId()).getString().toLowerCase(Locale.ROOT);
                if (effectName.startsWith(q) || effectName.contains(q)) {
                    return true;
                }
            }
        }

        for (MobEffectInstance effectInstance : PotionUtils.getCustomEffects(stack)) {
            ResourceLocation effectId = BuiltInRegistries.MOB_EFFECT.getKey(effectInstance.getEffect());
            if (effectId != null) {
                String effectPath = effectId.getPath().toLowerCase(Locale.ROOT);
                if (effectPath.startsWith(q) || effectPath.contains(q)) {
                    return true;
                }
            }
            String effectName = Component.translatable(effectInstance.getEffect().getDescriptionId()).getString().toLowerCase(Locale.ROOT);
            if (effectName.startsWith(q) || effectName.contains(q)) {
                return true;
            }
        }

        // 4b. Check Suspicious Stew effects (stored in NBT as 'Effects' with EffectId byte/int)
        if (stack.is(Items.SUSPICIOUS_STEW) && stack.hasTag()) {
            CompoundTag stewTag = stack.getTag();
            if (stewTag != null && stewTag.contains("Effects", Tag.TAG_LIST)) {
                net.minecraft.nbt.ListTag effectsList = stewTag.getList("Effects", Tag.TAG_COMPOUND);
                for (int i = 0; i < effectsList.size(); i++) {
                    CompoundTag effTag = effectsList.getCompound(i);
                    int effectIdNum = 0;
                    if (effTag.contains("EffectId", Tag.TAG_INT)) {
                        effectIdNum = effTag.getInt("EffectId");
                    } else if (effTag.contains("EffectId", Tag.TAG_BYTE)) {
                        effectIdNum = effTag.getByte("EffectId") & 0xFF;
                    }
                    net.minecraft.world.effect.MobEffect effect = net.minecraft.world.effect.MobEffect.byId(effectIdNum);
                    if (effect != null) {
                        ResourceLocation effLoc = BuiltInRegistries.MOB_EFFECT.getKey(effect);
                        if (effLoc != null) {
                            String effPath = effLoc.getPath().toLowerCase(Locale.ROOT);
                            if (effPath.startsWith(q) || effPath.contains(q) || effPath.replace("_", " ").startsWith(qSpaced)) {
                                return true;
                            }
                        }
                        String effName = Component.translatable(effect.getDescriptionId()).getString().toLowerCase(Locale.ROOT);
                        if (effName.startsWith(q) || effName.contains(q)) {
                            return true;
                        }
                    }
                }
            }
        }

        // 5. Check Enchantments on item (tools, armor, books)
        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
        for (Enchantment ench : enchants.keySet()) {
            ResourceLocation enchId = BuiltInRegistries.ENCHANTMENT.getKey(ench);
            if (enchId != null) {
                String enchPath = enchId.getPath().toLowerCase(Locale.ROOT);
                if (enchPath.startsWith(q) || enchPath.replace("_", " ").startsWith(qSpaced) || enchPath.contains(q)) {
                    return true;
                }
            }
            String enchName = Component.translatable(ench.getDescriptionId()).getString().toLowerCase(Locale.ROOT);
            if (enchName.startsWith(q) || enchName.contains(q)) {
                return true;
            }
        }

        // 6. Check NBT CompoundTag (tag keys, string values, nested tags)
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            if (tagContainsQuery(tag, q)) {
                return true;
            }
        }

        // 7. Check Item TagKey (e.g. #arrows, #wool, #planks)
        if (stack.getTags().anyMatch(tagKey -> {
            String tagPath = tagKey.location().getPath().toLowerCase(Locale.ROOT);
            return tagPath.startsWith(q) || tagPath.replace("_", " ").startsWith(qSpaced) || tagPath.contains(q);
        })) {
            return true;
        }

        return false;
    }

    private static boolean tagContainsQuery(CompoundTag tag, String q) {
        for (String key : tag.getAllKeys()) {
            if (key.toLowerCase(Locale.ROOT).startsWith(q)) {
                return true;
            }
            byte type = tag.getTagType(key);
            if (type == Tag.TAG_STRING) {
                String val = tag.getString(key).toLowerCase(Locale.ROOT);
                if (val.startsWith("minecraft:")) {
                    val = val.substring("minecraft:".length());
                }
                if (val.startsWith(q) || val.contains(q)) {
                    return true;
                }
            } else if (type == Tag.TAG_COMPOUND) {
                if (tagContainsQuery(tag.getCompound(key), q)) {
                    return true;
                }
            } else if (type == Tag.TAG_LIST) {
                net.minecraft.nbt.ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    if (tagContainsQuery(list.getCompound(i), q)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean applyTradeOffer(Villager villager, MerchantOffer newOffer) {
        MerchantOffers offers = villager.getOffers();
        if (offers == null) {
            offers = new MerchantOffers();
        }

        for (MerchantOffer existing : offers) {
            if (areOffersEquivalent(existing, newOffer)) {
                return true;
            }
        }

        int size = offers.size();
        int targetIndex = -1;

        if (size > 0) {
            int startIndex = Math.max(0, size - 2);
            targetIndex = size - 1;
            for (int i = startIndex; i < size; i++) {
                if (offers.get(i).getUses() == 0) {
                    targetIndex = i;
                    break;
                }
            }
        }

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

    private static boolean areOffersEquivalent(MerchantOffer a, MerchantOffer b) {
        if (!ItemStack.isSameItemSameTags(a.getResult(), b.getResult())) return false;
        if (a.getResult().getCount() != b.getResult().getCount()) return false;
        if (!ItemStack.isSameItemSameTags(a.getCostA(), b.getCostA())) return false;
        if (a.getCostA().getCount() != b.getCostA().getCount()) return false;
        if (!ItemStack.isSameItemSameTags(a.getCostB(), b.getCostB())) return false;
        if (a.getCostB().getCount() != b.getCostB().getCount()) return false;
        return true;
    }

    private static void sendTradeSuccess(ServerLevel serverLevel, Player player, Villager villager, MerchantOffer offer) {
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.0, villager.getZ(), 15, 0.5, 0.5, 0.5, 0.02);
        serverLevel.playSound(null, villager.getX(), villager.getY() + 1.0, villager.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);

        String resultDesc = offer.getResult().getHoverName().getString();
        String costADesc = offer.getCostA().getCount() + " " + offer.getCostA().getHoverName().getString();
        String costBDesc = !offer.getCostB().isEmpty() ? " + " + offer.getCostB().getCount() + " " + offer.getCostB().getHoverName().getString() : "";

        player.displayClientMessage(Component.literal("§a[Librarian Filter] Updated trade to: " + costADesc + costBDesc + " -> " + offer.getResult().getCount() + " " + resultDesc), false);
    }
}