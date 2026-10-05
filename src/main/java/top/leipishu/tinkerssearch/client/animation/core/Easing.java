package top.leipishu.tinkerssearch.client.animation.core;

/**
 * 缓动函数。
 *
 * <p>输入进度 {@code t ∈ [0,1]}，输出缓动后的进度（可能略超出 [0,1]，
 * 例如 {@link #EASE_OUT_BACK} 的过冲）。插值由调用方负责，缓动函数
 * 只做"进度 → 进度"的映射。
 *
 * <p>所有实现均无状态、可重入、线程安全。
 */
public enum Easing {

    /** 线性，无缓动。 */
    LINEAR {
        @Override public float apply(float t) { return t; }
    },

    /** 二次加速（慢 → 快）。 */
    EASE_IN_QUAD {
        @Override public float apply(float t) { return t * t; }
    },

    /** 三次加速。 */
    EASE_IN_CUBIC {
        @Override public float apply(float t) { return t * t * t; }
    },

    /** 二次减速（快 → 慢）。适合大多数"出现"动画。 */
    EASE_OUT_QUAD {
        @Override public float apply(float t) {
            float u = 1f - t;
            return 1f - u * u;
        }
    },

    /** 三次减速。默认推荐，比 QUAD 更"重"一些。 */
    EASE_OUT_CUBIC {
        @Override public float apply(float t) {
            float u = 1f - t;
            return 1f - u * u * u;
        }
    },

    /** 二次进出。适合"位置移动"类动画。 */
    EASE_IN_OUT_QUAD {
        @Override public float apply(float t) {
            return t < 0.5f
                    ? 2f * t * t
                    : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
        }
    },

    /** 三次进出。 */
    EASE_IN_OUT_CUBIC {
        @Override public float apply(float t) {
            return t < 0.5f
                    ? 4f * t * t * t
                    : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
        }
    },

    /** 过冲后回落。适合点击反馈、星标点亮等"弹一下"的场景。 */
    EASE_OUT_BACK {
        @Override public float apply(float t) {
            final float c1 = 1.70158f;
            final float c3 = c1 + 1f;
            float x = t - 1f;
            return 1f + c3 * x * x * x + c1 * x * x;
        }
    },

    /** 弹性震荡。适合"面板滑入"这种需要活泼感的位置，慎用。 */
    EASE_OUT_ELASTIC {
        @Override public float apply(float t) {
            if (t <= 0f) return 0f;
            if (t >= 1f) return 1f;
            final float c4 = (2f * (float) Math.PI) / 3f;
            return (float) Math.pow(2f, -10f * t)
                    * (float) Math.sin((t * 10f - 0.75f) * c4) + 1f;
        }
    },

    /** 落地弹跳。 */
    EASE_OUT_BOUNCE {
        @Override public float apply(float t) {
            final float n1 = 7.5625f;
            final float d1 = 2.75f;
            if (t < 1f / d1) {
                return n1 * t * t;
            } else if (t < 2f / d1) {
                t -= 1.5f / d1;
                return n1 * t * t + 0.75f;
            } else if (t < 2.5f / d1) {
                t -= 2.25f / d1;
                return n1 * t * t + 0.9375f;
            } else {
                t -= 2.625f / d1;
                return n1 * t * t + 0.984375f;
            }
        }
    };

    /**
     * 应用缓动。
     *
     * @param t 原始进度，通常已在 [0,1]，但允许略微越界（过冲类缓动返回值可能 &gt; 1）
     */
    public abstract float apply(float t);

    /** 先把 {@code t} 钳制到 [0,1] 再缓动。 */
    public float applyClamped(float t) {
        if (t <= 0f) t = 0f;
        else if (t >= 1f) t = 1f;
        return apply(t);
    }
}