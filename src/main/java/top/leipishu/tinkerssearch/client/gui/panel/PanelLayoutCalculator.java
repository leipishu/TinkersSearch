package top.leipishu.tinkerssearch.client.gui.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraftforge.fluids.FluidStack;
import top.leipishu.tinkerssearch.alloy.AlloyQueryHandler;
import top.leipishu.tinkerssearch.alloy.AlloyRecipeData;
import top.leipishu.tinkerssearch.alloy.AlloyResultCalculator;
import top.leipishu.tinkerssearch.client.gui.FloatingSearchPanel;
import java.util.ArrayList;
import java.util.List;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

/**
 * 面板布局计算器。
 *
 * <p>面板从上到下依次为：
 * <ol>
 *   <li>标题栏 + 搜索框（固定）</li>
 *   <li>收藏区：标题行 + 内容（折叠时内容高度为 0）</li>
 *   <li>冶炼炉区：标题行 + 内容</li>
 *   <li>全部材料区：标题行 + 内容（默认折叠）</li>
 * </ol>
 *
 * <p>布局规则：
 * <ul>
 *   <li>全部材料块高度由自身内容决定，不随冶炼炉变化</li>
 *   <li>冶炼炉高度 = 面板底部 - 冶炼炉内容起点 - 全部材料块 - 间距（若折叠则为 0）</li>
 *   <li>全部材料标题 Y = 冶炼炉内容起点 + 冶炼炉高度 + 间距（紧跟在冶炼炉之后）</li>
 * </ul>
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

    // ==================== 收藏区 ====================

    /** 收藏区标题行 Y（相对面板左上角）。 */
    public int getFavoriteTitleY() {
        return CARDS_START_Y;
    }

    /** 收藏区内容起始 Y。 */
    public int getFavoriteAreaStartY() {
        return getFavoriteTitleY() + SECTION_LABEL_HEIGHT;
    }

    /** 收藏区内容高度（折叠时为 0）。 */
    public int getFavoriteAreaHeight() {
        if (dataManager.isFavoritesCollapsed()) return 0;
        if (dataManager.getDisplayedFavoriteFluids().isEmpty()) return 0;
        int totalRows = (dataManager.getDisplayedFavoriteFluids().size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
        int contentHeight = totalRows * (CARD_HEIGHT + CARD_SPACING) - CARD_SPACING;
        return Math.min(contentHeight, (int)(panel.getPanelHeight() * 0.35));
    }

    // ==================== 冶炼炉区 ====================

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

    /**
     * 冶炼炉区内容高度。
     *
     * <p>计算方式：面板底部 - 冶炼炉内容起点 - 全部材料块高度 - 间距。
     * 折叠时返回 0。
     */
    public int getSmelteryAreaHeight() {
        if (dataManager.isSmelteryCollapsed()) return 0;

        int contentY = getSmelteryAreaStartY();
        int allMatBlock = getAllMaterialsBlockHeight();
        int panelBottom = panel.getPanelHeight() - 4;
        int h = panelBottom - contentY - allMatBlock - SECTION_SPACING;
        return Math.max(0, h);
    }

    // ==================== 全部材料区 ====================

    /** 全部材料区内容高度（折叠时为 0）。 */
    public int getAllMaterialsAreaHeight() {
        if (dataManager.isAllMaterialsCollapsed()) return 0;
        if (dataManager.getDisplayedAllMaterials().isEmpty()) return 0;
        int totalRows = (dataManager.getDisplayedAllMaterials().size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
        int contentHeight = totalRows * (CARD_HEIGHT + CARD_SPACING) - CARD_SPACING;
        return Math.min(contentHeight, (int)(panel.getPanelHeight() * 0.35));
    }

    /** 全部材料块（标题行 + 内容）的总高度。 */
    public int getAllMaterialsBlockHeight() {
        return SECTION_LABEL_HEIGHT + getAllMaterialsAreaHeight();
    }

    /**
     * 全部材料区标题行 Y。
     *
     * <p>紧跟在冶炼炉内容之后（间距 = {@code SECTION_SPACING}）。
     * 这样无论冶炼炉是否折叠，全部材料都位于其正下方，不会覆盖冶炼炉。
     */
    public int getAllMaterialsTitleY() {
        int smelteryBottom = getSmelteryAreaStartY() + getSmelteryAreaHeight();
        return smelteryBottom + SECTION_SPACING;
    }

    /** 全部材料区内容起始 Y。 */
    public int getAllMaterialsContentY() {
        return getAllMaterialsTitleY() + SECTION_LABEL_HEIGHT;
    }

    // ==================== 合金模式 ====================

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
            int startY = panel.getPanelY() + CARDS_START_Y + 18 + 18 + 4;
            int endY = panel.getPanelY() + panel.getPanelHeight() - 4;
            return Math.max(0, endY - startY);
        } else {
            int startY = panel.getPanelY() + CARDS_START_Y + 18 + 14 + 12 + 12;
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