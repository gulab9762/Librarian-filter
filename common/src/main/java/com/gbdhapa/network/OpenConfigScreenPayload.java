package com.gbdhapa.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenConfigScreenPayload(boolean enableReroll, boolean enableEachLevelReroll, boolean disableTradeRebalance, boolean enableSignSuggestions, boolean allowTreasureEnchantments) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenConfigScreenPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("librarian-filter", "open_config_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenConfigScreenPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, OpenConfigScreenPayload::enableReroll,
            ByteBufCodecs.BOOL, OpenConfigScreenPayload::enableEachLevelReroll,
            ByteBufCodecs.BOOL, OpenConfigScreenPayload::disableTradeRebalance,
            ByteBufCodecs.BOOL, OpenConfigScreenPayload::enableSignSuggestions,
            ByteBufCodecs.BOOL, OpenConfigScreenPayload::allowTreasureEnchantments,
            OpenConfigScreenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
