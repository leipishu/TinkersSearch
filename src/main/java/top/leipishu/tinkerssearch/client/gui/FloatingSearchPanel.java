package top.leipishu.tinkerssearch.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;
import top.leipishu.tinkerssearch.alloy.AlloyQueryHandler;
import top.leipishu.tinkerssearch.client.gui.panel.PanelAnimationManager;
import top.leipishu.tinkerssearch.client.gui.panel.PanelDataManager;
import top.leipishu.tinkerssearch.client.gui.panel.PanelDataManager.AreaKind;
import top.leipishu.tinkerssearch.client.gui.panel.PanelDataManager.Tab;
import top.leipishu.tinkerssearch.client.gui.panel.PanelLayoutCalculator;
import top.leipishu.tinkerssearch.client.gui.panel.PanelRenderer;
import top.leipishu.tinkerssearch.config.PanelConfig;
import top.leipishu.tinkerssearch.smeltery.SmelteryTemperatureReader;
import top.leipishu.tinkerssearch.data.FavoritesManager;
import top.leipishu.tinkerssearch.smeltery.SmelteryClickHandler;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import slimeknights.tconstruct.smeltery.block.entity.controller.SmelteryBlockEntity;

import java.util.List;

import static top.leipishu.tinkerssearch.config.PanelConfig.PANEL_WIDTH;

public class FloatingSearchPanel extends AbstractWidget {

    public static final int TAB_BUTTON_WIDTH = 14;
    public static final int TAB_BUTTON_HEIGHT = 30;

    private boolean isVisible = false;
    private boolean isActuallyVisible = false;

    private final PanelDataManager dataManager;
    private final PanelAnimationManager animationManager;
    private final PanelLayoutCalculator layoutCalculator;
    private final PanelRenderer renderer;
    private final SmelteryTemperatureReader temperatureReader;
    private final PanelInteractionHandler interactionHandler;
    private final AlloyQueryHandler alloyHandler;

    private boolean jeiAvailable = false;

    public FloatingSearchPanel() {
        super(0, 0, PANEL_WIDTH, 100, new TextComponent("Search Panel"));
        this.visible = false;
        this.isVisible = false;

        this.jeiAvailable = ModList.get().isLoaded("jei");
        this.alloyHandler = AlloyQueryHandler.getInstance();

        this.interactionHandler = new PanelInteractionHandler(
                this,
                this::refreshMoltenFluids,
                this::onFluidClicked
        );

        this.dataManager = new PanelDataManager(this, interactionHandler, alloyHandler);
        this.temperatureReader = new SmelteryTemperatureReader();

        this.layoutCalculator = new PanelLayoutCalculator(this, dataManager, alloyHandler, null);
        this.animationManager = new PanelAnimationManager(this, interactionHandler, alloyHandler, dataManager);

        this.renderer = new PanelRenderer(this, dataManager, layoutCalculator, animationManager,
                interactionHandler, alloyHandler);

        this.layoutCalculator.setPanelRenderer(renderer);

        interactionHandler.setDataRefs(dataManager.getAllFluids(), dataManager.getDisplayedFluids());

        updatePanelPosition();
    }

    // ==================== Getters ====================

    public int getPanelX() { return this.x + animationManager.getAnimationOffset(); }
    public int getPanelY() { return this.y; }
    public int getPanelWidth() { return this.width; }
    public int getPanelHeight() { return this.height; }
    public boolean isVisible() { return isVisible; }
    public boolean isActuallyVisible() { return isActuallyVisible; }
    public boolean isAnimating() { return animationManager.isAnimating(); }
    public int getAnimationOffset() { return animationManager.getAnimationOffset(); }
    public int getActualPanelX() { return this.x + animationManager.getAnimationOffset(); }
    public boolean isJeiAvailable() { return jeiAvailable; }
    public boolean isAlloyMode() { return dataManager.isAlloyMode(); }
    public PanelInteractionHandler getInteractionHandler() { return interactionHandler; }

    public boolean isExpanded() { return isVisible || isAnimating(); }

