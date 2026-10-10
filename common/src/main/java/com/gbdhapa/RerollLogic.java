package com.gbdhapa;

import com.gbdhapa.ai.RerollDiagnosticLogger;
import com.gbdhapa.config.TradeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class RerollLogic {
    private static final HashMap<UUID, Map<BlockPos, Long>> cooldownMap = new HashMap<>();
    private static final long COOLDOWN_TIME = 1000;
    private static final int VILLAGER_SEARCH_RADIUS = 128;
    private static final int MAX_REROLL_COUNT = 10000;
    private static final int durationTicks = 5;

    public record SignInfo(BlockPos pos, List<String> lines) {}

    public record RerollAttemptResult(
            FilterResult result,
            boolean hasAmountFilter,
            Integer lowestAmountFound,
            Integer targetPriceFilter,
            boolean priceFilterFailed
    ) {}

    public static InteractionResult handleBlockUse(Player player, Level world, BlockPos clickedPos) {
        if (!(world instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        String playerName = player.getName().getString();
        BlockState clickedState = world.getBlockState(clickedPos);
        Block blockClicked = clickedState.getBlock();
        String clickedBlockId = blockClicked.getDescriptionId();
        boolean tradeRebalanceActive = serverLevel.enabledFeatures().contains(FeatureFlags.TRADE_REBALANCE);

        String biome = serverLevel.getBiome(clickedPos).unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");

        // 1. Config check
        if (!TradeConfig.INSTANCE.enableReroll) {
            RerollDiagnosticLogger.info("[Reroll] Ignored: 'enableReroll' is currently false in mod config (player: {} at {}).",
                    playerName, clickedPos.toShortString());
            recordSnapshot(playerName, player.blockPosition(), clickedPos, clickedBlockId, biome, null,
                    List.of(), List.of(), 0, false, null, "none", 0, 0, false, 0,
                    tradeRebalanceActive, RerollDiagnosticLogger.Outcome.DISABLED,
                    "Reroll is disabled in mod configuration ('enableReroll' is false)");
            return InteractionResult.PASS;
        }

        // 2. Cooldown check
        UUID playerUUID = player.getUUID();
        long currentTime = System.currentTimeMillis();
        if (isRerollCooldown(playerUUID, clickedPos, currentTime)) {
            RerollDiagnosticLogger.info("[Reroll] Ignored: Player {} clicked workstation at {} within 1000ms cooldown.",
                    playerName, clickedPos.toShortString());
            return InteractionResult.PASS;
        }

        // 3. Convenience: if player right-clicked an attached wall sign directly, redirect to the workstation!
        BlockPos actualWorkstationPos = clickedPos;
        if (clickedState.getBlock() instanceof WallSignBlock) {
            Direction signFacing = clickedState.getValue(WallSignBlock.FACING);
            BlockPos attachedToPos = clickedPos.relative(signFacing.getOpposite());
            BlockState attachedState = world.getBlockState(attachedToPos);
            if (attachedState.is(Blocks.LECTERN) || PoiTypes.hasPoi(attachedState)) {
                RerollDiagnosticLogger.info("[Reroll] Player {} clicked attached sign directly at {}. Redirecting to workstation at {}.",
                        playerName, clickedPos.toShortString(), attachedToPos.toShortString());
                actualWorkstationPos = attachedToPos;
                clickedState = attachedState;
                blockClicked = attachedState.getBlock();
                clickedBlockId = blockClicked.getDescriptionId();
            }
        }

        biome = serverLevel.getBiome(actualWorkstationPos).unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");

        // 4. Verify that clicked block is a valid workstation (Lectern or POI)
        boolean isLectern = (blockClicked == Blocks.LECTERN);
        boolean isPoi = PoiTypes.hasPoi(clickedState);
        if (!isLectern && !isPoi) {
            RerollDiagnosticLogger.info("[Reroll] Clicked block {} at {} is not a recognized workstation (Lectern or POI job block).",
                    clickedBlockId, actualWorkstationPos.toShortString());
            recordSnapshot(playerName, player.blockPosition(), actualWorkstationPos, clickedBlockId, biome, null,
                    List.of(), List.of(), 0, false, null, "none", 0, 0, false, 0,
                    tradeRebalanceActive, RerollDiagnosticLogger.Outcome.NO_WORKSTATION,
                    "Clicked block (" + clickedBlockId + ") is not a villager workstation (Lectern, Composter, etc.)");
            return InteractionResult.PASS;
        }

        RerollDiagnosticLogger.info("[Reroll] Workstation interaction detected: {} at {} (biome: {}) by player {}.",
                clickedBlockId, actualWorkstationPos.toShortString(), biome, playerName);

        // 5. Look for attached sign
        SignInfo signInfo = findAttachedSign(world, blockClicked, actualWorkstationPos);
        if (signInfo == null || signInfo.lines().isEmpty()) {
            RerollDiagnosticLogger.warn("[Reroll] Workstation {} found at {}, but NO sign is attached to it! Players must attach a sign to the workstation and write the trade filter.",
                    clickedBlockId, actualWorkstationPos.toShortString());
            recordSnapshot(playerName, player.blockPosition(), actualWorkstationPos, clickedBlockId, biome, null,
                    List.of(), List.of(), 0, false, null, "none", 0, 0, false, 0,
                    tradeRebalanceActive, RerollDiagnosticLogger.Outcome.NO_SIGN,
                    "No sign attached to workstation. Place a sign directly on or in front of the workstation.");
            return InteractionResult.PASS;
        }

        List<String> rawLines = signInfo.lines();
        RerollDiagnosticLogger.info("[Reroll] Found attached sign at {} with lines: {}",
                signInfo.pos().toShortString(), rawLines);

        // Check if sign lines are completely blank
        boolean allBlank = rawLines.stream().allMatch(String::isBlank);
        if (allBlank) {
            RerollDiagnosticLogger.warn("[Reroll] Attached sign at {} has only empty/blank lines. Please write enchantment or item name on it.",
                    signInfo.pos().toShortString());
            recordSnapshot(playerName, player.blockPosition(), actualWorkstationPos, clickedBlockId, biome, signInfo.pos(),
                    rawLines, List.of(), 0, false, null, "none", 0, 0, false, 0,
                    tradeRebalanceActive, RerollDiagnosticLogger.Outcome.EMPTY_SIGN,
                    "The sign is blank! Write an enchantment name (e.g. 'mending' or 'eff 5') on the sign.");
            return InteractionResult.PASS;
        }

        // 6. Parse trade filters from sign lines
        List<TradeFilter> filters = getEnchFilters(rawLines);
        validateFilters(filters);

        if (filters.isEmpty()) {
            RerollDiagnosticLogger.warn("[Reroll] No valid trade filters could be parsed from sign lines: {}", rawLines);
            recordSnapshot(playerName, player.blockPosition(), actualWorkstationPos, clickedBlockId, biome, signInfo.pos(),
                    rawLines, List.of(), 0, false, null, "none", 0, 0, false, 0,
                    tradeRebalanceActive, RerollDiagnosticLogger.Outcome.INVALID_FILTER,
                    "Could not parse any valid item or enchantment filter from the text on the sign.");
            return InteractionResult.PASS;
        }

        List<String> parsedFilterDescs = filters.stream().map(TradeFilter::toString).toList();
        RerollDiagnosticLogger.info("[Reroll] Parsed filters: {}. allowTreasureEnchantments: {}, tradeRebalance: {}.",
                parsedFilterDescs, TradeConfig.INSTANCE.allowTreasureEnchantments, tradeRebalanceActive);

        // 7. Search for villager linked to this workstation
        AABB searchBox = player.getBoundingBox().inflate(VILLAGER_SEARCH_RADIUS);
        List<Villager> nearbyVillagers = serverLevel.getEntitiesOfClass(Villager.class, searchBox, v -> true);
        RerollDiagnosticLogger.info("[Reroll] Searching for villager claiming workstation at {} among {} nearby villagers in {} block radius...",
                actualWorkstationPos.toShortString(), nearbyVillagers.size(), VILLAGER_SEARCH_RADIUS);

        Villager villager = null;
        Villager lockedVillager = null;

        for (Villager candidate : nearbyVillagers) {
            Optional<GlobalPos> jobSitePosOptional = candidate.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            if (jobSitePosOptional.isPresent()) {
                BlockPos jobSitePos = jobSitePosOptional.get().pos();
                if (jobSitePos.equals(actualWorkstationPos)) {
                    // Check if villager trades are locked (XP > 0)
                    boolean isLocked = candidate.getVillagerXp() > 0 && !TradeConfig.INSTANCE.enableEachLevelReroll;
                    if (isLocked) {
                        lockedVillager = candidate;
                    } else {
                        villager = candidate;
                        break;
                    }
                }
            }
        }

        if (villager == null && lockedVillager != null) {
            int xp = lockedVillager.getVillagerXp();
            int levelNum = lockedVillager.getVillagerData().level();
            String prof = lockedVillager.getVillagerData().profession().getRegisteredName();
            String vType = lockedVillager.getVillagerData().type().unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");
            RerollDiagnosticLogger.warn("[Reroll] Linked villager found at {} (Profession: {}, Type: {}, Level: {}, XP: {}), but TRADES ARE LOCKED because XP > 0! 'enableEachLevelReroll' is currently false.",
                    lockedVillager.blockPosition().toShortString(), prof, vType, levelNum, xp);
            recordSnapshot(playerName, player.blockPosition(), actualWorkstationPos, clickedBlockId, biome, signInfo.pos(),
                    rawLines, parsedFilterDescs, nearbyVillagers.size(), true, prof, vType, xp, levelNum, true,
                    lockedVillager.getOffers().size(), tradeRebalanceActive,
                    RerollDiagnosticLogger.Outcome.VILLAGER_LOCKED,
                    "Villager has already been traded with (XP = " + xp + "). Trades are permanently locked unless 'enableEachLevelReroll' is enabled in /reroll config.");
            return InteractionResult.PASS;
        }

        if (villager == null) {
            RerollDiagnosticLogger.warn("[Reroll] No villager found linked to workstation at {}! (Found {} nearby villagers, but none have job_site memory matching this position).",
                    actualWorkstationPos.toShortString(), nearbyVillagers.size());
            recordSnapshot(playerName, player.blockPosition(), actualWorkstationPos, clickedBlockId, biome, signInfo.pos(),
                    rawLines, parsedFilterDescs, nearbyVillagers.size(), false, "none", "none", 0, 0, false, 0,
                    tradeRebalanceActive, RerollDiagnosticLogger.Outcome.NO_VILLAGER,
                    "No villager has claimed this workstation. Make sure a villager has clear line of sight/pathfinding to the workstation and isn't trapped or sleeping.");
            return InteractionResult.PASS;
        }

        // 8. Villager found and unlocked - perform reroll!
        String profName = villager.getVillagerData().profession().getRegisteredName();
        String villagerType = villager.getVillagerData().type().unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");
        int xp = villager.getVillagerXp();
        int villagerLevel = villager.getVillagerData().level();
        int offersCount = villager.getOffers().size();

        RerollDiagnosticLogger.info("[Reroll] Found eligible villager at {} (Profession: {}, Type: {}, Level: {}, XP: {}, Offers: {}). Starting reroll process...",
                villager.blockPosition().toShortString(), profName, villagerType, villagerLevel, xp, offersCount);

        RerollAttemptResult rerollResult = filterTrade(villager, filters, tradeRebalanceActive);
        RerollDiagnosticLogger.info("[Reroll] Reroll process finished with result: {}", rerollResult.result());

        villager.refreshBrain(serverLevel);
        spawnParticles(serverLevel, rerollResult.result(), villager, actualWorkstationPos);
        cooldownMap.put(playerUUID, Map.of(actualWorkstationPos, currentTime));

        if (rerollResult.result() == FilterResult.SUCCESS) {
            recordSnapshot(playerName, player.blockPosition(), actualWorkstationPos, clickedBlockId, biome, signInfo.pos(),
                    rawLines, parsedFilterDescs, nearbyVillagers.size(), true, profName, villagerType, xp, villagerLevel, false,
                    villager.getOffers().size(), tradeRebalanceActive,
                    RerollDiagnosticLogger.Outcome.SUCCESS,
                    "Reroll succeeded! Found matching trade offer.",
                    rerollResult.lowestAmountFound());
        } else if (rerollResult.priceFilterFailed()) {
            recordSnapshot(playerName, player.blockPosition(), actualWorkstationPos, clickedBlockId, biome, signInfo.pos(),
                    rawLines, parsedFilterDescs, nearbyVillagers.size(), true, profName, villagerType, xp, villagerLevel, false,
                    villager.getOffers().size(), tradeRebalanceActive,
                    RerollDiagnosticLogger.Outcome.FAILED_PRICE_FILTER,
                    "Trade failed due to pricing filter! Matching trade was rolled, but lowest emerald cost found was " + rerollResult.lowestAmountFound() + " (filter requested <= " + rerollResult.targetPriceFilter() + " emeralds).",
                    rerollResult.lowestAmountFound());
        } else {
            recordSnapshot(playerName, player.blockPosition(), actualWorkstationPos, clickedBlockId, biome, signInfo.pos(),
                    rawLines, parsedFilterDescs, nearbyVillagers.size(), true, profName, villagerType, xp, villagerLevel, false,
                    villager.getOffers().size(), tradeRebalanceActive,
                    RerollDiagnosticLogger.Outcome.FAILED_NO_MATCH,
                    "Reroll tested " + MAX_REROLL_COUNT + " trade combinations without matching the requested filters in biome '" + biome + "'. (Trade Rebalance: " + tradeRebalanceActive + ")",
                    null);
        }

        return InteractionResult.PASS;
    }

    private static void recordSnapshot(String player, BlockPos pPos, BlockPos wPos, String blockId, String biome, BlockPos sPos,
                                       List<String> rawLines, List<String> parsedFilters, int nearbyCount,
                                       boolean villagerFound, String prof, String villagerType, int xp, int level, boolean locked,
                                       int offersCount, boolean rebalance, RerollDiagnosticLogger.Outcome outcome, String reason) {
        recordSnapshot(player, pPos, wPos, blockId, biome, sPos, rawLines, parsedFilters, nearbyCount,
                villagerFound, prof, villagerType, xp, level, locked, offersCount, rebalance, outcome, reason, null);
    }

    private static void recordSnapshot(String player, BlockPos pPos, BlockPos wPos, String blockId, String biome, BlockPos sPos,
                                       List<String> rawLines, List<String> parsedFilters, int nearbyCount,
                                       boolean villagerFound, String prof, String villagerType, int xp, int level, boolean locked,
                                       int offersCount, boolean rebalance, RerollDiagnosticLogger.Outcome outcome, String reason,
                                       Integer lowestAmountFound) {
        String timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        RerollDiagnosticLogger.DiagnosticSnapshot snapshot = new RerollDiagnosticLogger.DiagnosticSnapshot(
                timeStr, player, pPos, wPos, blockId, biome, sPos, rawLines, parsedFilters,
                nearbyCount, villagerFound, prof, villagerType, xp, level, locked, offersCount,
                TradeConfig.INSTANCE.allowTreasureEnchantments, rebalance, outcome, reason,
                lowestAmountFound
        );
        RerollDiagnosticLogger.recordSnapshot(snapshot);
    }

    private static Boolean isRerollCooldown(UUID playerUUID, BlockPos clickedPos, long currentTime) {
        if (cooldownMap.containsKey(playerUUID)) {
            Long lastClickTime = cooldownMap.get(playerUUID).get(clickedPos);
            if (lastClickTime != null) {
                long difference = currentTime - lastClickTime;
                if (difference < COOLDOWN_TIME) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int parsePrice(String s) {
        if (s == null) return 0;
        String cleaned = s.replaceAll("[^0-9]", "");
        if (!cleaned.isEmpty()) {
            try {
                return Integer.parseInt(cleaned);
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private static List<TradeFilter> getEnchFilters(List<String> signTexts) {
        List<TradeFilter> filters = new ArrayList<>();
        if (signTexts != null) {
            for (String line : signTexts) {
                if (line != null && !line.trim().isEmpty()) {
                    String[] filterText = line.trim().split("\\s+");
                    if (filterText.length > 2) {
                        int enchLevel = StringUtils.isNumeric(filterText[1]) ? Integer.parseInt(filterText[1]) : 0;
                        int price = parsePrice(filterText[2]);
                        filters.add(new TradeFilter(filterText[0], enchLevel, price));
                    } else if (filterText.length == 2) {
                        String token = filterText[1];
                        if (token.contains("e") || token.contains("<") || token.contains(">")) {
                            int price = parsePrice(token);
                            filters.add(new TradeFilter(filterText[0], 0, price));
                        } else if (StringUtils.isNumeric(token)) {
                            int num = Integer.parseInt(token);
                            if (num > 5) {
                                filters.add(new TradeFilter(filterText[0], 0, num));
                            } else {
                                filters.add(new TradeFilter(filterText[0], num, 0));
                            }
                        } else {
                            filters.add(new TradeFilter(filterText[0], 0, 0));
                        }
                    } else {
                        filters.add(new TradeFilter(filterText[0], 0, 0));
                    }
                }
            }
        }
        return filters;
    }

    private static void validateFilters(List<TradeFilter> filters) {
        for (TradeFilter filter : filters) {
            String name = filter.filterName().toLowerCase();
            boolean exactOrPrefixMatch = false;
            for (String known : EnchantmentDescriptions.DESCRIPTIONS.keySet()) {
                if (known.equals(name) || known.startsWith(name)) {
                    exactOrPrefixMatch = true;
                    break;
                }
            }

            if (!exactOrPrefixMatch) {
                String closest = findClosestEnchantment(name);
                if (closest != null) {
                    RerollDiagnosticLogger.warn("[Reroll] Typo warning: Filter '{}' does not match any vanilla enchantment. Did you mean '{}'?",
                            filter.filterName(), closest);
                } else {
                    RerollDiagnosticLogger.warn("[Reroll] Unrecognized filter '{}'. Does not match any standard enchantment name.",
                            filter.filterName());
                }
            }
        }
    }

    private static String findClosestEnchantment(String input) {
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String candidate : EnchantmentDescriptions.DESCRIPTIONS.keySet()) {
            int dist = levenshteinDistance(input.toLowerCase(), candidate.toLowerCase());
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return (bestDist <= 3) ? best : null;
    }

    private static int levenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) costs[j] = j;
                else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1))
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0) costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }

    public static SignInfo findAttachedSign(Level world, Block blockClicked, BlockPos clickedPos) {
        // 1. If it's a Lectern, check facing direction first
        if (blockClicked == Blocks.LECTERN) {
            Direction facingDirection = world.getBlockState(clickedPos).getValue(LecternBlock.FACING);
            BlockPos frontPos = clickedPos.relative(facingDirection);
            if (world.getBlockEntity(frontPos) instanceof SignBlockEntity signEntity) {
                return new SignInfo(frontPos, getSignMessages(signEntity));
            }
            BlockPos backPos = clickedPos.relative(facingDirection.getOpposite());
            if (world.getBlockEntity(backPos) instanceof SignBlockEntity signEntity) {
                return new SignInfo(backPos, getSignMessages(signEntity));
            }
        }

        // 2. Check all 4 horizontal sides
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = clickedPos.relative(dir);
            if (world.getBlockEntity(side) instanceof SignBlockEntity signEntity) {
                return new SignInfo(side, getSignMessages(signEntity));
            }
        }

        // 3. Check directly above or below (hanging signs or standing signs on top)
        BlockPos above = clickedPos.above();
        if (world.getBlockEntity(above) instanceof SignBlockEntity signEntity) {
            return new SignInfo(above, getSignMessages(signEntity));
        }

        return null;
    }

    private static List<String> getSignMessages(SignBlockEntity signEntity) {
        return Arrays.stream(signEntity.getFrontText().getMessages(false))
                .map(Component::getString)
                .toList();
    }

    public static SignBlockEntity getAttachedSign(Level world, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            if (world.getBlockEntity(side) instanceof SignBlockEntity signEntity)
                return signEntity;
        }
        return null;
    }

    private static RerollAttemptResult filterTrade(Villager villager, List<TradeFilter> filters, boolean tradeRebalanceActive) {
        RegistryAccess access = villager.level().registryAccess();
        int recycleCount = 0;
        MerchantOffers originalOffers = villager.getOffers();

        if (checkIfPlayerHasTradedLastOffers(originalOffers)) {
            RerollDiagnosticLogger.warn("[Reroll] Villager offers have uses > 0 (player already traded with this villager). Trades cannot be rerolled!");
            return new RerollAttemptResult(FilterResult.FAILED, false, null, 0, false);
        }

        boolean hasAmountFilter = filters.stream().anyMatch(f -> f.price > 0);
        int lowestAmountFound = Integer.MAX_VALUE;
        int targetPriceFilter = 0;

        while (recycleCount <= MAX_REROLL_COUNT) {
            VillagerData data = villager.getVillagerData();
            Holder<VillagerProfession> profession = data.profession();
            Holder<VillagerProfession> noneProfession = access.getOrThrow(VillagerProfession.NONE);
            villager.setVillagerData(data.withProfession(noneProfession));
            villager.setVillagerData(villager.getVillagerData().withProfession(profession));

            recycleCount++;
            MerchantOffers offers = villager.getOffers();

            if (TradeConfig.INSTANCE.allowTreasureEnchantments && profession.is(VillagerProfession.LIBRARIAN)) {
                injectTreasureEnchantments(villager, offers);
            }

            for (MerchantOffer trade : offers) {
                if (profession.is(VillagerProfession.LIBRARIAN)) {
                    if (trade.getResult().getItem() == Items.ENCHANTED_BOOK) {
                        TradeFilter matchedFilter = findMatchingEnchantmentFilter(filters, trade);
                        if (matchedFilter != null) {
                            int cost = trade.getCostA().getCount();
                            if (matchedFilter.price > 0) {
                                if (cost < lowestAmountFound) {
                                    lowestAmountFound = cost;
                                    targetPriceFilter = matchedFilter.price;
                                }
                                if (cost <= matchedFilter.price) {
                                    applyNewOffers(villager, originalOffers, offers);
                                    RerollDiagnosticLogger.info("[Reroll] Successfully matched enchanted book trade after {} attempts! Emerald cost: {} (lowest amount found: {})",
                                            recycleCount, cost, lowestAmountFound);
                                    return new RerollAttemptResult(FilterResult.SUCCESS, true, lowestAmountFound, targetPriceFilter, false);
                                }
                            } else {
                                applyNewOffers(villager, originalOffers, offers);
                                if (hasAmountFilter && lowestAmountFound != Integer.MAX_VALUE) {
                                    RerollDiagnosticLogger.info("[Reroll] Successfully matched enchanted book trade after {} attempts! Emerald cost: {} (lowest amount found: {})",
                                            recycleCount, cost, Math.min(lowestAmountFound, cost));
                                } else {
                                    RerollDiagnosticLogger.info("[Reroll] Successfully matched enchanted book trade after {} attempts! Emerald cost: {}",
                                            recycleCount, cost);
                                }
                                return new RerollAttemptResult(FilterResult.SUCCESS, hasAmountFilter, (hasAmountFilter && lowestAmountFound != Integer.MAX_VALUE) ? Math.min(lowestAmountFound, cost) : null, targetPriceFilter, false);
                            }
                        }
                    }
                } else {
                    for (TradeFilter filter : filters) {
                        if (matchesTradeFilter(filter, trade)) {
                            int cost = trade.getCostA().getCount();
                            if (filter.price > 0) {
                                if (cost < lowestAmountFound) {
                                    lowestAmountFound = cost;
                                    targetPriceFilter = filter.price;
                                }
                                if (cost <= filter.price) {
                                    applyNewOffers(villager, originalOffers, offers);
                                    RerollDiagnosticLogger.info("[Reroll] Successfully matched item trade after {} attempts! Emerald cost: {} (lowest amount found: {})",
                                            recycleCount, cost, lowestAmountFound);
                                    return new RerollAttemptResult(FilterResult.SUCCESS, true, lowestAmountFound, targetPriceFilter, false);
                                }
                            } else {
                                applyNewOffers(villager, originalOffers, offers);
                                RerollDiagnosticLogger.info("[Reroll] Successfully matched item trade after {} attempts!", recycleCount);
                                return new RerollAttemptResult(FilterResult.SUCCESS, hasAmountFilter, null, targetPriceFilter, false);
                            }
                        }
                    }
                }
            }
        }

        villager.setOffers(new MerchantOffers());
        villager.setOffers(originalOffers);

        if (hasAmountFilter) {
            if (lowestAmountFound != Integer.MAX_VALUE) {
                RerollDiagnosticLogger.warn("[Reroll] Trade failed due to pricing filter! Reached maximum cycle count ({}) without finding emerald cost <= {}. Lowest amount found: {} emeralds.",
                        MAX_REROLL_COUNT, targetPriceFilter, lowestAmountFound);
                return new RerollAttemptResult(FilterResult.FAILED, true, lowestAmountFound, targetPriceFilter, true);
            } else {
                RerollDiagnosticLogger.warn("[Reroll] Reached maximum cycle count ({}) without finding a matching trade (price filter was used, but matching trade never rolled).",
                        MAX_REROLL_COUNT);
                return new RerollAttemptResult(FilterResult.FAILED, true, null, targetPriceFilter, false);
            }
        } else {
            RerollDiagnosticLogger.warn("[Reroll] Reached maximum cycle count ({}) without finding a matching trade. (TradeRebalance active: {}, allowTreasureEnchantments: {})",
                    MAX_REROLL_COUNT, tradeRebalanceActive, TradeConfig.INSTANCE.allowTreasureEnchantments);
            return new RerollAttemptResult(FilterResult.FAILED, false, null, 0, false);
        }
    }

    private static void applyNewOffers(Villager villager, MerchantOffers originalOffers, MerchantOffers newOffers) {
        villager.setOffers(new MerchantOffers());
        originalOffers.removeLast();
        originalOffers.removeLast();
        originalOffers.addAll(newOffers);
        villager.setOffers(originalOffers);
    }

    private static boolean checkIfPlayerHasTradedLastOffers(MerchantOffers originalOffers) {
        if (originalOffers == null || originalOffers.isEmpty())
            return false;
        int offersSize = originalOffers.size();
        if (offersSize % 2 == 0 && offersSize >= 2) {
            return originalOffers.get(offersSize - 2).getUses() > 0 || originalOffers.get(offersSize - 1).getUses() > 0;
        }
        return originalOffers.get(offersSize - 1).getUses() > 0;
    }

    private static void injectTreasureEnchantments(Villager villager, MerchantOffers offers) {
        var registry = villager.level().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        List<Holder.Reference<Enchantment>> allEnchants = registry.listElements().toList();
        if (allEnchants.isEmpty())
            return;

        net.minecraft.util.RandomSource random = villager.getRandom();
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer trade = offers.get(i);
            if (trade.getResult().getItem() == Items.ENCHANTED_BOOK) {
                Holder.Reference<Enchantment> holder = allEnchants.get(random.nextInt(allEnchants.size()));
                Enchantment enchantment = holder.value();

                int minLvl = enchantment.getMinLevel();
                int maxLvl = enchantment.getMaxLevel();
                int l = net.minecraft.util.Mth.nextInt(random, Math.max(minLvl, 1), Math.max(maxLvl, 1));
                ItemStack itemstack = EnchantmentHelper.createBook(new EnchantmentInstance(holder, l));

                int cost = 2 + random.nextInt(5 + l * 10) + 3 * l;
                if (holder.is(net.minecraft.tags.EnchantmentTags.DOUBLE_TRADE_PRICE)) {
                    cost *= 2;
                }
                if (cost > 64)
                    cost = 64;

                MerchantOffer newOffer = new MerchantOffer(
                        new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, cost),
                        Optional.of(new net.minecraft.world.item.trading.ItemCost(Items.BOOK)),
                        itemstack,
                        trade.getUses(),
                        trade.getMaxUses(),
                        trade.getXp(),
                        trade.getPriceMultiplier(),
                        trade.getDemand());
                offers.set(i, newOffer);
            }
        }
    }

    private static TradeFilter findMatchingEnchantmentFilter(List<TradeFilter> filters, MerchantOffer trade) {
        ItemEnchantments enchantments = trade.getResult().getOrDefault(DataComponents.STORED_ENCHANTMENTS,
                ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> enchHolder = entry.getKey();
            int enchBookLevel = entry.getIntValue();
            String enchName = enchHolder.unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");

            for (TradeFilter filter : filters) {
                int expectedLevel = filter.enchLevel == 0 ? enchHolder.value().getMaxLevel() : filter.enchLevel;
                if (enchName.toLowerCase().startsWith(filter.filterName.toLowerCase())) {
                    if (enchBookLevel == expectedLevel) {
                        return filter;
                    }
                }
            }
        }
        return null;
    }

    private static boolean matchesTradeFilter(TradeFilter f, MerchantOffer trade) {
        String formatted = formatFilterName(f);
        if (trade.getResult().getItemName().getString().toLowerCase().contains(formatted)) return true;
        if (trade.getCostA().getItemName().getString().toLowerCase().contains(formatted)) return true;
        ItemStack costB = trade.getCostB();
        if (costB != ItemStack.EMPTY && costB.getItemName().getString().toLowerCase().contains(formatted)) return true;
        return false;
    }

    private static FilterResult filterEnchantmentBook(List<TradeFilter> filters, MerchantOffer trade) {
        TradeFilter filter = findMatchingEnchantmentFilter(filters, trade);
        if (filter != null) {
            if (filter.price == 0 || trade.getCostA().getCount() <= filter.price) {
                return FilterResult.SUCCESS;
            }
        }
        return null;
    }

    private static FilterResult filterTrades(List<TradeFilter> filters, MerchantOffer trade) {
        for (TradeFilter filter : filters) {
            if (matchesTradeFilter(filter, trade)) {
                if (filter.price == 0 || trade.getCostA().getCount() <= filter.price) {
                    return FilterResult.SUCCESS;
                }
            }
        }
        return null;
    }

    private static String formatFilterName(TradeFilter f) {
        return f.filterName.toLowerCase().replaceAll("_", " ");
    }

    private static void spawnParticles(ServerLevel world, FilterResult filterResult, Villager villager,
            BlockPos clickedPos) {
        if (filterResult == FilterResult.SUCCESS) {
            RerollDiagnosticLogger.info("[Reroll] Spawning green HAPPY_VILLAGER particles for successful trade match.");
            world.playSound(null, villager, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
            for (int i = 0; i < durationTicks; i++) {
                world.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX() + 0.5, villager.getY() + 1,
                        villager.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.01);
                world.sendParticles(ParticleTypes.HAPPY_VILLAGER, clickedPos.getX() + 0.5, clickedPos.getY() + 1,
                        clickedPos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.01);
            }
        } else if (filterResult == FilterResult.FAILED) {
            RerollDiagnosticLogger.info("[Reroll] Spawning angry ANGRY_VILLAGER particles for failed trade reroll.");
            world.playSound(null, villager, SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, 1f);
            for (int i = 0; i < durationTicks; i++) {
                world.sendParticles(ParticleTypes.ANGRY_VILLAGER, villager.getX() + 0.5, villager.getY() + 1,
                        villager.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.01);
                world.sendParticles(ParticleTypes.ANGRY_VILLAGER, clickedPos.getX() + 0.5, clickedPos.getY() + 1,
                        clickedPos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.01);
            }
        }
    }

    public static int executeFind(CommandSourceStack source, String query)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();

        double radius = 128.0;
        AABB box = player.getBoundingBox().inflate(radius);
        List<Villager> villagers = level.getEntitiesOfClass(
                Villager.class,
                box,
                v -> v.isAlive());

        for (Villager villager : villagers) {
            villager.removeEffect(MobEffects.GLOWING);
        }

        int matchCount = 0;
        for (Villager villager : villagers) {
            MerchantOffers offers = villager.getOffers();
            boolean matchFound = false;
            for (MerchantOffer trade : offers) {
                if (trade.getResult().getItem() == Items.ENCHANTED_BOOK) {
                    ItemEnchantments enchantments = trade.getResult().getOrDefault(
                            DataComponents.STORED_ENCHANTMENTS,
                            ItemEnchantments.EMPTY);
                    for (var entry : enchantments.entrySet()) {
                        Holder<Enchantment> enchHolder = entry.getKey();
                        String enchName = enchHolder.unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");
                        if (enchName.toLowerCase().contains(query.toLowerCase())) {
                            matchFound = true;
                            break;
                        }
                    }
                }
                if (matchFound) {
                    break;
                }
            }

            if (matchFound) {
                villager.addEffect(new MobEffectInstance(
                        MobEffects.GLOWING,
                        600,
                        0,
                        false,
                        false));
                matchCount++;
            }
        }

        final int count = matchCount;
        if (count > 0) {
            source.sendSuccess(() -> Component.literal(
                    "§aFound " + count + " villager(s) offering '" + query + "'. They are now glowing for 30 seconds!"),
                    true);
        } else {
            source.sendFailure(Component.literal("No loaded villagers found offering '" + query + "'."));
        }

        return count;
    }

    public static int executeSetup(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
        if (!player.isCreative()) {
            source.sendFailure(Component.literal("This command can only be used in Creative mode."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        BlockPos playerPos = player.blockPosition();
        Direction facing = player.getDirection();
        BlockPos center = playerPos.relative(facing, 3);

        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 3; y++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (y == -1) {
                        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                    } else if (x == -2 || x == 2 || z == -2 || z == 2 || y == 3) {
                        level.setBlock(pos, Blocks.GLASS.defaultBlockState(), 3);
                    } else {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }

        Villager villager = new Villager(net.minecraft.world.entity.EntityTypes.VILLAGER, level);
        villager.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        level.addFreshEntity(villager);

        BlockPos lecternPos = center.relative(facing.getOpposite(), 1);
        level.setBlock(lecternPos, Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, facing), 3);

        BlockPos signPos = lecternPos.relative(facing.getOpposite());
        level.setBlock(signPos,
                Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(net.minecraft.world.level.block.WallSignBlock.FACING,
                        facing.getOpposite()), 3);

        source.sendSuccess(() -> Component.literal("§aSetup complete! A small room with a villager and lectern has been generated."), true);
        return 1;
    }

    public record TradeFilter(String filterName, int enchLevel, int price) {
        @Override
        public String toString() {
            return filterName + (enchLevel > 0 ? " L" + enchLevel : " (max lvl)") + (price > 0 ? " <=" + price + "e" : "");
        }
    }

    enum FilterResult {
        SUCCESS, FAILED
    }
}
