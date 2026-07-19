package com.gbdhapa.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TradeConfigUpdatePayload(boolean enableReroll, boolean enableEachLevelReroll, boolean disableTradeRebalance, boolean enableSignSuggestions, boolean allowTreasureEnchantments) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TradeConfigUpdatePayload> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("librarian-filter", "trade_config_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeConfigUpdatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TradeConfigUpdatePayload::enableReroll,
            ByteBufCodecs.BOOL, TradeConfigUpdatePayload::enableEachLevelReroll,
            ByteBufCodecs.BOOL, TradeConfigUpdatePayload::disableTradeRebalance,
            ByteBufCodecs.BOOL, TradeConfigUpdatePayload::enableSignSuggestions,
            ByteBufCodecs.BOOL, TradeConfigUpdatePayload::allowTreasureEnchantments,
            TradeConfigUpdatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
