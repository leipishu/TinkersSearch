package top.leipishu.tinkerssearch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fluids.FluidStack;
import top.leipishu.tinkerssearch.config.PanelConfig;
import top.leipishu.tinkerssearch.utils.SmelteryClickHandler;

import java.util.List;
import java.util.function.Consumer;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

/**
 * 面板交互处理器 - 处理鼠标和键盘事件
 * 与渲染逻辑完全分离
 */
public class PanelInteractionHandler {

    private final FloatingSearchPanel panel;
    private final Runnable onRefresh;
    private final Consumer<List<FluidStack>> onFluidClick;

    // 交互状态
    private boolean isSearchBoxFocused = false;
    private String searchKeyword = "";
    private long lastClickTime = 0;

    // 数据引用
    private List<FluidStack> allFluids;
    private List<FluidStack> displayedFluids;

    public PanelInteractionHandler(FloatingSearchPanel panel,
                                   Runnable onRefresh,
                                   Consumer<List<FluidStack>> onFluidClick) {
        this.panel = panel;
        this.onRefresh = onRefresh;
        this.onFluidClick = onFluidClick;
    }

    /**
     * 设置数据引用（由面板调用）
     */
    public void setDataRefs(List<FluidStack> allFluids, List<FluidStack> displayedFluids) {
        this.allFluids = allFluids;
        this.displayedFluids = displayedFluids;
    }

    // ==================== 搜索框状态 ====================

    public boolean isSearchBoxFocused() {
        return isSearchBoxFocused;
    }

    public String getSearchKeyword() {
        return searchKeyword;
    }

    public void setSearchKeyword(String keyword) {
        this.searchKeyword = keyword;
    }

    // ==================== 键盘事件 ====================

