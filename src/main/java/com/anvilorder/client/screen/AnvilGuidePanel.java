package com.anvilorder.client.screen;

import com.anvilorder.solver.CombineStep;
import com.anvilorder.solver.SolverResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Docked side panel next to the anvil screen showing step-by-step
 * enchantment combination instructions with level costs.
 */
public class AnvilGuidePanel {

    public static final int PANEL_WIDTH = 190;
    private static final int PADDING = 6;
    private static final int HEADER_HEIGHT = 26;
    private static final int LINE_HEIGHT = 12;
    private static final int STEP_GAP = 4; // gap between steps
    private static final int SCROLL_BAR_WIDTH = 5;
    private static final int H_SCROLL_RESERVE = 8;

    private final MinecraftClient client;
    private SolverResult result;
    private double scrollY;
    private double scrollX;
    private boolean visible;
    private boolean draggingVertical;
    private boolean draggingHorizontal;

    public int x;
    public int y;
    public int height;
    public int width;

    public AnvilGuidePanel() {
        this.client = MinecraftClient.getInstance();
    }

    public void setResult(SolverResult result) {
        this.result = result;
        this.scrollY = 0;
        this.scrollX = 0;
        this.visible = result != null && result.success;
    }

    public void clear() {
        this.result = null;
        this.visible = false;
    }

    public boolean isVisible() { return visible && result != null; }

    private int totalContentH() {
        if (result == null) return 0;
        // Each step: 2 lines (combine + cost) + gap, then summary line
        return result.steps.size() * (LINE_HEIGHT * 2 + STEP_GAP) + LINE_HEIGHT + 10;
    }

    private int maxTextW(TextRenderer font) {
        int max = 0;
        if (result != null) {
            for (int i = 0; i < result.steps.size(); i++) {
                CombineStep s = result.steps.get(i);
                String line1 = (i + 1) + ". Combine " + s.leftDescription + " with " + s.rightDescription;
                String line2 = "   Cost: " + s.levelCost + " levels (" + s.xpCost + " xp), PWP: " + s.priorWorkPenalty + " levels";
                max = Math.max(max, font.getWidth(line1));
                max = Math.max(max, font.getWidth(line2));
            }
            max = Math.max(max, font.getWidth("Total: " + result.totalLevels + " levels (" + result.totalXp + " XP)"));
        }
        return max + PADDING;
    }

    // --- Layout helpers (no recursion) ---

    private int contentTop()    { return y + HEADER_HEIGHT + 3; }
    private int contentBottom() { return y + height - 4 - H_SCROLL_RESERVE; }
    private int visibleH()      { return contentBottom() - contentTop(); }
    private int maxScrollY()    { return Math.max(0, totalContentH() - visibleH()); }
    private int maxScrollX()    {
        TextRenderer f = client.textRenderer;
        int panelW = Math.max(PANEL_WIDTH, this.width);
        int textAreaW = panelW - PADDING * 2 - SCROLL_BAR_WIDTH - 3;
        return Math.max(0, maxTextW(f) - textAreaW);
    }

    // --- Render ---

    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        if (ResultHolder.showGuidePanel && ResultHolder.pendingResult != null) {
            setResult(ResultHolder.pendingResult);
            ResultHolder.clear();
        }
        if (!isVisible()) return;

        TextRenderer font = client.textRenderer;
        int pw = Math.max(PANEL_WIDTH, this.width);
        int px = x, py = y, ph = height;
        int ct = contentTop(), cb = contentBottom();
        int vh = visibleH();
        int tc = totalContentH();
        boolean needV = tc > vh;
        int my = maxScrollY();
        int mx = maxScrollX();

        scrollY = Math.clamp(scrollY, 0, my);
        scrollX = Math.clamp(scrollX, 0, mx);

        // Background + border
        graphics.fill(px, py, px + pw, py + ph, 0xDD1A1A1A);
        graphics.fill(px, py, px + pw, py + 1, 0xFF444444);
        graphics.fill(px, py + ph - 1, px + pw, py + ph, 0xFF444444);
        graphics.fill(px, py, px + 1, py + ph, 0xFF444444);
        graphics.fill(px + pw - 1, py, px + pw, py + ph, 0xFF444444);

