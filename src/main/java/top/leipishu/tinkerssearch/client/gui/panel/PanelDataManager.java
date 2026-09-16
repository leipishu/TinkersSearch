package top.leipishu.tinkerssearch.client.gui.panel;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import top.leipishu.tinkerssearch.alloy.AlloyQueryHandler;
import top.leipishu.tinkerssearch.alloy.TinkersAlloyReader;
import top.leipishu.tinkerssearch.client.gui.FloatingSearchPanel;
import top.leipishu.tinkerssearch.client.gui.PanelInteractionHandler;
import top.leipishu.tinkerssearch.data.FavoritesManager;
import top.leipishu.tinkerssearch.utils.SearchHelper;
import top.leipishu.tinkerssearch.smeltery.SmelteryDataHelper;

import java.util.ArrayList;
import java.util.List;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

/**
 * 面板数据管理器 - 管理流体列表、收藏、滚动偏移、折叠状态
 */
public class PanelDataManager {

    /** 卡片所在区域类型，影响显示与交互逻辑。 */
    public enum AreaKind { SMELTERY, FAVORITE, ALL_MATERIALS }

    private final FloatingSearchPanel panel;
    private final PanelInteractionHandler interactionHandler;
    private final AlloyQueryHandler alloyHandler;

    // ===== 数据 =====
    private List<FluidStack> allFluids = new ArrayList<>();
    private List<FluidStack> displayedFluids = new ArrayList<>();

    // ===== 收藏数据 =====
    private List<FluidStack> allFavoriteFluids = new ArrayList<>();
    private List<FluidStack> displayedFavoriteFluids = new ArrayList<>();

    // ===== 全部材料数据 =====
    private List<FluidStack> allMaterials = new ArrayList<>();
    private List<FluidStack> displayedAllMaterials = new ArrayList<>();

    private BlockEntity smelteryTileEntity = null;
    private BlockEntity cachedTileEntity = null;

    // ===== 最下方流体名称（用于高亮） =====
    private String bottomFluidName = null;

    // ===== 滚动 =====
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;

    private int favScrollOffset = 0;
    private int maxFavScrollOffset = 0;

    private int allMaterialsScrollOffset = 0;
    private int maxAllMaterialsScrollOffset = 0;

    // ===== 合金查询 =====
    private boolean isAlloyMode = false;
    private int alloyScrollOffset = 0;

    // ===== 折叠状态 =====
    private boolean favoritesCollapsed = false;
    private boolean smelteryCollapsed = false;
    private boolean allMaterialsCollapsed = true;

    // ===== 移动后刷新标记 =====
    private boolean pendingHighlightUpdate = false;
    private long pendingHighlightTime = 0;

    public PanelDataManager(FloatingSearchPanel panel, PanelInteractionHandler interactionHandler, AlloyQueryHandler alloyHandler) {
        this.panel = panel;
        this.interactionHandler = interactionHandler;
        this.alloyHandler = alloyHandler;
    }

    // ==================== Getters ====================

    public List<FluidStack> getAllFluids() { return allFluids; }
    public List<FluidStack> getDisplayedFluids() { return displayedFluids; }
    public List<FluidStack> getAllFavoriteFluids() { return allFavoriteFluids; }
    public List<FluidStack> getDisplayedFavoriteFluids() { return displayedFavoriteFluids; }
    public List<FluidStack> getAllMaterials() { return allMaterials; }
    public List<FluidStack> getDisplayedAllMaterials() { return displayedAllMaterials; }

    public BlockEntity getSmelteryTileEntity() { return smelteryTileEntity; }
    public BlockEntity getCachedTileEntity() { return cachedTileEntity; }
    public String getBottomFluidName() { return bottomFluidName; }
    public void setBottomFluidName(String name) { this.bottomFluidName = name; }

    public int getScrollOffset() { return scrollOffset; }
    public int getMaxScrollOffset() { return maxScrollOffset; }
    public int getFavScrollOffset() { return favScrollOffset; }
    public int getMaxFavScrollOffset() { return maxFavScrollOffset; }
    public int getAllMaterialsScrollOffset() { return allMaterialsScrollOffset; }
    public int getMaxAllMaterialsScrollOffset() { return maxAllMaterialsScrollOffset; }
    public int getAlloyScrollOffset() { return alloyScrollOffset; }

    public boolean isAlloyMode() { return isAlloyMode; }
    public boolean hasPendingHighlightUpdate() { return pendingHighlightUpdate; }
    public long getPendingHighlightTime() { return pendingHighlightTime; }

    // ===== 折叠状态 =====
    public boolean isFavoritesCollapsed() { return favoritesCollapsed; }
    public boolean isSmelteryCollapsed() { return smelteryCollapsed; }
    public boolean isAllMaterialsCollapsed() { return allMaterialsCollapsed; }

    public void toggleFavoritesCollapsed() { favoritesCollapsed = !favoritesCollapsed; }
    public void toggleSmelteryCollapsed() { smelteryCollapsed = !smelteryCollapsed; }
    public void toggleAllMaterialsCollapsed() { allMaterialsCollapsed = !allMaterialsCollapsed; }

