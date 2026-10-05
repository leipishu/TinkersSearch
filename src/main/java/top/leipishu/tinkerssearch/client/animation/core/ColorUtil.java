package top.leipishu.tinkerssearch.client.animation.core;

/**
 * ARGB 颜色插值工具。
 *
 * <p>所有方法均按 ARGB 四个分量独立线性插值/缩放，结果钳制到 [0,255]。
 * 无状态，可重入。
 */
public final class ColorUtil {

    private ColorUtil() {}

    /**
     * 在两个 ARGB 颜色之间线性插值。
     *
     * @param from 起始颜色（t=0）
     * @param to   目标颜色（t=1）
     * @param t    插值参数，自动钳制到 [0,1]
     */
    public static int lerpARGB(int from, int to, float t) {
        if (t <= 0f) return from;
        if (t >= 1f) return to;

        int fa = (from >>> 24) & 0xFF;
        int fr = (from >>> 16) & 0xFF;
        int fg = (from >>> 8) & 0xFF;
        int fb = from & 0xFF;

        int ta = (to >>> 24) & 0xFF;
        int tr = (to >>> 16) & 0xFF;
        int tg = (to >>> 8) & 0xFF;
        int tb = to & 0xFF;

        int ra = (int) (fa + (ta - fa) * t);
        int rr = (int) (fr + (tr - fr) * t);
        int rg = (int) (fg + (tg - fg) * t);
        int rb = (int) (fb + (tb - fb) * t);

        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    /**
     * 保持 RGB 不变，把 alpha 分量乘以 {@code alphaFactor}。
     *
     * @param alphaFactor 通常在 [0,1]，&gt;1 会被钳制到原始 alpha
     */
    public static int withAlphaFactor(int color, float alphaFactor) {
        if (alphaFactor <= 0f) return color & 0x00FFFFFF;
        if (alphaFactor >= 1f) return color;

        int a = (color >>> 24) & 0xFF;
        int newA = (int) (a * alphaFactor);
        if (newA > 255) newA = 255;
        return (newA << 24) | (color & 0x00FFFFFF);
    }

    /**
     * 设置绝对 alpha 值（0..255），RGB 保持不变。
     */
    public static int withAlpha(int color, int alpha) {
        if (alpha < 0) alpha = 0;
        else if (alpha > 255) alpha = 255;
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * 按比例缩放 RGB（用于 hover 亮度提升/降低），alpha 保持不变。
     *
     * @param factor &gt;1 提亮，&lt;1 压暗
     */
    public static int scaleRGB(int color, float factor) {
        if (factor == 1f) return color;

        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;

        r = clamp255((int) (r * factor));
        g = clamp255((int) (g * factor));
        b = clamp255((int) (b * factor));

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 把 ARGB 颜色的 alpha 置为 {@code factor ∈ [0,1]} 的比例（覆盖写）。 */
    public static int withAlphaUnit(int color, float factor) {
        if (factor <= 0f) return color & 0x00FFFFFF;
        if (factor >= 1f) factor = 1f;
        int a = (int) (255f * factor);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}