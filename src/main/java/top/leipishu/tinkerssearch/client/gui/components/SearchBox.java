package top.leipishu.tinkerssearch.client.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * 通用搜索框组件。
 *
 * <p>包含：背景框、边框、提示文字、光标闪烁、清空按钮、键盘/鼠标交互。
 *
 * <p>组件本身不感知父容器布局——坐标由调用方通过 {@link #setBounds} 每帧设置。
 * 文本变化通过 {@link #setOnTextChanged} 注册的回调通知。
 *
 * <p>点击语义：{@link #mouseClicked} 只在点击落在框内时返回 {@code true}；
 * 点击框外返回 {@code false}，由调用方决定是否取消焦点。
 */
public class SearchBox {

    private int x, y, width, height;

    private SearchBoxStyle style;
    private Component hintText = Component.literal("");
    private Consumer<String> onTextChanged;

    private String text = "";
    private int cursorPosition = 0;
    private boolean focused = false;

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

        int bg = focused ? style.bgColorFocused : style.bgColor;
        graphics.fill(x, y, x + width, y + height, bg);

        int border = focused ? style.borderColorFocused : style.borderColor;
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
