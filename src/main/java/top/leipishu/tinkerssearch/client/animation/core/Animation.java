package top.leipishu.tinkerssearch.client.animation.core;

/**
 * 单次触发型动画。
 *
 * <p>生命周期：
 * <ol>
 *   <li>{@link AnimationManager#play} 复用/创建实例并写入配置</li>
 *   <li>渲染时通过 {@link #getValue()} 惰性求值（与帧率无关）</li>
 *   <li>进度达 1.0 后 {@link #isComplete()} 返回 true，
 *       {@link #notifyCompletionIfNeeded()} 触发一次 onComplete</li>
 * </ol>
 *
 * <p>实例由 {@link AnimationManager} 持有与复用，外部不应自行 new。
 * 调用方通常不持有引用，而是每帧通过 {@code AnimationManager.get(key)} 查询。
 *
 * <p>注意：{@link #getValue()} 的求值基于系统时间戳，因此可以在任意时刻
 * 调用，无需等待 {@code AnimationManager.update()}。
 */
public final class Animation {

    public final String key;

    private float from;
    private float to;
    private long duration;
    private long delay;
    private long startTime;
    private Easing easing;
    private Runnable onComplete;
    private boolean completionNotified;

    Animation(String key) {
        this.key = key;
    }

    /** 由 {@link AnimationManager} 调用。 */
    void configure(long now, float from, float to, long duration, long delay,
                   Easing easing, Runnable onComplete) {
        this.from = from;
        this.to = to;
        this.duration = Math.max(1L, duration);
        this.delay = Math.max(0L, delay);
        this.easing = (easing != null) ? easing : Easing.LINEAR;
        this.onComplete = onComplete;
        this.startTime = now + this.delay;
        this.completionNotified = false;
    }

    /** 原始进度 [0,1]，未缓动。 */
    public float getProgress() {
        long now = System.currentTimeMillis();
        if (now <= startTime) return 0f;
        long elapsed = now - startTime;
        if (elapsed >= duration) return 1f;
        return (float) elapsed / (float) duration;
    }

    /** 缓动后的进度，可能越界（过冲类）。 */
    public float getEasedProgress() {
        return easing.apply(getProgress());
    }

    /** 当前插值结果：{@code from + (to - from) * eased}。 */
    public float getValue() {
        return from + (to - from) * getEasedProgress();
    }

    /** 进度是否已到达终点（与 onComplete 无关）。 */
    public boolean isComplete() {
        return System.currentTimeMillis() - startTime >= duration;
    }

    /** 是否仍在进行中。 */
    public boolean isRunning() {
        return !isComplete();
    }

    /** 内部：通知完成回调一次。 */
    void notifyCompletionIfNeeded() {
        if (completionNotified) return;
        completionNotified = true;
        if (onComplete != null) {
            try {
                onComplete.run();
            } catch (Throwable ignored) {
                // 用户回调不应影响动画系统
            }
        }
    }

    // ===== 只读访问 =====

    public float getFrom()        { return from; }
    public float getTo()          { return to; }
    public long  getDuration()    { return duration; }
    public long  getDelay()       { return delay; }
    public Easing getEasing()     { return easing; }

    @Override
    public String toString() {
        return "Animation{" + key + ", " + from + "→" + to + ", " + duration + "ms}";
    }
}