package top.leipishu.tinkerssearch.client.animation.controller;

import top.leipishu.tinkerssearch.client.animation.core.AnimationManager;
import top.leipishu.tinkerssearch.client.animation.core.Animator;

/**
 * 通用组件的动画读写封装。
 *
 * <p>组件本身不持有 {@link Animator}，每次渲染通过这里按 id 查询。
 * id 由组件调用 {@code setAnimationId} 显式指定，避免每次 new 组件时
 * 生成新 key 造成累积。
 */
public final class WidgetAnimations {

    private WidgetAnimations() {}

    // ============================================================
    // ===== SearchBox ============================================
    // ============================================================

    private static final String SEARCHBOX_FOCUS_PREFIX = "widget.searchbox.focus:";
    private static final String SEARCHBOX_HOVER_PREFIX = "widget.searchbox.hover:";

    /**
     * 查询搜索框的 focus 插值。
     *
     * @return 0=未聚焦，1=已聚焦（或中间值）
     */
    public static float searchBoxFocus(String boxId, boolean focused) {
        Animator a = AnimationManager.get().animator(
                SEARCHBOX_FOCUS_PREFIX + boxId, focused ? 1f : 0f, 80f);
        a.setTarget(focused ? 1f : 0f);
        return a.getValue();
    }

    /**
     * 查询搜索框的 hover 插值（鼠标悬停但未聚焦时的轻微高亮）。
     */
    public static float searchBoxHover(String boxId, boolean hover) {
        Animator a = AnimationManager.get().animator(
                SEARCHBOX_HOVER_PREFIX + boxId, 0f, 80f);
        a.setTarget(hover ? 1f : 0f);
        return a.getValue();
    }

    /** 清空某个搜索框的动画状态。 */
    public static void clearSearchBox(String boxId) {
        AnimationManager.get().stopAnimator(SEARCHBOX_FOCUS_PREFIX + boxId);
        AnimationManager.get().stopAnimator(SEARCHBOX_HOVER_PREFIX + boxId);
    }

    // ============================================================
    // ===== ScrollBar ============================================
    // ============================================================

    private static final String SCROLLBAR_THUMB_PREFIX = "widget.scrollbar.thumb:";
    private static final String SCROLLBAR_HOVER_PREFIX = "widget.scrollbar.hover:";

    /**
     * 查询/驱动滚动条 thumb 的位置比例（0=顶，1=底）。
     *
     * @param offset     当前偏移
     * @param maxOffset  最大偏移
     * @param barId      滚动条实例 id
     * @param immediate  true = 立即跳到目标（拖拽时用），false = 平滑趋近
     * @return 用于绘制的比例值（已插值）
     */
    public static float scrollBarThumbRatio(int offset, int maxOffset,
                                            String barId, boolean immediate) {
        if (maxOffset <= 0) {
            Animator a = AnimationManager.get().animator(
                    SCROLLBAR_THUMB_PREFIX + barId, 0f, 80f);
            a.snap(0f);
            return 0f;
        }

        float target = (float) offset / (float) maxOffset;
        Animator a = AnimationManager.get().animator(
                SCROLLBAR_THUMB_PREFIX + barId, target, 80f);
        if (immediate) {
            a.snap(target);
        } else {
            a.setTarget(target);
        }
        return a.getValue();
    }

    /**
     * 查询滚动条的 hover / 拖拽高亮因子。
     *
     * @return 0=常态，1=悬停或拖拽
     */
    public static float scrollBarHoverFactor(String barId, boolean hover, boolean dragging) {
        Animator a = AnimationManager.get().animator(
                SCROLLBAR_HOVER_PREFIX + barId, 0f, 80f);
        a.setTarget((hover || dragging) ? 1f : 0f);
        return a.getValue();
    }

    /** 清空某个滚动条的全部动画状态。 */
    public static void clearScrollBar(String barId) {
        AnimationManager.get().stopAnimator(SCROLLBAR_THUMB_PREFIX + barId);
        AnimationManager.get().stopAnimator(SCROLLBAR_HOVER_PREFIX + barId);
    }

    // ============================================================
    // ===== 批量清理 =============================================
    // ============================================================

    /** 清空所有组件动画。在面板关闭 / 详情页关闭时调用。 */
    public static void clearAll() {
        AnimationManager.get().stopPrefix("widget.");
    }
}