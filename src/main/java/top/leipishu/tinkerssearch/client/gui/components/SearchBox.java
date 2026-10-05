package top.leipishu.tinkerssearch.client.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.leipishu.tinkerssearch.client.animation.controller.WidgetAnimations;
import top.leipishu.tinkerssearch.client.animation.core.ColorUtil;

import java.util.function.Consumer;

/**
 * 通用搜索框组件（1.20.1）。
 *
 * <p>动画：{@link #setAnimationId} 指定实例 id 后，focus / hover 会平滑过渡。
 * 未指定时退化为无动画（直接切换颜色），保证向后兼容。
 */
public class SearchBox {

    private int x, y, width, height;

    private SearchBoxStyle style;
    private Component hintText = Component.literal("");
    private Consumer<String> onTextChanged;

    private String text = "";
    private int cursorPosition = 0;
    private boolean focused = false;

    /** 动画实例 id；null 表示不做动画。 */
    private String animationId = null;

    public SearchBox(SearchBoxStyle style) {
        this.style = style != null ? style : SearchBoxStyle.detail();
    }

    // ==================== 布局 ====================

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    // ==================== 配置 ====================

    public void setStyle(SearchBoxStyle style) {
        this.style = style != null ? style : SearchBoxStyle.detail();
    }

    public void setHintText(Component hintText) {
        this.hintText = hintText != null ? hintText : Component.literal("");
    }

    public void setOnTextChanged(Consumer<String> listener) {
        this.onTextChanged = listener;
    }

    /**
     * 指定动画实例 id。不同搜索框（面板 / 详情页 / 合金页）应使用不同 id，
     * 避免共享动画状态。为 {@code null} 时禁用动画。
     */
    public void setAnimationId(String id) {
        if (this.animationId != null && !this.animationId.equals(id)) {
            WidgetAnimations.clearSearchBox(this.animationId);
        }
        this.animationId = id;
    }

    // ==================== 状态访问 ====================

    public String getText() { return text; }

    public void setText(String text) {
        this.text = text != null ? text : "";
        this.cursorPosition = this.text.length();
        fireChanged();
    }

    public void clear() {
        setText("");
    }

    public int getCursorPosition() { return cursorPosition; }

    public void setCursorPosition(int pos) {
        cursorPosition = clampCursor(pos);
    }

    public boolean isFocused() { return focused; }

    public void setFocused(boolean focused) { this.focused = focused; }

    // ==================== 渲染 ====================

    public void render(GuiGraphics graphics, int mouseX, int mouseY, Font font) {
        if (width <= 0 || height <= 0) return;

        boolean hover = isInside(mouseX, mouseY);

        // focus 优先，其次是 hover：无动画时退化为硬切
        float focusT;
        float hoverT;
        if (animationId != null) {
            focusT = WidgetAnimations.searchBoxFocus(animationId, focused);
            hoverT = WidgetAnimations.searchBoxHover(animationId, hover && !focused);
        } else {
            focusT = focused ? 1f : 0f;
            hoverT = (hover && !focused) ? 1f : 0f;
        }

        // 背景：常态 → hover 轻微提亮 → focus 更强
        int bg = ColorUtil.lerpARGB(style.bgColor, style.bgColorFocused, focusT);
        if (hoverT > 0f && focusT < 1f) {
            bg = ColorUtil.lerpARGB(bg, style.bgColorFocused, hoverT * 0.5f);
        }
        graphics.fill(x, y, x + width, y + height, bg);

        // 边框
        int border = ColorUtil.lerpARGB(style.borderColor, style.borderColorFocused, focusT);
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, border);
        graphics.fill(x, y, x + 1, y + height, border);
        graphics.fill(x + width - 1, y, x + width, y + height, border);

        int textX = x + 4;
        int textY = y + 4;

        if (text.isEmpty()) {
            graphics.drawString(font, hintText, textX, textY, style.hintColor);
        } else {
            int cursorPos = clampCursor(cursorPosition);
            String before = text.substring(0, cursorPos);
            String after = text.substring(cursorPos);
            int beforeWidth = font.width(before);

            graphics.drawString(font, before, textX, textY, style.textColor);
            graphics.drawString(font, after, textX + beforeWidth, textY, style.textColor);

            if (focused && (System.currentTimeMillis() / 500 % 2 == 0)) {
                int cursorX = textX + beforeWidth;
                if (cursorX < x + width - 2) {
                    graphics.fill(cursorX, y + 2, cursorX + 1, y + height - 2,
                            style.cursorColor);
                }
            }
        }

        if (!text.isEmpty()) {
            int size = style.clearButtonSize;
            int clearX = x + width - style.clearButtonRightMargin;
            int clearY = y + (height - size) / 2;
            graphics.fill(clearX, clearY, clearX + size, clearY + size,
                    style.clearButtonBgColor);
            graphics.drawString(font, "\u00a7f\u2715", clearX + 2, clearY + 1, style.clearButtonTextColor);
        }
    }

    // ==================== 输入处理 ====================

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isInside(mouseX, mouseY)) return false;

        if (!text.isEmpty()) {
            int size = style.clearButtonSize;
            int clearX = x + width - style.clearButtonRightMargin;
            int clearY = y + (height - size) / 2;
            if (mouseX >= clearX && mouseX <= clearX + size
                    && mouseY >= clearY && mouseY <= clearY + size) {
                clear();
                focused = true;
                return true;
            }
        }

        focused = true;
        cursorPosition = calculateCursorFromMouse(mouseX);
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused) return false;

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (cursorPosition > 0 && !text.isEmpty()) {
                    text = text.substring(0, cursorPosition - 1) + text.substring(cursorPosition);
                    cursorPosition--;
                    fireChanged();
                }
                return true;

            case GLFW.GLFW_KEY_DELETE:
                if (cursorPosition < text.length()) {
                    text = text.substring(0, cursorPosition) + text.substring(cursorPosition + 1);
                    fireChanged();
                }
                return true;

            case GLFW.GLFW_KEY_LEFT:
                if (cursorPosition > 0) cursorPosition--;
                return true;

            case GLFW.GLFW_KEY_RIGHT:
                if (cursorPosition < text.length()) cursorPosition++;
                return true;

            case GLFW.GLFW_KEY_HOME:
                cursorPosition = 0;
                return true;

            case GLFW.GLFW_KEY_END:
                cursorPosition = text.length();
                return true;

            case GLFW.GLFW_KEY_ESCAPE:
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                focused = false;
                return true;

            default:
                return false;
        }
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!focused) return false;
        if (Character.isISOControl(codePoint)) return false;

        String before = text.substring(0, cursorPosition);
        String after = text.substring(cursorPosition);
        text = before + codePoint + after;
        cursorPosition++;
        fireChanged();
        return true;
    }

    // ==================== 内部工具 ====================

    private boolean isInside(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + height;
    }

    private int clampCursor(int pos) {
        return Math.max(0, Math.min(pos, text.length()));
    }

    private int calculateCursorFromMouse(double mouseX) {
        Font font = Minecraft.getInstance().font;
        int textStartX = x + 4;
        int clickX = (int) mouseX - textStartX;
        if (clickX <= 0 || text.isEmpty()) return 0;

        int charIndex = 0;
        int currentX = 0;
        for (int i = 0; i < text.length(); i++) {
            int charWidth = font.width(text.substring(0, i + 1)) - font.width(text.substring(0, i));
            if (currentX + charWidth / 2 > clickX) break;
            currentX += charWidth;
            charIndex = i + 1;
        }
        return charIndex;
    }

    private void fireChanged() {
        if (onTextChanged != null) {
            onTextChanged.accept(text);
        }
    }
}