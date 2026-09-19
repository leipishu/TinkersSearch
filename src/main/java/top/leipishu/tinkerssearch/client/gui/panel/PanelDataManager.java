package top.leipishu.tinkerssearch.client.gui.panel;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
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
 *
 * <p><b>1.20.1 修复</b>：
 * <ul>
 *   <li>滚动快照（save/restore/discard/hasSnapshot）——打开详情窗口返回后恢复位置</li>
 *   <li>合金页拆两个独立滚动偏移（材料列表 / 结果页）</li>
 *   <li>{@code refreshAllMaterials} 不再强制清零 {@code allMaterialsScrollOffset}</li>
 * </ul>
 */
public class PanelDataManager {

    public enum Tab { SMELTERY, MATERIALS, ALLOY }
    public enum AreaKind { SMELTERY, FAVORITE, ALL_MATERIALS }

    private final FloatingSearchPanel panel;
    private final PanelInteractionHandler interactionHandler;
    private final AlloyQueryHandler alloyHandler;

    private Tab currentTab = Tab.SMELTERY;

    private List<FluidStack> allFluids = new ArrayList<>();
    private List<FluidStack> displayedFluids = new ArrayList<>();

    private List<FluidStack> allFavoriteFluids = new ArrayList<>();
    private List<FluidStack> displayedFavoriteFluids = new ArrayList<>();

    private List<FluidStack> allMaterials = new ArrayList<>();
    private List<FluidStack> displayedAllMaterials = new ArrayList<>();

    private BlockEntity smelteryTileEntity = null;
    private BlockEntity cachedTileEntity = null;

    private String bottomFluidName = null;

    private int scrollOffset = 0;
    private int maxScrollOffset = 0;

    private int favScrollOffset = 0;
    private int maxFavScrollOffset = 0;

    private int allMaterialsScrollOffset = 0;
    private int maxAllMaterialsScrollOffset = 0;

    // ★ 合金页拆两个滚动偏移：材料列表 & 结果页，避免互相覆盖
    private int alloyMaterialsScrollOffset = 0;
    private int alloyResultsScrollOffset = 0;

    // ★ 打开 Detail 时的滚动快照
    private int snapshotScrollOffset = -1;
    private int snapshotFavScrollOffset = -1;
    private int snapshotAllMaterialsScrollOffset = -1;
    private int snapshotAlloyMaterialsScrollOffset = -1;
    private int snapshotAlloyResultsScrollOffset = -1;
    private boolean hasSnapshot = false;

    private boolean pendingHighlightUpdate = false;
    private long pendingHighlightTime = 0;

    public PanelDataManager(FloatingSearchPanel panel, PanelInteractionHandler interactionHandler,
                            AlloyQueryHandler alloyHandler) {
        this.panel = panel;
        this.interactionHandler = interactionHandler;
        this.alloyHandler = alloyHandler;
    }

    // ==================== Tab ====================

    public Tab getCurrentTab() { return currentTab; }
    public void setCurrentTab(Tab tab) { if (tab != null) this.currentTab = tab; }
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

    /** ★ 结果页存在时返回结果偏移，否则返回材料列表偏移。 */
    public int getAlloyScrollOffset() {
        if (alloyHandler.getSelectedMaterial() != null) return alloyResultsScrollOffset;
        return alloyMaterialsScrollOffset;
    }

    public int getAlloyMaterialsScrollOffset() { return alloyMaterialsScrollOffset; }
    public int getAlloyResultsScrollOffset() { return alloyResultsScrollOffset; }

    public boolean hasPendingHighlightUpdate() { return pendingHighlightUpdate; }
    public long getPendingHighlightTime() { return pendingHighlightTime; }

    // ==================== Setters ====================

    /** ★ 结果页存在时写入结果偏移，否则写入材料列表偏移。 */
    public void setAlloyScrollOffset(int offset) {
        if (alloyHandler.getSelectedMaterial() != null) {
            alloyResultsScrollOffset = Math.max(0, offset);
        } else {
            alloyMaterialsScrollOffset = Math.max(0, offset);
        }
    }

    public void resetAlloyResultsScrollOffset() { alloyResultsScrollOffset = 0; }
    public void resetAlloyMaterialsScrollOffset() { alloyMaterialsScrollOffset = 0; }

    public void setSmelteryTileEntity(BlockEntity tileEntity) {
        this.smelteryTileEntity = tileEntity;
        this.cachedTileEntity = tileEntity;
    }

    public void setScrollOffset(int offset) { scrollOffset = Math.max(0, Math.min(offset, maxScrollOffset)); }
    public void setFavScrollOffset(int offset) { favScrollOffset = Math.max(0, Math.min(offset, maxFavScrollOffset)); }
    public void setAllMaterialsScrollOffset(int offset) { allMaterialsScrollOffset = Math.max(0, Math.min(offset, maxAllMaterialsScrollOffset)); }

