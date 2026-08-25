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

    // ===== 这些常量从 PanelConfig 静态导入，不需要再定义 =====
    // SECTION_LABEL_HEIGHT, TITLE_CARD_SPACING, SECTION_SPACING
    // 都已通过 import static top.leipishu.tinkerssearch.config.PanelConfig.* 导入

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

    public int getAlloyContentHeight() {
        if (alloyHandler.getSelectedMaterial() == null) {
            List<FluidStack> materials = alloyHandler.getFilteredMaterials();
            if (materials.isEmpty()) return 0;
            int totalRows = (materials.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
            return totalRows * (CARD_HEIGHT + CARD_SPACING) - CARD_SPACING;
        } else {
            List<AlloyResultCalculator.AlloyChainResult> results = alloyHandler.getCurrentResults();
            if (results.isEmpty()) return 0;
            int cardH = 44;
            for (AlloyResultCalculator.AlloyChainResult result : results) {
                AlloyRecipeData.AlloyFeasibility feasibility = result.getFeasibility();
                if (!feasibility.isFeasible() || result.getNext() != null) {
                    cardH = 54;
                    break;
                }
            }
            return results.size() * (cardH + 6) - 6;
        }
    }

    public int getAlloyVisibleHeight() {
        int startY = panel.getPanelY() + CARDS_START_Y + 18;
        if (alloyHandler.getSelectedMaterial() != null) {
            startY = panel.getPanelY() + CARDS_START_Y + 18 + 14 + 12 + 12;
        } else {
            startY = panel.getPanelY() + CARDS_START_Y + 18 + 14 + 4;
        }
        int endY = panel.getPanelY() + panel.getPanelHeight() - 4;
        return Math.max(0, endY - startY);
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