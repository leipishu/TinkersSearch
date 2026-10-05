package top.leipishu.tinkerssearch.client.animation.controller;

import top.leipishu.tinkerssearch.client.animation.core.AnimationManager;
import top.leipishu.tinkerssearch.client.animation.core.Animator;

/**
 * 浮动面板的动画读写封装。
 *
 * <p>涵盖冶炼炉 / 材料 / 合金三个 Tab 共用的卡片 hover、Tab hover、
 * Tab 指示器位置、空状态淡入、刷新按钮 hover。
 */
public final class PanelAnimations {

    private PanelAnimations() {}

    // ============================================================
    // ===== 卡片 hover ============================================
    // ============================================================

    private static final String CARD_HOVER_PREFIX       = "panel.card.hover:";
    private static final String ALLOY_CARD_HOVER_PREFIX = "panel.alloy.card.hover:";

    /**
     * 查询/驱动冶炼炉 & 材料 Tab 的卡片 hover 值。
     *
     * @param fluidId 流体注册名（如 {@code "minecraft:lava"}）
     * @return 0=常态，1=完全悬停
     */
    public static float cardHover(String fluidId, boolean hover) {
        Animator a = AnimationManager.get().animator(
                CARD_HOVER_PREFIX + fluidId, 0f, 70f);
        a.setTarget(hover ? 1f : 0f);
        return a.getValue();
    }

    /** 合金 Tab 的卡片 hover（前缀区分，避免与普通 Tab 冲突）。 */
    public static float alloyCardHover(String fluidId, boolean hover) {
        Animator a = AnimationManager.get().animator(
                ALLOY_CARD_HOVER_PREFIX + fluidId, 0f, 70f);
        a.setTarget(hover ? 1f : 0f);
        return a.getValue();
    }

    // ============================================================
    // ===== Tab hover ============================================
    // ============================================================

    private static final String TAB_HOVER_PREFIX = "panel.tab.hover:";

    /**
     * 查询/驱动单个 Tab 按钮的 hover 值。
     */
    public static float tabHover(int tabIndex, boolean hover) {
        Animator a = AnimationManager.get().animator(
                TAB_HOVER_PREFIX + tabIndex, 0f, 70f);
        a.setTarget(hover ? 1f : 0f);
        return a.getValue();
    }

    // ============================================================
    // ===== Tab 指示器 ============================================
    // ============================================================

    private static final String TAB_INDICATOR_KEY = "panel.tab.indicator";

    /**
     * 更新 Tab 指示器的目标 x 坐标（相对面板左上角）并返回当前插值。
     *
     * <p>首次调用时直接定位（不产生滑动）；后续目标变化时平滑过渡。
     */
    public static float tabIndicatorX(int targetX) {
        Animator a = AnimationManager.get().animator(
                TAB_INDICATOR_KEY, targetX, 120f);
        a.setTarget(targetX);
        return a.getValue();
    }

    // ============================================================
    // ===== 空状态淡入 ============================================
    // ============================================================

    private static final String EMPTY_ALPHA_PREFIX = "panel.empty:";

    /**
     * 查询/驱动"无匹配 / 无流体"提示的透明度。
     *
     * @param areaKey 区分不同区域的空状态（{@code "smeltery"} / {@code "materials"} / {@code "alloy"}）
     * @return 0=不可见，1=完全不透明
     */
    public static float emptyAlpha(String areaKey, boolean visible) {
        Animator a = AnimationManager.get().animator(
                EMPTY_ALPHA_PREFIX + areaKey, visible ? 1f : 0f, 120f);
        a.setTarget(visible ? 1f : 0f);
        return a.getValue();
    }

    // ============================================================
    // ===== 刷新按钮 ==============================================
    // ============================================================

    private static final String REFRESH_HOVER_KEY = "panel.refresh.hover";

    public static float refreshHover(boolean hover) {
        Animator a = AnimationManager.get().animator(REFRESH_HOVER_KEY, 0f, 70f);
        a.setTarget(hover ? 1f : 0f);
        return a.getValue();
    }

    // ============================================================
    // ===== 批量清理 =============================================
    // ============================================================

    /**
     * 清空所有面板动画。
     *
     * <p>调用时机：
     * <ul>
     *   <li>{@code TinkersSearch.handleSmelteryClose()} — 关闭界面</li>
     *   <li>{@code PanelAnimationManager.hideImmediate()} — 立即隐藏</li>
     * </ul>
     */
    public static void clearAll() {
        AnimationManager.get().stopPrefix("panel.");
    }
}