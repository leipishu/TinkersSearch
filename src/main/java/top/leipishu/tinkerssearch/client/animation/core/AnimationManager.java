package top.leipishu.tinkerssearch.client.animation.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局动画管理器（单例）。
 *
 * <p>职责：
 * <ul>
 *   <li>注册与生命周期管理：触发型 {@link Animation} / 持久型 {@link Animator}</li>
 *   <li>每帧驱动：由 {@code RenderTickEvent.Phase.START} 调用 {@link #update()}</li>
 *   <li>按 key 前缀批量清理（面板隐藏、世界切换等时机）</li>
 * </ul>
 *
 * <h2>key 命名约定</h2>
 * <pre>
 *   &lt;模块&gt;.&lt;类型&gt;[:&lt;身份&gt;]
 * </pre>
 * 例：
 * <ul>
 *   <li>{@code panel.slide} — 面板滑入滑出</li>
 *   <li>{@code panel.card.hover:minecraft:lava} — 单个卡片的 hover</li>
 *   <li>{@code detail.part.expand:head|tconstruct:iron} — 单个部件的展开</li>
 * </ul>
 *
 * <h2>线程约束</h2>
 * 所有方法必须在渲染主线程调用（渲染与 {@code RenderTickEvent} 都在主线程）。
 * 内部使用 {@link HashMap}，不做并发保护。
 *
 * <h2>引用稳定性</h2>
 * 同 key 的 {@link Animation}/{@link Animator} 实例会被复用，
 * 因此调用方可以安全地持有 play() / animator() 返回的引用。
 */
public final class AnimationManager {

    private static final AnimationManager INSTANCE = new AnimationManager();

    public static AnimationManager get() {
        return INSTANCE;
    }

    private final Map<String, Animation> animations = new HashMap<>();
    private final Map<String, Animator>  animators  = new HashMap<>();

    // update() 时遍历的快照，避免并发修改
    private final List<Animation> snapshotAnimations = new ArrayList<>();
    private final List<Animator>  snapshotAnimators  = new ArrayList<>();

    private boolean paused = false;

    private AnimationManager() {}

    // ============================================================
    // ===== 触发型 ================================================
    // ============================================================

    /** 无延迟、无回调的便捷版本。 */
    public Animation play(String key, float from, float to,
                          long durationMs, Easing easing) {
        return play(key, from, to, durationMs, 0L, easing, null);
    }

    /**
     * 播放一次触发型动画。同 key 会打断上一次并立即从新 from 开始。
     *
     * @param key        唯一标识
     * @param from       起始值
     * @param to         目标值
     * @param durationMs 时长（毫秒，&lt;1 会被钳到 1）
     * @param delayMs    延迟（毫秒）
     * @param easing     缓动，null 视为 {@link Easing#LINEAR}
     * @param onComplete 完成回调（主线程调用，至多一次），可为 null
     * @return 复用的 {@link Animation} 实例，可持有
     */
    public Animation play(String key, float from, float to,
                          long durationMs, long delayMs,
                          Easing easing, Runnable onComplete) {
        Animation anim = animations.get(key);
        if (anim == null) {
            anim = new Animation(key);
            animations.put(key, anim);
        }
        anim.configure(System.currentTimeMillis(),
                from, to, durationMs, delayMs, easing, onComplete);
        return anim;
    }

    /** 获取动画实例（可能为 null，也可能已完成）。 */
    public Animation get(String key) {
        return animations.get(key);
    }

    /** 该 key 是否正在播放。 */
    public boolean isRunning(String key) {
        Animation a = animations.get(key);
        return a != null && !a.isComplete();
    }

    /**
     * 是否存在任一 key 以 {@code prefix} 开头且正在播放的动画。
     *
     * <p>典型用途：{@code isAnyRunning("detail.part.expand:")}。
     */
    public boolean isAnyRunning(String prefix) {
        if (prefix == null || prefix.isEmpty()) return false;
        for (Animation a : animations.values()) {
            if (a.key.startsWith(prefix) && !a.isComplete()) return true;
        }
        return false;
    }

    /** 以 {@code prefix} 开头且仍在播放的动画数量。 */
    public int getRunningCount(String prefix) {
        if (prefix == null || prefix.isEmpty()) return 0;
        int n = 0;
        for (Animation a : animations.values()) {
            if (a.key.startsWith(prefix) && !a.isComplete()) n++;
        }
        return n;
    }

    /**
     * 读取动画当前值；不存在或已完成时返回 {@code fallback}。
     *
     * <p>对于"完成后应保持终值"的场景，通常传 {@code fallback = to}。
     */
    public float getValue(String key, float fallback) {
        Animation a = animations.get(key);
        if (a == null || a.isComplete()) return fallback;
        return a.getValue();
    }

    // ============================================================
    // ===== 持久型 ================================================
    // ============================================================

    /** 默认 tau = 60ms。 */
    public Animator animator(String key) {
        return animator(key, 0f, 60f);
    }

    /**
     * 获取/创建持久型动画器。首次创建时以 {@code initial} 初始化
     * current 和 target。
     */
    public Animator animator(String key, float initial, float tauMs) {
        return animators.computeIfAbsent(key, k -> new Animator(k, initial, tauMs));
    }

    public Animator getAnimator(String key) {
        return animators.get(key);
    }

    /**
     * 便捷方法：确保 key 存在，并把目标设为 {@code target}。
     */
    public Animator driveTo(String key, float target) {
        Animator a = animator(key);
        a.setTarget(target);
        return a;
    }

    // ============================================================
    // ===== 驱动 ==================================================
    // ============================================================

    /**
     * 每帧由 {@code RenderTickEvent.Phase.START} 调用。
     *
     * <p>无活跃动画时几乎零成本。
     */
    public void update() {
        if (paused) return;
        long now = System.currentTimeMillis();

        if (!animations.isEmpty()) {
            snapshotAnimations.clear();
            snapshotAnimations.addAll(animations.values());
            for (Animation a : snapshotAnimations) {
                if (a.isComplete()) {
                    a.notifyCompletionIfNeeded();
                }
            }
        }

        if (!animators.isEmpty()) {
            snapshotAnimators.clear();
            snapshotAnimators.addAll(animators.values());
            for (Animator anim : snapshotAnimators) {
                anim.update(now);
            }
        }
    }

    // ============================================================
    // ===== 清理 ==================================================
    // ============================================================

    public void stop(String key) {
        animations.remove(key);
    }

    public void stopAnimator(String key) {
        animators.remove(key);
    }

    /** 移除所有以 {@code prefix} 开头的动画/动画器。 */
    public void stopPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return;
        animations.keySet().removeIf(k -> k.startsWith(prefix));
        animators.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** 清空全部。 */
    public void clear() {
        animations.clear();
        animators.clear();
    }

    // ============================================================
    // ===== 暂停 / 调试 ==========================================
    // ============================================================

    public void setPaused(boolean paused)  { this.paused = paused; }
    public boolean isPaused()              { return paused; }

    public int getActiveAnimationCount()   { return animations.size(); }
    public int getActiveAnimatorCount()    { return animators.size(); }

    /** 调试用：清空并重置全部状态。 */
    public void reset() {
        animations.clear();
        animators.clear();
        paused = false;
    }
}