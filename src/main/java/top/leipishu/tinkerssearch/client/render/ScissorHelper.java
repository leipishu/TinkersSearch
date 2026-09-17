package top.leipishu.tinkerssearch.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

/**
 * Scissor Test 安全工具类
 * 解决 GL_INVALID_ENUM 错误
 * 兼容 1.20.1
 */
public class ScissorHelper {

    private static boolean scissorEnabled = false;
    private static int lastX = 0, lastY = 0, lastW = 0, lastH = 0;

    /**
     * 安全启用 Scissor Test
     * @return 是否成功启用
     */
    public static boolean enableScissor(int x, int y, int width, int height) {
        try {
            // 参数验证
            if (width <= 0 || height <= 0) {
                return false;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.getWindow() == null) {
                return false;
            }

            int scale = (int) mc.getWindow().getGuiScale();
            int screenX = x * scale;
            int screenY = mc.getWindow().getScreenHeight() - (y + height) * scale;
            int screenW = Math.max(0, width * scale);
            int screenH = Math.max(0, height * scale);

            // 边界裁剪
            int maxW = mc.getWindow().getScreenWidth();
            int maxH = mc.getWindow().getScreenHeight();
            screenX = Math.max(0, Math.min(screenX, maxW));
            screenY = Math.max(0, Math.min(screenY, maxH));
            screenW = Math.max(0, Math.min(screenW, maxW - screenX));
            screenH = Math.max(0, Math.min(screenH, maxH - screenY));

            if (screenW == 0 || screenH == 0) {
                return false;
            }

            // 检查是否和上次设置相同，避免重复调用
            if (scissorEnabled && screenX == lastX && screenY == lastY &&
                    screenW == lastW && screenH == lastH) {
                return true;
            }

            // ===== 1.20.1 兼容方式：直接使用 GL11 =====
            // 先用 GlStateManager 启用
            GlStateManager._enableScissorTest();

            // 然后用 GL11 设置裁剪区域
            GL11.glScissor(screenX, screenY, screenW, screenH);

            lastX = screenX;
            lastY = screenY;
            lastW = screenW;
            lastH = screenH;
            scissorEnabled = true;

            return true;

        } catch (Exception e) {
            // OpenGL 错误静默忽略
            scissorEnabled = false;
            return false;
        }
    }

    /**
     * 安全禁用 Scissor Test
     */
    public static void disableScissor() {
        if (scissorEnabled) {
            try {
                GlStateManager._disableScissorTest();
            } catch (Exception ignored) {
                // 忽略禁用时的错误
            }
            scissorEnabled = false;
        }
    }

    /**
     * 强制重置 Scissor 状态（渲染结束后调用）
     */
    public static void reset() {
        try {
            GlStateManager._disableScissorTest();
        } catch (Exception ignored) {}
        scissorEnabled = false;
        lastX = lastY = lastW = lastH = 0;
    }

    /**
     * 检查 Scissor 是否已启用
     */
    public static boolean isScissorEnabled() {
        return scissorEnabled;
    }
}
