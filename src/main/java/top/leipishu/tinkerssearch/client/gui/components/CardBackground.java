package top.leipishu.tinkerssearch.client.gui.components;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 卡片背景绘制。
 *
 * <p>只画底色与 1px 边框，不涉及内容与交互。六个卡片渲染点共享这段
 * 重复的 fill 逻辑（详见 {@code PanelRenderer} 与 {@code FluidDetailScreen}）。
 */
public final class CardBackground {

    private CardBackground() {}

    /** 底色 + 1px 边框。 */
    public static void draw(GuiGraphics graphics, int x, int y, int w, int h, int bg, int border) {
        if (w <= 0 || h <= 0) return;

        graphics.fill(x, y, x + w, y + h, bg);

        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        graphics.fill(x, y, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);
    }

    /** 底色 + 1px 边框 + 右下角 1px 阴影。 */
    public static void drawWithShadow(GuiGraphics graphics, int x, int y, int w, int h,
                                      int bg, int border, int shadow) {
        if (w <= 0 || h <= 0) return;

        graphics.fill(x + 1, y + h, x + w + 1, y + h + 1, shadow);
        draw(graphics, x, y, w, h, bg, border);
    }
}