    public boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!panel.isVisible() || !isSearchBoxFocused) return false;

        switch (keyCode) {
            case 259: // 退格
                if (!searchKeyword.isEmpty()) {
                    searchKeyword = searchKeyword.substring(0, searchKeyword.length() - 1);
                    onRefresh.run();
                }
                return true;
            case 256: // ESC
            case 257: // 回车
            case 335: // 回车 (数字键盘)
                isSearchBoxFocused = false;
                return true;
            default:
                return false;
        }
    }

    public boolean handleCharTyped(char codePoint, int modifiers) {
        if (!panel.isVisible() || !isSearchBoxFocused) return false;
        if (Character.isISOControl(codePoint)) return false;

        searchKeyword += codePoint;
        onRefresh.run();
        return true;
    }

    // ==================== 鼠标事件 ====================

    public boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        if (!panel.isVisible()) return false;

        int px = panel.getPanelX();
        int py = panel.getPanelY();
        int pw = panel.getPanelWidth();

        // 防抖
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < CLICK_COOLDOWN) {
            return false;
        }

        // 1. 刷新按钮
        if (isInRefreshButton(mouseX, mouseY, px, py)) {
            onRefresh.run();
            return true;
        }

        // 2. 搜索框点击
        if (isInSearchBox(mouseX, mouseY, px, py, pw)) {
            isSearchBoxFocused = true;
            return true;
        }

        // 3. 点击卡片
        if (handleCardClick(mouseX, mouseY, px, py, pw)) {
            lastClickTime = currentTime;
            return true;
        }

        // 4. 点击面板其他区域取消搜索焦点
        if (isInPanel(mouseX, mouseY, px, py, pw)) {
            if (isSearchBoxFocused) {
                isSearchBoxFocused = false;
                return true;
            }
        }

        return false;
    }

    // ==================== 坐标检测 ====================

    private boolean isInRefreshButton(double mouseX, double mouseY, int px, int py) {
        return mouseX >= px + REFRESH_BTN_X && mouseX <= px + REFRESH_BTN_X + REFRESH_BTN_W &&
                mouseY >= py + REFRESH_BTN_Y && mouseY <= py + REFRESH_BTN_Y + REFRESH_BTN_H;
    }

    private boolean isInSearchBox(double mouseX, double mouseY, int px, int py, int pw) {
        return mouseX >= px + 5 && mouseX <= px + 5 + pw - 10 &&
                mouseY >= py + SEARCH_BOX_Y && mouseY <= py + SEARCH_BOX_Y + SEARCH_BOX_H;
    }

    private boolean isInPanel(double mouseX, double mouseY, int px, int py, int pw) {
        return mouseX >= px && mouseX <= px + pw &&
                mouseY >= py && mouseY <= py + panel.getPanelHeight();
    }

    // ==================== 卡片点击（优化版 - 直接使用 FluidStack） ====================

    private boolean handleCardClick(double mouseX, double mouseY, int px, int py, int pw) {
        if (displayedFluids == null || displayedFluids.isEmpty()) return false;

        // 获取点击到的流体对象
        FluidStack clickedFluid = getFluidFromClick(mouseX, mouseY, px, py, pw);
        if (clickedFluid == null) return false;

        // 获取当前冶炼炉中所有流体的完整列表（用于查找匹配）
        FluidStack matchedFluid = findMatchingFluidInAll(clickedFluid);
        if (matchedFluid == null) {
            System.out.println("Tinker's Search: Fluid not found in allFluids list: " +
                    clickedFluid.getDisplayName().getString());
            return false;
        }

        System.out.println("Tinker's Search: Clicked: " + matchedFluid.getDisplayName().getString() +
                ", amount: " + matchedFluid.getAmount() + " mB");

        // 直接传递 FluidStack 对象给处理器
        boolean success = SmelteryClickHandler.clickFluidByStack(matchedFluid);

        if (!success) {
            // 如果直接传递失败，尝试按名称匹配
            System.out.println("Tinker's Search: Direct click failed, trying fallback...");
            success = SmelteryClickHandler.clickFluidByName(matchedFluid);
        }

        if (success) {
            System.out.println("Tinker's Search: Fluid clicked successfully!");
        } else {
            System.out.println("Tinker's Search: Failed to click fluid!");
        }

        // 执行回调（刷新面板）
        if (onFluidClick != null) {
            onFluidClick.accept(allFluids);
        }

        return true;
    }

    /**
     * 从鼠标位置获取点击到的流体对象（直接从 displayedFluids 中获取）
     */
    private FluidStack getFluidFromClick(double mouseX, double mouseY, int px, int py, int pw) {
        if (displayedFluids == null || displayedFluids.isEmpty()) return null;

        // 计算卡片尺寸
        int cardW = (pw - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
        int cardH = CARD_HEIGHT;

        // 获取滚动偏移
        int scrollOffset = panel.getScrollOffset();
        int startY = py + CARDS_START_Y - scrollOffset;

        for (int i = 0; i < displayedFluids.size(); i++) {
            int row = i / ITEMS_PER_ROW;
            int col = i % ITEMS_PER_ROW;
            int cardX = px + 5 + col * (cardW + CARD_SPACING);
            int cardY = startY + row * (cardH + CARD_SPACING);

            // 检查鼠标是否在卡片区域内
            if (mouseX >= cardX && mouseX <= cardX + cardW &&
                    mouseY >= cardY && mouseY <= cardY + cardH) {
                return displayedFluids.get(i);
            }
        }
        return null;
    }

    /**
     * 在 allFluids 中查找匹配的流体（通过 FluidStack 比较）
     */
    private FluidStack findMatchingFluidInAll(FluidStack targetFluid) {
        if (targetFluid == null || allFluids == null) return null;

        // 优先通过 FluidStack.isFluidEqual 比较（比较流体类型和NBT）
        for (FluidStack fluid : allFluids) {
            if (fluid.isFluidEqual(targetFluid)) {
                return fluid;
            }
        }

        // 如果找不到，尝试通过流体名称匹配（兼容不同模组）
        String targetName = targetFluid.getDisplayName().getString();
        for (FluidStack fluid : allFluids) {
            String fluidName = fluid.getDisplayName().getString();
            if (fluidName.equals(targetName)) {
                return fluid;
            }
        }

        // 最后尝试通过 FluidRegistry 名称匹配
        String targetRegistryName = targetFluid.getFluid().getRegistryName().toString();
        for (FluidStack fluid : allFluids) {
            String registryName = fluid.getFluid().getRegistryName().toString();
            if (registryName.equals(targetRegistryName)) {
                return fluid;
            }
        }

        return null;
    }

    // ==================== 外部控制 ====================

    public void setSearchBoxFocused(boolean focused) {
        this.isSearchBoxFocused = focused;
    }

    public void clearSearch() {
        this.searchKeyword = "";
        this.isSearchBoxFocused = false;
        onRefresh.run();
    }

    public void refreshData() {
        onRefresh.run();
    }
}