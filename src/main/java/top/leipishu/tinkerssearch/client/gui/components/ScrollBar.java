package top.leipishu.tinkerssearch.client.gui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import top.leipishu.tinkerssearch.client.animation.controller.WidgetAnimations;
import top.leipishu.tinkerssearch.client.animation.core.ColorUtil;

import java.util.function.Consumer;

/**
 * 通用滚动条组件。
 *
 * <p>包含轨道、thumb 的绘制，以及鼠标拖拽逻辑。
 * 组件本身不感知数据源，通过 {@link #setOnOffsetChanged} 注册的回调写回偏移。
 *
 * <p>动画：{@link #setAnimationId} 指定实例 id 后，thumb 位置与 hover
 * 颜色会平滑过渡。拖拽时 thumb 立即跟随（不做平滑），滚轮滚动时平滑。
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

    /** 动画实例 id；null 表示不做动画。 */
    private String animationId = null;

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

    /**
     * 指定动画实例 id。不同滚动条应使用不同 id。
     * 为 {@code null} 时禁用动画（thumb 立即定位、颜色硬切）。
     */
    public void setAnimationId(String id) {
        if (this.animationId != null && !this.animationId.equals(id)) {
            WidgetAnimations.clearScrollBar(this.animationId);
        }
        this.animationId = id;
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

    public void render(PoseStack ps, double mouseX, double mouseY) {
        if (!isActive() || width <= 0 || height <= 0) return;

        GuiComponent.fill(ps, x, y, x + width, y + height, trackColor);

        // thumb 位置：拖拽时立即跟随，滚轮时平滑
        float ratio;
        if (animationId != null) {
            ratio = WidgetAnimations.scrollBarThumbRatio(offset, maxOffset, animationId, dragging);
        } else {
            ratio = (float) offset / (float) maxOffset;
        }
        if (ratio < 0f) ratio = 0f;
        else if (ratio > 1f) ratio = 1f;

        int thumbH = Math.max(thumbMinHeight, (int) (height * thumbRatio));
        if (thumbH > height) thumbH = height;
        int thumbY = y + (int) (ratio * (height - thumbH));

        // hover / 拖拽高亮
        int color;
        if (animationId != null) {
            float hoverT = WidgetAnimations.scrollBarHoverFactor(
                    animationId, isHovered(mouseX, mouseY), dragging);
            color = ColorUtil.lerpARGB(thumbColor, thumbColorHover, hoverT);
        } else {
            color = (dragging || isHovered(mouseX, mouseY)) ? thumbColorHover : thumbColor;
        }

        GuiComponent.fill(ps, x, thumbY, x + width, thumbY + thumbH, color);
    }

    // ==================== 拖拽 ====================

    public boolean tryBeginDrag(double mouseX, double mouseY) {
        if (!isActive() || !isHovered(mouseX, mouseY)) return false;
        dragging = true;
        dragStartMouseY = (int) mouseY;
        dragStartOffset = offset;
        return true;
    }

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