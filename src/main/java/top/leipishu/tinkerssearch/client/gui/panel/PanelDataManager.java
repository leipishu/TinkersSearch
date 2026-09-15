package top.leipishu.tinkerssearch.client.gui.panel;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import top.leipishu.tinkerssearch.alloy.AlloyQueryHandler;
import top.leipishu.tinkerssearch.client.gui.FloatingSearchPanel;
import top.leipishu.tinkerssearch.client.gui.PanelInteractionHandler;
import top.leipishu.tinkerssearch.data.FavoritesManager;
import top.leipishu.tinkerssearch.utils.SearchHelper;
import top.leipishu.tinkerssearch.smeltery.SmelteryDataHelper;

import java.util.ArrayList;
import java.util.List;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

/**
 * 面板数据管理器 - 管理流体列表、收藏、滚动偏移
 */
public class PanelDataManager {

    private final FloatingSearchPanel panel;
    private final PanelInteractionHandler interactionHandler;
    private final AlloyQueryHandler alloyHandler;

    // ===== 数据 =====
    private List<FluidStack> allFluids = new ArrayList<>();
    private List<FluidStack> displayedFluids = new ArrayList<>();

    // ===== 收藏数据 =====
    private List<FluidStack> allFavoriteFluids = new ArrayList<>();
    private List<FluidStack> displayedFavoriteFluids = new ArrayList<>();

    private BlockEntity smelteryTileEntity = null;
    private BlockEntity cachedTileEntity = null;

    // ===== 最下方流体名称（用于高亮） =====
    private String bottomFluidName = null;

    // ===== 滚动 =====
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;

    // ===== 收藏区域滚动 =====
    private int favScrollOffset = 0;
    private int maxFavScrollOffset = 0;

    // ===== 合金查询 =====
    private boolean isAlloyMode = false;
    private int alloyScrollOffset = 0;

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
    public BlockEntity getSmelteryTileEntity() { return smelteryTileEntity; }
    public BlockEntity getCachedTileEntity() { return cachedTileEntity; }
    public String getBottomFluidName() { return bottomFluidName; }
    public void setBottomFluidName(String name) { this.bottomFluidName = name; }
    public int getScrollOffset() { return scrollOffset; }
    public int getMaxScrollOffset() { return maxScrollOffset; }
    public int getFavScrollOffset() { return favScrollOffset; }
    public int getMaxFavScrollOffset() { return maxFavScrollOffset; }
    public int getAlloyScrollOffset() { return alloyScrollOffset; }
    public boolean isAlloyMode() { return isAlloyMode; }
    public boolean hasPendingHighlightUpdate() { return pendingHighlightUpdate; }
    public long getPendingHighlightTime() { return pendingHighlightTime; }

    public void setAlloyScrollOffset(int offset) { this.alloyScrollOffset = offset; }
    public void setAlloyMode(boolean alloyMode) { isAlloyMode = alloyMode; }

    // ==================== Setters ====================

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

    public void resetScrollOffsets() {
        scrollOffset = 0;
        favScrollOffset = 0;
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
        bottomFluidName = null;
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

        if (keyword == null || keyword.trim().isEmpty()) {
            displayedFluids = new ArrayList<>(allFluids);
            displayedFavoriteFluids = new ArrayList<>(allFavoriteFluids);
        } else {
            displayedFluids = SearchHelper.filterFluids(allFluids, keyword);
            displayedFavoriteFluids = SearchHelper.filterFluids(allFavoriteFluids, keyword);
        }

        interactionHandler.setDataRefs(allFluids, displayedFluids);
        updateMaxScrollOffset();
        scrollOffset = 0;
        favScrollOffset = 0;
    }

    public void updateMaxScrollOffset() {
        int panelWidth = panel.getPanelWidth();
        int panelHeight = panel.getPanelHeight();
        int panelY = panel.getPanelY();

        if (displayedFluids.isEmpty()) {
            maxScrollOffset = 0;
        } else {
            int py = panelY;
            int pw = panelWidth;
            int ph = panelHeight;

            int startY = py + panel.getSmelteryAreaStartY();
            int endY = py + ph - 4;

            int cardW = (pw - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
            int cardH = CARD_HEIGHT;

            int totalRows = (displayedFluids.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
            int totalContentHeight = totalRows * (cardH + CARD_SPACING) - CARD_SPACING;
            int availableHeight = endY - startY;

            maxScrollOffset = Math.max(0, totalContentHeight - availableHeight);
            if (scrollOffset > maxScrollOffset) {
                scrollOffset = maxScrollOffset;
            }
        }

        if (displayedFavoriteFluids.isEmpty()) {
            maxFavScrollOffset = 0;
        } else {
            int favStartY = panelY + panel.getFavoriteAreaStartY();
            int favEndY = favStartY + panel.getFavoriteAreaHeight();

            int cardW = (panelWidth - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
            int cardH = CARD_HEIGHT;

            int totalRows = (displayedFavoriteFluids.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
            int totalContentHeight = totalRows * (cardH + CARD_SPACING) - CARD_SPACING;
            int availableHeight = favEndY - favStartY;

            maxFavScrollOffset = Math.max(0, totalContentHeight - availableHeight);
            if (favScrollOffset > maxFavScrollOffset) {
                favScrollOffset = maxFavScrollOffset;
            }
        }
    }
}