    public void resetScrollOffsets() {
        scrollOffset = 0;
        favScrollOffset = 0;
        allMaterialsScrollOffset = 0;
        alloyMaterialsScrollOffset = 0;
        alloyResultsScrollOffset = 0;
    }

    // ==================== 滚动快照 ====================

    public void saveScrollSnapshot() {
        snapshotScrollOffset = scrollOffset;
        snapshotFavScrollOffset = favScrollOffset;
        snapshotAllMaterialsScrollOffset = allMaterialsScrollOffset;
        snapshotAlloyMaterialsScrollOffset = alloyMaterialsScrollOffset;
        snapshotAlloyResultsScrollOffset = alloyResultsScrollOffset;
        hasSnapshot = true;
    }

    public void restoreScrollSnapshot() {
        if (!hasSnapshot) return;
        scrollOffset = Math.max(0, Math.min(snapshotScrollOffset, maxScrollOffset));
        favScrollOffset = Math.max(0, Math.min(snapshotFavScrollOffset, maxFavScrollOffset));
        allMaterialsScrollOffset = Math.max(0, Math.min(snapshotAllMaterialsScrollOffset, maxAllMaterialsScrollOffset));
        alloyMaterialsScrollOffset = snapshotAlloyMaterialsScrollOffset;
        alloyResultsScrollOffset = snapshotAlloyResultsScrollOffset;
        hasSnapshot = false;
    }

    public boolean hasScrollSnapshot() { return hasSnapshot; }
    public void discardScrollSnapshot() { hasSnapshot = false; }

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
        bottomFluidName = (bottomFluid != null) ? bottomFluid.getDisplayName().getString() : null;
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

    public void refreshMoltenFluids() {
        String keyword = interactionHandler.getSearchKeyword();
        String trimmed = (keyword == null) ? "" : keyword.trim();

        BlockEntity target = smelteryTileEntity != null ? smelteryTileEntity : cachedTileEntity;

        if (target != null) {
            allFluids = SmelteryDataHelper.getMoltenFluids(target);
            FluidStack bottomFluid = SmelteryDataHelper.getBottomFluid(target);
            bottomFluidName = (bottomFluid != null) ? bottomFluid.getDisplayName().getString() : null;
        } else {
            allFluids = new ArrayList<>();
            bottomFluidName = null;
        }

        allFavoriteFluids.clear();
        List<FluidStack> favList = FavoritesManager.getFavorites();
        for (FluidStack favFluid : favList) {
            if (favFluid == null || favFluid.isEmpty()) continue;
            ResourceLocation rl = ForgeRegistries.FLUIDS.getKey(favFluid.getFluid());
            if (rl == null) continue;
            FluidStack matched = null;
            for (FluidStack fs : allFluids) {
                ResourceLocation fsRl = ForgeRegistries.FLUIDS.getKey(fs.getFluid());
                if (fsRl != null && fsRl.equals(rl)) { matched = fs; break; }
            }
            allFavoriteFluids.add(matched != null ? matched.copy() : favFluid.copy());
        }

        if (trimmed.isEmpty()) {
            displayedFluids = new ArrayList<>(allFluids);
            displayedFavoriteFluids = new ArrayList<>(allFavoriteFluids);
        } else {
            displayedFluids = SearchHelper.filterFluids(allFluids, trimmed);
            displayedFavoriteFluids = SearchHelper.filterFluids(allFavoriteFluids, trimmed);
        }

        refreshAllMaterials(trimmed);

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
     * <p>★ 不再重置 {@code allMaterialsScrollOffset}——滚动位置由
     * {@code switchTab} / {@code startHideAnimation} 统一处理。
     */
    public void refreshAllMaterials(String keyword) {
        List<FluidStack> source = null;
        try { source = TinkersAlloyReader.getAllSmelteryFluids(); } catch (Throwable ignored) {}
        if (source == null || source.isEmpty()) {
            TinkersAlloyReader.forceReload();
            try { source = TinkersAlloyReader.getAllSmelteryFluids(); } catch (Throwable ignored) {}
        }
        allMaterials = (source != null) ? source : new ArrayList<>();

        if (keyword == null || keyword.trim().isEmpty()) {
            displayedAllMaterials = new ArrayList<>(allMaterials);
        } else {
            displayedAllMaterials = SearchHelper.filterFluids(allMaterials, keyword);
        }
    }

    public void updateMaxScrollOffset() {
        int panelY = panel.getPanelY();
        if (panelY < Integer.MIN_VALUE) return; // 屏蔽未使用变量警告

        // 收藏
        if (displayedFavoriteFluids.isEmpty()) {
            maxFavScrollOffset = 0;
        } else {
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