    public int[] getTabButtonPosition() {
        boolean expanded = isExpanded();
        int panelX = getPanelX();
        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();

        int btnX, btnY;
        if (expanded) btnX = panelX + panelWidth - 1;
        else btnX = 0;
        btnY = (panelHeight - TAB_BUTTON_HEIGHT) / 2;

        if (btnX < 0) btnX = 0;
        return new int[]{btnX, btnY};
    }

    // ===== 数据委托 =====
    public List<FluidStack> getAllFluids() { return dataManager.getAllFluids(); }
    public List<FluidStack> getDisplayedFluids() { return dataManager.getDisplayedFluids(); }
    public List<FluidStack> getAllFavoriteFluids() { return dataManager.getAllFavoriteFluids(); }
    public List<FluidStack> getDisplayedFavoriteFluids() { return dataManager.getDisplayedFavoriteFluids(); }
    public List<FluidStack> getDisplayedAllMaterials() { return dataManager.getDisplayedAllMaterials(); }
    public String getBottomFluidName() { return dataManager.getBottomFluidName(); }
    public int getScrollOffset() { return dataManager.getScrollOffset(); }
    public int getMaxScrollOffset() { return dataManager.getMaxScrollOffset(); }
    public int getFavScrollOffset() { return dataManager.getFavScrollOffset(); }
    public int getMaxFavScrollOffset() { return dataManager.getMaxFavScrollOffset(); }
    public int getAllMaterialsScrollOffset() { return dataManager.getAllMaterialsScrollOffset(); }
    public int getMaxAllMaterialsScrollOffset() { return dataManager.getMaxAllMaterialsScrollOffset(); }
    public int getAlloyScrollOffset() { return dataManager.getAlloyScrollOffset(); }

    // ===== 布局委托 =====
    public int getFavoriteAreaStartY() { return layoutCalculator.getFavoriteAreaStartY(); }
    public int getFavoriteAreaHeight() { return layoutCalculator.getFavoriteAreaHeight(); }
    public int getSmelteryAreaStartY() { return layoutCalculator.getSmelteryAreaStartY(); }
    public int getSmelteryAreaHeight() { return layoutCalculator.getSmelteryAreaHeight(); }
    public int getAllMaterialsContentY() { return layoutCalculator.getAllMaterialsContentY(); }
    public int getAllMaterialsAreaHeight() { return layoutCalculator.getAllMaterialsAreaHeight(); }

    // ===== Setters =====
    public void setPanelX(int x) { this.x = x; }
    public void setPanelY(int y) { this.y = y; }
    public void setPanelWidth(int width) { this.width = width; }
    public void setPanelHeight(int height) { this.height = height; }
    public void setVisibleInternal(boolean visible) {
        this.isVisible = visible;
        this.visible = visible;
    }
    public void setActuallyVisible(boolean actuallyVisible) { this.isActuallyVisible = actuallyVisible; }
    public void setScrollOffset(int offset) { dataManager.setScrollOffset(offset); }
    public void setFavScrollOffset(int offset) { dataManager.setFavScrollOffset(offset); }
    public void setAllMaterialsScrollOffset(int offset) { dataManager.setAllMaterialsScrollOffset(offset); }
    public void setAlloyScrollOffset(int offset) { dataManager.setAlloyScrollOffset(offset); }

    // ==================== 布局方法 ====================

    public void updatePanelPosition() { layoutCalculator.updatePanelPosition(); }
    public void forceUpdatePosition() { layoutCalculator.updatePanelPosition(); }

    // ==================== 碰撞检测 ====================

    public boolean isPointInsidePanel(double mouseX, double mouseY) {
        if (!isVisible && !isAnimating()) return false;
        int actualX = getPanelX();
        return mouseX >= actualX && mouseX <= actualX + this.width &&
                mouseY >= this.y && mouseY <= this.y + this.height;
    }

