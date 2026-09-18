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
 * 面板数据管理器 - 管理 Tab 状态、流体列表、收藏、滚动偏移。
 */
public class PanelDataManager {

    /** Tab 页。 */
    public enum Tab { SMELTERY, MATERIALS, ALLOY }

    /** 卡片所在区域类型，影响显示与交互逻辑。 */
    public enum AreaKind { SMELTERY, FAVORITE, ALL_MATERIALS }

    private final FloatingSearchPanel panel;
    private final PanelInteractionHandler interactionHandler;
    private final AlloyQueryHandler alloyHandler;

    // ===== 当前 Tab =====
    private Tab currentTab = Tab.SMELTERY;

    // ===== 数据 =====
    private List<FluidStack> allFluids = new ArrayList<>();
    private List<FluidStack> displayedFluids = new ArrayList<>();

    private List<FluidStack> allFavoriteFluids = new ArrayList<>();
    private List<FluidStack> displayedFavoriteFluids = new ArrayList<>();

    private List<FluidStack> allMaterials = new ArrayList<>();
    private List<FluidStack> displayedAllMaterials = new ArrayList<>();

    private BlockEntity smelteryTileEntity = null;
    private BlockEntity cachedTileEntity = null;

    private String bottomFluidName = null;

    // ===== 滚动 =====
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;

    private int favScrollOffset = 0;
    private int maxFavScrollOffset = 0;

    private int allMaterialsScrollOffset = 0;
    private int maxAllMaterialsScrollOffset = 0;

    // ★ 合金页拆成两个独立的滚动偏移：
    //   材料列表页 & 结果页，避免切换时相互覆盖。
    private int alloyMaterialsScrollOffset = 0;
    private int alloyResultsScrollOffset = 0;

    // ===== 打开 Detail 时的滚动快照 =====
    private int snapshotScrollOffset = -1;
    private int snapshotFavScrollOffset = -1;
    private int snapshotAllMaterialsScrollOffset = -1;
    private int snapshotAlloyMaterialsScrollOffset = -1;
    private int snapshotAlloyResultsScrollOffset = -1;
    private boolean hasSnapshot = false;

    // ===== 移动后刷新标记 =====
    private boolean pendingHighlightUpdate = false;
    private long pendingHighlightTime = 0;

    public PanelDataManager(FloatingSearchPanel panel, PanelInteractionHandler interactionHandler, AlloyQueryHandler alloyHandler) {
        this.panel = panel;
        this.interactionHandler = interactionHandler;
        this.alloyHandler = alloyHandler;
    }

    // ==================== Tab ====================

    public Tab getCurrentTab() { return currentTab; }

    public void setCurrentTab(Tab tab) {
        if (tab == null) return;
        this.currentTab = tab;
    }

    public boolean isAlloyMode() { return currentTab == Tab.ALLOY; }

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

    /**
     * 获取当前活动的合金滚动偏移。
     * ★ 结果页存在时返回结果偏移，否则返回材料列表偏移。
     */
    public int getAlloyScrollOffset() {
        if (alloyHandler.getSelectedMaterial() != null) {
            return alloyResultsScrollOffset;
        }
        return alloyMaterialsScrollOffset;
    }

    public int getAlloyMaterialsScrollOffset() { return alloyMaterialsScrollOffset; }
    public int getAlloyResultsScrollOffset() { return alloyResultsScrollOffset; }

    public boolean hasPendingHighlightUpdate() { return pendingHighlightUpdate; }
    public long getPendingHighlightTime() { return pendingHighlightTime; }

    // ==================== Setters ====================

    /**
     * 写入当前活动的合金滚动偏移。
     * ★ 结果页存在时写入结果偏移，否则写入材料列表偏移。
     */
    public void setAlloyScrollOffset(int offset) {
        if (alloyHandler.getSelectedMaterial() != null) {
            alloyResultsScrollOffset = Math.max(0, offset);
        } else {
            alloyMaterialsScrollOffset = Math.max(0, offset);
        }
    }

    /** 重置结果页滚动偏移（选中新材料时调用）。 */
    public void resetAlloyResultsScrollOffset() {
        alloyResultsScrollOffset = 0;
    }

