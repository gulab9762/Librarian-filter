package com.gbdhapa.mixin;

import com.gbdhapa.LibrarianFilter1122;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.GuiMerchant;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.inventory.ContainerMerchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Mixin(GuiMerchant.class)
public abstract class GuiMerchantMixin extends GuiContainer {

    @Shadow @Final private IMerchant merchant;

    @Unique private GuiTextField searchField;
    @Unique private List<String> suggestions = new ArrayList<>();
    @Unique private String selectedSuggestion = "";

    public GuiMerchantMixin() {
        super(null);
    }

    @Inject(method = "initGui", at = @At("TAIL"))
    private void onInitGui(CallbackInfo ci) {
        // Position search field on the left side of the trading GUI
        this.searchField = new GuiTextField(0, this.fontRenderer, this.guiLeft - 125, this.guiTop + 20, 115, 20);
        this.searchField.setMaxStringLength(32);
        this.searchField.setFocused(true);

        // Add Reroll Button
        this.buttonList.add(new GuiButton(99, this.guiLeft - 125, this.guiTop + 45, 115, 20, "Reroll"));
        updateSuggestions();
    }

    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void onDrawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        // Draw search field
        if (this.searchField != null) {
            this.searchField.drawTextBox();
        }

        // Draw background highlight for suggestions
        int x = this.guiLeft - 125;
        int y = this.guiTop + 75;
        drawRect(x - 2, y - 2, x + 117, y + (suggestions.size() * 12) + 2, 0x80000000);

        // Draw suggestion texts
        for (int i = 0; i < suggestions.size(); i++) {
            String suggestion = suggestions.get(i);
            int color = 0xFFCCCCCC;
            // Check hover
            if (mouseX >= x && mouseX <= x + 115 && mouseY >= y + (i * 12) && mouseY < y + (i * 12) + 12) {
                color = 0xFFFFFF00;
            }
            this.fontRenderer.drawStringWithShadow(suggestion, x + 2, y + (i * 12) + 2, color);
        }
    }

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void onKeyTyped(char typedChar, int keyCode, CallbackInfo ci) throws IOException {
        if (this.searchField != null && this.searchField.isFocused()) {
            if (keyCode == 28 || keyCode == 156) { // ENTER
                triggerReroll();
                ci.cancel();
                return;
            }
            if (this.searchField.textboxKeyTyped(typedChar, keyCode)) {
                updateSuggestions();
                ci.cancel();
                return;
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) throws IOException {
        if (this.searchField != null) {
            this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        // Handle clicking suggestions
        int x = this.guiLeft - 125;
        int y = this.guiTop + 75;
        for (int i = 0; i < suggestions.size(); i++) {
            if (mouseX >= x && mouseX <= x + 115 && mouseY >= y + (i * 12) && mouseY < y + (i * 12) + 12) {
                if (this.searchField != null) {
                    this.searchField.setText(suggestions.get(i));
                    updateSuggestions();
                    ci.cancel();
                    return;
                }
            }
        }
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void onActionPerformed(GuiButton button, CallbackInfo ci) {
        if (button.id == 99) {
            triggerReroll();
            ci.cancel();
        }
    }

    @Unique
    private void updateSuggestions() {
        suggestions.clear();
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        if (query.isEmpty()) {
            return;
        }

        int count = 0;
        for (Enchantment ench : Enchantment.REGISTRY) {
            if (ench.getRegistryName() != null) {
                String path = ench.getRegistryName().getPath();
                if (path.toLowerCase().contains(query)) {
                    suggestions.add(path);
                    count++;
                    if (count >= 5) {
                        break;
                    }
                }
            }
        }
    }

    @Unique
    private void triggerReroll() {
        if (this.merchant instanceof EntityVillager) {
            int entityId = ((EntityVillager) this.merchant).getEntityId();
            String filter = this.searchField != null ? this.searchField.getText() : "";
            LibrarianFilter1122.NETWORK.sendToServer(new LibrarianFilter1122.RerollMessage(entityId, filter));
        }
    }
}
