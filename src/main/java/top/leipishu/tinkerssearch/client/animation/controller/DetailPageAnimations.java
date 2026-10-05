package top.leipishu.tinkerssearch.client.animation.controller;

import top.leipishu.tinkerssearch.client.animation.core.Animation;
import top.leipishu.tinkerssearch.client.animation.core.AnimationManager;
import top.leipishu.tinkerssearch.client.animation.core.Easing;

/**
 * 详情页翻页动画（v2）。
 *
 * <p>两阶段：
 * <pre>
 *   阶段 1（{@code OUT_DURATION}）：卡片淡出 + 向一侧滑出
 *     ↓ 完成时执行 midPoint 回调（真正切换页）
 *   阶段 2（{@code IN_DURATION}）：新页卡片从另一侧滑入 + 淡入
 * </pre>
 *
 * <p>只有卡片网格参与，标题与翻页按钮不受影响。
 * 数据切换延迟到"完全淡出"的那一刻，避免内容硬切。
 */
public final class DetailPageAnimations {

    private DetailPageAnimations() {}

    // ============================================================
    // ===== key ==================================================
    // ============================================================

    private static final String OUT_KEY       = "detail.page.out";
    private static final String OUT_SLIDE_KEY = "detail.page.out.slide";
    private static final String IN_KEY        = "detail.page.in";
    private static final String IN_SLIDE_KEY  = "detail.page.in.slide";
    private static final String PREFIX        = "detail.page.";

    // ============================================================
    // ===== 时长 / 距离 ==========================================
    // ============================================================

    private static final long  OUT_DURATION   = 90L;
    private static final long  IN_DURATION    = 140L;
    private static final float SLIDE_DISTANCE = 40f;

    // ============================================================
    // ===== 状态 ==================================================
    // ============================================================

    private static int pendingDirection = 0;
    private static Runnable pendingMidCallback = null;

    // ============================================================
    // ===== 控制 ==================================================
    // ============================================================

    /**
     * 触发翻页。
     *
     * @param direction 方向：{@code +1} = 下一页（卡片从右侧滑入），
     *                  {@code -1} = 上一页（卡片从左侧滑入）
     * @param midPoint  淡出完成后执行的回调——通常用于真正切换页数据
     */
    public static void startTurn(int direction, Runnable midPoint) {
        if (direction == 0) return;
        if (isTurning()) return;   // 防抖：翻页进行中不重复触发

        int dir = direction > 0 ? 1 : -1;
        pendingDirection = dir;
        pendingMidCallback = midPoint;

        // 阶段 1：淡出 + 滑出（滑向 -dir 方向）
        AnimationManager.get().play(
                OUT_KEY, 1f, 0f,
                OUT_DURATION, 0L,
                Easing.EASE_IN_QUAD,
                DetailPageAnimations::onOutComplete);

        AnimationManager.get().play(
                OUT_SLIDE_KEY, 0f, -dir * 1f,
                OUT_DURATION, 0L,
                Easing.EASE_OUT_CUBIC, null);
    }

    private static void onOutComplete() {
        // 中点：切换页数据
        if (pendingMidCallback != null) {
            Runnable cb = pendingMidCallback;
            pendingMidCallback = null;
            cb.run();
        }

        // 阶段 2：滑入（从 +dir 方向滑回 0）+ 淡入
        AnimationManager.get().play(
                IN_KEY, 0f, 1f,
                IN_DURATION, 0L,
                Easing.EASE_OUT_CUBIC, null);

        AnimationManager.get().play(
                IN_SLIDE_KEY, pendingDirection * 1f, 0f,
                IN_DURATION, 0L,
                Easing.EASE_OUT_CUBIC, null);
    }

    /** 清空翻页动画。 */
    public static void clear() {
        AnimationManager.get().stopPrefix(PREFIX);
        pendingDirection = 0;
        pendingMidCallback = null;
    }

    // ============================================================
    // ===== 状态查询 ==============================================
    // ============================================================

    /**
     * 卡片当前 alpha。
     *
     * <p>翻页进行中：读 out / in 的插值；
     * 否则：1.0。
     */
    public static float getContentAlpha() {
        Animation out = AnimationManager.get().get(OUT_KEY);
        if (out != null && !out.isComplete()) return out.getValue();

        Animation in = AnimationManager.get().get(IN_KEY);
        if (in != null && !in.isComplete()) return in.getValue();

        return 1f;
    }

    /**
     * 卡片当前水平偏移像素。
     *
     * <p>翻页进行中：{@code [-40, 40]} 之间；
     * 否则：0。
     */
    public static float getSlideX() {
        Animation outSlide = AnimationManager.get().get(OUT_SLIDE_KEY);
        if (outSlide != null && !outSlide.isComplete()) {
            return outSlide.getValue() * SLIDE_DISTANCE;
        }

        Animation inSlide = AnimationManager.get().get(IN_SLIDE_KEY);
        if (inSlide != null && !inSlide.isComplete()) {
            return inSlide.getValue() * SLIDE_DISTANCE;
        }

        return 0f;
    }

    /** 是否正在翻页。 */
    public static boolean isTurning() {
        return AnimationManager.get().isRunning(OUT_KEY)
                || AnimationManager.get().isRunning(IN_KEY);
    }
}