        // Header
        graphics.fill(px + 2, py + 2, px + pw - 2, py + HEADER_HEIGHT, 0xFF2A2A2A);
        Text title = Text.translatable("panel.anvilorder.title").formatted(Formatting.GOLD);
        graphics.drawCenteredTextWithShadow(font, title, px + pw / 2, py + 6, 0xFFFFFFFF);
        graphics.fill(px + 2, py + HEADER_HEIGHT, px + pw - 2, py + HEADER_HEIGHT + 1, 0xFF444444);

        // Text area bounds
        int tl = px + PADDING;
        int tr = px + pw - PADDING - SCROLL_BAR_WIDTH - 3;

        // Scissor
        graphics.enableScissor(px + 2, ct, tr, cb);

        int dy = ct - (int) scrollY;
        int sx = (int) scrollX;

        if (result != null && result.success && !result.steps.isEmpty()) {
            for (int i = 0; i < result.steps.size(); i++) {
                CombineStep s = result.steps.get(i);

                // Line 1: "N. Combine Left with Right"
                String num = (i + 1) + ". ";
                int nw = font.getWidth(num);
                graphics.drawText(font, Text.literal(num).formatted(Formatting.GOLD),
                        tl - sx, dy, 0xFFFFAA00, false);

                String combine = "Combine ";
                graphics.drawText(font, Text.literal(combine).formatted(Formatting.WHITE),
                        tl + nw - sx, dy, 0xFFCCCCCC, false);

                int cx = tl + nw + font.getWidth(combine);
                graphics.drawText(font, Text.literal(s.leftDescription).formatted(Formatting.GREEN),
                        cx - sx, dy, 0xFF55FF55, false);

                cx += font.getWidth(s.leftDescription);
                graphics.drawText(font, Text.literal(" with ").formatted(Formatting.WHITE),
                        cx - sx, dy, 0xFFCCCCCC, false);

                cx += font.getWidth(" with ");
                graphics.drawText(font, Text.literal(s.rightDescription).formatted(Formatting.AQUA),
                        cx - sx, dy, 0xFF55FFFF, false);

                dy += LINE_HEIGHT;

                // Line 2: "   Cost: X levels (Y xp), PWP: Z levels"
                String costLine = "   Cost: " + s.levelCost + " levels (" + s.xpCost + " xp), PWP: " + s.priorWorkPenalty
                        + (s.priorWorkPenalty == 1 ? " level" : " levels");
                graphics.drawText(font, Text.literal(costLine).formatted(Formatting.GRAY),
                        tl - sx, dy, 0xFF999999, false);

                dy += LINE_HEIGHT + STEP_GAP;
            }

            // Summary
            dy += 2;
            String totalS = "Total: " + result.totalLevels + " levels (" + result.totalXp + " XP)";
            graphics.drawText(font, Text.literal(totalS).formatted(Formatting.GOLD),
                    tl - sx, dy, 0xFFFFAA00, false);
        }

        graphics.disableScissor();

        // Vertical scrollbar
        if (needV) {
            int sbX = px + pw - SCROLL_BAR_WIDTH - 3;
            int thumbH = Math.max(20, vh * vh / Math.max(1, tc));
            int thumbY = ct + (my == 0 ? 0 : (int)(scrollY * (vh - thumbH) / (double) my));
            graphics.fill(sbX, ct, sbX + SCROLL_BAR_WIDTH, cb, 0xFF0D0D0D);
            graphics.fill(sbX, thumbY, sbX + SCROLL_BAR_WIDTH, thumbY + thumbH,
                    draggingVertical ? 0xFFAAAAAA : 0xFF666666);
        }

