package top.leipishu.tinkerssearch.client.animation.controller;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import top.leipishu.tinkerssearch.client.animation.core.AnimationManager;
import top.leipishu.tinkerssearch.client.animation.core.Animator;
import top.leipishu.tinkerssearch.client.animation.core.Easing;

/**
 * 浮动面板的动画读写封装。
 *
 * <p>涵盖：卡片 hover、Tab hover、Tab 指示器位置、空状态淡入、
 * 刷新按钮 hover，以及三类触发型脉冲（卡片点击 / 星标点亮 / 刷新按钮）。
 */
public final class PanelAnimations {

    private PanelAnimations() {}

    // ============================================================
    // ===== 卡片 key 工具 ========================================
    // ============================================================

    /**
     * 为流体生成稳定的动画 key 后缀。
     *
     * <p>优先用注册名（如 {@code "minecraft:lava"}），空则退化为显示名。
     * 所有卡片动画（hover / click / star）共用此 id，避免重复构造。
     */
    public static String cardKeyFor(FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) return "unknown";
        ResourceLocation rl = fluid.getFluid().getRegistryName();
        if (rl != null) return rl.toString();
        return fluid.getDisplayName().getString();
    }

    // ============================================================
    // ===== 卡片 hover ============================================
    // ============================================================

    private static final String CARD_HOVER_PREFIX       = "panel.card.hover:";
    private static final String ALLOY_CARD_HOVER_PREFIX = "panel.alloy.card.hover:";

    /**
     * 查询/驱动冶炼炉 & 材料 Tab 的卡片 hover 值。
     *
     * @return 0=常态，1=完全悬停
     */
    public static float cardHover(String cardKey, boolean hover) {
        Animator a = AnimationManager.get().animator(
                CARD_HOVER_PREFIX + cardKey, 0f, 70f);
        a.setTarget(hover ? 1f : 0f);
        return a.getValue();
    }

    /** 合金 Tab 的卡片 hover（前缀区分，避免与普通 Tab 冲突）。 */
    public static float alloyCardHover(String cardKey, boolean hover) {
        Animator a = AnimationManager.get().animator(
                ALLOY_CARD_HOVER_PREFIX + cardKey, 0f, 70f);
        a.setTarget(hover ? 1f : 0f);
        return a.getValue();
    }

    // ============================================================
    // ===== 触发型：卡片点击脉冲 ==================================
    // ============================================================

    private static final String CARD_CLICK_PREFIX = "panel.card.click:";

    /**
     * 触发一次卡片点击脉冲。链式两段：
     * <ol>
     *   <li>缩放 1.0 → 0.94（70ms，EASE_OUT_QUAD）</li>
     *   <li>缩放 0.94 → 1.0（150ms，EASE_OUT_BACK，带过冲）</li>
     * </ol>
     */
    public static void triggerCardClick(String cardKey) {
        final String key = CARD_CLICK_PREFIX + cardKey;
        AnimationManager.get().play(
                key,
                1f, 0.94f,
                70L, 0L,
                Easing.EASE_OUT_QUAD,
                () -> AnimationManager.get().play(
                        key,
                        0.94f, 1f,
                        150L, 0L,
                        Easing.EASE_OUT_BACK,
                        null));
    }

    /**
     * 查询卡片当前缩放。
     *
     * @return 1.0 = 常态；&lt;1 表示正在点击脉冲
     */
    public static float cardClickScale(String cardKey) {
        return AnimationManager.get().getValue(CARD_CLICK_PREFIX + cardKey, 1f);
    }

    // ============================================================
    // ===== 触发型：星标点亮脉冲 ==================================
    // ============================================================

    private static final String STAR_PULSE_PREFIX = "panel.star.click:";

    /**
     * 触发一次星标脉冲。链式两段：
     * <ol>
     *   <li>缩放 1.0 → 1.5（90ms，EASE_OUT_QUAD）</li>
     *   <li>缩放 1.5 → 1.0（180ms，EASE_OUT_BACK）</li>
     * </ol>
     */
    public static void triggerStarPulse(String cardKey) {
        final String key = STAR_PULSE_PREFIX + cardKey;
        AnimationManager.get().play(
                key,
                1f, 1.5f,
                90L, 0L,
                Easing.EASE_OUT_QUAD,
                () -> AnimationManager.get().play(
                        key,
                        1.5f, 1f,
                        180L, 0L,
                        Easing.EASE_OUT_BACK,
                        null));
    }

    /**
     * 查询星标当前缩放。
     *
     * @return 1.0 = 常态；&gt;1 表示正在点亮脉冲
     */
    public static float starPulseScale(String cardKey) {
        return AnimationManager.get().getValue(STAR_PULSE_PREFIX + cardKey, 1f);
    }

    // ============================================================
    // ===== 触发型：刷新按钮脉冲 ==================================
    // ============================================================

    private static final String REFRESH_PULSE_KEY = "panel.refresh.click";

    /**
     * 触发一次刷新按钮点击脉冲。链式两段：
     * <ol>
     *   <li>缩放 1.0 → 0.92（60ms，EASE_OUT_QUAD）</li>
     *   <li>缩放 0.92 → 1.0（130ms，EASE_OUT_BACK）</li>
     * </ol>
     */
    public static void triggerRefreshPulse() {
        AnimationManager.get().play(
                REFRESH_PULSE_KEY,
                1f, 0.92f,
                60L, 0L,
                Easing.EASE_OUT_QUAD,
                () -> AnimationManager.get().play(
                        REFRESH_PULSE_KEY,
                        0.92f, 1f,
                        130L, 0L,
                        Easing.EASE_OUT_BACK,
                        null));
    }

    /**
     * 查询刷新按钮当前缩放。
     */
    public static float refreshPulseScale() {
        return AnimationManager.get().getValue(REFRESH_PULSE_KEY, 1f);
    }

    // ============================================================
    // ===== Tab hover ============================================
    // ============================================================

    private static final String TAB_HOVER_PREFIX = "panel.tab.hover:";

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

    public static float emptyAlpha(String areaKey, boolean visible) {
        Animator a = AnimationManager.get().animator(
                EMPTY_ALPHA_PREFIX + areaKey, visible ? 1f : 0f, 120f);
        a.setTarget(visible ? 1f : 0f);
        return a.getValue();
    }

    // ============================================================
    // ===== 刷新按钮 hover ========================================
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

    public static void clearAll() {
        AnimationManager.get().stopPrefix("panel.");
    }
}