    /** 重置材料列表滚动偏移。 */
    public void resetAlloyMaterialsScrollOffset() {
        alloyMaterialsScrollOffset = 0;
    }

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
        alloyMaterialsScrollOffset = 0;
        alloyResultsScrollOffset = 0;
    }

    // ==================== 滚动快照 ====================

    /**
     * 保存当前所有滚动位置。
     * 打开详情界面时调用，关闭后恢复。
     */
    public void saveScrollSnapshot() {
        snapshotScrollOffset = scrollOffset;
        snapshotFavScrollOffset = favScrollOffset;
        snapshotAllMaterialsScrollOffset = allMaterialsScrollOffset;
        snapshotAlloyMaterialsScrollOffset = alloyMaterialsScrollOffset;
        snapshotAlloyResultsScrollOffset = alloyResultsScrollOffset;
        hasSnapshot = true;
    }

    /**
     * 恢复之前保存的滚动位置。
     * 越界值会被 clamp 到 max。
     */
    public void restoreScrollSnapshot() {
        if (!hasSnapshot) return;
        scrollOffset = Math.max(0, Math.min(snapshotScrollOffset, maxScrollOffset));
        favScrollOffset = Math.max(0, Math.min(snapshotFavScrollOffset, maxFavScrollOffset));
        allMaterialsScrollOffset = Math.max(0, Math.min(snapshotAllMaterialsScrollOffset, maxAllMaterialsScrollOffset));
        alloyMaterialsScrollOffset = snapshotAlloyMaterialsScrollOffset;
        alloyResultsScrollOffset = snapshotAlloyResultsScrollOffset;
        hasSnapshot = false;
    }

    public boolean hasScrollSnapshot() {
        return hasSnapshot;
    }

    public void discardScrollSnapshot() {
        hasSnapshot = false;
    }

    // ==================== 高亮 ====================

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

    // ==================== 数据刷新 ====================

    /**
     * 统一刷新所有数据。
     */
    public void refreshMoltenFluids() {
        String keyword = interactionHandler.getSearchKeyword();
        String trimmed = (keyword == null) ? "" : keyword.trim();

        BlockEntity target = smelteryTileEntity != null ? smelteryTileEntity : cachedTileEntity;

        // ===== 炉内流体（总是刷新）=====
        if (target != null) {
            allFluids = SmelteryDataHelper.getMoltenFluids(target);
            FluidStack bottomFluid = SmelteryDataHelper.getBottomFluid(target);
            bottomFluidName = (bottomFluid != null) ? bottomFluid.getDisplayName().getString() : null;
        } else {
            allFluids = new ArrayList<>();
            bottomFluidName = null;
        }

        // ===== 收藏（总是刷新）=====
        allFavoriteFluids.clear();
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

        // ===== 应用过滤（冶炼炉 + 收藏）=====
        if (trimmed.isEmpty()) {
            displayedFluids = new ArrayList<>(allFluids);
            displayedFavoriteFluids = new ArrayList<>(allFavoriteFluids);
        } else {
            displayedFluids = SearchHelper.filterFluids(allFluids, trimmed);
            displayedFavoriteFluids = SearchHelper.filterFluids(allFavoriteFluids, trimmed);
        }

        // ===== 全部材料（总是刷新）=====
        refreshAllMaterials(trimmed);

        // ===== 合金 Tab：执行查询 =====
        if (currentTab == Tab.ALLOY) {
            int currentTemp = panel.getCurrentSmelteryTemperature();
            alloyHandler.refreshTemperature(currentTemp);
            alloyHandler.performQuery(trimmed, allFluids, currentTemp);
        }

        interactionHandler.setDataRefs(allFluids, displayedFluids);
        updateMaxScrollOffset();
    }

    /**
     * 刷新全部材料列表。
     *
     * <p>★ 不再重置 {@code allMaterialsScrollOffset}。
     * 滚动重置由 {@code switchTab} / {@code startHideAnimation} 统一处理。
     */
    public void refreshAllMaterials(String keyword) {
        List<FluidStack> source = null;
        try {
            source = TinkersAlloyReader.getAllSmelteryFluids();
        } catch (Throwable t) {
            source = null;
        }
        if (source == null || source.isEmpty()) {
            TinkersAlloyReader.forceReload();
            try {
                source = TinkersAlloyReader.getAllSmelteryFluids();
            } catch (Throwable t) {
                source = null;
            }
        }
        allMaterials = (source != null) ? source : new ArrayList<>();

        if (keyword == null || keyword.trim().isEmpty()) {
            displayedAllMaterials = new ArrayList<>(allMaterials);
        } else {
            displayedAllMaterials = SearchHelper.filterFluids(allMaterials, keyword);
        }
    }

    public void updateMaxScrollOffset() {
        int panelWidth = panel.getPanelWidth();
        int panelHeight = panel.getPanelHeight();
        int panelY = panel.getPanelY();

        // 收藏
        if (displayedFavoriteFluids.isEmpty()) {
            maxFavScrollOffset = 0;
        } else {
            int favStartY = panelY + panel.getFavoriteAreaStartY();
            int favHeight = panel.getFavoriteAreaHeight();

            int totalRows = (displayedFavoriteFluids.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
            int totalContentHeight = totalRows * (CARD_HEIGHT + CARD_SPACING) - CARD_SPACING;

            maxFavScrollOffset = Math.max(0, totalContentHeight - favHeight);
            if (favScrollOffset > maxFavScrollOffset) favScrollOffset = maxFavScrollOffset;
        }

        // 冶炼炉
        if (displayedFluids.isEmpty()) {
            maxScrollOffset = 0;
        } else {
            int startY = panelY + panel.getSmelteryAreaStartY();
            int height = panel.getSmelteryAreaHeight();

            int totalRows = (displayedFluids.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
            int totalContentHeight = totalRows * (CARD_HEIGHT + CARD_SPACING) - CARD_SPACING;

            maxScrollOffset = Math.max(0, totalContentHeight - height);
            if (scrollOffset > maxScrollOffset) scrollOffset = maxScrollOffset;
        }

        // 全部材料
        if (displayedAllMaterials.isEmpty()) {
            maxAllMaterialsScrollOffset = 0;
        } else {
            int startY = panelY + panel.getAllMaterialsContentY();
            int height = panel.getAllMaterialsAreaHeight();

            int totalRows = (displayedAllMaterials.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
            int totalContentHeight = totalRows * (CARD_HEIGHT + CARD_SPACING) - CARD_SPACING;

            maxAllMaterialsScrollOffset = Math.max(0, totalContentHeight - height);
            if (allMaterialsScrollOffset > maxAllMaterialsScrollOffset) {
                allMaterialsScrollOffset = maxAllMaterialsScrollOffset;
            }
        }
    }
}