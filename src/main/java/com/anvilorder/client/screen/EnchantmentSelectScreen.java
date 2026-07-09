package com.anvilorder.client.screen;

import com.anvilorder.data.EnchantmentData;
import com.anvilorder.solver.EnchantmentSolver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class EnchantmentSelectScreen extends Screen {

    private static final int ITEM_H = 24, HEADER_H = 30, FOOTER_H = 42, SCROLL_W = 6;
    private static final int PW = 240, MARGIN = 10; // MARGIN = min space above/below panel
    private final ItemStack targetItem;
    private final Screen parentScreen;
    private final List<EnchantSlot> slots = new ArrayList<>();
    private int panelX, panelY, panelH;
    private int contentTop, contentBottom, footerTop;
    private ButtonWidget calcBtn, cancelBtn;
    private boolean calculating;
    private double scrollAmount;
    private boolean draggingScroll;

    public EnchantmentSelectScreen(Text title, ItemStack target, Screen parent) {
        super(title);
        this.targetItem = target;
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();
        slots.clear();
        scrollAmount = 0;
        draggingScroll = false;
        String itemName = EnchantmentData.getSimpleItemName(targetItem.getItem());
        Set<String> applicable = new LinkedHashSet<>(EnchantmentData.getEnchantmentsForItem(itemName));

        if (this.client == null || this.client.getNetworkHandler() == null) return;
        Registry<Enchantment> enchantRegistry = this.client.getNetworkHandler().getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT);

        for (var entry : enchantRegistry.getEntrySet()) {
            String p = entry.getKey().getValue().getPath();
            if (!EnchantmentData.getAllEnchantPaths().contains(p)) continue;
            if (entry.getValue().isAcceptableItem(targetItem)) applicable.add(p);
            if (targetItem.isOf(Items.BOOK) || targetItem.isOf(Items.ENCHANTED_BOOK)) applicable.add(p);
        }

        for (String ep : applicable) {
            Optional<RegistryEntry<Enchantment>> e = EnchantmentData.getEnchantEntry(ep);
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

        calcBtn = ButtonWidget.builder(Text.translatable("button.anvilorder.calculate"), b -> runCalc())
                .dimensions(panelX + 20, footerTop + 10, 90, 20).build();
        cancelBtn = ButtonWidget.builder(Text.translatable("gui.cancel"), b -> close())
                .dimensions(panelX + PW - 110, footerTop + 10, 90, 20).build();

        rebuild();
    }

    private void rebuild() {
        clearChildren();
        int visibleH = contentBottom - contentTop;
        int totalH = slots.size() * ITEM_H;
        int maxScroll = Math.max(0, totalH - visibleH);
        scrollAmount = Math.clamp(scrollAmount, 0, maxScroll);

        // Only add widgets for slots whose Y falls within the visible content area
        for (EnchantSlot s : slots) {
            int slotY = contentTop + s.index * ITEM_H - (int) scrollAmount;
            if (slotY + ITEM_H <= contentTop || slotY >= contentBottom) continue;
            addDrawableChild(CheckboxWidget.builder(s.text(), MinecraftClient.getInstance().textRenderer)
                    .checked(s.on).pos(panelX + 4, slotY + 2).maxWidth(180)
                    .callback((c, v) -> { s.on = v; rebuild(); }).build());
            if (s.maxLvl > 1) {
                var sl = new LevSlider(panelX + 190, slotY + 2, 44, 20, s);
                sl.active = s.on;
                addDrawableChild(sl);
            }
        }
        addDrawableChild(calcBtn);
        addDrawableChild(cancelBtn);
    }

    @Override
    public void render(DrawContext g, int mx, int my, float d) {
        int cb = panelY + panelH;
        g.fill(panelX - 2, panelY - 2, panelX + PW + 2, cb + 2, 0xCC000000);
        g.fill(panelX, panelY, panelX + PW, cb, 0xFF222222);
        g.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, this.title, panelX + PW / 2, panelY + 8, 0xFFFFFFFF);
        g.fill(panelX + 4, panelY + HEADER_H - 2, panelX + PW - 4, panelY + HEADER_H - 1, 0xFF555555);
        g.fill(panelX + 4, contentBottom + 1, panelX + PW - 4, contentBottom + 2, 0xFF555555);

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
            int bg = cfl ? 0xAA662222 : (s.on ? 0xFF333333 : 0xFF222222);
            g.fill(panelX + 2, sy, contentRight, sy + ITEM_H, bg);
            if (s.on && s.maxLvl > 1)
                g.drawText(MinecraftClient.getInstance().textRenderer, "Lvl " + s.lvl, contentRight - 42, sy + 6, cfl ? 0xFF6666 : 0xAAAAAA, false);
            if (cfl) g.drawText(MinecraftClient.getInstance().textRenderer, "\u26A0", panelX + PW - 58, sy + 6, 0xFFFF55, false);
        }

        // Scrollbar — flush right inside panel
        if (needScroll) {
            int sbX = panelX + PW - SCROLL_W - 1;
            int sbH = Math.max(20, visibleH * visibleH / Math.max(1, totalH));
            int sbY = contentTop + (maxScroll == 0 ? 0 : (int)(scrollAmount * (visibleH - sbH) / (double) maxScroll));
            g.fill(sbX, contentTop, sbX + SCROLL_W, contentBottom, 0xFF0D0D0D);
            g.fill(sbX, sbY, sbX + SCROLL_W, sbY + sbH, draggingScroll ? 0xFFAAAAAA : 0xFF666666);
        }

        if (calculating) g.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.translatable("text.anvilorder.calculating"),
                panelX + PW / 2, footerTop + 4, 0xFFFF55);

        super.render(g, mx, my, d);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        int totalH = slots.size() * ITEM_H;
        int visH = contentBottom - contentTop;
        if (totalH > visH) {
            scrollAmount = Math.clamp(scrollAmount - vert * 24, 0, totalH - visH);
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, horiz, vert);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int totalH = slots.size() * ITEM_H;
        int visH = contentBottom - contentTop;
        if (totalH <= visH) return super.mouseClicked(mouseX, mouseY, button);

        int sbX = panelX + PW - SCROLL_W - 1;
        if (mouseX < sbX || mouseX > sbX + SCROLL_W
                || mouseY < contentTop || mouseY > contentBottom) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int thumbH = Math.max(20, visH * visH / totalH);
        int trackH = visH;
        int maxScroll = totalH - visH;
        int thumbY = contentTop + (maxScroll == 0 ? 0
                : (int)(scrollAmount / maxScroll * (trackH - thumbH)));

        if (mouseY < thumbY) {
            scrollAmount = Math.max(0, scrollAmount - visH);
        } else if (mouseY > thumbY + thumbH) {
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

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!draggingScroll) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        int totalH = slots.size() * ITEM_H;
        int visH = contentBottom - contentTop;
        int trackH = visH;
        int thumbH = Math.max(20, visH * visH / totalH);
        int maxScroll = totalH - visH;

        // Map mouse Y within track to scroll position
        double ratio = (mouseY - contentTop - thumbH / 2.0) / Math.max(1, trackH - thumbH);
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
            if (this.client != null) {
                this.client.setScreen(new IncompatibleEnchantmentsScreen(
                        Text.translatable("text.anvilorder.incompatible"),
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
        calculating = true;
        calcBtn.active = false;
        boolean book = targetItem.isOf(Items.BOOK) || targetItem.isOf(Items.ENCHANTED_BOOK);
        CompletableFuture.supplyAsync(() -> EnchantmentSolver.solve(book, in, nameMap)).thenAcceptAsync(r -> {
            calculating = false;
            if (r.success) { ResultHolder.setResult(r); close(); } else calcBtn.active = true;
        }, MinecraftClient.getInstance());
    }

    private static int computeNumericId(String path) {
        return path.hashCode();
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parentScreen);
    }

    static class EnchantSlot {
        String path;
        RegistryEntry<Enchantment> entry;
        int maxLvl, weight, index, lvl = 1;
        boolean on;

        EnchantSlot(String p, RegistryEntry<Enchantment> e, int ml, int w) {
            path = p;
            entry = e;
            maxLvl = ml;
            weight = w;
        }

        Text text() {
            MutableText t = entry.value().description().copy();
            if (maxLvl > 1) t.append(" (" + lvl + "/" + maxLvl + ")");
            return t;
        }
    }

    static class LevSlider extends SliderWidget {
        EnchantSlot slot;

        LevSlider(int x, int y, int w, int h, EnchantSlot s) {
            super(x, y, w, h, Text.literal("" + s.lvl), (s.lvl - 1.0) / Math.max(1, s.maxLvl - 1));
            slot = s;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("" + slot.lvl));
        }

        @Override
        protected void applyValue() {
            slot.lvl = (int) Math.round(value * (slot.maxLvl - 1)) + 1;
            updateMessage();
        }
    }
}

/** Simple error popup shown when incompatible enchantments are selected. */
class IncompatibleEnchantmentsScreen extends Screen {
    private final Screen parentScreen;

    protected IncompatibleEnchantmentsScreen(Text title, Screen parent) {
        super(title);
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        int midX = this.width / 2;
        int midY = this.height / 2;
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.ok"),
                b -> close()
        ).dimensions(midX - 40, midY + 10, 80, 20).build());
    }

    @Override
    public void render(DrawContext g, int mx, int my, float d) {
        super.render(g, mx, my, d);
        int midX = this.width / 2;
        int midY = this.height / 2;
        g.fill(midX - 130, midY - 30, midX + 130, midY + 40, 0xDD000000);
        g.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, this.title, midX, midY - 15, 0xFFFF5555);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parentScreen);
    }
}