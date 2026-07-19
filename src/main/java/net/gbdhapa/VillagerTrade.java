package net.gbdhapa;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.gbdhapa.config.TradeConfig;
import net.gbdhapa.network.ModPackets;
import net.gbdhapa.network.PacketHandlers;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class VillagerTrade implements ModInitializer {
    public static final String MOD_ID = "elt";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final HashMap<UUID, Map<BlockPos, Long>> cooldownMap = new HashMap<>();
    private static final long COOLDOWN_TIME = 1000; // in milliseconds
    private static final int VILLAGER_SEARCH_RADIUS = 128; //in blocks
    private static final int MAX_REROLL_COUNT = 10000;
    private static final int MAX_PROFESSION_LEVEL = 5;
    private static final int durationTicks = 5; //in ticks


    @Override
    public void onInitialize() {
        LOGGER.info("Villager trade reroll mod initialized!");
        ModPackets.register();
        PacketHandlers.register();
        registerEvent();
        CountCommand.register();
        RerollCommand.register();
    }

    private void registerEvent() {
        // Register the event to listen for right-click interactions
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!TradeConfig.INSTANCE.enableReroll) {
                return InteractionResult.PASS;
            }
            BlockPos clickedPos = hitResult.getBlockPos();
            UUID playerUUID = player.getUUID();
            long currentTime = System.currentTimeMillis();
            if (isRerollCooldown(playerUUID, clickedPos, currentTime)) {
                return InteractionResult.PASS;
            }
            Block blockClicked = world.getBlockState(clickedPos).getBlock();
            List<String> signTexts = getSignTexts(world, blockClicked, clickedPos);
            if (signTexts.isEmpty()) {
                return InteractionResult.PASS;
            }
            List<TradeFilter> filters = getEnchFilters(signTexts);
            if (!filters.isEmpty() && world instanceof ServerLevel) {
                Villager villager = getVillagerForWorkstation(player, (ServerLevel) world, clickedPos);
                if (villager != null) {
//                    villager.getGossips().add(playerUUID, GossipType.MAJOR_POSITIVE, 100);
                    FilterResult filterResult = filterTrade(villager, filters);
                    villager.refreshBrain((ServerLevel) world);
                    spawnParticles((ServerLevel) world, filterResult, villager, clickedPos);
                    cooldownMap.put(playerUUID, Map.of(clickedPos, currentTime));
                }
            }
            return InteractionResult.PASS; // Continue normal behavior for other blocks
        });
    }

    private Boolean isRerollCooldown(UUID playerUUID, BlockPos clickedPos, long currentTime) {
        // Check if the player is still on cooldown
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

    private List<TradeFilter> getEnchFilters(List<String> signTexts) {
        List<TradeFilter> filters = new ArrayList<>();
        if (signTexts != null) {
            for (String line : signTexts) {
                if (line != null && !line.isEmpty()) {
                    String[] filterText = line.trim().split(" ");
                    if (filterText.length > 1) {
                        if (StringUtils.isNumeric(filterText[1])) {
                            int enchLevel = Integer.parseInt(filterText[1]);
                            if (enchLevel > 0) {
                                if (filterText.length > 2 && StringUtils.isNumeric(filterText[2])) {
                                    int price = Integer.parseInt(filterText[2]);
                                    filters.add(new TradeFilter(filterText[0], enchLevel, price));
                                } else {
                                    filters.add(new TradeFilter(filterText[0], enchLevel, 0));
                                }
                            }
                        }
                    } else {
                        filters.add(new TradeFilter(filterText[0], 0, 0));
                    }
                }
            }
        }

        return filters;
    }

    private List<String> getSignTexts(Level world, Block blockClicked, BlockPos clickedPos) {

        // Check if the block clicked is a lectern
        if (blockClicked == Blocks.LECTERN) {
            // Get the lectern's facing direction
            Direction facingDirection = world.getBlockState(clickedPos).getValue(LecternBlock.FACING);

            // Get the position in front of the lectern (based on its facing direction)
            BlockPos signPos = clickedPos.relative(facingDirection);
            // Get the BlockEntity (SignBlockEntity) of the WallSign
            if (world.getBlockEntity(signPos) instanceof SignBlockEntity signEntity) {
                // Retrieve the text written on the sign
                return Arrays.stream(signEntity.getFrontText().getMessages(false)).map(Component::getString).toList();
            }

        }

        if (blockClicked != Blocks.AIR) {
            // Get the facing direction
            BlockState blockState = world.getBlockState(clickedPos);
            if (PoiTypes.hasPoi(blockState)) {
                SignBlockEntity signEntity = getAttachedSign(world, clickedPos);
                // Get the position in front of the lectern (based on its facing direction)
                // Get the BlockEntity (SignBlockEntity) of the WallSign
                if (signEntity != null) {
                    // Retrieve the text written on the sign
                    return Arrays.stream(signEntity.getFrontText().getMessages(false)).map(Component::getString).toList();
                }
            }
        }

        return new ArrayList<>();
    }

    private Villager getVillagerForWorkstation(Player player, ServerLevel world, BlockPos clickedPos) {
        AABB box = player.getBoundingBox().inflate(VILLAGER_SEARCH_RADIUS); // use inflate, not expandTowards

        List<Villager> nearbyVillagers = world.getEntitiesOfClass(Villager.class, box, v -> true);
        for (Villager villager : nearbyVillagers) {
            // Check if the villager is a librarianProfession
            if (villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
                Optional<GlobalPos> jobSitePosOptional = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
                // Convert GlobalPos to BlockPos and compare with clicked lectern
                if (jobSitePosOptional.isPresent()) {
                    BlockPos jobSitePos = jobSitePosOptional.get().pos(); // Extract BlockPos from GlobalPos
                    if (jobSitePos.equals(clickedPos)) {
                        TradeConfig.load();
                        if (TradeConfig.INSTANCE.enableEachLevelReroll) {
                            return villager;
                        }
                        if (villager.getVillagerXp() == 0) {
                            return villager;
                        }
                    }
                }
            } else {
                Optional<GlobalPos> jobSitePosOptional = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
                // Convert GlobalPos to BlockPos and compare with clicked block
                if (jobSitePosOptional.isPresent()) {
                    BlockPos jobSitePos = jobSitePosOptional.get().pos(); // Extract BlockPos from GlobalPos
                    if (jobSitePos.equals(clickedPos)) {
                        if (TradeConfig.INSTANCE.enableEachLevelReroll) {
                            return villager;
                        }
                        if (villager.getVillagerXp() == 0) {
                            return villager;
                        }
                    }
                }
            }
        }
        return null;
    }

    private FilterResult filterTrade(Villager villager, List<TradeFilter> filters) {
        if (villager != null) {
            RegistryAccess access = villager.level().registryAccess();
            int recycleCount = 0;
            MerchantOffers originalOffers = villager.getOffers();
            boolean hasTradedLastOffers = checkIfPlayerHasTradedLastOffers(originalOffers);
            if (hasTradedLastOffers) {
                return FilterResult.FAILED;
            }
            while (recycleCount <= MAX_REROLL_COUNT) {
                // --- Reset profession to NONE ---
                VillagerData data = villager.getVillagerData();
                Holder<VillagerProfession> profession = villager.getVillagerData().profession();
                Holder<VillagerProfession> noneProfession = access.getOrThrow(VillagerProfession.NONE);
                villager.setVillagerData(data.withProfession(noneProfession));
                // --- Reassign to same profession ---
                villager.setVillagerData(villager.getVillagerData().withProfession(profession));

                recycleCount++;
//                System.out.println("✅ Retry count: " + recycleCount);
                // --- Check trades ---
                MerchantOffers offers = villager.getOffers();

                if (TradeConfig.INSTANCE.allowTreasureEnchantments && profession.is(VillagerProfession.LIBRARIAN)) {
                    injectTreasureEnchantments(villager, offers);
                }

                for (MerchantOffer trade : offers) {
                    // Only look at enchanted books
                    if (profession.is(VillagerProfession.LIBRARIAN)) {
                        if (trade.getResult().getItem() == Items.ENCHANTED_BOOK) {
                            FilterResult result = filterEnchantmentBook(filters, trade);
                            if (result == FilterResult.SUCCESS) {
//                                System.out.println("✅ Retry count: " + recycleCount);
                                villager.setOffers(new MerchantOffers());
                                originalOffers.removeLast();
                                originalOffers.removeLast();
                                originalOffers.addAll(offers);
                                villager.setOffers(originalOffers);
                                return result;
                            }
                        }
                    } else {
                        FilterResult result = filterTrades(filters, trade);
                        if (result == FilterResult.SUCCESS) {
//                            System.out.println("✅ Retry count: " + recycleCount);
                            villager.setOffers(new MerchantOffers());
                            originalOffers.removeLast();
                            originalOffers.removeLast();
                            originalOffers.addAll(offers);
                            villager.setOffers(originalOffers);
                            return result;
                        }
                    }
                }
            }

            villager.setOffers(new MerchantOffers());
            villager.setOffers(originalOffers);
        }
        return FilterResult.FAILED;
    }

    private static boolean checkIfPlayerHasTradedLastOffers(MerchantOffers originalOffers) {
        int offersSize = originalOffers.size();
        if (offersSize == 0) return false;
        if (offersSize % 2 == 0 && offersSize >= 2) {
            MerchantOffer secondLast = originalOffers.get(offersSize - 2);
            MerchantOffer last = originalOffers.get(offersSize - 1);
            return secondLast.getUses() > 0 || last.getUses() > 0;
        } else {
            MerchantOffer last = originalOffers.get(offersSize - 1);
            return last.getUses() > 0;
        }
    }

    private static void injectTreasureEnchantments(Villager villager, MerchantOffers offers) {
        var registry = villager.level().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        List<net.minecraft.core.Holder.Reference<Enchantment>> allEnchants = registry.listElements().toList();
        if (allEnchants.isEmpty())
            return;

        net.minecraft.util.RandomSource random = villager.getRandom();
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer trade = offers.get(i);
            if (trade.getResult().getItem() == Items.ENCHANTED_BOOK) {
                net.minecraft.core.Holder.Reference<Enchantment> holder = allEnchants.get(random.nextInt(allEnchants.size()));
                Enchantment enchantment = holder.value();

                int minLvl = enchantment.getMinLevel();
                int maxLvl = enchantment.getMaxLevel();
                int l = net.minecraft.util.Mth.nextInt(random, Math.max(minLvl, 1), Math.max(maxLvl, 1));
                ItemStack itemstack = net.minecraft.world.item.enchantment.EnchantmentHelper.createBook(new net.minecraft.world.item.enchantment.EnchantmentInstance(holder, l));

                int cost = 2 + random.nextInt(5 + l * 10) + 3 * l;
                if (holder.is(net.minecraft.tags.EnchantmentTags.DOUBLE_TRADE_PRICE)) {
                    cost *= 2;
                }
                if (cost > 64) {
                    cost = 64;
                }

                offers.set(i, new MerchantOffer(
                        new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, cost),
                        Optional.of(new net.minecraft.world.item.trading.ItemCost(Items.BOOK)),
                        itemstack,
                        12,
                        trade.getXp(),
                        0.2F
                ));
            }
        }
    }

    private FilterResult filterEnchantmentBook(List<TradeFilter> filters, MerchantOffer trade) {
        ItemEnchantments enchantments = trade.getResult().getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> enchHolder = entry.getKey();
            int enchBookLevel = entry.getIntValue();

            // Get the simple name (e.g., "efficiency")
            String enchName = enchHolder.unwrapKey()
                    .map(k -> k.identifier().getPath())
                    .orElse("unknown");

//            System.out.println("Found enchantment: " + enchName + " enchBookLevel " + enchBookLevel + " price " + trade.getCostA().getCount());

            // Compare with filters (partial match, exact enchBookLevel)
            for (TradeFilter filter : filters) {
                int expectedLevel = filter.enchLevel;
                if (enchName.toLowerCase().startsWith(filter.filterName.toLowerCase())) {
                    if (expectedLevel == 0) {
                        Enchantment enchantment = enchHolder.value();
                        expectedLevel = enchantment.getMaxLevel();
                    }
                    if (enchBookLevel == expectedLevel) {
                        if (filter.price > 0) {
                            if (trade.getCostA().getCount() <= filter.price) {
//                                System.out.println("Found matching enchantment: " + enchName + " " + enchBookLevel);
                                return FilterResult.SUCCESS;
                            }
                        } else {
//                            System.out.println("Found matching enchantment: " + enchName + " " + enchBookLevel);
                            return FilterResult.SUCCESS;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static @Nullable FilterResult filterTrades(List<TradeFilter> filters, MerchantOffer trade) {
        String sellItemName = trade.getResult().getItemName().getString().toLowerCase();
//        System.out.println("✅ Found sellItem: " + sellItemName);
        Optional<TradeFilter> filteredTrade;
        filteredTrade = filters.stream().filter(f -> sellItemName.contains(formatFilterName(f))).findFirst();
        if (filteredTrade.isPresent()) {
            return FilterResult.SUCCESS;
        }
        String buyItem1Name = trade.getCostA().getItemName().getString().toLowerCase();
//        System.out.println("✅ Found BuyIem1: " + buyItem1Name);
        filteredTrade = filters.stream().filter(f -> buyItem1Name.contains(formatFilterName(f))).findFirst();
        if (filteredTrade.isPresent()) {
            return FilterResult.SUCCESS;
        }

        ItemStack costB = trade.getCostB();
        if (costB != ItemStack.EMPTY) {
            String buyItem2Name = costB.getItemName().getString().toLowerCase();
//            System.out.println("✅ Found BuyIem2: " + buyItem2Name);
            filteredTrade = filters.stream().filter(f -> buyItem2Name.contains(formatFilterName(f))).findFirst();
            if (filteredTrade.isPresent()) {
                return FilterResult.SUCCESS;
            }
        }

        return null;
    }

    private static @NotNull String formatFilterName(TradeFilter f) {
        return f.filterName.toLowerCase().replaceAll("_", " ");
    }

    public record TradeFilter(String filterName, int enchLevel, int price) {
    }

    private static Map<Integer, Integer> getPriceMap() {
        return Map.of(
                1, 5,
                2, 8,
                3, 11,
                4, 14,
                5, 17);
    }

    enum FilterResult {
        SUCCESS,
        FAILED
    }

    private void spawnParticles(ServerLevel world, FilterResult filterResult, Villager villager, BlockPos clickedPos) {
        if (filterResult == FilterResult.SUCCESS) {
            world.playSound(null, villager,
                    SoundEvents.VILLAGER_YES,
                    SoundSource.NEUTRAL, 1f, 1f);
            for (int i = 0; i < durationTicks; i++) {
                world.sendParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        villager.getX() + 0.5,
                        villager.getY() + 1,
                        villager.getZ() + 0.5,
                        8, 0.3, 0.3, 0.3, 0.01
                );
                world.sendParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        clickedPos.getX() + 0.5,
                        clickedPos.getY() + 1,
                        clickedPos.getZ() + 0.5,
                        8, 0.3, 0.3, 0.3, 0.01
                );
            }
        }
        if (filterResult == FilterResult.FAILED) {
            world.playSound(null, villager,
                    SoundEvents.VILLAGER_NO,
                    SoundSource.NEUTRAL, 1f, 1f);
            for (int i = 0; i < durationTicks; i++) {
                world.sendParticles(
                        ParticleTypes.ANGRY_VILLAGER,
                        villager.getX() + 0.5,
                        villager.getY() + 1,
                        villager.getZ() + 0.5,
                        8, 0.3, 0.3, 0.3, 0.01
                );
                world.sendParticles(
                        ParticleTypes.ANGRY_VILLAGER,
                        clickedPos.getX() + 0.5,
                        clickedPos.getY() + 1,
                        clickedPos.getZ() + 0.5,
                        8, 0.3, 0.3, 0.3, 0.01
                );
            }

        }
    }

    public static void spawnLineParticles(ServerLevel world, BlockPos from, BlockPos to) {
        double x1 = from.getX() + 0.5;
        double y1 = from.getY() + 0.5;
        double z1 = from.getZ() + 0.5;

        double x2 = to.getX() + 0.5;
        double y2 = to.getY() + 0.5;
        double z2 = to.getZ() + 0.5;

        int steps = 20; // number of points along the line

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;

            double x = x1 + (x2 - x1) * t;
            double y = y1 + (y2 - y1) * t;
            double z = z1 + (z2 - z1) * t;

            world.sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    x, y, z,
                    1,   // count
                    0, 0, 0, // no spread
                    0     // no speed
            );
        }
    }


    public static SignBlockEntity getAttachedSign(Level world, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            // check if it is any sign (standing/wall, modded/vanilla)
            if (world.getBlockEntity(side) instanceof SignBlockEntity signEntity) {
                return signEntity;
            }
        }
        return null;
    }


}
