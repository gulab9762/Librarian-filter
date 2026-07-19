package net.gbdhapa;

import net.gbdhapa.config.TradeConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TradeConfigScreen extends Screen {

    public TradeConfigScreen() {
        super(Component.literal("Trade Config"));
    }

    @Override
    protected void init() {
        // toggle button
        this.addRenderableWidget(
                Button.builder(Component.literal(getToggleText()), btn -> {
                            TradeConfig.INSTANCE.enableEachLevelReroll =
                                    !TradeConfig.INSTANCE.enableEachLevelReroll;

                            TradeConfig.save();
                            btn.setMessage(Component.literal(getToggleText()));
                        }).pos(this.width / 2 - 75, this.height / 2 - 10)
                        .size(200, 20)
                        .build()
        );

        // toggle button
        this.addRenderableWidget(
                Button.builder(Component.literal(getToggleText1()), btn -> {
                            TradeConfig.INSTANCE.enableReroll =
                                    !TradeConfig.INSTANCE.enableReroll;

                            TradeConfig.save();
                            btn.setMessage(Component.literal(getToggleText1()));
                        }).pos(this.width / 2 - 75, this.height / 2 - 40)
                        .size(200, 20)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal(getToggleText2()), btn -> {
                            TradeConfig.INSTANCE.allowTreasureEnchantments =
                                    !TradeConfig.INSTANCE.allowTreasureEnchantments;

                            TradeConfig.save();
                            btn.setMessage(Component.literal(getToggleText2()));
                        }).pos(this.width / 2 - 75, this.height / 2 + 20)
                        .size(200, 20)
                        .build()
        );
    }

    private String getToggleText2() {
        return "Allow Treasure Enchants: " + (TradeConfig.INSTANCE.allowTreasureEnchantments ? "ON" : "OFF");
    }

    private String getToggleText1() {
        return "Reroll Trades: " + (TradeConfig.INSTANCE.enableReroll ? "ON" : "OFF");
    }

    private String getToggleText() {
        return "Reroll locked villager Trades: " + (TradeConfig.INSTANCE.enableEachLevelReroll ? "ON" : "OFF");
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        // Custom non-blur background (prevents crash)
        gfx.fillGradient(
                0, 0,
                this.width, this.height,
                0xAA000000, 0xAA000000
        );

        super.render(gfx, mouseX, mouseY, delta);
    }
}
