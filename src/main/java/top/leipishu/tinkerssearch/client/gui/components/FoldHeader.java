package top.leipishu.tinkerssearch.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;

/**
 * 折叠区域的标题栏渲染工具。
 *
 * <p>纯静态方法，状态（是否展开）由调用方维护。样式：
 * 半透明背景 + 底部细线 + 箭头指示（▼ 展开 / ▶ 折叠）。
 */
public final class FoldHeader {

    private FoldHeader() {}

    /**
     * 渲染折叠标题栏。
     *
     * @param title    纯文本，不含色码（内部会加 §f）
     * @param expanded 当前是否展开
     * @param hover    鼠标是否悬停
     */
    public static void render(PoseStack ps, Font font, int x, int y, int w, int h,
                              String title, boolean expanded, boolean hover) {
        int bg = hover ? 0x33FFFFFF : 0x1AFFFFFF;
        GuiComponent.fill(ps, x, y, x + w, y + h, bg);
        GuiComponent.fill(ps, x, y + h - 1, x + w, y + h, 0x44FFFFFF);

        String arrow = expanded ? "\u25bc" : "\u25b6";
        font.draw(ps, "\u00a76" + arrow + " \u00a7f" + title,
                x + 4, y + (h - font.lineHeight) / 2 + 1, 0xFFFFFF);
    }

    /** 命中检测。 */
    public static boolean isHovered(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}