    // ==================== Setters ====================

    public void setAlloyScrollOffset(int offset) { this.alloyScrollOffset = offset; }
    public void setAlloyMode(boolean alloyMode) { isAlloyMode = alloyMode; }

    public void setSmelteryTileEntity(BlockEntity tileEntity) {
        this.smelteryTileEntity = tileEntity;
        this.cachedTileEntity = tileEntity;
    }

    public void setScrollOffset(int offset) {
        scrollOffset = Math.max(0, Math.min(offset, maxScrollOffset));
    }

    public void setFavScrollOffset(int offset) {
        favScrollOffset = Math.max(0, Math.min(offset, maxFavScrollOffset));
    }

    public void setAllMaterialsScrollOffset(int offset) {
        allMaterialsScrollOffset = Math.max(0, Math.min(offset, maxAllMaterialsScrollOffset));
    }

    public void resetScrollOffsets() {
        scrollOffset = 0;
        favScrollOffset = 0;
        allMaterialsScrollOffset = 0;
        alloyScrollOffset = 0;
    }

    public void scheduleHighlightUpdate() {
        pendingHighlightUpdate = true;
        pendingHighlightTime = System.currentTimeMillis();
    }

    public void checkPendingHighlightUpdate() {
        if (!pendingHighlightUpdate) return;

        long elapsed = System.currentTimeMillis() - pendingHighlightTime;
        if (elapsed >= 500) {
            pendingHighlightUpdate = false;
            updateBottomFluidHighlight();
        }
    }

    public void updateBottomFluidHighlight() {
        BlockEntity target = smelteryTileEntity != null ? smelteryTileEntity : cachedTileEntity;
        if (target == null) return;

        FluidStack bottomFluid = SmelteryDataHelper.getBottomFluid(target);
        if (bottomFluid != null) {
            bottomFluidName = bottomFluid.getDisplayName().getString();
        } else {
            bottomFluidName = null;
        }
    }

    public void clearAllData() {
        allFluids.clear();
        displayedFluids.clear();
        allFavoriteFluids.clear();
        displayedFavoriteFluids.clear();
        allMaterials.clear();
        displayedAllMaterials.clear();
        bottomFluidName = null;
    }

    // ==================== 全部材料 ====================

    /**
     * 刷新全部材料列表，数据源参考合金模式（{@link TinkersAlloyReader}）。
     *
     * <p>keyword 会同时作用于该区的显示列表；空字符串表示不过滤。
     */
    public void refreshAllMaterials(String keyword) {
        try {
            allMaterials = TinkersAlloyReader.getAllSmelteryFluids();
        } catch (Throwable t) {
            allMaterials = new ArrayList<>();
        }

        if (keyword == null || keyword.trim().isEmpty()) {
            displayedAllMaterials = new ArrayList<>(allMaterials);
        } else {
            displayedAllMaterials = SearchHelper.filterFluids(allMaterials, keyword);
        }
        allMaterialsScrollOffset = 0;
    }

    // ==================== 核心数据刷新 ====================

