package top.leipishu.tinkerssearch.client.animation.controller;

import top.leipishu.tinkerssearch.client.animation.core.Animation;
import top.leipishu.tinkerssearch.client.animation.core.AnimationManager;
import top.leipishu.tinkerssearch.client.animation.core.Easing;

/**
 * 详情页窗口开关动画。
 *
 * <p>只做缩放 + 遮罩 alpha，**不使用 {@code setShaderColor}**——
 * 那是全局 GL 状态，会泄漏到后续绘制（如 tooltip），造成"蒙层"。
 *
 * <p>状态机：
 * <pre>
 *   构造 → startOpenAnimation() → [scale 0.92→1.0, mask 0→1, 200ms] → 静止
 *        → onClose() → startCloseAnimation(cb) → [scale 1.0→0.96, mask 1→0, 150ms] → cb
 * </pre>
 */
public final class DetailAnimations {

    private DetailAnimations() {}

    // ============================================================
    // ===== key ==================================================
    // ============================================================

    private static final String OPEN_SCALE_KEY  = "detail.window.open.scale";
    private static final String OPEN_MASK_KEY   = "detail.window.open.mask";
    private static final String CLOSE_SCALE_KEY = "detail.window.close.scale";
    private static final String CLOSE_MASK_KEY  = "detail.window.close.mask";

    private static final String PREFIX = "detail.window.";

    // ============================================================
    // ===== 时长 / 缓动 ==========================================
    // ============================================================

    private static final long   OPEN_DURATION   = 200L;
    private static final Easing OPEN_EASING     = Easing.EASE_OUT_CUBIC;
    private static final float  OPEN_SCALE_FROM = 0.92f;

    private static final long   CLOSE_DURATION  = 150L;
    private static final Easing CLOSE_EASING    = Easing.EASE_IN_QUAD;
    private static final float  CLOSE_SCALE_TO  = 0.96f;

    // ============================================================
    // ===== 控制 ==================================================
    // ============================================================

    /** 开始打开动画。应在 {@code FluidDetailScreen} 构造末尾调用一次。 */
    public static void startOpenAnimation() {
        // 清掉可能的关闭动画残留（跨屏幕生命周期）
        AnimationManager.get().stop(CLOSE_SCALE_KEY);
        AnimationManager.get().stop(CLOSE_MASK_KEY);

        AnimationManager.get().play(
                OPEN_SCALE_KEY, OPEN_SCALE_FROM, 1f,
                OPEN_DURATION, 0L, OPEN_EASING, null);
        AnimationManager.get().play(
                OPEN_MASK_KEY, 0f, 1f,
                OPEN_DURATION, 0L, OPEN_EASING, null);
    }

    /**
     * 开始关闭动画。从"当前视觉状态"起步（可能处于打开动画中途），
     * 避免反向时跳变。{@code onComplete} 在动画完成时（主线程）触发一次。
     */
    public static void startCloseAnimation(Runnable onComplete) {
        float startScale = getWindowScale();
        float startMask = getMaskProgress();

        // 打断打开动画
        AnimationManager.get().stop(OPEN_SCALE_KEY);
        AnimationManager.get().stop(OPEN_MASK_KEY);

        AnimationManager.get().play(
                CLOSE_SCALE_KEY, startScale, CLOSE_SCALE_TO,
                CLOSE_DURATION, 0L, CLOSE_EASING, null);
        AnimationManager.get().play(
                CLOSE_MASK_KEY, startMask, 0f,
                CLOSE_DURATION, 0L, CLOSE_EASING, onComplete);
    }

    /** 清空全部窗口动画。 */
    public static void clear() {
        AnimationManager.get().stopPrefix(PREFIX);
    }

    // ============================================================
    // ===== 状态查询 ==============================================
    // ============================================================

    /**
     * 当前窗口缩放。优先级：关闭动画 → 打开动画 → 1.0。
     */
    public static float getWindowScale() {
        Animation closeAnim = AnimationManager.get().get(CLOSE_SCALE_KEY);
        if (closeAnim != null) return closeAnim.getValue();

        Animation openAnim = AnimationManager.get().get(OPEN_SCALE_KEY);
        if (openAnim != null) return openAnim.getValue();

        return 1f;
    }

    /**
     * 遮罩进度（0 = 完全透明，1 = 完全不透明）。
     *
     * <p>用于计算全屏黑色遮罩的颜色 alpha：{@code (int)(0x80 * getMaskProgress())}。
     */
    public static float getMaskProgress() {
        Animation closeAnim = AnimationManager.get().get(CLOSE_MASK_KEY);
        if (closeAnim != null) return closeAnim.getValue();

        Animation openAnim = AnimationManager.get().get(OPEN_MASK_KEY);
        if (openAnim != null) return openAnim.getValue();

        return 1f;
    }

    /** 是否正在播放关闭动画（用于屏蔽输入）。 */
    public static boolean isClosing() {
        return AnimationManager.get().isRunning(CLOSE_MASK_KEY);
    }

    /** 是否窗口动画进行中（打开或关闭）。 */
    public static boolean isAnimating() {
        return AnimationManager.get().isRunning(OPEN_MASK_KEY)
                || AnimationManager.get().isRunning(CLOSE_MASK_KEY);
    }
}