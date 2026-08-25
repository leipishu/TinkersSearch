package top.leipishu.tinkerssearch.client.gui.panel;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fluids.FluidStack;
import top.leipishu.tinkerssearch.alloy.AlloyQueryHandler;
import top.leipishu.tinkerssearch.alloy.AlloyRecipeData;
import top.leipishu.tinkerssearch.alloy.AlloyResultCalculator;
import top.leipishu.tinkerssearch.client.gui.FloatingSearchPanel;

import java.util.List;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

/**
 * 面板布局计算器 - 计算区域位置、卡片尺寸等
 */
public class PanelLayoutCalculator {

    private final FloatingSearchPanel panel;
    private final PanelDataManager dataManager;
    private final AlloyQueryHandler alloyHandler;

    private int lastScreenWidth = 0;
    private int lastScreenHeight = 0;

    public PanelLayoutCalculator(FloatingSearchPanel panel, PanelDataManager dataManager, AlloyQueryHandler alloyHandler) {
        this.panel = panel;
        this.dataManager = dataManager;
        this.alloyHandler = alloyHandler;
    }

    // ==================== 区域位置 ====================

    public int getFavoriteAreaStartY() {
        return CARDS_START_Y + SECTION_LABEL_HEIGHT + TITLE_CARD_SPACING;
    }

    public int getFavoriteAreaHeight() {
        if (dataManager.getDisplayedFavoriteFluids().isEmpty()) return 0;
        int cardW = (panel.getPanelWidth() - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
        int totalRows = (dataManager.getDisplayedFavoriteFluids().size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
        int contentHeight = totalRows * (CARD_HEIGHT + CARD_SPACING) - CARD_SPACING;
        return Math.min(contentHeight, (int)(panel.getPanelHeight() * 0.35));
    }

    public int getSmelteryAreaStartY() {
        int favEndY = panel.getPanelY() + getFavoriteAreaStartY() + getFavoriteAreaHeight();
        if (dataManager.getDisplayedFavoriteFluids().isEmpty()) {
            return panel.getPanelY() + CARDS_START_Y + SECTION_LABEL_HEIGHT + TITLE_CARD_SPACING;
        }
        return favEndY + SECTION_SPACING + SECTION_LABEL_HEIGHT + TITLE_CARD_SPACING;
    }

    // ==================== 合金模式 ====================

    /**
     * 计算单个合金卡片的高度
     */
    private int calculateCardHeight(AlloyResultCalculator.AlloyChainResult result) {
        AlloyRecipeData.AlloyFeasibility feasibility = result.getFeasibility();

        int lineCount = 3; // 状态行 + 配方链行 + 分割线

        List<AlloyRecipeData.AlloyFeasibility.MissingFluid> missing = feasibility.getMissingFluids();
        if (!missing.isEmpty()) {
            lineCount++;
        }
        if (result.getNext() != null) {
            lineCount++;
        }
        if (feasibility.isFeasible() && result.getNext() == null) {
            lineCount++;
        }
        // 原料详情行
        lineCount++;

        return Math.max(72, lineCount * 12 + 16);
    }

    public int getAlloyContentHeight() {
        if (alloyHandler.getSelectedMaterial() == null) {
            List<FluidStack> materials = alloyHandler.getFilteredMaterials();
            if (materials.isEmpty()) return 0;
            int totalRows = (materials.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
            return totalRows * (CARD_HEIGHT + CARD_SPACING) - CARD_SPACING;
        } else {
            List<AlloyResultCalculator.AlloyChainResult> results = alloyHandler.getCurrentResults();
            if (results.isEmpty()) return 0;
            int totalHeight = 0;
            for (AlloyResultCalculator.AlloyChainResult result : results) {
                totalHeight += calculateCardHeight(result) + 6;
            }
            return totalHeight - 6;
        }
    }

    public int getAlloyVisibleHeight() {
        if (alloyHandler.getSelectedMaterial() == null) {
            // 材料列表模式
            int startY = panel.getPanelY() + CARDS_START_Y + 18 + 18 + 4;
            int endY = panel.getPanelY() + panel.getPanelHeight() - 4;
            return Math.max(0, endY - startY);
        } else {
            // 结果模式
            int startY = panel.getPanelY() + CARDS_START_Y + 18 + 14 + 12 + 12;
            int endY = panel.getPanelY() + panel.getPanelHeight() - 4;
            int visible = Math.max(0, endY - startY);
            // ===== 调试日志 =====
            // System.out.println("[Tinker's Search] Alloy visible height: " + visible +
            //         " (startY=" + startY + ", endY=" + endY + ", panelHeight=" + panel.getPanelHeight() + ")");
            return visible;
        }
    }

    // ==================== 面板位置 ====================

    public void updatePanelPosition() {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        panel.setPanelX(0);
        panel.setPanelY(0);
        panel.setPanelWidth(PANEL_WIDTH);
        panel.setPanelHeight(screenHeight);

        this.lastScreenWidth = screenWidth;
        this.lastScreenHeight = screenHeight;

        dataManager.resetScrollOffsets();
        dataManager.updateMaxScrollOffset();
    }

    public boolean checkWindowResize() {
        Minecraft mc = Minecraft.getInstance();
        int currentWidth = mc.getWindow().getGuiScaledWidth();
        int currentHeight = mc.getWindow().getGuiScaledHeight();

        if (currentWidth != lastScreenWidth || currentHeight != lastScreenHeight) {
            updatePanelPosition();
            return true;
        }
        return false;
    }
}