package com.anvilorder.client.screen;

import com.anvilorder.data.EnchantmentData;
import com.anvilorder.solver.EnchantmentSolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class EnchantmentSelectScreen extends Screen {

    private static final int ITEM_H = 26, HEADER_H = 38, FOOTER_H = 46, SCROLL_W = 6;
    private static final int PW = 270, MARGIN = 10; // MARGIN = min space above/below panel
    private final ItemStack targetItem;
    private final Screen parentScreen;
    private final List<EnchantSlot> slots = new ArrayList<>();
    private final List<AbstractWidget> slotWidgets = new ArrayList<>();
    private int panelX, panelY, panelH;
    private int contentTop, contentBottom, footerTop;
    private Button calcBtn, cancelBtn;
    private boolean calculating;
    private double scrollAmount;
    private boolean draggingScroll;
    private double scrollDragOffset;

    public EnchantmentSelectScreen(Component title, ItemStack target, Screen parent) {
        super(title);
        this.targetItem = target;
        this.parentScreen = parent;
    }

    @Override protected void init() {
        super.init(); slots.clear(); scrollAmount = 0; draggingScroll = false;
        String itemName = EnchantmentData.getSimpleItemName(targetItem.getItem());
        Set<String> applicable = new LinkedHashSet<>(EnchantmentData.getEnchantmentsForItem(itemName));

        if (this.minecraft == null || this.minecraft.getConnection() == null) return;
        Registry<Enchantment> enchantRegistry = this.minecraft.getConnection().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);

        for (var entry : enchantRegistry.entrySet()) {
            String p = entry.getKey().identifier().getPath();
            if (!EnchantmentData.getAllEnchantPaths().contains(p)) continue;
            if (entry.getValue().canEnchant(targetItem)) applicable.add(p);
            if (targetItem.getItem() == Items.BOOK || targetItem.getItem() == Items.ENCHANTED_BOOK) applicable.add(p);
        }

        for (String ep : applicable) {
            Optional<Holder<Enchantment>> e = EnchantmentData.getEnchantEntry(ep);
            if (e.isEmpty()) continue;
            Enchantment ench = e.get().value();
            slots.add(new EnchantSlot(ep, e.get(), ench.getMaxLevel(), EnchantmentData.getWeight(e.get())));
        }
        slots.sort(Comparator.comparing(s -> s.entry.value().description().getString()));
        for (int i = 0; i < slots.size(); i++) slots.get(i).index = i;

        // Compute how many slots fit on screen
        int availH = this.height - 2 * MARGIN; // total available pixel height
        int overhead = HEADER_H + FOOTER_H + 8; // header + footer + padding
        int maxVisible = Math.max(3, (availH - overhead) / ITEM_H); // at least 3
        int vis = Math.min(slots.size(), maxVisible);
        panelH = HEADER_H + vis * ITEM_H + FOOTER_H + 4;
        panelX = (this.width - PW) / 2;
        panelY = (this.height - panelH) / 2;
        contentTop = panelY + HEADER_H + 2;
        contentBottom = contentTop + vis * ITEM_H;
        footerTop = contentBottom + 4;

        calcBtn = Button.builder(Component.translatable("button.anvilorder.calculate"), b -> runCalc())
                .bounds(panelX + 18, footerTop + 13, 104, 20).build();
        cancelBtn = Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(panelX + PW - 122, footerTop + 13, 104, 20).build();

        buildWidgets();
    }

    private void buildWidgets() {
        clearFocus();
        clearWidgets();
        slotWidgets.clear();
        int visibleH = contentBottom - contentTop;
        int totalH = slots.size() * ITEM_H;
        int maxScroll = Math.max(0, totalH - visibleH);
        scrollAmount = Math.clamp(scrollAmount, 0, maxScroll);

        for (EnchantSlot s : slots) {
            int slotY = contentTop + s.index * ITEM_H - (int) scrollAmount;
            s.checkbox = Checkbox.builder(s.entry.value().description(), Minecraft.getInstance().font)
                    .selected(s.on).pos(panelX + 8, slotY + 3).maxWidth(PW - 112)
                    .onValueChange((c, v) -> {
                        s.on = v;
                        if (s.slider != null) s.slider.active = v;
                    }).build();
            addWidget(s.checkbox);
            slotWidgets.add(s.checkbox);

            if (s.maxLvl > 1) {
                s.slider = new LevSlider(panelX + PW - 59, slotY + 3, 46, 20, s);
                s.slider.active = s.on;
                addWidget(s.slider);
                slotWidgets.add(s.slider);
            }
        }
        addRenderableWidget(calcBtn);
        addRenderableWidget(cancelBtn);
        updateSlotPositions();
    }

    private void updateSlotPositions() {
        for (EnchantSlot s : slots) {
            int slotY = contentTop + s.index * ITEM_H - (int) scrollAmount;
            boolean visible = slotY + ITEM_H > contentTop && slotY < contentBottom;

            s.checkbox.setY(slotY + 3);
            s.checkbox.visible = visible;
            if (s.slider != null) {
                s.slider.setY(slotY + 3);
                s.slider.visible = visible;
            }
        }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
        int cb = panelY + panelH;
        int selectedCount = (int) slots.stream().filter(s -> s.on).count();

        // Layered forge-like frame, distinct from the vanilla inventory panels.
        g.fill(panelX - 3, panelY - 3, panelX + PW + 3, cb + 3, 0xCC080A0D);
        g.fill(panelX - 1, panelY - 1, panelX + PW + 1, cb + 1, 0xFF8A6B3D);
        g.fill(panelX, panelY, panelX + PW, cb, 0xFF171A20);
        g.fill(panelX + 2, panelY + 2, panelX + PW - 2, panelY + HEADER_H, 0xFF252A33);
        g.fill(panelX + 3, panelY + HEADER_H - 2, panelX + PW - 3, panelY + HEADER_H, 0xFFB58A4B);
        g.centeredText(Minecraft.getInstance().font, this.title, panelX + PW / 2, panelY + 8, 0xFFFFD98A);
        g.centeredText(Minecraft.getInstance().font, Component.literal(selectedCount + " selected"),
                panelX + PW / 2, panelY + 21, 0xFF9DA7B3);
        g.fill(panelX + 4, contentBottom + 2, panelX + PW - 4, contentBottom + 3, 0xFF4A5360);
        g.fill(panelX + 3, footerTop + 5, panelX + PW - 3, footerTop + 6, 0xFF252A33);

        int visibleH = contentBottom - contentTop;
        int totalH = slots.size() * ITEM_H;
        boolean needScroll = totalH > visibleH;
        int maxScroll = Math.max(0, totalH - visibleH);

        int contentRight = panelX + PW - SCROLL_W - 4;

        // Rows and row widgets are clipped as one viewport. Partially visible
        // rows now slide smoothly behind the header/footer instead of drawing
        // over their separator lines.
        g.enableScissor(panelX + 4, contentTop, contentRight, contentBottom);
        for (EnchantSlot s : slots) {
            int sy = contentTop + s.index * ITEM_H - (int) scrollAmount;
            if (sy + ITEM_H <= contentTop || sy >= contentBottom) continue;

            boolean cfl = s.on && hasSelectedConflict(s);
            int bg = cfl ? 0xFF54282A : (s.on ? 0xFF283A42 : 0xFF1C2027);
            g.fill(panelX + 4, sy, contentRight, sy + ITEM_H - 1, bg);
            if (s.on) g.fill(panelX + 4, sy, panelX + 6, sy + ITEM_H - 1,
                    cfl ? 0xFFFF6B6B : 0xFF69C6A0);
            if (cfl) {
                g.text(Minecraft.getInstance().font, "!", panelX + PW - 69, sy + 8, 0xFFFFD45A);
                g.text(Minecraft.getInstance().font, "Conflict", panelX + PW - 104, sy + 8, 0xFFFF8E8E);
            }

            if (s.maxLvl > 1) {
                int suffixX = s.checkbox.getX() + Checkbox.getBoxSize(this.font) + 4
                        + this.font.width(s.entry.value().description());
                g.text(this.font, Component.literal(" (" + s.lvl + "/" + s.maxLvl + ")"),
                        suffixX, s.checkbox.getY() + 4, 0xFFFFFFFF);
            }
        }

        for (AbstractWidget widget : slotWidgets) {
            if (widget.visible) widget.extractRenderState(g, mx, my, d);
        }
        g.disableScissor();

        // Scrollbar — flush right inside panel
        if (needScroll) {
            int sbX = panelX + PW - SCROLL_W - 1;
            int sbH = Math.max(20, visibleH * visibleH / Math.max(1, totalH));
            int sbY = contentTop + (maxScroll == 0 ? 0 : (int)(scrollAmount * (visibleH - sbH) / (double) maxScroll));
            g.fill(sbX, contentTop, sbX + SCROLL_W, contentBottom, 0xFF0C0E12);
            g.fill(sbX + 1, sbY, sbX + SCROLL_W - 1, sbY + sbH,
                    draggingScroll ? 0xFFFFD98A : 0xFF7C8794);
        }

        if (calculating) g.centeredText(Minecraft.getInstance().font,
                Component.translatable("text.anvilorder.calculating"),
                panelX + PW / 2, footerTop + 4, 0xFFFF55);

        super.extractRenderState(g, mx, my, d);
    }

    private boolean hasSelectedConflict(EnchantSlot slot) {
        for (EnchantSlot other : slots) {
            if (other != slot && other.on && !Enchantment.areCompatible(slot.entry, other.entry)) {
                return true;
            }
        }
        return false;
    }

    @Override public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        int totalH = slots.size() * ITEM_H;
        int visH = contentBottom - contentTop;
        boolean overList = mx >= panelX + 4 && mx < panelX + PW - 4
                && my >= contentTop && my < contentBottom;
        if (totalH > visH && overList) {
            clearFocus();
            scrollAmount = Math.clamp(scrollAmount - vert * ITEM_H, 0, totalH - visH);
            updateSlotPositions();
            return true;
        }
        return super.mouseScrolled(mx, my, horiz, vert);
    }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent evt, boolean dragging) {
        int totalH = slots.size() * ITEM_H;
        int visH = contentBottom - contentTop;
        if (totalH > visH) {
            int sbX = panelX + PW - SCROLL_W - 1;
            if (evt.x() >= sbX && evt.x() <= sbX + SCROLL_W
                    && evt.y() >= contentTop && evt.y() < contentBottom) {
                int thumbH = Math.max(20, visH * visH / totalH);
                int maxScroll = totalH - visH;
                int thumbY = contentTop + (int)(scrollAmount / maxScroll * (visH - thumbH));

                if (evt.y() < thumbY) {
                    scrollAmount = Math.max(0, scrollAmount - visH);
                } else if (evt.y() >= thumbY + thumbH) {
                    scrollAmount = Math.min(maxScroll, scrollAmount + visH);
                } else {
                    draggingScroll = true;
                    scrollDragOffset = evt.y() - thumbY;
                }
                clearFocus();
                updateSlotPositions();
                return true;
            }
        }

        // Hidden row widgets must never receive clicks through the clipped
        // header/footer. Footer buttons are delegated explicitly.
        if (evt.y() < contentTop || evt.y() >= contentBottom) {
            if (calcBtn != null && calcBtn.mouseClicked(evt, dragging)) return true;
            return cancelBtn != null && cancelBtn.mouseClicked(evt, dragging);
        }
        return super.mouseClicked(evt, dragging);
    }

    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent evt) {
        draggingScroll = false;
        return super.mouseReleased(evt);
    }

    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent evt, double dx, double dy) {
        if (!draggingScroll) return super.mouseDragged(evt, dx, dy);
        int totalH = slots.size() * ITEM_H;
        int visH = contentBottom - contentTop;
        int thumbH = Math.max(20, visH * visH / totalH);
        int maxScroll = totalH - visH;

        double ratio = (evt.y() - contentTop - scrollDragOffset) / Math.max(1, visH - thumbH);
        scrollAmount = Math.clamp(ratio * maxScroll, 0, maxScroll);
        updateSlotPositions();
        return true;
    }

    private void runCalc() {
        // Check for incompatible enchantments first
        boolean hasConflict = slots.stream().anyMatch(s -> s.on && hasSelectedConflict(s));
        if (hasConflict) {
            // Show error and return without calculating
            if (this.minecraft != null) {
                this.minecraft.setScreenAndShow(new IncompatibleEnchantmentsScreen(
                        Component.translatable("text.anvilorder.incompatible"),
                        this
                ));
            }
            return;
        }

        List<EnchantmentSolver.EnchantInput> in = new ArrayList<>();
        Map<Integer, String> nameMap = new HashMap<>();
        int numericId = 0;
        for (EnchantSlot s : slots) {
            if (s.on) {
                in.add(new EnchantmentSolver.EnchantInput(numericId, s.lvl, s.weight));
                nameMap.put(numericId, s.path + (s.maxLvl > 1 ? " " + s.lvl : ""));
                numericId++;
            }
        }
        if (in.isEmpty()) return;
        calculating = true; calcBtn.active = false;
        boolean book = targetItem.getItem() == Items.BOOK || targetItem.getItem() == Items.ENCHANTED_BOOK;
        CompletableFuture.supplyAsync(() -> EnchantmentSolver.solve(book, in, nameMap)).thenAcceptAsync(r -> {
            calculating = false;
            if (r.success) { ResultHolder.setResult(r); onClose(); } else calcBtn.active = true;
        }, Minecraft.getInstance());
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parentScreen); }

    static class EnchantSlot {
        String path;
        Holder<Enchantment> entry;
        int maxLvl, weight, index, lvl = 1;
        boolean on;
        Checkbox checkbox;
        LevSlider slider;

        EnchantSlot(String p, Holder<Enchantment> e, int ml, int w) {
            path = p;
            entry = e;
            maxLvl = ml;
            weight = w;
        }
    }

    static class LevSlider extends AbstractSliderButton {
        EnchantSlot slot;

        LevSlider(int x, int y, int w, int h, EnchantSlot s) {
            super(x, y, w, h, Component.literal("" + s.lvl),
                    (s.lvl - 1.0) / Math.max(1, s.maxLvl - 1));
            slot = s;
            updateMessage();
        }

        @Override protected void updateMessage() {
            setMessage(Component.literal("" + slot.lvl));
        }

        @Override protected void applyValue() {
            int newLevel = (int) Math.round(value * (slot.maxLvl - 1)) + 1;
            if (newLevel != slot.lvl) slot.lvl = newLevel;
            updateMessage();
        }
    }
}

