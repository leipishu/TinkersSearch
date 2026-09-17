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

    private int alloyScrollOffset = 0;

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
    public int getAlloyScrollOffset() { return alloyScrollOffset; }

    public boolean hasPendingHighlightUpdate() { return pendingHighlightUpdate; }
    public long getPendingHighlightTime() { return pendingHighlightTime; }

    // ==================== Setters ====================

    public void setAlloyScrollOffset(int offset) { this.alloyScrollOffset = offset; }

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

    // ==================== 数据刷新 ====================

    /**
     * 统一刷新所有数据。
     *
     * <p>根据当前 Tab 应用不同的过滤/查询：
     * <ul>
     *   <li>{@link Tab#SMELTERY} — 过滤炉内 + 收藏</li>
     *   <li>{@link Tab#MATERIALS} — 过滤全部材料</li>
     *   <li>{@link Tab#ALLOY} — 用关键字执行合金查询</li>
     * </ul>
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
            // ✅ 1.19.2：通过 ForgeRegistries 获取注册名
            ResourceLocation rl = ForgeRegistries.FLUIDS.getKey(favFluid.getFluid());
            if (rl == null) continue;
            FluidStack matched = null;
            for (FluidStack fs : allFluids) {
                ResourceLocation fsRl = ForgeRegistries.FLUIDS.getKey(fs.getFluid());
                if (fsRl != null && fsRl.equals(rl)) {
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
        allMaterialsScrollOffset = 0;
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