    public void refreshMoltenFluids() {
        String keyword = interactionHandler.getSearchKeyword();

        if (keyword != null && keyword.startsWith("/a/")) {
            BlockEntity target = smelteryTileEntity != null ? smelteryTileEntity : cachedTileEntity;
            if (target != null) {
                allFluids = SmelteryDataHelper.getMoltenFluids(target);
            }
            String searchTerm = keyword.substring(3).trim();
            int currentTemp = panel.getCurrentSmelteryTemperature();
            alloyHandler.refreshTemperature(currentTemp);
            alloyHandler.performQuery(searchTerm, allFluids, currentTemp);
            isAlloyMode = true;
            alloyScrollOffset = 0;
            return;
        } else {
            if (isAlloyMode) {
                isAlloyMode = false;
                alloyHandler.exitQueryMode();
                interactionHandler.setSearchKeyword("");
                interactionHandler.setSearchBoxFocused(false);

                BlockEntity target = smelteryTileEntity != null ? smelteryTileEntity : cachedTileEntity;
                if (target != null) {
                    allFluids = SmelteryDataHelper.getMoltenFluids(target);

                    allFavoriteFluids.clear();
                    displayedFavoriteFluids.clear();
                    List<FluidStack> favList = FavoritesManager.getFavorites();
                    for (FluidStack favFluid : favList) {
                        if (favFluid == null || favFluid.isEmpty()) continue;
                        ResourceLocation rl = favFluid.getFluid().getRegistryName();
                        if (rl == null) continue;
                        FluidStack matched = null;
                        for (FluidStack fs : allFluids) {
                            if (fs.getFluid().getRegistryName().equals(rl)) {
                                matched = fs;
                                break;
                            }
                        }
                        if (matched != null) {
                            allFavoriteFluids.add(matched.copy());
                        } else {
                            allFavoriteFluids.add(favFluid.copy());
                        }
                    }

                    FluidStack bottomFluid = SmelteryDataHelper.getBottomFluid(target);
                    if (bottomFluid != null) {
                        bottomFluidName = bottomFluid.getDisplayName().getString();
                    } else {
                        bottomFluidName = null;
                    }

                    displayedFluids = new ArrayList<>(allFluids);
                    displayedFavoriteFluids = new ArrayList<>(allFavoriteFluids);
                    refreshAllMaterials("");

                    interactionHandler.setDataRefs(allFluids, displayedFluids);
                    updateMaxScrollOffset();
                    scrollOffset = 0;
                    favScrollOffset = 0;
                    return;
                }
            }
        }

        allFluids.clear();
        displayedFluids.clear();
        allFavoriteFluids.clear();
        displayedFavoriteFluids.clear();
        bottomFluidName = null;

        BlockEntity target = smelteryTileEntity != null ? smelteryTileEntity : cachedTileEntity;
        if (target == null) return;

        allFluids = SmelteryDataHelper.getMoltenFluids(target);

        FluidStack bottomFluid = SmelteryDataHelper.getBottomFluid(target);
        if (bottomFluid != null) {
            bottomFluidName = bottomFluid.getDisplayName().getString();
        }

        List<FluidStack> favList = FavoritesManager.getFavorites();
        for (FluidStack favFluid : favList) {
            if (favFluid == null || favFluid.isEmpty()) continue;
            ResourceLocation rl = favFluid.getFluid().getRegistryName();
            if (rl == null) continue;
            FluidStack matched = null;
            for (FluidStack fs : allFluids) {
                if (fs.getFluid().getRegistryName().equals(rl)) {
                    matched = fs;
                    break;
                }
            }
            if (matched != null) {
                allFavoriteFluids.add(matched.copy());
            } else {
                allFavoriteFluids.add(favFluid.copy());
            }
        }

        // ===== 三区同步过滤 =====
        String trimmed = (keyword == null) ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            displayedFluids = new ArrayList<>(allFluids);
            displayedFavoriteFluids = new ArrayList<>(allFavoriteFluids);
        } else {
            displayedFluids = SearchHelper.filterFluids(allFluids, trimmed);
            displayedFavoriteFluids = SearchHelper.filterFluids(allFavoriteFluids, trimmed);
        }

        refreshAllMaterials(trimmed);

        interactionHandler.setDataRefs(allFluids, displayedFluids);
        updateMaxScrollOffset();
        scrollOffset = 0;
        favScrollOffset = 0;
    }

    public void updateMaxScrollOffset() {
        int panelWidth = panel.getPanelWidth();
        int panelHeight = panel.getPanelHeight();
        int panelY = panel.getPanelY();

        // 冶炼炉内容
        if (displayedFluids.isEmpty() || smelteryCollapsed) {
            maxScrollOffset = 0;
        } else {
            int startY = panelY + panel.getSmelteryAreaStartY();
            int height = panel.getSmelteryAreaHeight();
            int endY = startY + height;

            int cardW = (panelWidth - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
            int cardH = CARD_HEIGHT;

            int totalRows = (displayedFluids.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
            int totalContentHeight = totalRows * (cardH + CARD_SPACING) - CARD_SPACING;
            int availableHeight = endY - startY;

            maxScrollOffset = Math.max(0, totalContentHeight - availableHeight);
            if (scrollOffset > maxScrollOffset) {
                scrollOffset = maxScrollOffset;
            }
        }

        // 收藏内容
        if (displayedFavoriteFluids.isEmpty() || favoritesCollapsed) {
            maxFavScrollOffset = 0;
        } else {
            int favStartY = panelY + panel.getFavoriteAreaStartY();
            int favHeight = panel.getFavoriteAreaHeight();

            int cardW = (panelWidth - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
            int cardH = CARD_HEIGHT;

            int totalRows = (displayedFavoriteFluids.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
            int totalContentHeight = totalRows * (cardH + CARD_SPACING) - CARD_SPACING;

            maxFavScrollOffset = Math.max(0, totalContentHeight - favHeight);
            if (favScrollOffset > maxFavScrollOffset) {
                favScrollOffset = maxFavScrollOffset;
            }
        }

        // 全部材料内容
        if (displayedAllMaterials.isEmpty() || allMaterialsCollapsed) {
            maxAllMaterialsScrollOffset = 0;
        } else {
            int startY = panelY + panel.getAllMaterialsContentY();
            int height = panel.getAllMaterialsAreaHeight();

            int cardH = CARD_HEIGHT;
            int totalRows = (displayedAllMaterials.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
            int totalContentHeight = totalRows * (cardH + CARD_SPACING) - CARD_SPACING;

            maxAllMaterialsScrollOffset = Math.max(0, totalContentHeight - height);
            if (allMaterialsScrollOffset > maxAllMaterialsScrollOffset) {
                allMaterialsScrollOffset = maxAllMaterialsScrollOffset;
            }
        }
    }
}