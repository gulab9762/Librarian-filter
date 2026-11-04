package com.gbdhapa;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LibrarianCore {

    public record MatchResult(Holder.Reference<Enchantment> holder, int level) {}

    private static final Pattern LEVEL_PATTERN = Pattern.compile("^(.*?)[\\s\\-_:]+(\\d+)$");

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

    public static final Map<net.minecraft.world.level.block.Block, ResourceKey<VillagerProfession>> WORKSTATION_PROFESSIONS = Map.ofEntries(
            Map.entry(Blocks.LECTERN, VillagerProfession.LIBRARIAN),
            Map.entry(Blocks.COMPOSTER, VillagerProfession.FARMER),
            Map.entry(Blocks.FLETCHING_TABLE, VillagerProfession.FLETCHER),
            Map.entry(Blocks.BLAST_FURNACE, VillagerProfession.ARMORER),
            Map.entry(Blocks.SMOKER, VillagerProfession.BUTCHER),
            Map.entry(Blocks.CARTOGRAPHY_TABLE, VillagerProfession.CARTOGRAPHER),
            Map.entry(Blocks.BREWING_STAND, VillagerProfession.CLERIC),
            Map.entry(Blocks.BARREL, VillagerProfession.FISHERMAN),
            Map.entry(Blocks.CAULDRON, VillagerProfession.LEATHERWORKER),
            Map.entry(Blocks.WATER_CAULDRON, VillagerProfession.LEATHERWORKER),
            Map.entry(Blocks.LAVA_CAULDRON, VillagerProfession.LEATHERWORKER),
            Map.entry(Blocks.POWDER_SNOW_CAULDRON, VillagerProfession.LEATHERWORKER),
            Map.entry(Blocks.STONECUTTER, VillagerProfession.MASON),
            Map.entry(Blocks.LOOM, VillagerProfession.SHEPHERD),
            Map.entry(Blocks.SMITHING_TABLE, VillagerProfession.TOOLSMITH),
            Map.entry(Blocks.GRINDSTONE, VillagerProfession.WEAPONSMITH)
    );

    public static boolean isWorkstation(BlockState state) {
        return state != null && WORKSTATIONS.contains(state.getBlock());
    }

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
        String path = holder.key().identifier().getPath().toLowerCase(Locale.ROOT);
        Enchantment enchantment = holder.value();

        if (level == enchantment.getMaxLevel() && LOWEST_PRICES_FOR_MAX_LEVEL.containsKey(path)) {
            return LOWEST_PRICES_FOR_MAX_LEVEL.get(path);
        }

        boolean isDoublePrice = holder.is(EnchantmentTags.DOUBLE_TRADE_PRICE);
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

        // Auto-assign profession if unemployed
        Holder<VillagerProfession> profHolder = villager.getVillagerData().profession();
        if (profHolder.is(VillagerProfession.NONE)) {
            ResourceKey<VillagerProfession> defaultProfKey = WORKSTATION_PROFESSIONS.get(clickedState.getBlock());
            if (defaultProfKey != null) {
                var opt = serverLevel.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).get(defaultProfKey);
                if (opt.isPresent()) {
                    profHolder = opt.get();
                    villager.setVillagerData(villager.getVillagerData().withProfession(profHolder));
                }
            }
        }

        if (hasTradedForCurrentLevel(villager)) {
            sendFailure(serverLevel, player, villager, workstationPos, "Villager has already been traded with at their current level.");
            return InteractionResult.SUCCESS;
        }

        VillagerData villagerData = villager.getVillagerData();
        int villagerLevel = villagerData.level();

        // 1. If Librarian, try matching an enchantment book first (with guaranteed lowest price)
        if (profHolder.is(VillagerProfession.LIBRARIAN)) {
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

        // 2. Other villager professions or general item trade queries (supporting item tags, potions, stew, etc.)
        List<String> queries = cleanSignQueries(rawLines);
        if (queries.isEmpty()) {
            return InteractionResult.PASS;
        }

        MerchantOffer matchedOffer = findMatchingTradeOffer(serverLevel, villager, queries);
        if (matchedOffer == null) {
            String filterSummary = String.join(", ", queries);
            String profName = villager.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("villager");
            sendFailure(serverLevel, player, villager, workstationPos,
                    "No matching trade found for " + profName + " (level " + villagerLevel + ") for: " + filterSummary);
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

    public static SignBlockEntity findAttachedSign(Level level, BlockPos workstationPos, BlockState workstationState) {
        List<BlockPos> candidates = new ArrayList<>();

        if (workstationState.hasProperty(LecternBlock.FACING)) {
            Direction lecternFacing = workstationState.getValue(LecternBlock.FACING);
            candidates.add(workstationPos.relative(lecternFacing));
            candidates.add(workstationPos.relative(lecternFacing.getOpposite()));
            candidates.add(workstationPos.relative(lecternFacing.getClockWise()));
            candidates.add(workstationPos.relative(lecternFacing.getCounterClockWise()));
        } else {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                candidates.add(workstationPos.relative(dir));
            }
        }

        candidates.add(workstationPos.above());
        candidates.add(workstationPos.below());

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

    public static List<String> getSignLines(SignBlockEntity sign) {
        List<String> lines = new ArrayList<>();
        SignText frontText = sign.getText(true);
        for (int i = 0; i < 4; i++) {
            String line = frontText.getMessage(i, false).getString().trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            SignText backText = sign.getText(false);
            for (int i = 0; i < 4; i++) {
                String line = backText.getMessage(i, false).getString().trim();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        }
        return lines;
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

    public static Villager findAssignedVillager(ServerLevel level, BlockPos workstationPos) {
        AABB box = new AABB(workstationPos).inflate(16);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive());

        for (Villager v : villagers) {
            Optional<GlobalPos> jobSite = v.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            if (jobSite.isPresent()) {
                GlobalPos pos = jobSite.get();
                if (pos.dimension().equals(level.dimension()) && pos.pos().equals(workstationPos)) {
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
            double d = v.distanceToSqr(workstationPos.getX() + 0.5, workstationPos.getY() + 0.5, workstationPos.getZ() + 0.5);
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

    public static MatchResult findMatchingEnchantment(ServerLevel level, List<String> lines) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<Holder.Reference<Enchantment>> allEnchantments = registry.listElements().toList();

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
            Identifier id = holder.key().identifier();
            String path = id.getPath().toLowerCase(Locale.ROOT);
            String fullId = id.toString().toLowerCase(Locale.ROOT);
            String desc = holder.value().description().getString().toLowerCase(Locale.ROOT);

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
                bestMatch = holder;
            }
        }

        return bestMatch;
    }

    public static MerchantOffer findMatchingTradeOffer(ServerLevel serverLevel, Villager villager, List<String> queries) {
        VillagerData villagerData = villager.getVillagerData();
        VillagerProfession profession = villagerData.profession().value();
        int villagerLevel = villagerData.level();

        // 1. First check existing offers on the villager
        MerchantOffers existingOffers = villager.getOffers();
        if (existingOffers != null) {
            for (String query : queries) {
                for (MerchantOffer existing : existingOffers) {
                    if (offerMatchesQuery(existing, query)) {
                        return existing;
                    }
                }
            }
        }

        // 2. Sample possible trade listings for this profession and level from TRADE_SET
        ResourceKey<TradeSet> tradeSetKey = profession.getTrades(villagerLevel);
        if (tradeSetKey != null) {
            var tradeSetRegistry = serverLevel.registryAccess().lookupOrThrow(Registries.TRADE_SET);
            Optional<TradeSet> opt = tradeSetRegistry.getOptional(tradeSetKey);
            if (opt.isPresent()) {
                TradeSet tradeSet = opt.get();
                LootParams lootParams = new LootParams.Builder(serverLevel)
                        .withParameter(LootContextParams.ORIGIN, villager.position())
                        .withParameter(LootContextParams.THIS_ENTITY, villager)
                        .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
                        .create(LootContextParamSets.VILLAGER_TRADE);
                LootContext lootContext = new LootContext.Builder(lootParams)
                        .withOptionalRandomSource(villager.getRandom())
                        .create(tradeSet.randomSequence());

                for (String query : queries) {
                    for (Holder<VillagerTrade> tradeHolder : tradeSet.getTrades()) {
                        VillagerTrade trade = tradeHolder.value();
                        for (int attempt = 0; attempt < 64; attempt++) {
                            MerchantOffer offer = trade.getOffer(lootContext);
                            if (offer != null && offerMatchesQuery(offer, query)) {
                                return offer;
                            }
                        }
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

        // 1. Check Item TagKey (e.g. #arrows, #wool, #planks, or without '#')
        String tagQuery = q.startsWith("#") ? q.substring(1).trim() : q;
        String tagSpaced = tagQuery.replace("_", " ");
        if (stack.typeHolder().tags().anyMatch(tagKey -> {
            String tagPath = tagKey.location().getPath().toLowerCase(Locale.ROOT);
            String fullTag = tagKey.location().toString().toLowerCase(Locale.ROOT);
            return tagPath.equals(tagQuery) || tagPath.startsWith(tagQuery)
                    || tagPath.replace("_", " ").startsWith(tagSpaced)
                    || tagPath.contains(tagQuery)
                    || fullTag.startsWith(tagQuery);
        })) {
            return true;
        }

        // 2. Check item hover name (e.g. "Potato", "Arrow of Weakness", "Bread")
        String hoverName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        if (hoverName.startsWith(q) || hoverName.startsWith(qSpaced) || hoverName.contains(q) || hoverName.contains(qSpaced)) {
            return true;
        }
        for (String word : hoverName.split("\\s+")) {
            if (word.startsWith(q) || word.startsWith(qClean)) {
                return true;
            }
        }

        // 3. Check item registry path (e.g. "potato", "tipped_arrow", "bread")
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId != null) {
            String path = itemId.getPath().toLowerCase(Locale.ROOT);
            String fullId = itemId.toString().toLowerCase(Locale.ROOT);
            if (path.startsWith(q) || path.replace("_", " ").startsWith(qSpaced) || path.contains(q) || fullId.startsWith(q)) {
                return true;
            }
        }

        // 4. Check item translation key description (e.g. "item.minecraft.potato")
        String descName = Component.translatable(stack.getItem().getDescriptionId()).getString().toLowerCase(Locale.ROOT);
        if (descName.startsWith(q) || descName.contains(q)) {
            return true;
        }
        for (String word : descName.split("\\s+")) {
            if (word.startsWith(q) || word.startsWith(qClean)) {
                return true;
            }
        }

        // 5. Check Potion & Mob Effects (crucial for Tipped Arrows, Potions)
        PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
        if (potionContents != null) {
            if (potionContents.potion().isPresent()) {
                Holder<Potion> potionHolder = potionContents.potion().get();
                Identifier potionId = potionHolder.unwrapKey().map(k -> k.identifier()).orElse(null);
                if (potionId != null) {
                    String potionPath = potionId.getPath().toLowerCase(Locale.ROOT);
                    if (potionPath.startsWith(q) || potionPath.contains(q) || potionId.toString().startsWith(q)) {
                        return true;
                    }
                    String stripped = potionPath.replaceFirst("^(long_|strong_)", "");
                    if (stripped.startsWith(q) || stripped.contains(q)) {
                        return true;
                    }
                }
            }

            for (MobEffectInstance effectInstance : potionContents.getAllEffects()) {
                Holder<MobEffect> effectHolder = effectInstance.getEffect();
                Identifier effectId = effectHolder.unwrapKey().map(k -> k.identifier()).orElse(null);
                if (effectId != null) {
                    String effectPath = effectId.getPath().toLowerCase(Locale.ROOT);
                    if (effectPath.startsWith(q) || effectPath.contains(q) || effectPath.replace("_", " ").startsWith(qSpaced)) {
                        return true;
                    }
                }
                String effectName = effectHolder.value().getDisplayName().getString().toLowerCase(Locale.ROOT);
                if (effectName.startsWith(q) || effectName.contains(q)) {
                    return true;
                }
            }
        }

        // 6. Check Suspicious Stew effects
        SuspiciousStewEffects stewEffects = stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
        if (stewEffects != null) {
            for (SuspiciousStewEffects.Entry entry : stewEffects.effects()) {
                Holder<MobEffect> effectHolder = entry.effect();
                Identifier effectId = effectHolder.unwrapKey().map(k -> k.identifier()).orElse(null);
                if (effectId != null) {
                    String effectPath = effectId.getPath().toLowerCase(Locale.ROOT);
                    if (effectPath.startsWith(q) || effectPath.contains(q) || effectPath.replace("_", " ").startsWith(qSpaced)) {
                        return true;
                    }
                }
                String effectName = effectHolder.value().getDisplayName().getString().toLowerCase(Locale.ROOT);
                if (effectName.startsWith(q) || effectName.contains(q)) {
                    return true;
                }
            }
        }

        // 7. Check Enchantments on item (tools, armor, books)
        ItemEnchantments enchants = stack.get(DataComponents.ENCHANTMENTS);
        if (enchants != null) {
            for (Holder<Enchantment> enchHolder : enchants.keySet()) {
                Identifier enchId = enchHolder.unwrapKey().map(k -> k.identifier()).orElse(null);
                if (enchId != null) {
                    String enchPath = enchId.getPath().toLowerCase(Locale.ROOT);
                    if (enchPath.startsWith(q) || enchPath.replace("_", " ").startsWith(qSpaced) || enchPath.contains(q)) {
                        return true;
                    }
                }
                String enchName = enchHolder.value().description().getString().toLowerCase(Locale.ROOT);
                if (enchName.startsWith(q) || enchName.contains(q)) {
                    return true;
                }
            }
        }
        ItemEnchantments storedEnchants = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (storedEnchants != null) {
            for (Holder<Enchantment> enchHolder : storedEnchants.keySet()) {
                Identifier enchId = enchHolder.unwrapKey().map(k -> k.identifier()).orElse(null);
                if (enchId != null) {
                    String enchPath = enchId.getPath().toLowerCase(Locale.ROOT);
                    if (enchPath.startsWith(q) || enchPath.replace("_", " ").startsWith(qSpaced) || enchPath.contains(q)) {
                        return true;
                    }
                }
                String enchName = enchHolder.value().description().getString().toLowerCase(Locale.ROOT);
                if (enchName.startsWith(q) || enchName.contains(q)) {
                    return true;
                }
            }
        }

        // 8. Check CustomData (NBT CompoundTag)
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tagContainsQuery(tag, q)) {
                return true;
            }
        }

        // 9. Check Item Lore
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                String loreText = line.getString().toLowerCase(Locale.ROOT);
                if (loreText.contains(q) || loreText.contains(qSpaced)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean tagContainsQuery(CompoundTag tag, String q) {
        if (tag == null) return false;
        for (String key : tag.keySet()) {
            if (key.toLowerCase(Locale.ROOT).startsWith(q) || key.toLowerCase(Locale.ROOT).contains(q)) {
                return true;
            }
            Tag element = tag.get(key);
            if (element instanceof net.minecraft.nbt.StringTag strTag) {
                String val = strTag.value().toLowerCase(Locale.ROOT);
                if (val.startsWith("minecraft:")) {
                    val = val.substring("minecraft:".length());
                }
                if (val.startsWith(q) || val.contains(q)) {
                    return true;
                }
            } else if (element instanceof CompoundTag compTag) {
                if (tagContainsQuery(compTag, q)) {
                    return true;
                }
            } else if (element instanceof net.minecraft.nbt.ListTag listTag) {
                for (int i = 0; i < listTag.size(); i++) {
                    Tag sub = listTag.get(i);
                    if (sub instanceof CompoundTag compSub) {
                        if (tagContainsQuery(compSub, q)) {
                            return true;
                        }
                    } else if (sub instanceof net.minecraft.nbt.StringTag strSub) {
                        String val = strSub.value().toLowerCase(Locale.ROOT);
                        if (val.startsWith("minecraft:")) {
                            val = val.substring("minecraft:".length());
                        }
                        if (val.startsWith(q) || val.contains(q)) {
                            return true;
                        }
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
        if (!ItemStack.matches(a.getResult(), b.getResult())) return false;
        if (a.getResult().getCount() != b.getResult().getCount()) return false;
        if (!ItemStack.matches(a.getCostA(), b.getCostA())) return false;
        if (a.getCostA().getCount() != b.getCostA().getCount()) return false;
        if (!ItemStack.matches(a.getCostB(), b.getCostB())) return false;
        if (a.getCostB().getCount() != b.getCostB().getCount()) return false;
        return true;
    }

    public static boolean applyEnchantedBookTrade(Villager villager, Holder.Reference<Enchantment> holder, int level) {
        MerchantOffers offers = villager.getOffers();
        if (offers == null) {
            offers = new MerchantOffers();
        }

        ItemStack bookStack = EnchantmentHelper.createBook(new EnchantmentInstance(holder, level));
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

        villager.addEffect(new MobEffectInstance(MobEffects.GLOWING, 10 * 20, 0, false, false));

        String enchName = match.holder().value().description().getString();
        int lowestCost = getLowestPrice(match.holder(), match.level());
        player.sendSystemMessage(Component.literal("§a[Librarian Filter] Updated trade to: " + enchName + " " + match.level() + " (" + lowestCost + " Emeralds - Lowest Price)"));
    }

    private static void sendTradeSuccess(ServerLevel serverLevel, Player player, Villager villager, MerchantOffer offer) {
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.0, villager.getZ(), 15, 0.5, 0.5, 0.5, 0.02);
        serverLevel.playSound(null, villager.getX(), villager.getY() + 1.0, villager.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);

        villager.addEffect(new MobEffectInstance(MobEffects.GLOWING, 10 * 20, 0, false, false));

        String resultDesc = offer.getResult().getHoverName().getString();
        String costADesc = offer.getCostA().getCount() + " " + offer.getCostA().getHoverName().getString();
        String costBDesc = !offer.getCostB().isEmpty() ? " + " + offer.getCostB().getCount() + " " + offer.getCostB().getHoverName().getString() : "";

        player.sendSystemMessage(Component.literal("§a[Villager Filter] Updated trade to: " + costADesc + costBDesc + " -> " + offer.getResult().getCount() + " " + resultDesc));
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