/** Simple error popup shown when incompatible enchantments are selected. */
class IncompatibleEnchantmentsScreen extends Screen {
    private final Screen parentScreen;

    protected IncompatibleEnchantmentsScreen(Component title, Screen parent) {
        super(title);
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        int midX = this.width / 2;
        int midY = this.height / 2;
        addRenderableWidget(Button.builder(
                Component.translatable("gui.ok"),
                b -> onClose()
        ).bounds(midX - 50, midY + 22, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
        int midX = this.width / 2;
        int midY = this.height / 2;
        g.fill(midX - 152, midY - 48, midX + 152, midY + 54, 0xE0080A0D);
        g.fill(midX - 150, midY - 46, midX + 150, midY + 52, 0xFF252126);
        g.fill(midX - 148, midY - 44, midX + 148, midY - 41, 0xFFC24B4B);
        g.centeredText(Minecraft.getInstance().font, Component.literal("INCOMPATIBLE ENCHANTMENTS"),
                midX, midY - 29, 0xFFFFD0D0);
        g.centeredText(Minecraft.getInstance().font, this.title, midX, midY - 9, 0xFFFF7777);
        g.centeredText(Minecraft.getInstance().font, Component.literal("Choose a compatible combination to continue."),
                midX, midY + 5, 0xFFB8B8C0);
        super.extractRenderState(g, mx, my, d);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parentScreen);
    }
}
