package com.anvilorder.client.mixin;

import com.anvilorder.client.screen.AnvilGuidePanel;
import com.anvilorder.client.screen.EnchantmentSelectScreen;
import com.anvilorder.client.screen.ResultHolder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to add "Plan enchantment order" button and results panel to the anvil GUI.
 */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin extends ItemCombinerScreen<AnvilMenu> {

    // AnvilScreen in MC 26.2 takes 3 args; ItemCombinerScreen wants 4.
    // Supply the well-known anvil background texture identifier.
    private static final Identifier ANVIL_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/anvil.png");

    protected AnvilScreenMixin(AnvilMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ANVIL_LOCATION);
    }

    @Unique
    private Button planButton;

    @Unique
    private final AnvilGuidePanel guidePanel = new AnvilGuidePanel();

    @Shadow
    private EditBox name;

    /** Position the anvil GUI in the left half and panel in the right half. */
    @Unique
    private void applySplitLayout() {
        if (this.minecraft == null) return;
        int screenW = this.width;
        int halfW = screenW / 2;
        // Center anvil+inventory in the left half
        this.leftPos = (halfW - this.imageWidth) / 2;

        // Panel fills the right half
        guidePanel.x = halfW + 6;
        guidePanel.y = this.topPos;
        guidePanel.height = this.imageHeight;
        guidePanel.width = screenW - halfW - 12;

        // Reposition the name EditBox to match the new leftPos
        if (name != null) {
            name.setPosition(this.leftPos + 62, this.topPos + 24);
        }
    }

    @Inject(method = "subInit", at = @At("TAIL"))
    private void anvilorder$subInit(CallbackInfo ci) {
        applySplitLayout();

        // Place plan button right next to the left anvil input slot (slot 0 at x=27,y=47)
        int buttonX = this.leftPos + 50;
        int buttonY = this.topPos + 47;

        this.planButton = Button.builder(
                Component.literal("\u2692"),
                (btn) -> {
                    // Anvil: target always in left slot (0), sacrifice in right slot (1)
                    ItemStack target = this.menu.getSlot(0).getItem();
                    if (!target.isEmpty() && this.minecraft != null) {
                        // Save the current anvil name before leaving the screen
                        if (name != null) {
                            ResultHolder.savedAnvilName = name.getValue();
                        }
                        this.minecraft.setScreenAndShow(new EnchantmentSelectScreen(
                                Component.translatable("screen.anvilorder.enchant_select"),
                                target.copy(),
                                this
                        ));
                    }
                }
        ).bounds(buttonX, buttonY, 20, 18).build();

        updateButtonState();
        this.addRenderableWidget(this.planButton);
    }

    // AnvilScreen overrides resize() — inject here to reposition on window resize
    @Inject(method = "resize", at = @At("TAIL"))
    private void anvilorder$resize(int width, int height, CallbackInfo ci) {
        applySplitLayout();
    }

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void anvilorder$tick(CallbackInfo ci) {
        updateButtonState();

        // Restore saved anvil name if present
        if (name != null && ResultHolder.savedAnvilName != null) {
            name.setValue(ResultHolder.savedAnvilName);
            ResultHolder.savedAnvilName = null;
        }
    }

    @Unique
    private void syncPanelPosition() {
        // No-op: repositionElements handles all layout now
    }

    @Unique
    private void updateButtonState() {
        if (this.planButton == null) return;
        ItemStack inputStack = this.menu.getSlot(0).getItem();
        boolean enabled = !inputStack.isEmpty() && inputStack.isEnchantable();
        this.planButton.active = enabled;
        if (enabled) {
            this.planButton.setTooltip(Tooltip.create(Component.translatable("button.anvilorder.tooltip")));
        } else {
            this.planButton.setTooltip(Tooltip.create(Component.translatable("button.anvilorder.tooltip_disabled")));
        }
    }

    @Inject(method = "extractBackground", at = @At("HEAD"))
    private void anvilorder$extractBackgroundHead(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // Force split layout every frame BEFORE the anvil renders
        applySplitLayout();
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void anvilorder$extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        guidePanel.render(extractor, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (guidePanel != null && guidePanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean dragging) {
        if (guidePanel != null && guidePanel.isVisible() && guidePanel.mouseClicked(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, dragging);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (guidePanel != null && guidePanel.isVisible() && guidePanel.mouseDragged(event.x(), event.y(), event.button(), dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (guidePanel != null && guidePanel.isVisible() && guidePanel.mouseReleased(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        guidePanel.clear();
        super.onClose();
    }
}
