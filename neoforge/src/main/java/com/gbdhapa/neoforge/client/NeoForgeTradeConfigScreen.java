package com.gbdhapa.neoforge.client;

import com.gbdhapa.network.TradeConfigUpdatePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class NeoForgeTradeConfigScreen extends Screen {
    private boolean enableReroll;
    private boolean enableEachLevelReroll;
    private boolean disableTradeRebalance;
    private boolean enableSignSuggestions;
    private boolean allowTreasureEnchantments;

    public NeoForgeTradeConfigScreen(boolean enableReroll, boolean enableEachLevelReroll, boolean disableTradeRebalance, boolean enableSignSuggestions, boolean allowTreasureEnchantments) {
        super(Component.literal("Trade Reroll Configuration"));
        this.enableReroll = enableReroll;
        this.enableEachLevelReroll = enableEachLevelReroll;
        this.disableTradeRebalance = disableTradeRebalance;
        this.enableSignSuggestions = enableSignSuggestions;
        this.allowTreasureEnchantments = allowTreasureEnchantments;
     }

    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 200;
        int buttonHeight = 20;
        int startX = (this.width - buttonWidth) / 2;
        int startY = this.height / 5; // shifted up slightly to make room

        this.addRenderableWidget(Button.builder(Component.literal("Enable Reroll: " + (enableReroll ? "ON" : "OFF")), button -> {
            this.enableReroll = !this.enableReroll;
            button.setMessage(Component.literal("Enable Reroll: " + (enableReroll ? "ON" : "OFF")));
        }).bounds(startX, startY, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.literal("Enable Each Level Reroll: " + (enableEachLevelReroll ? "ON" : "OFF")), button -> {
            this.enableEachLevelReroll = !this.enableEachLevelReroll;
            button.setMessage(Component.literal("Enable Each Level Reroll: " + (enableEachLevelReroll ? "ON" : "OFF")));
        }).bounds(startX, startY + 30, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.literal("Disable Trade Rebalance: " + (disableTradeRebalance ? "ON" : "OFF")), button -> {
            this.disableTradeRebalance = !this.disableTradeRebalance;
            button.setMessage(Component.literal("Disable Trade Rebalance: " + (disableTradeRebalance ? "ON" : "OFF")));
        }).bounds(startX, startY + 60, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.literal("Enable Sign Suggestions: " + (enableSignSuggestions ? "ON" : "OFF")), button -> {
            this.enableSignSuggestions = !this.enableSignSuggestions;
            button.setMessage(Component.literal("Enable Sign Suggestions: " + (enableSignSuggestions ? "ON" : "OFF")));
        }).bounds(startX, startY + 90, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.literal("Allow Treasure Enchants: " + (allowTreasureEnchantments ? "ON" : "OFF")), button -> {
            this.allowTreasureEnchantments = !this.allowTreasureEnchantments;
            button.setMessage(Component.literal("Allow Treasure Enchants: " + (allowTreasureEnchantments ? "ON" : "OFF")));
        }).bounds(startX, startY + 120, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
            ClientPacketDistributor.sendToServer(new TradeConfigUpdatePayload(this.enableReroll, this.enableEachLevelReroll, this.disableTradeRebalance, this.enableSignSuggestions, this.allowTreasureEnchantments));
            this.onClose();
        }).bounds(startX, startY + 160, buttonWidth, buttonHeight).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }
}
