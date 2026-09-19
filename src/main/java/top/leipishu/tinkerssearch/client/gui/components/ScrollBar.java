package top.leipishu.tinkerssearch.client.gui.components;

import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

/**
 * 通用滚动条组件（1.20.1）。
 *
 * <p>相比 1.19.2 版本：
 * <ul>
 *   <li>渲染入口 {@code PoseStack} → {@link GuiGraphics}</li>
 *   <li>{@code GuiComponent.fill(ps, ...)} → {@code graphics.fill(...)}</li>
 * </ul>
 *
 * <p>API 与 1.19.2 版本<b>完全一致</b>（含 {@link #setHoverExpandX(int)}），
 * {@code PanelRenderer} / {@code FluidDetailScreen} 无需改动即可直接调用。
 *
 * <p>每帧调用顺序：
 * <ol>
 *   <li>{@link #setBounds} 设置位置与尺寸</li>
 *   <li>{@link #setRange} 设置当前偏移与最大偏移</li>
 *   <li>{@link #render} 绘制</li>
 * </ol>
 *
 * <p>拖拽接口：
 * <ul>
 *   <li>{@link #tryBeginDrag} — 鼠标按下时调用，命中返回 true</li>
 *   <li>{@link #updateDrag} — 鼠标拖动时调用</li>
 *   <li>{@link #endDrag} — 鼠标松开时调用</li>
 *   <li>{@link #isDragging}</li>
 * </ul>
 */
public class ScrollBar {

    // ===== 布局 =====
    private int x, y, width, height;

    // ===== 状态 =====
    private int offset = 0;
    private int maxOffset = 0;

    // ===== 拖拽 =====
    private boolean dragging = false;
    private int dragStartMouseY = 0;
    private int dragStartOffset = 0;

    // ===== 回调 =====
    private Consumer<Integer> onOffsetChanged;

    // ===== 视觉 =====
    private int trackColor = 0x33FFFFFF;
    private int thumbColor = 0x99FFFFFF;
    private int thumbColorHover = 0xCCFFFFFF;
    private int thumbMinHeight = 16;
    private float thumbRatio = 0.3f;

    /** 鼠标命中容差（水平方向向外扩展的像素）。 */
    private int hoverExpandX = 0;

    public ScrollBar() {}

    // ==================== 配置 ====================

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setRange(int offset, int maxOffset) {
        this.offset = Math.max(0, offset);
        this.maxOffset = Math.max(0, maxOffset);
    }

    public void setOnOffsetChanged(Consumer<Integer> cb) {
        this.onOffsetChanged = cb;
    }

    public void setThumbRatio(float ratio) {
        this.thumbRatio = Math.max(0.05f, Math.min(0.95f, ratio));
    }

    public void setThumbMinHeight(int h) {
        this.thumbMinHeight = Math.max(4, h);
    }

    /** ★ 水平方向命中扩展：窄轨道也方便拖动。 */
    public void setHoverExpandX(int px) {
        this.hoverExpandX = Math.max(0, px);
    }

    // ==================== 状态 ====================

    public boolean isActive() { return maxOffset > 0; }
    public boolean isDragging() { return dragging; }
    public int getOffset() { return offset; }

    public boolean isHovered(double mouseX, double mouseY) {
        if (width <= 0 || height <= 0) return false;
        return mouseX >= x - hoverExpandX && mouseX <= x + width + hoverExpandX
                && mouseY >= y && mouseY <= y + height;
    }

    // ==================== 渲染 ====================

    public void render(GuiGraphics graphics, double mouseX, double mouseY) {
        if (!isActive() || width <= 0 || height <= 0) return;

        graphics.fill(x, y, x + width, y + height, trackColor);

        float ratio = (float) offset / (float) maxOffset;
        int thumbH = Math.max(thumbMinHeight, (int) (height * thumbRatio));
        if (thumbH > height) thumbH = height;
        int thumbY = y + (int) (ratio * (height - thumbH));

        int color = (dragging || isHovered(mouseX, mouseY)) ? thumbColorHover : thumbColor;
        graphics.fill(x, thumbY, x + width, thumbY + thumbH, color);
    }

    // ==================== 拖拽 ====================

    /**
     * 鼠标按下时调用。命中轨道返回 true，并进入拖拽状态。
     */
    public boolean tryBeginDrag(double mouseX, double mouseY) {
        if (!isActive() || !isHovered(mouseX, mouseY)) return false;
        dragging = true;
        dragStartMouseY = (int) mouseY;
        dragStartOffset = offset;
        return true;
    }

    /**
     * 鼠标拖动时调用。按"内容比例"换算偏移，避免 thumb 长度不准导致跳变。
     */
    public boolean updateDrag(double mouseY) {
        if (!dragging) return false;

        int thumbH = Math.max(thumbMinHeight, (int) (height * thumbRatio));
        int scrollablePixels = height - thumbH;
        if (scrollablePixels <= 0) return true;

        float scale = (float) maxOffset / (float) scrollablePixels;
        int delta = (int) mouseY - dragStartMouseY;
        int newOffset = dragStartOffset + (int) (delta * scale);
        newOffset = Math.max(0, Math.min(newOffset, maxOffset));

        if (newOffset != offset) {
            offset = newOffset;
            if (onOffsetChanged != null) onOffsetChanged.accept(offset);
        }
        return true;
    }

    public void endDrag() {
        dragging = false;
    }
}