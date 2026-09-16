package top.leipishu.tinkerssearch.client.gui.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraftforge.fluids.FluidStack;
import top.leipishu.tinkerssearch.alloy.AlloyQueryHandler;
import top.leipishu.tinkerssearch.alloy.AlloyResultCalculator;
import top.leipishu.tinkerssearch.client.gui.FloatingSearchPanel;

import java.util.List;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

/**
 * 面板布局计算器。
 *
 * <p>三个 Tab 共用同一套搜索框位置（{@code SEARCH_BOX_Y}），
 * 内容区起点都是 {@code CARDS_START_Y}。
 */
public class PanelLayoutCalculator {

    private final FloatingSearchPanel panel;
    private final PanelDataManager dataManager;
    private final AlloyQueryHandler alloyHandler;
    private PanelRenderer panelRenderer;

    private int lastScreenWidth = 0;
    private int lastScreenHeight = 0;

    public PanelLayoutCalculator(FloatingSearchPanel panel, PanelDataManager dataManager,
                                 AlloyQueryHandler alloyHandler, PanelRenderer panelRenderer) {
        this.panel = panel;
        this.dataManager = dataManager;
        this.alloyHandler = alloyHandler;
        this.panelRenderer = panelRenderer;
    }

    public void setPanelRenderer(PanelRenderer panelRenderer) {
        this.panelRenderer = panelRenderer;
    }

    // ==================== 冶炼炉 Tab ====================

    /** 收藏区标题行 Y。 */
    public int getFavoriteTitleY() {
        return CARDS_START_Y;
    }

    /** 收藏区内容起始 Y。 */
    public int getFavoriteAreaStartY() {
        return getFavoriteTitleY() + SECTION_LABEL_HEIGHT;
    }

    /** 收藏区内容高度。 */
    public int getFavoriteAreaHeight() {
        if (dataManager.getDisplayedFavoriteFluids().isEmpty()) return 0;
        int totalRows = (dataManager.getDisplayedFavoriteFluids().size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
        int contentHeight = totalRows * (CARD_HEIGHT + CARD_SPACING) - CARD_SPACING;
        return Math.min(contentHeight, (int)(panel.getPanelHeight() * 0.35));
    }

    /** 冶炼炉区标题行 Y。 */
    public int getSmelteryTitleY() {
        int y = getFavoriteAreaStartY() + getFavoriteAreaHeight();
        if (getFavoriteAreaHeight() > 0) y += SECTION_SPACING;
        return y;
    }

    /** 冶炼炉区内容起始 Y。 */
    public int getSmelteryAreaStartY() {
        return getSmelteryTitleY() + SECTION_LABEL_HEIGHT;
    }

    /** 冶炼炉区内容高度（吃满剩余空间）。 */
    public int getSmelteryAreaHeight() {
        int contentY = getSmelteryAreaStartY();
        int panelBottom = panel.getPanelHeight() - 4;
        return Math.max(0, panelBottom - contentY);
    }

    // ==================== 全部材料 Tab ====================

    /** 全部材料区内容起始 Y。 */
    public int getAllMaterialsContentY() {
        return CARDS_START_Y;
    }

    /** 全部材料区内容高度（吃满剩余空间）。 */
    public int getAllMaterialsAreaHeight() {
        int panelBottom = panel.getPanelHeight() - 4;
        return Math.max(0, panelBottom - CARDS_START_Y);
    }

    // ==================== 合金 Tab ====================

    private int calculateCardHeight(AlloyResultCalculator.AlloyChainResult result) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int cardWidth = panel.getPanelWidth() - 12 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING;
        return panelRenderer.calculateActualCardHeight(result, font, cardWidth);
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
            int startY = panel.getPanelY() + CARDS_START_Y + 18;
            int endY = panel.getPanelY() + panel.getPanelHeight() - 4;
            return Math.max(0, endY - startY);
        } else {
            int startY = panel.getPanelY() + CARDS_START_Y + 14 + 12;
            int endY = panel.getPanelY() + panel.getPanelHeight() - 4;
            return Math.max(0, endY - startY);
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