    public FluidStack getFluidAt(double mouseX, double mouseY) {
        if (!isVisible && !isAnimating()) return null;
        if (dataManager.isAlloyMode()) return getAlloyFluidAt(mouseX, mouseY);

        int px = getPanelX();
        int py = this.y;
        int pw = this.width;

        int cardW = (pw - 10 - PanelConfig.CARD_SPACING - PanelConfig.SCROLL_BAR_WIDTH - PanelConfig.SCROLL_BAR_PADDING) / PanelConfig.ITEMS_PER_ROW;
        int cardH = PanelConfig.CARD_HEIGHT;

        Tab tab = dataManager.getCurrentTab();

        if (tab == Tab.MATERIALS) {
            int startY = py + layoutCalculator.getAllMaterialsContentY();
            for (int i = 0; i < dataManager.getDisplayedAllMaterials().size(); i++) {
                int row = i / PanelConfig.ITEMS_PER_ROW;
                int col = i % PanelConfig.ITEMS_PER_ROW;
                int cardX = px + 5 + col * (cardW + PanelConfig.CARD_SPACING);
                int cardY = startY - dataManager.getAllMaterialsScrollOffset() + row * (cardH + PanelConfig.CARD_SPACING);
                if (mouseX >= cardX && mouseX <= cardX + cardW &&
                        mouseY >= cardY && mouseY <= cardY + cardH) {
                    return dataManager.getDisplayedAllMaterials().get(i);
                }
            }
            return null;
        }

        // SMELTERY Tab
        int favStartY = py + layoutCalculator.getFavoriteAreaStartY();
        for (int i = 0; i < dataManager.getDisplayedFavoriteFluids().size(); i++) {
            int row = i / PanelConfig.ITEMS_PER_ROW;
            int col = i % PanelConfig.ITEMS_PER_ROW;
            int cardX = px + 5 + col * (cardW + PanelConfig.CARD_SPACING);
            int cardY = favStartY - dataManager.getFavScrollOffset() + row * (cardH + PanelConfig.CARD_SPACING);
            if (mouseX >= cardX && mouseX <= cardX + cardW &&
                    mouseY >= cardY && mouseY <= cardY + cardH) {
                return dataManager.getDisplayedFavoriteFluids().get(i);
            }
        }

        int smelteryStartY = py + layoutCalculator.getSmelteryAreaStartY();
        for (int i = 0; i < dataManager.getDisplayedFluids().size(); i++) {
            int row = i / PanelConfig.ITEMS_PER_ROW;
            int col = i % PanelConfig.ITEMS_PER_ROW;
            int cardX = px + 5 + col * (cardW + PanelConfig.CARD_SPACING);
            int cardY = smelteryStartY - dataManager.getScrollOffset() + row * (cardH + PanelConfig.CARD_SPACING);
            if (mouseX >= cardX && mouseX <= cardX + cardW &&
                    mouseY >= cardY && mouseY <= cardY + cardH) {
                return dataManager.getDisplayedFluids().get(i);
            }
        }

        return null;
    }

    public boolean isTabButtonClicked(double mouseX, double mouseY) {
        int[] pos = getTabButtonPosition();
        return mouseX >= pos[0] && mouseX <= pos[0] + TAB_BUTTON_WIDTH &&
                mouseY >= pos[1] && mouseY <= pos[1] + TAB_BUTTON_HEIGHT;
    }

    // ==================== 可见性控制 ====================

    public void setVisible(boolean visible) {
        if (this.isVisible == visible && !isAnimating()) return;
        if (!visible && !this.isVisible && !isAnimating()) return;

        if (!visible) {
            animationManager.startHideAnimation();
        } else {
            refreshSmelteryEntity();
            animationManager.startShowAnimation();
        }
    }

    private void refreshSmelteryEntity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return;

