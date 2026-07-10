package com.anvilorder.client.screen;

import com.anvilorder.data.EnchantmentData;
import com.anvilorder.solver.EnchantmentSolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
    private int panelX, panelY, panelH;
    private int contentTop, contentBottom, footerTop;
    private Button calcBtn, cancelBtn;
    private boolean calculating;
    private double scrollAmount;
    private boolean draggingScroll;

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
            slots.add(new EnchantSlot(ep, e.get(), ench.getMaxLevel(), EnchantmentData.getWeight(ep)));
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
        contentBottom = panelY + HEADER_H + vis * ITEM_H;
        footerTop = contentBottom + 4;

        calcBtn = Button.builder(Component.translatable("button.anvilorder.calculate"), b -> runCalc())
                .bounds(panelX + 18, footerTop + 13, 104, 20).build();
        cancelBtn = Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(panelX + PW - 122, footerTop + 13, 104, 20).build();

        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int visibleH = contentBottom - contentTop;
        int totalH = slots.size() * ITEM_H;
        int maxScroll = Math.max(0, totalH - visibleH);
        scrollAmount = Math.clamp(scrollAmount, 0, maxScroll);

        // Only add widgets for slots whose Y falls within the visible content area
        for (EnchantSlot s : slots) {
            int slotY = contentTop + s.index * ITEM_H - (int) scrollAmount;
            if (slotY + ITEM_H <= contentTop || slotY >= contentBottom) continue;
            addRenderableWidget(Checkbox.builder(s.text(), Minecraft.getInstance().font)
                    .selected(s.on).pos(panelX + 8, slotY + 3).maxWidth(PW - 82)
                    .onValueChange((c, v) -> { s.on = v; rebuild(); }).build());
            if (s.maxLvl > 1) {
                var sl = new LevSlider(panelX + PW - 56, slotY + 3, 46, 20, s);
                sl.active = s.on;
                addRenderableWidget(sl);
            }
        }
        addRenderableWidget(calcBtn);
        addRenderableWidget(cancelBtn);
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

        Set<String> sel = new LinkedHashSet<>();
        for (EnchantSlot s : slots) if (s.on) sel.add(s.path);

        int contentRight = panelX + PW - SCROLL_W - 4;

        for (EnchantSlot s : slots) {
            int sy = contentTop + s.index * ITEM_H - (int) scrollAmount;
            if (sy + ITEM_H <= contentTop || sy >= contentBottom) continue;

            boolean cfl = false;
            if (s.on) for (String cp : EnchantmentData.getIncompatible(s.path)) if (sel.contains(cp)) { cfl = true; break; }
            int bg = cfl ? 0xFF54282A : (s.on ? 0xFF283A42 : 0xFF1C2027);
            g.fill(panelX + 4, sy, contentRight, sy + ITEM_H - 1, bg);
            if (s.on) g.fill(panelX + 4, sy, panelX + 6, sy + ITEM_H - 1,
                    cfl ? 0xFFFF6B6B : 0xFF69C6A0);
            if (cfl) {
                g.text(Minecraft.getInstance().font, "!", panelX + PW - 69, sy + 8, 0xFFFFD45A);
                g.text(Minecraft.getInstance().font, "Conflict", panelX + PW - 104, sy + 8, 0xFFFF8E8E);
            }
        }

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

    @Override public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        int totalH = slots.size() * ITEM_H;
        int visH = contentBottom - contentTop;
        if (totalH > visH) {
            scrollAmount = Math.clamp(scrollAmount - vert * 24, 0, totalH - visH);
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, horiz, vert);
    }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent evt, boolean dragging) {
        int totalH = slots.size() * ITEM_H;
        int visH = contentBottom - contentTop;
        if (totalH <= visH) return super.mouseClicked(evt, dragging);

        int sbX = panelX + PW - SCROLL_W - 1;
        if (evt.x() < sbX || evt.x() > sbX + SCROLL_W
                || evt.y() < contentTop || evt.y() > contentBottom) {
            return super.mouseClicked(evt, dragging);
        }

        int thumbH = Math.max(20, visH * visH / totalH);
        int trackH = visH;
        int maxScroll = totalH - visH;
        int thumbY = contentTop + (maxScroll == 0 ? 0
                : (int)(scrollAmount / maxScroll * (trackH - thumbH)));

        if (evt.y() < thumbY) {
            scrollAmount = Math.max(0, scrollAmount - visH);
        } else if (evt.y() > thumbY + thumbH) {
            scrollAmount = Math.min(maxScroll, scrollAmount + visH);
        } else {
            // Click on thumb — start drag, snap thumb center to mouse
            draggingScroll = true;
            double thumbCenter = thumbY + thumbH / 2.0;
            double ratio = (thumbCenter - contentTop - thumbH / 2.0) / Math.max(1, trackH - thumbH);
            scrollAmount = ratio * maxScroll;
        }
        rebuild();
        return true;
    }

    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent evt) {
        draggingScroll = false;
        return super.mouseReleased(evt);
    }

    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent evt, double dx, double dy) {
        if (!draggingScroll) return super.mouseDragged(evt, dx, dy);
        int totalH = slots.size() * ITEM_H;
        int visH = contentBottom - contentTop;
        int trackH = visH;
        int thumbH = Math.max(20, visH * visH / totalH);
        int maxScroll = totalH - visH;

        // Map mouse Y within track to scroll position
        double ratio = (evt.y() - contentTop - thumbH / 2.0) / Math.max(1, trackH - thumbH);
        scrollAmount = Math.clamp(ratio * maxScroll, 0, maxScroll);
        rebuild();
        return true;
    }

    private void runCalc() {
        // Check for incompatible enchantments first
        Set<String> sel = new LinkedHashSet<>();
        for (EnchantSlot s : slots) if (s.on) sel.add(s.path);
        boolean hasConflict = false;
        for (EnchantSlot s : slots) {
            if (!s.on) continue;
            for (String cp : EnchantmentData.getIncompatible(s.path)) {
                if (sel.contains(cp)) { hasConflict = true; break; }
            }
            if (hasConflict) break;
        }
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
        for (EnchantSlot s : slots) {
            if (s.on) {
                int numericId = computeNumericId(s.path);
                in.add(new EnchantmentSolver.EnchantInput(numericId, s.lvl, s.weight));
                nameMap.put(numericId, s.path);
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

    private static int computeNumericId(String path) {
        return path.hashCode();
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parentScreen); }

    static class EnchantSlot { String path; Holder<Enchantment> entry; int maxLvl, weight, index, lvl = 1; boolean on; EnchantSlot(String p, Holder<Enchantment> e, int ml, int w) { path = p; entry = e; maxLvl = ml; weight = w; } Component text() { MutableComponent t = entry.value().description().copy(); if (maxLvl > 1) t.append(" (" + lvl + "/" + maxLvl + ")"); return t; } }

    static class LevSlider extends AbstractSliderButton { EnchantSlot slot; LevSlider(int x, int y, int w, int h, EnchantSlot s) { super(x, y, w, h, Component.literal("" + s.lvl), (s.lvl - 1.0) / Math.max(1, s.maxLvl - 1)); slot = s; updateMessage(); } @Override protected void updateMessage() { setMessage(Component.literal("" + slot.lvl)); } @Override protected void applyValue() { slot.lvl = (int) Math.round(value * (slot.maxLvl - 1)) + 1; updateMessage(); } }
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
        super.extractRenderState(g, mx, my, d);
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
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parentScreen);
    }
}
