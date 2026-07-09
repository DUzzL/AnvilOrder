package com.anvilorder.client.mixin;

import com.anvilorder.client.screen.AnvilGuidePanel;
import com.anvilorder.client.screen.EnchantmentSelectScreen;
import com.anvilorder.client.screen.ResultHolder;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.ForgingScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
public abstract class AnvilScreenMixin extends ForgingScreen<AnvilScreenHandler> {

    private static final Identifier ANVIL_LOCATION = Identifier.ofVanilla("textures/gui/container/anvil.png");

    protected AnvilScreenMixin(AnvilScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title, ANVIL_LOCATION);
    }

    @Unique
    private ButtonWidget planButton;

    @Unique
    private final AnvilGuidePanel guidePanel = new AnvilGuidePanel();

    @Shadow
    private TextFieldWidget nameField;

    /** Position the anvil GUI in the left half and panel in the right half. */
    @Unique
    private void applySplitLayout() {
        if (this.client == null) return;
        int screenW = this.width;
        int halfW = screenW / 2;
        // Center anvil+inventory in the left half
        this.x = (halfW - this.backgroundWidth) / 2;

        // Panel fills the right half
        guidePanel.x = halfW + 6;
        guidePanel.y = this.y;
        guidePanel.height = this.backgroundHeight;
        guidePanel.width = screenW - halfW - 12;

        // Reposition the name text field to match the new x
        if (nameField != null) {
            nameField.setPosition(this.x + 62, this.y + 24);
        }
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void anvilorder$setup(CallbackInfo ci) {
        applySplitLayout();

        // Place plan button right next to the left anvil input slot (slot 0 at x=27,y=47)
        int buttonX = this.x + 50;
        int buttonY = this.y + 47;

        this.planButton = ButtonWidget.builder(
                Text.literal("\u2692"),
                (btn) -> {
                    // Anvil: target always in left slot (0), sacrifice in right slot (1)
                    ItemStack target = this.handler.getSlot(0).getStack();
                    if (!target.isEmpty() && this.client != null) {
                        // Save the current anvil name before leaving the screen
                        if (nameField != null) {
                            ResultHolder.savedAnvilName = nameField.getText();
                        }
                        this.client.setScreen(new EnchantmentSelectScreen(
                                Text.translatable("screen.anvilorder.enchant_select"),
                                target.copy(),
                                this
                        ));
                    }
                }
        ).dimensions(buttonX, buttonY, 20, 18).build();

        updateButtonState();
        this.addDrawableChild(this.planButton);
    }

    // AnvilScreen overrides resize() — inject here to reposition on window resize
    @Inject(method = "resize", at = @At("TAIL"))
    private void anvilorder$resize(net.minecraft.client.MinecraftClient client, int width, int height, CallbackInfo ci) {
        applySplitLayout();
    }

    @Unique
    private void updateButtonState() {
        if (this.planButton == null) return;
        ItemStack inputStack = this.handler.getSlot(0).getStack();
        boolean enabled = !inputStack.isEmpty() && inputStack.isEnchantable();
        this.planButton.active = enabled;
        if (enabled) {
            this.planButton.setTooltip(Tooltip.of(Text.translatable("button.anvilorder.tooltip")));
        } else {
            this.planButton.setTooltip(Tooltip.of(Text.translatable("button.anvilorder.tooltip_disabled")));
        }
    }

    @Inject(method = "drawBackground", at = @At("HEAD"))
    private void anvilorder$drawBackgroundHead(DrawContext context, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        // Force split layout every frame BEFORE the anvil renders
        applySplitLayout();
        updateButtonState();

        // Restore saved anvil name if present (handledScreenTick is only on HandledScreen)
        if (nameField != null && ResultHolder.savedAnvilName != null) {
            nameField.setText(ResultHolder.savedAnvilName);
            ResultHolder.savedAnvilName = null;
        }
    }

    @Inject(method = "drawBackground", at = @At("TAIL"))
    private void anvilorder$drawBackground(DrawContext context, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        guidePanel.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (guidePanel != null && guidePanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (guidePanel != null && guidePanel.isVisible() && guidePanel.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (guidePanel != null && guidePanel.isVisible() && guidePanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (guidePanel != null && guidePanel.isVisible() && guidePanel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        guidePanel.clear();
        super.close();
    }
}