        String[] fieldNames = {"te", "tileEntity", "blockEntity", "smeltery", "tile"};
        for (String name : fieldNames) {
            try {
                java.lang.reflect.Field field = mc.screen.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object obj = field.get(mc.screen);
                if (obj instanceof BlockEntity) {
                    dataManager.setSmelteryTileEntity((BlockEntity) obj);
                    break;
                }
            } catch (Exception ignored) {}
        }
    }

    public void toggleVisibility() { setVisible(!this.isVisible); }

    // ==================== 温度 ====================

    public int getCurrentSmelteryTemperature() { return temperatureReader.getCurrentSmelteryTemperature(); }
    public int forceRefreshTemperature() { return temperatureReader.forceRefreshTemperature(); }
    public void refreshTemperature() { forceRefreshTemperature(); }

    // ==================== 数据刷新 ====================

    public void setSmelteryBlockEntity(BlockEntity tileEntity) {
        dataManager.setSmelteryTileEntity(tileEntity);
        if (tileEntity != null) refreshMoltenFluids();
    }

    public void refreshMoltenFluids() { dataManager.refreshMoltenFluids(); }

    public boolean moveFluidToBottom(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.isEmpty()) return false;
        dataManager.setBottomFluidName(fluidStack.getDisplayName().getString());
        boolean success = SmelteryClickHandler.clickFluidByStack(fluidStack);
        if (success) dataManager.scheduleHighlightUpdate();
        return success;
    }

    // ==================== Tab 切换 ====================

    private void switchTab(Tab newTab) {
        Tab old = dataManager.getCurrentTab();
        if (old == newTab) return;

        // 退出旧 Tab 的特殊状态
        if (old == Tab.ALLOY) {
            alloyHandler.exitQueryMode();
        }

        dataManager.setCurrentTab(newTab);
        dataManager.resetScrollOffsets();

        // 进入新 Tab
        if (newTab == Tab.ALLOY) {
            // 合金 Tab：用当前搜索词执行查询（在 refreshMoltenFluids 里做）
            refreshMoltenFluids();
        } else {
            refreshMoltenFluids();
        }
    }

    // ==================== 滚动 ====================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isVisible) return false;

        int actualX = getPanelX();
        if (mouseX < actualX || mouseX > actualX + this.width ||
                mouseY < this.y || mouseY > this.y + this.height) {
            return false;
        }

        Tab tab = dataManager.getCurrentTab();

        if (tab == Tab.ALLOY) {
            int totalHeight = layoutCalculator.getAlloyContentHeight();
            int visibleHeight = layoutCalculator.getAlloyVisibleHeight();
            int maxOffset = Math.max(0, totalHeight - visibleHeight);
            int newOffset = dataManager.getAlloyScrollOffset() - (int) (delta * PanelConfig.SCROLL_SPEED);
            dataManager.setAlloyScrollOffset(Math.max(0, Math.min(newOffset, maxOffset)));
            return true;
        }

        if (tab == Tab.MATERIALS) {
            int startY = this.y + layoutCalculator.getAllMaterialsContentY();
            int endY = startY + layoutCalculator.getAllMaterialsAreaHeight();
            if (mouseY >= startY && mouseY <= endY) {
                int newOffset = dataManager.getAllMaterialsScrollOffset() - (int) (delta * PanelConfig.SCROLL_SPEED);
                dataManager.setAllMaterialsScrollOffset(newOffset);
                return true;
            }
            return false;
        }

        // SMELTERY Tab
        int favStartY = this.y + layoutCalculator.getFavoriteAreaStartY();
        int favEndY = favStartY + layoutCalculator.getFavoriteAreaHeight();
        if (layoutCalculator.getFavoriteAreaHeight() > 0 && mouseY >= favStartY && mouseY <= favEndY) {
            int newOffset = dataManager.getFavScrollOffset() - (int) (delta * PanelConfig.SCROLL_SPEED);
            dataManager.setFavScrollOffset(newOffset);
            return true;
        }

        int smelteryStartY = this.y + layoutCalculator.getSmelteryAreaStartY();
        int smelteryEndY = smelteryStartY + layoutCalculator.getSmelteryAreaHeight();
        if (layoutCalculator.getSmelteryAreaHeight() > 0 && mouseY >= smelteryStartY && mouseY <= smelteryEndY) {
            int newOffset = dataManager.getScrollOffset() - (int) (delta * PanelConfig.SCROLL_SPEED);
            dataManager.setScrollOffset(newOffset);
            return true;
        }

        return false;
    }

    // ==================== 鼠标和键盘事件 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (dataManager.isAlloyMode() && renderer.isClickingResultName((int)mouseX, (int)mouseY)) {
            List<String> registryNames = renderer.getClickableResultRegistryNames((int)mouseX, (int)mouseY);
            List<String> displayNames = renderer.getClickableResultNames((int)mouseX, (int)mouseY);

            if (!registryNames.isEmpty() || !displayNames.isEmpty()) {
                FluidStack matchedMaterial = null;
                for (String regName : registryNames) {
                    FluidStack material = findTargetMaterial(regName, "");
                    if (material != null) { matchedMaterial = material; break; }
                }
                if (matchedMaterial == null) {
                    for (String dispName : displayNames) {
                        FluidStack material = findTargetMaterial("", dispName);
                        if (material != null) { matchedMaterial = material; break; }
                    }
                }
                if (matchedMaterial != null) {
                    int currentTemp = getCurrentSmelteryTemperature();
                    alloyHandler.refreshTemperature(currentTemp);
                    alloyHandler.selectMaterial(matchedMaterial, dataManager.getAllFluids(), currentTemp);
                    return true;
                }
                return true;
            }
            return true;
        }

        if (isTabButtonClicked(mouseX, mouseY)) {
            if (isAnimating()) return true;
            toggleVisibility();
            return true;
        }

        if (!isVisible && !isAnimating()) return false;

        int px = getPanelX();
        int py = this.y;
        int pw = this.width;

        if (!(mouseX >= px && mouseX <= px + pw && mouseY >= py && mouseY <= py + this.height)) {
            return false;
        }

        // ===== Tab 点击 =====
        int tabIdx = getTabIndexAt(mouseX, mouseY, px, py);
        if (tabIdx >= 0) {
            Tab[] tabs = Tab.values();
            if (tabIdx < tabs.length) {
                switchTab(tabs[tabIdx]);
                return true;
            }
        }

        Font font = Minecraft.getInstance().font;

        if (dataManager.isAlloyMode() && alloyHandler.getSelectedMaterial() != null) {
            String backText = new TranslatableComponent("gui.tinkerssearch.alloy_back").getString();
            int backX = px + 5;
            int backY = py + PanelConfig.CARDS_START_Y;
            if (mouseX >= backX && mouseX <= backX + font.width(backText) &&
                    mouseY >= backY && mouseY <= backY + 12) {
                alloyHandler.backToMaterials();
                return true;
            }
        }

        if (mouseX >= px + PanelConfig.REFRESH_BTN_X && mouseX <= px + PanelConfig.REFRESH_BTN_X + PanelConfig.REFRESH_BTN_W &&
                mouseY >= py + PanelConfig.REFRESH_BTN_Y && mouseY <= py + PanelConfig.REFRESH_BTN_Y + PanelConfig.REFRESH_BTN_H) {
            alloyHandler.invalidateTemperatureCache();
            temperatureReader.invalidateCache();
            refreshSmelteryEntity();
            forceRefreshTemperature();
            refreshMoltenFluids();
            return true;
        }

        if (interactionHandler.handleSearchBoxClick(mouseX, mouseY)) {
            return true;
        }

        if (interactionHandler.isSearchBoxFocused()) {
            interactionHandler.setSearchBoxFocused(false);
        }

        return handleCardClick(mouseX, mouseY, button);
    }

    private int getTabIndexAt(double mouseX, double mouseY, int px, int py) {
        int relX = (int) mouseX - px;
        int relY = (int) mouseY - py;
        if (relY < PanelConfig.TAB_ITEM_Y || relY > PanelConfig.TAB_ITEM_Y + PanelConfig.TAB_ITEM_HEIGHT) return -1;
        for (int i = 0; i < PanelConfig.TAB_COUNT; i++) {
            int tabX = PanelConfig.TAB_START_X + i * PanelConfig.TAB_ITEM_WIDTH;
            if (relX >= tabX && relX < tabX + PanelConfig.TAB_ITEM_WIDTH) return i;
        }
        return -1;
    }

    private FluidStack findTargetMaterial(String registryName, String displayName) {
        if (registryName == null) registryName = "";
        if (displayName == null) displayName = "";

        List<FluidStack> materials = alloyHandler.getFilteredMaterials();
        if (materials == null || materials.isEmpty()) return null;

        if (!registryName.isEmpty()) {
            for (FluidStack fs : materials) {
                ResourceLocation rl = fs.getFluid().getRegistryName();
                if (rl != null && rl.getPath().equalsIgnoreCase(registryName)) return fs;
            }
        }
        if (!displayName.isEmpty()) {
            for (FluidStack fs : materials) {
                String name = fs.getDisplayName().getString().replace("Molten ", "").replace("熔融", "").trim();
                if (name.equalsIgnoreCase(displayName.trim())) return fs;
            }
        }
        if (!displayName.isEmpty()) {
            String lowerDisplay = displayName.trim().toLowerCase();
            for (FluidStack fs : materials) {
                String name = fs.getDisplayName().getString().replace("Molten ", "").replace("熔融", "").trim().toLowerCase();
                if (name.contains(lowerDisplay) || lowerDisplay.contains(name)) return fs;
            }
        }
        if (!registryName.isEmpty()) {
            String lowerRegistry = registryName.toLowerCase();
            for (FluidStack fs : materials) {
                ResourceLocation rl = fs.getFluid().getRegistryName();
                if (rl != null) {
                    String path = rl.getPath().toLowerCase();
                    if (path.contains(lowerRegistry) || lowerRegistry.contains(path)) return fs;
                }
            }
        }
        return null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isVisible) return false;

        if (interactionHandler.isSearchBoxFocused()) {
            if (interactionHandler.handleKeyPressed(keyCode, scanCode, modifiers)) return true;

            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER ||
                    keyCode == GLFW.GLFW_KEY_ESCAPE) {
                interactionHandler.setSearchBoxFocused(false);
                return true;
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return false;

        if (interactionHandler.handleKeyPressedGlobal(keyCode, scanCode, modifiers)) return true;
        return interactionHandler.handleKeyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!isVisible) return false;
        return interactionHandler.handleCharTyped(codePoint, modifiers);
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderer.renderButton(poseStack, mouseX, mouseY, partialTick);
    }

    private void onFluidClicked(List<FluidStack> fluids) {
        new Thread(() -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            Minecraft.getInstance().execute(this::refreshMoltenFluids);
        }).start();
    }

    private boolean handleCardClick(double mouseX, double mouseY, int button) {
        if (!isVisible) return false;
        if (dataManager.isAlloyMode()) return handleAlloyCardClick(mouseX, mouseY, button);

        int px = getPanelX();
        int py = this.y;
        int pw = this.width;

        int cardW = (pw - 10 - PanelConfig.CARD_SPACING - PanelConfig.SCROLL_BAR_WIDTH - PanelConfig.SCROLL_BAR_PADDING) / PanelConfig.ITEMS_PER_ROW;
        int cardH = PanelConfig.CARD_HEIGHT;

        Tab tab = dataManager.getCurrentTab();

        if (tab == Tab.MATERIALS) {
            int startY = py + layoutCalculator.getAllMaterialsContentY();
            int endY = startY + layoutCalculator.getAllMaterialsAreaHeight();
            if (mouseY >= startY && mouseY <= endY) {
                return handleAreaCardClick(mouseX, mouseY, button,
                        dataManager.getDisplayedAllMaterials(),
                        dataManager.getAllMaterialsScrollOffset(),
                        startY, endY, px, cardW, cardH, AreaKind.ALL_MATERIALS);
            }
            return false;
        }

        // SMELTERY Tab：收藏 + 冶炼炉
        int favStartY = py + layoutCalculator.getFavoriteAreaStartY();
        int favEndY = favStartY + layoutCalculator.getFavoriteAreaHeight();
        if (layoutCalculator.getFavoriteAreaHeight() > 0 && mouseY >= favStartY && mouseY <= favEndY) {
            return handleAreaCardClick(mouseX, mouseY, button,
                    dataManager.getDisplayedFavoriteFluids(),
                    dataManager.getFavScrollOffset(),
                    favStartY, favEndY, px, cardW, cardH, AreaKind.FAVORITE);
        }

        int smelteryStartY = py + layoutCalculator.getSmelteryAreaStartY();
        int smelteryEndY = smelteryStartY + layoutCalculator.getSmelteryAreaHeight();
        if (layoutCalculator.getSmelteryAreaHeight() > 0 && mouseY >= smelteryStartY && mouseY <= smelteryEndY) {
            return handleAreaCardClick(mouseX, mouseY, button,
                    dataManager.getDisplayedFluids(),
                    dataManager.getScrollOffset(),
                    smelteryStartY, smelteryEndY, px, cardW, cardH, AreaKind.SMELTERY);
        }

        return false;
    }

    private boolean handleAreaCardClick(double mouseX, double mouseY, int button,
                                        List<FluidStack> list, int scrollOff,
                                        int areaStartY, int areaEndY,
                                        int px, int cardW, int cardH, AreaKind areaKind) {
        if (list.isEmpty()) return false;

        int startY = areaStartY - scrollOff;

        for (int i = 0; i < list.size(); i++) {
            int row = i / PanelConfig.ITEMS_PER_ROW;
            int col = i % PanelConfig.ITEMS_PER_ROW;
            int cardX = px + 5 + col * (cardW + PanelConfig.CARD_SPACING);
            int cardY = startY + row * (cardH + PanelConfig.CARD_SPACING);

            if (cardY + cardH < areaStartY || cardY > areaEndY) continue;

            if (mouseX >= cardX && mouseX <= cardX + cardW &&
                    mouseY >= cardY && mouseY <= cardY + cardH) {

                FluidStack fluid = list.get(i);
                if (fluid == null || fluid.isEmpty()) return false;

                boolean existsInSmeltery = true;
                if (areaKind != AreaKind.SMELTERY) {
                    existsInSmeltery = false;
                    for (FluidStack fs : dataManager.getAllFluids()) {
                        if (fs.getFluid().getRegistryName().equals(fluid.getFluid().getRegistryName())) {
                            existsInSmeltery = true;
                            break;
                        }
                    }
                }

                int iconSize = PanelConfig.ICON_SIZE;
                int iconX = cardX + 3;
                int iconY = cardY + (cardH - iconSize) / 2;
                int padding = 2;
                boolean onIcon = mouseX >= iconX - padding && mouseX <= iconX + iconSize + padding &&
                        mouseY >= iconY - padding && mouseY <= iconY + iconSize + padding;

                int starSize = 12;
                int starX = cardX + cardW - starSize - 4;
                int starY = cardY + 4;
                boolean onStar = mouseX >= starX && mouseX <= starX + starSize &&
                        mouseY >= starY && mouseY <= starY + starSize;
                if (onStar) {
                    FavoritesManager.toggleFavorite(fluid);
                    refreshMoltenFluids();
                    return true;
                }

                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    openFluidDetailScreen(fluid);
                    return true;
                }

                if (onIcon && jeiAvailable) {
                    return interactionHandler.handleJeiIconClick(fluid, button);
                }

                if (areaKind != AreaKind.SMELTERY && !existsInSmeltery) {
                    return true;
                }

                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !onIcon) {
                    return moveFluidToBottom(fluid);
                }

                return true;
            }
        }
        return false;
    }

    private void openFluidDetailScreen(FluidStack fluid) {
        Minecraft mc = Minecraft.getInstance();
        SmelteryBlockEntity smeltery = null;

        BlockEntity target = dataManager.getSmelteryTileEntity();
        if (target instanceof SmelteryBlockEntity) smeltery = (SmelteryBlockEntity) target;

        if (smeltery == null && mc.screen instanceof AbstractContainerScreen) {
            BlockEntity be = SmelteryClickHandler.getSmelteryFromScreen((AbstractContainerScreen<?>) mc.screen);
            if (be instanceof SmelteryBlockEntity) smeltery = (SmelteryBlockEntity) be;
        }

        if (smeltery == null) {
            BlockEntity be = dataManager.getCachedTileEntity();
            if (be instanceof SmelteryBlockEntity) smeltery = (SmelteryBlockEntity) be;
        }

        Screen savedScreen = mc.screen;
        mc.setScreen(new FluidDetailScreen(fluid, smeltery, savedScreen));
    }

    private boolean handleAlloyCardClick(double mouseX, double mouseY, int button) {
        int px = getPanelX();
        int py = this.y;
        int pw = this.width;

        Font font = Minecraft.getInstance().font;

        if (alloyHandler.getSelectedMaterial() != null) {
            String backText = new TranslatableComponent("gui.tinkerssearch.alloy_back").getString();
            int backX = px + 5;
            int backY = py + PanelConfig.CARDS_START_Y;
            if (mouseX >= backX && mouseX <= backX + font.width(backText) &&
                    mouseY >= backY && mouseY <= backY + 12) {
                alloyHandler.backToMaterials();
                return true;
            }
        }

        if (alloyHandler.getSelectedMaterial() == null) {
            List<FluidStack> materials = alloyHandler.getFilteredMaterials();
            int startY = py + PanelConfig.CARDS_START_Y + 18;
            int endY = py + this.height - 4;

            if (materials.isEmpty()) return false;

            int cardW = (pw - 10 - PanelConfig.CARD_SPACING - PanelConfig.SCROLL_BAR_WIDTH - PanelConfig.SCROLL_BAR_PADDING) / PanelConfig.ITEMS_PER_ROW;
            int cardH = PanelConfig.CARD_HEIGHT;
            int actualStartY = startY - dataManager.getAlloyScrollOffset();

            for (int i = 0; i < materials.size(); i++) {
                int row = i / PanelConfig.ITEMS_PER_ROW;
                int col = i % PanelConfig.ITEMS_PER_ROW;
                int cardX = px + 5 + col * (cardW + PanelConfig.CARD_SPACING);
                int cardY = actualStartY + row * (cardH + PanelConfig.CARD_SPACING);

                if (cardY + cardH < startY || cardY > endY) continue;

                if (mouseX >= cardX && mouseX <= cardX + cardW &&
                        mouseY >= cardY && mouseY <= cardY + cardH) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        int currentTemp = getCurrentSmelteryTemperature();
                        alloyHandler.refreshTemperature(currentTemp);
                        alloyHandler.selectMaterial(materials.get(i), dataManager.getAllFluids(), currentTemp);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private FluidStack getAlloyFluidAt(double mouseX, double mouseY) {
        if (alloyHandler.getSelectedMaterial() != null) return null;

        int px = getPanelX();
        int py = this.y;
        int pw = this.width;

        List<FluidStack> materials = alloyHandler.getFilteredMaterials();
        int startY = py + PanelConfig.CARDS_START_Y + 18;
        int endY = py + this.height - 4;

        if (materials.isEmpty()) return null;

        int cardW = (pw - 10 - PanelConfig.CARD_SPACING - PanelConfig.SCROLL_BAR_WIDTH - PanelConfig.SCROLL_BAR_PADDING) / PanelConfig.ITEMS_PER_ROW;
        int cardH = PanelConfig.CARD_HEIGHT;
        int actualStartY = startY - dataManager.getAlloyScrollOffset();

        for (int i = 0; i < materials.size(); i++) {
            int row = i / PanelConfig.ITEMS_PER_ROW;
            int col = i % PanelConfig.ITEMS_PER_ROW;
            int cardX = px + 5 + col * (cardW + PanelConfig.CARD_SPACING);
            int cardY = actualStartY + row * (cardH + PanelConfig.CARD_SPACING);

            if (cardY + cardH < startY || cardY > endY) continue;

            if (mouseX >= cardX && mouseX <= cardX + cardW &&
                    mouseY >= cardY && mouseY <= cardY + cardH) {
                return materials.get(i);
            }
        }
        return null;
    }

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {}
}