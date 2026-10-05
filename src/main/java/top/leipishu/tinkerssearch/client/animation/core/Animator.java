package top.leipishu.tinkerssearch.client.animation.core;

/**
 * 持久型动画器：向目标值指数趋近。
 *
 * <p>适合 hover 颜色、滚动偏移、滚动条 thumb、focus 边框等"有目标值的
 * 连续状态"。与 {@link Animation} 的区别：
 * <ul>
 *   <li>{@code Animation}：给定 from/to/时长，一次走完</li>
 *   <li>{@code Animator}：只持有 current/target，每帧按时间常数趋近</li>
 * </ul>
 *
 * <p>时间常数 {@code tauMs} 表示"收敛到 63% 所需毫秒数"，越小越快。
 * 默认 60ms → 约 150ms 收敛到 95%。帧率无关。
 *
 * <p>{@link #update(long)} 由 {@link AnimationManager} 每帧调用，
 * 外部不应直接调用。
 */
public final class Animator {

    public final String key;

    private float current;
    private float target;
    private float tauMs;
    private boolean settled;
    private long lastUpdateTime;

    Animator(String key, float initial, float tauMs) {
        this.key = key;
        this.current = initial;
        this.target = initial;
        this.tauMs = Math.max(1f, tauMs);
        this.settled = true;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    // ===== 目标控制 =====

    /** 设置新目标，开始趋近。相同目标会被忽略。 */
    public void setTarget(float target) {
        if (Math.abs(target - this.target) < 0.0001f) return;
        this.target = target;
        this.settled = false;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /** 立即跳到指定值，不产生动画。用于初始化或重置。 */
    public void snap(float value) {
        this.current = value;
        this.target = value;
        this.settled = true;
    }

    /** 修改时间常数。不影响当前值。 */
    public void setTau(float tauMs) {
        this.tauMs = Math.max(1f, tauMs);
    }

    // ===== 驱动 =====

    /** 由 {@link AnimationManager} 每帧调用。 */
    void update(long now) {
        if (settled) return;

        long dt = now - lastUpdateTime;
        lastUpdateTime = now;
        if (dt <= 0) return;
        // 防止卡顿后跳变
        if (dt > 100L) dt = 100L;

        float factor = 1f - (float) Math.exp(-dt / tauMs);
        current += (target - current) * factor;

        if (Math.abs(target - current) < 0.0005f) {
            current = target;
            settled = true;
        }
    }

    // ===== 只读 =====

    public float   getValue()     { return current; }
    public float   getTarget()    { return target; }
    public boolean isSettled()    { return settled; }
    public float   getTau()       { return tauMs; }

    @Override
    public String toString() {
        return "Animator{" + key + ", " + current + "→" + target + ", τ=" + tauMs + "ms}";
    }
}