package com.gbdhapa.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;
import com.gbdhapa.EnchantmentInfo;
import com.gbdhapa.EnchantmentDescriptions;

@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin extends Screen {

    @Shadow @Final private String[] messages;
    @Shadow private int line;
    @Shadow private TextFieldHelper signField;
    @Shadow @Final protected net.minecraft.world.level.block.entity.SignBlockEntity sign;

    @Unique private List<EnchantmentInfo> suggestions = new ArrayList<>();
    @Unique private int selectedSuggestionIndex = 0;
    @Unique private boolean suggestionsVisible = false;

    @Unique
    private static final ItemStack ENCHANTED_BOOK_STACK = new ItemStack(Items.ENCHANTED_BOOK);



    @Unique
    private static final List<EnchantmentInfo> ALL_ENCHANTMENTS = List.of(
        new EnchantmentInfo("aqua_affinity", 1),
        new EnchantmentInfo("bane_of_arthropods", 5),
        new EnchantmentInfo("blast_protection", 4),
        new EnchantmentInfo("breach", 4),
        new EnchantmentInfo("channeling", 1),
        new EnchantmentInfo("curse_of_binding", 1),
        new EnchantmentInfo("curse_of_vanishing", 1),
        new EnchantmentInfo("depth_strider", 3),
        new EnchantmentInfo("density", 5),
        new EnchantmentInfo("efficiency", 5),
        new EnchantmentInfo("feather_falling", 4),
        new EnchantmentInfo("fire_aspect", 2),
        new EnchantmentInfo("fire_protection", 4),
        new EnchantmentInfo("flame", 1),
        new EnchantmentInfo("fortune", 3),
        new EnchantmentInfo("frost_walker", 2),
        new EnchantmentInfo("impaling", 5),
        new EnchantmentInfo("infinity", 1),
        new EnchantmentInfo("knockback", 2),
        new EnchantmentInfo("looting", 3),
        new EnchantmentInfo("loyalty", 3),
        new EnchantmentInfo("luck_of_the_sea", 3),
        new EnchantmentInfo("lure", 3),
        new EnchantmentInfo("mending", 1),
        new EnchantmentInfo("multishot", 1),
        new EnchantmentInfo("piercing", 4),
        new EnchantmentInfo("power", 5),
        new EnchantmentInfo("projectile_protection", 4),
        new EnchantmentInfo("protection", 4),
        new EnchantmentInfo("punch", 2),
        new EnchantmentInfo("quick_charge", 3),
        new EnchantmentInfo("respiration", 3),
        new EnchantmentInfo("riptide", 3),
        new EnchantmentInfo("sharpness", 5),
        new EnchantmentInfo("silk_touch", 1),
        new EnchantmentInfo("smite", 5),
        new EnchantmentInfo("sweeping_edge", 3),
        new EnchantmentInfo("thorns", 3),
        new EnchantmentInfo("unbreaking", 3)
    );

    protected AbstractSignEditScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private String getRoman(int level) {
        switch (level) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            case 6: return "VI";
            case 7: return "VII";
            case 8: return "VIII";
            case 9: return "IX";
            case 10: return "X";
            default: return String.valueOf(level);
        }
    }

    @Unique
    private List<EnchantmentInfo> getEnchantments() {
        List<EnchantmentInfo> list = new ArrayList<>();
        try {
            var client = net.minecraft.client.Minecraft.getInstance();
            if (client.level != null) {
                var registry = client.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                for (var key : registry.listElementIds().toList()) {
                    String path = key.location().getPath();
                    if (!com.gbdhapa.config.TradeConfig.INSTANCE.allowTreasureEnchantments) {
                        if (path.equals("soul_speed") || path.equals("swift_sneak") || path.equals("wind_burst")) {
                            continue;
                        }
                    }
                    int maxLevel = 1;
                    var holder = registry.get(key);
                    if (holder.isPresent()) {
                        maxLevel = holder.get().value().getMaxLevel();
                    }
                    list.add(new EnchantmentInfo(path, maxLevel));
                }
            }
        } catch (Exception e) {
            // fallback
        }
        if (list.isEmpty()) {
            if (com.gbdhapa.config.TradeConfig.INSTANCE.allowTreasureEnchantments) {
                List<EnchantmentInfo> fallback = new ArrayList<>(ALL_ENCHANTMENTS);
                fallback.add(new EnchantmentInfo("soul_speed", 3));
                fallback.add(new EnchantmentInfo("swift_sneak", 3));
                fallback.add(new EnchantmentInfo("wind_burst", 3));
                return fallback;
            }
            return ALL_ENCHANTMENTS;
        }
        return list;
    }

    @Unique
    private boolean isLecternAttached() {
        if (this.sign == null) return false;
        var level = this.sign.getLevel();
        if (level == null) {
            level = net.minecraft.client.Minecraft.getInstance().level;
        }
        if (level == null) return false;
        
        net.minecraft.core.BlockPos pos = this.sign.getBlockPos();
        net.minecraft.core.BlockPos[] adjacent = new net.minecraft.core.BlockPos[]{
            pos.north(), pos.south(), pos.east(), pos.west(), pos.above(), pos.below()
        };
        
        for (var adj : adjacent) {
            var state = level.getBlockState(adj);
            if (state.is(net.minecraft.world.level.block.Blocks.LECTERN)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private void updateSuggestions() {
        if (!com.gbdhapa.config.TradeConfig.INSTANCE.enableSignSuggestions || !isLecternAttached()) {
            suggestions.clear();
            suggestionsVisible = false;
            return;
        }

        if (messages == null || line < 0 || line >= messages.length) {
            suggestions.clear();
            suggestionsVisible = false;
            return;
        }
        String currentText = messages[line];
        if (currentText == null || currentText.trim().isEmpty()) {
            suggestions.clear();
            suggestionsVisible = false;
            return;
        }

        String query = currentText.toLowerCase().trim();
        List<EnchantmentInfo> list = getEnchantments();
        List<EnchantmentInfo> matching = new ArrayList<>();
        for (EnchantmentInfo ench : list) {
            if (ench.path().toLowerCase().startsWith(query)) {
                matching.add(ench);
            }
        }

        // Sort: alphabetical
        matching.sort((a, b) -> a.path().compareTo(b.path()));

        suggestions = matching.size() > 5 ? matching.subList(0, 5) : matching;
        if (suggestions.isEmpty()) {
            suggestionsVisible = false;
        } else {
            suggestionsVisible = true;
            if (selectedSuggestionIndex >= suggestions.size()) {
                selectedSuggestionIndex = 0;
            }
        }
    }

    @Unique
    private void applySuggestion(EnchantmentInfo suggestion) {
        if (signField != null) {
            signField.selectAll();
            signField.insertText(suggestion.path());
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (suggestionsVisible && !suggestions.isEmpty()) {
            if (keyCode == 264) { // GLFW_KEY_DOWN
                selectedSuggestionIndex = (selectedSuggestionIndex + 1) % suggestions.size();
                cir.setReturnValue(true);
                cir.cancel();
                return;
            } else if (keyCode == 265) { // GLFW_KEY_UP
                selectedSuggestionIndex = (selectedSuggestionIndex - 1 + suggestions.size()) % suggestions.size();
                cir.setReturnValue(true);
                cir.cancel();
                return;
            } else if (keyCode == 258 || keyCode == 257 || keyCode == 335) { // TAB, ENTER, KP_ENTER
                EnchantmentInfo selected = suggestions.get(selectedSuggestionIndex);
                applySuggestion(selected);
                suggestionsVisible = false;
                suggestions.clear();
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("TAIL"))
    private void postKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        updateSuggestions();
    }

    @Inject(method = "charTyped", at = @At("TAIL"))
    private void postCharTyped(char codePoint, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        updateSuggestions();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (suggestionsVisible && !suggestions.isEmpty() && button == 0) {
            int boxX = 15;
            int boxY = 30;

            int maxNameWidth = 0;
            int maxLevelWidth = 0;
            for (EnchantmentInfo s : suggestions) {
                maxNameWidth = Math.max(maxNameWidth, this.font.width(s.path()));
                maxLevelWidth = Math.max(maxLevelWidth, this.font.width(getRoman(s.maxLevel())));
            }
            int boxWidth = Math.max(110, 22 + maxNameWidth + 12 + maxLevelWidth + 8);

            int currentY = boxY;
            for (int i = 0; i < suggestions.size(); i++) {
                boolean isHovered = (mouseX >= boxX && mouseX <= boxX + boxWidth && mouseY >= currentY - 1 && mouseY < currentY + 17);
                if (isHovered) {
                    applySuggestion(suggestions.get(i));
                    suggestionsVisible = false;
                    suggestions.clear();
                    return true;
                }
                currentY += 18;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (suggestionsVisible && !suggestions.isEmpty()) {
            int boxX = 15;
            int boxY = 30;

            // Calculate responsive box width based on suggestions
            int maxNameWidth = 0;
            int maxLevelWidth = 0;
            for (EnchantmentInfo s : suggestions) {
                maxNameWidth = Math.max(maxNameWidth, this.font.width(s.path()));
                maxLevelWidth = Math.max(maxLevelWidth, this.font.width(getRoman(s.maxLevel())));
            }
            int boxWidth = Math.max(110, 22 + maxNameWidth + 12 + maxLevelWidth + 8);

            int currentY = boxY;
            for (int i = 0; i < suggestions.size(); i++) {
                EnchantmentInfo s = suggestions.get(i);
                boolean isSelected = (i == selectedSuggestionIndex);
                boolean isHovered = (mouseX >= boxX && mouseX <= boxX + boxWidth && mouseY >= currentY - 1 && mouseY < currentY + 17);

                // If selected or hovered, draw background highlight
                if (isSelected || isHovered) {
                    guiGraphics.fill(boxX, currentY - 1, boxX + boxWidth, currentY + 17, 0x80000000);
                }

                // Highlighted/hovered text is bright yellow with shadow, inactive is light gray
                int textColor = (isSelected || isHovered) ? 0xFFFFFF00 : 0xFFCCCCCC;
                guiGraphics.drawString(this.font, s.path(), boxX + 22, currentY + 4, textColor, true);

                // Render the enchanted book icon at the start (left side)
                guiGraphics.renderItem(ENCHANTED_BOOK_STACK, boxX + 2, currentY);

                // Render the max level Roman numeral on the right
                int levelX = boxX + boxWidth - 16;
                String roman = getRoman(s.maxLevel());
                int levelColor = (isSelected || isHovered) ? 0xFFFFFFFF : 0x88FFFFFF;
                guiGraphics.drawString(this.font, roman, levelX, currentY + 4, levelColor, true);

                // If hovered, set tooltip
                if (isHovered) {
                    String desc = EnchantmentDescriptions.get(s.path());
                    if (desc != null) {
                        List<Component> tooltipText = new ArrayList<>();
                        String formattedName = s.path().substring(0, 1).toUpperCase() + s.path().substring(1).replace('_', ' ');
                        tooltipText.add(Component.literal("§e" + formattedName));
                        tooltipText.add(Component.literal("§7" + desc));
                        guiGraphics.renderComponentTooltip(this.font, tooltipText, mouseX, mouseY);
                    }
                }

                currentY += 18;
            }

            // Draw the "Tab/Enter to apply" tip in italicized, translucent white text
            guiGraphics.drawString(this.font, "§oTab/Enter to apply", boxX + 6, currentY + 4, 0x55FFFFFF, true);
        }
    }
}
