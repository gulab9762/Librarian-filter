package com.gbdhapa.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TradeConfigSyncPayload(boolean enableReroll, boolean enableEachLevelReroll, boolean disableTradeRebalance, boolean enableSignSuggestions, boolean allowTreasureEnchantments) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TradeConfigSyncPayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("librarian-filter", "trade_config_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeConfigSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TradeConfigSyncPayload::enableReroll,
            ByteBufCodecs.BOOL, TradeConfigSyncPayload::enableEachLevelReroll,
            ByteBufCodecs.BOOL, TradeConfigSyncPayload::disableTradeRebalance,
            ByteBufCodecs.BOOL, TradeConfigSyncPayload::enableSignSuggestions,
            ByteBufCodecs.BOOL, TradeConfigSyncPayload::allowTreasureEnchantments,
            TradeConfigSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
