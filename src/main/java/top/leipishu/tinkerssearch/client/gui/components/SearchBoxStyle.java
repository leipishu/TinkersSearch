package top.leipishu.tinkerssearch.client.gui.components;

/**
 * 搜索框样式。
 *
 * <p>颜色、清空按钮尺寸等视觉参数。样式对象可共享，不会在渲染过程中被修改。
 */
public final class SearchBoxStyle {

    public int bgColor = 0xFF222222;
    public int bgColorFocused = 0xFF3A3A3A;
    public int borderColor = 0xFF444444;
    public int borderColorFocused = 0xFF888888;
    public int textColor = 0xFFFFFF;
    public int hintColor = 0x666666;
    public int cursorColor = 0xFFFFFFFF;

    public int clearButtonBgColor = 0x88AA4444;
    public int clearButtonTextColor = 0xFFFFFF;
    /** 清空按钮边长。 */
    public int clearButtonSize = 10;
    /** 清空按钮距右边缘的距离。 */
    public int clearButtonRightMargin = 14;

    public SearchBoxStyle() {}

    /** 详情页默认配色（深灰）。 */
    public static SearchBoxStyle detail() {
        return new SearchBoxStyle();
    }

    /** 浮动面板普通模式配色（与详情页相同）。 */
    public static SearchBoxStyle panel() {
        return new SearchBoxStyle();
    }

    /** 浮动面板合金模式配色（暖金，与标题和 Tab 同色系）。 */
    public static SearchBoxStyle alloy() {
        SearchBoxStyle s = new SearchBoxStyle();
        s.bgColor = 0xFF2A2318;
        s.bgColorFocused = 0xFF3A3020;
        s.borderColor = 0xFF6A5030;
        s.borderColorFocused = 0xFFFFAA00;
        return s;
    }
}