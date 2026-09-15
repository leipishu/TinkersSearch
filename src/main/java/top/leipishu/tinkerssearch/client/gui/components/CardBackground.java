package top.leipishu.tinkerssearch.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;

/**
 * 卡片背景绘制。
 *
 * <p>只画底色与 1px 边框，不涉及内容与交互。六个卡片渲染点共享这段
 * 重复的 fill 逻辑（详见 {@code PanelRenderer} 与 {@code FluidDetailScreen}）。
 */
public final class CardBackground {

    private CardBackground() {}

    /** 底色 + 1px 边框。 */
    public static void draw(PoseStack ps, int x, int y, int w, int h, int bg, int border) {
        if (w <= 0 || h <= 0) return;

        GuiComponent.fill(ps, x, y, x + w, y + h, bg);

        GuiComponent.fill(ps, x, y, x + w, y + 1, border);
        GuiComponent.fill(ps, x, y + h - 1, x + w, y + h, border);
        GuiComponent.fill(ps, x, y, x + 1, y + h, border);
        GuiComponent.fill(ps, x + w - 1, y, x + w, y + h, border);
    }

    /** 底色 + 1px 边框 + 右下角 1px 阴影。 */
    public static void drawWithShadow(PoseStack ps, int x, int y, int w, int h,
                                      int bg, int border, int shadow) {
        if (w <= 0 || h <= 0) return;

        GuiComponent.fill(ps, x + 1, y + h, x + w + 1, y + h + 1, shadow);
        draw(ps, x, y, w, h, bg, border);
    }
}