        // Horizontal scrollbar
        if (mx > 0) {
            int hY = cb + 2;
            int hH = H_SCROLL_RESERVE - 2;
            int trW = pw - PADDING * 2 - SCROLL_BAR_WIDTH - 3;
            int thumbW = Math.max(20, trW * trW / Math.max(1, trW + mx));
            int thumbX = tl + (mx == 0 ? 0 : (int)(scrollX * (trW - thumbW) / (double) mx));
            graphics.fill(tl, hY, tr, hY + hH, 0xFF0D0D0D);
            graphics.fill(thumbX, hY, thumbX + thumbW, hY + hH,
                    draggingHorizontal ? 0xFFAAAAAA : 0xFF666666);
        }
    }

    // --- Mouse ---

    public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        if (!isVisible()) return false;
        boolean handled = false;
        int msx = maxScrollX();
        if (msx > 0 && Math.abs(horiz) > 0.01) {
            scrollX = Math.clamp(scrollX + horiz * 15, 0, msx);
            handled = true;
        }
        int msy = maxScrollY();
        if (msy > 0) {
            scrollY = Math.clamp(scrollY - vert * 14, 0, msy);
            handled = true;
        }
        return handled;
    }

    public boolean mouseClicked(double mx, double my, int btn) {
        if (!isVisible() || btn != 0) return false;
        int pw = Math.max(PANEL_WIDTH, this.width);
        int ct = contentTop(), cb = contentBottom();
        int vh = visibleH(), tc = totalContentH();
        int msy = maxScrollY(), msx = maxScrollX();

        // Vertical scrollbar
        if (msy > 0) {
            int sbX = x + pw - SCROLL_BAR_WIDTH - 3;
            int thumbH = Math.max(20, vh * vh / Math.max(1, tc));
            int thumbY = ct + (msy == 0 ? 0 : (int)(scrollY * (vh - thumbH) / (double) msy));
            if (mx >= sbX && mx <= sbX + SCROLL_BAR_WIDTH && my >= ct && my <= cb) {
                if (my < thumbY) scrollY = Math.max(0, scrollY - vh);
                else if (my > thumbY + thumbH) scrollY = Math.min(msy, scrollY + vh);
                else draggingVertical = true;
                return true;
            }
        }

        // Horizontal scrollbar
        if (msx > 0) {
            int hY = cb + 2, hH = H_SCROLL_RESERVE - 2;
            int tl = x + PADDING;
            int trW = pw - PADDING * 2 - SCROLL_BAR_WIDTH - 3;
            int thumbW = Math.max(20, trW * trW / Math.max(1, trW + msx));
            int thumbX = tl + (msx == 0 ? 0 : (int)(scrollX * (trW - thumbW) / (double) msx));
            if (mx >= tl && mx <= x + pw - SCROLL_BAR_WIDTH - 3 && my >= hY && my <= hY + hH) {
                draggingHorizontal = true;
                dragH(mx);
                return true;
            }
        }

        return mx >= x && mx <= x + pw && my >= y && my <= y + height;
    }

    private void dragH(double mouseX) {
        int msx = maxScrollX();
        if (msx <= 0) return;
        int pw = Math.max(PANEL_WIDTH, this.width);
        int tl = x + PADDING, trW = pw - PADDING * 2 - SCROLL_BAR_WIDTH - 3;
        int thumbW = Math.max(20, trW * trW / Math.max(1, trW + msx));
        double ratio = (mouseX - tl - thumbW / 2.0) / Math.max(1, trW - thumbW);
        scrollX = Math.clamp(ratio * msx, 0, msx);
    }

    public boolean mouseReleased(double mx, double my, int btn) {
        draggingVertical = false;
        draggingHorizontal = false;
        int pw = Math.max(PANEL_WIDTH, this.width);
        return mx >= x && mx <= x + pw && my >= y && my <= y + height;
    }

    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (!isVisible()) return false;
        if (draggingVertical) {
            int msy = maxScrollY();
            scrollY = Math.clamp(scrollY - dy, 0, msy);
            return true;
        }
        if (draggingHorizontal) {
            dragH(mx);
            return true;
        }
        return false;
    }
}
