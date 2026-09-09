package top.leipishu.tinkerssearch.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
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
import top.leipishu.tinkerssearch.client.gui.panel.PanelLayoutCalculator;
import top.leipishu.tinkerssearch.client.gui.panel.PanelRenderer;
import top.leipishu.tinkerssearch.config.PanelConfig;
import top.leipishu.tinkerssearch.utils.SmelteryTemperatureReader;
import top.leipishu.tinkerssearch.utils.FavoritesManager;
import top.leipishu.tinkerssearch.utils.SmelteryClickHandler;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import slimeknights.tconstruct.smeltery.block.entity.controller.SmelteryBlockEntity;

import java.util.List;

import static top.leipishu.tinkerssearch.config.PanelConfig.PANEL_WIDTH;

/**
 * 浮动搜索面板 - 核心控制类
 */
public class FloatingSearchPanel extends AbstractWidget {

    // ===== Tab按钮常量 =====
    public static final int TAB_BUTTON_WIDTH = 14;
    public static final int TAB_BUTTON_HEIGHT = 30;

    // ===== 状态 =====
    private boolean isVisible = false;
    private boolean isActuallyVisible = false;

    // ===== 子管理器 =====
    private final PanelDataManager dataManager;
    private final PanelAnimationManager animationManager;
    private final PanelLayoutCalculator layoutCalculator;
    private final PanelRenderer renderer;
    private final SmelteryTemperatureReader temperatureReader;
    private final PanelInteractionHandler interactionHandler;
    private final AlloyQueryHandler alloyHandler;

    // ===== JEI =====
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

        // ===== 先创建 layoutCalculator（panelRenderer 暂为 null） =====
        this.layoutCalculator = new PanelLayoutCalculator(this, dataManager, alloyHandler, null);
        this.animationManager = new PanelAnimationManager(this, interactionHandler, alloyHandler, dataManager);

        // ===== 再创建 renderer =====
        this.renderer = new PanelRenderer(this, dataManager, layoutCalculator, animationManager,
                interactionHandler, alloyHandler);

        // ===== 设置 layoutCalculator 的 panelRenderer 引用 =====
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

    // ===== 展开状态（供外部Tab按钮使用） =====
    public boolean isExpanded() {
        return isVisible || isAnimating();
    }

    // ===== Tab按钮位置（供外部点击检测和绘制使用） =====
    public int[] getTabButtonPosition() {
        boolean expanded = isExpanded();
        int panelX = getPanelX();
        int panelWidth = getPanelWidth();
        int panelHeight = getPanelHeight();

        int btnX, btnY;
        if (expanded) {
            btnX = panelX + panelWidth - 1;
        } else {
            btnX = 0;
        }
        btnY = (panelHeight - TAB_BUTTON_HEIGHT) / 2;

        if (btnX < 0) {
            btnX = 0;
        }

        return new int[]{btnX, btnY};
    }

    // ===== 数据委托 =====
    public List<FluidStack> getAllFluids() { return dataManager.getAllFluids(); }
    public List<FluidStack> getDisplayedFluids() { return dataManager.getDisplayedFluids(); }
    public List<FluidStack> getAllFavoriteFluids() { return dataManager.getAllFavoriteFluids(); }
    public List<FluidStack> getDisplayedFavoriteFluids() { return dataManager.getDisplayedFavoriteFluids(); }
    public String getBottomFluidName() { return dataManager.getBottomFluidName(); }
    public int getScrollOffset() { return dataManager.getScrollOffset(); }
    public int getMaxScrollOffset() { return dataManager.getMaxScrollOffset(); }
    public int getFavScrollOffset() { return dataManager.getFavScrollOffset(); }
    public int getMaxFavScrollOffset() { return dataManager.getMaxFavScrollOffset(); }
    public int getAlloyScrollOffset() { return dataManager.getAlloyScrollOffset(); }

    // ===== 布局委托 =====
    public int getFavoriteAreaStartY() { return layoutCalculator.getFavoriteAreaStartY(); }
    public int getFavoriteAreaHeight() { return layoutCalculator.getFavoriteAreaHeight(); }
    public int getSmelteryAreaStartY() { return layoutCalculator.getSmelteryAreaStartY(); }

    // ===== Setters =====
    public void setPanelX(int x) { this.x = x; }
    public void setPanelY(int y) { this.y = y; }
    public void setPanelWidth(int width) { this.width = width; }
    public void setPanelHeight(int height) { this.height = height; }
    public void setVisibleInternal(boolean visible) {
        this.isVisible = visible;
        this.visible = visible;
    }
    public void setActuallyVisible(boolean actuallyVisible) {
        this.isActuallyVisible = actuallyVisible;
    }
    public void setScrollOffset(int offset) { dataManager.setScrollOffset(offset); }
    public void setFavScrollOffset(int offset) { dataManager.setFavScrollOffset(offset); }
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

        if (dataManager.isAlloyMode()) {
            return getAlloyFluidAt(mouseX, mouseY);
        }

        if (dataManager.getDisplayedFluids().isEmpty() && dataManager.getDisplayedFavoriteFluids().isEmpty()) return null;

        int px = getPanelX();
        int py = this.y;
        int pw = this.width;

        int cardW = (pw - 10 - PanelConfig.CARD_SPACING - PanelConfig.SCROLL_BAR_WIDTH - PanelConfig.SCROLL_BAR_PADDING) / PanelConfig.ITEMS_PER_ROW;
        int cardH = PanelConfig.CARD_HEIGHT;

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
        return null;
    }

    // ==================== Tab按钮点击检测 ====================

    public boolean isTabButtonClicked(double mouseX, double mouseY) {
        int[] pos = getTabButtonPosition();
        int btnX = pos[0];
        int btnY = pos[1];
        return mouseX >= btnX && mouseX <= btnX + TAB_BUTTON_WIDTH &&
                mouseY >= btnY && mouseY <= btnY + TAB_BUTTON_HEIGHT;
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

    public void toggleVisibility() {
        setVisible(!this.isVisible);
    }

    // ==================== 温度读取 ====================

    public int getCurrentSmelteryTemperature() {
        return temperatureReader.getCurrentSmelteryTemperature();
    }

    public int forceRefreshTemperature() {
        return temperatureReader.forceRefreshTemperature();
    }

    public void refreshTemperature() {
        forceRefreshTemperature();
    }

    // ==================== 数据刷新 ====================

    public void setSmelteryBlockEntity(BlockEntity tileEntity) {
        dataManager.setSmelteryTileEntity(tileEntity);
        if (tileEntity != null) {
            refreshMoltenFluids();
        }
    }

    public void refreshMoltenFluids() {
        dataManager.refreshMoltenFluids();
    }

    public boolean moveFluidToBottom(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.isEmpty()) return false;

        dataManager.setBottomFluidName(fluidStack.getDisplayName().getString());

        boolean success = SmelteryClickHandler.clickFluidByStack(fluidStack);
        if (success) {
            dataManager.scheduleHighlightUpdate();
        }
        return success;
    }

    // ==================== 滚动 ====================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isVisible) return false;

        if (dataManager.isAlloyMode()) {
            // ===== 计算合金内容总高度和可见高度 =====
            int totalHeight = layoutCalculator.getAlloyContentHeight();
            int visibleHeight = layoutCalculator.getAlloyVisibleHeight();
            int maxOffset = Math.max(0, totalHeight - visibleHeight);
            int newOffset = dataManager.getAlloyScrollOffset() - (int) (delta * PanelConfig.SCROLL_SPEED);
            dataManager.setAlloyScrollOffset(Math.max(0, Math.min(newOffset, maxOffset)));
            return true;
        }

        if (dataManager.getDisplayedFluids().isEmpty() && dataManager.getDisplayedFavoriteFluids().isEmpty()) return false;

        int actualX = getPanelX();
        if (mouseX < actualX || mouseX > actualX + this.width ||
                mouseY < this.y || mouseY > this.y + this.height) {
            return false;
        }

        int favStartY = this.y + layoutCalculator.getFavoriteAreaStartY();
        int favEndY = favStartY + layoutCalculator.getFavoriteAreaHeight();
        int smelteryStartY = this.y + layoutCalculator.getSmelteryAreaStartY();
        int smelteryEndY = this.y + this.height - 4;

        if (mouseY >= favStartY && mouseY <= favEndY) {
            int newOffset = dataManager.getFavScrollOffset() - (int) (delta * PanelConfig.SCROLL_SPEED);
            dataManager.setFavScrollOffset(Math.max(0, Math.min(newOffset, dataManager.getMaxFavScrollOffset())));
            return true;
        } else if (mouseY >= smelteryStartY && mouseY <= smelteryEndY) {
            int newOffset = dataManager.getScrollOffset() - (int) (delta * PanelConfig.SCROLL_SPEED);
            dataManager.setScrollOffset(newOffset);
            return true;
        }
        return false;
    }

    // ==================== 鼠标和键盘事件 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ===== 优先检测：点击合金产物名称跳转 =====
        if (dataManager.isAlloyMode() && renderer.isClickingResultName((int)mouseX, (int)mouseY)) {
            // 获取该位置的所有目标
            List<String> registryNames = renderer.getClickableResultRegistryNames((int)mouseX, (int)mouseY);
            List<String> displayNames = renderer.getClickableResultNames((int)mouseX, (int)mouseY);

            if ((registryNames != null && !registryNames.isEmpty()) ||
                    (displayNames != null && !displayNames.isEmpty())) {

                // 尝试匹配所有目标
                FluidStack matchedMaterial = null;
                String matchedRegistry = "";
                String matchedDisplay = "";

                // 优先用注册名匹配
                for (String regName : registryNames) {
                    FluidStack material = findTargetMaterial(regName, "");
                    if (material != null) {
                        matchedMaterial = material;
                        matchedRegistry = regName;
                        break;
                    }
                }

                // 如果注册名匹配失败，用显示名匹配
                if (matchedMaterial == null) {
                    for (String dispName : displayNames) {
                        FluidStack material = findTargetMaterial("", dispName);
                        if (material != null) {
                            matchedMaterial = material;
                            matchedDisplay = dispName;
                            break;
                        }
                    }
                }

                if (matchedMaterial != null) {
                    int currentTemp = getCurrentSmelteryTemperature();
                    alloyHandler.refreshTemperature(currentTemp);
                    alloyHandler.selectMaterial(matchedMaterial, dataManager.getAllFluids(), currentTemp);
                    return true;
                }

                // 如果都匹配失败，用第一个作为搜索词
                String searchReg = registryNames.isEmpty() ? "" : registryNames.get(0);
                String searchDisp = displayNames.isEmpty() ? "" : displayNames.get(0);
                String searchText = "/a/ " + (searchReg.isEmpty() ? searchDisp : searchReg);
                interactionHandler.setSearchKeyword(searchText);
                refreshMoltenFluids();
                return true;
            }
            return true;
        }

        // ===== 检测Tab按钮 =====
        if (isTabButtonClicked(mouseX, mouseY)) {
            if (isAnimating()) {
                return true;
            }
            toggleVisibility();
            return true;
        }

        if (!isVisible && !isAnimating()) {
            return false;
        }

        int px = getPanelX();
        int py = this.y;
        int pw = this.width;

        if (!(mouseX >= px && mouseX <= px + pw && mouseY >= py && mouseY <= py + this.height)) {
            return false;
        }

        Font font = Minecraft.getInstance().font;

        if (dataManager.isAlloyMode() && alloyHandler.getSelectedMaterial() != null) {
            String backText = new TranslatableComponent("gui.tinkerssearch.alloy_back").getString();
            int backX = px + 5;
            int backY = py + PanelConfig.CARDS_START_Y + 16;
            int backW = font.width(backText);
            int backH = 12;
            if (mouseX >= backX && mouseX <= backX + backW &&
                    mouseY >= backY && mouseY <= backY + backH) {
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

        // ===== 搜索框点击 =====
        if (mouseX >= px + 5 && mouseX <= px + 5 + pw - 10 &&
                mouseY >= py + PanelConfig.SEARCH_BOX_Y && mouseY <= py + PanelConfig.SEARCH_BOX_Y + PanelConfig.SEARCH_BOX_H) {
            interactionHandler.setSearchBoxFocused(true);
            // ===== 新增：点击搜索框设置光标位置 =====
            interactionHandler.handleMouseClickSetCursor(mouseX, mouseY, px, py, pw);
            return true;
        }

        if (interactionHandler.isSearchBoxFocused()) {
            interactionHandler.setSearchBoxFocused(false);
        }

        return handleCardClick(mouseX, mouseY, button);
    }

    /**
     * 从多个来源查找目标材料
     */
    private FluidStack findTargetMaterial(String registryName, String displayName) {
        if (registryName == null) registryName = "";
        if (displayName == null) displayName = "";

        List<FluidStack> materials = alloyHandler.getFilteredMaterials();
        if (materials == null || materials.isEmpty()) {
            return null;
        }

        // ===== 策略1：注册名精确匹配 =====
        if (!registryName.isEmpty()) {
            for (FluidStack fs : materials) {
                ResourceLocation rl = fs.getFluid().getRegistryName();
                if (rl != null && rl.getPath().equalsIgnoreCase(registryName)) {
                    return fs;
                }
            }
        }

        // ===== 策略2：显示名精确匹配 =====
        if (!displayName.isEmpty()) {
            for (FluidStack fs : materials) {
                String name = fs.getDisplayName().getString()
                        .replace("Molten ", "")
                        .replace("熔融", "")
                        .trim();
                if (name.equalsIgnoreCase(displayName.trim())) {
                    return fs;
                }
            }
        }

        // ===== 策略3：显示名包含匹配 =====
        if (!displayName.isEmpty()) {
            String lowerDisplay = displayName.trim().toLowerCase();
            for (FluidStack fs : materials) {
                String name = fs.getDisplayName().getString()
                        .replace("Molten ", "")
                        .replace("熔融", "")
                        .trim()
                        .toLowerCase();
                if (name.contains(lowerDisplay) || lowerDisplay.contains(name)) {
                    return fs;
                }
            }
        }

        // ===== 策略4：注册名包含匹配 =====
        if (!registryName.isEmpty()) {
            String lowerRegistry = registryName.toLowerCase();
            for (FluidStack fs : materials) {
                ResourceLocation rl = fs.getFluid().getRegistryName();
                if (rl != null) {
                    String path = rl.getPath().toLowerCase();
                    if (path.contains(lowerRegistry) || lowerRegistry.contains(path)) {
                        return fs;
                    }
                }
            }
        }

        // ===== 策略5：ForgeRegistries 直接查找 =====
        if (!registryName.isEmpty()) {
            try {
                ResourceLocation rl = new ResourceLocation(registryName);
                Fluid fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(rl);
                if (fluid != null) {
                    for (FluidStack fs : materials) {
                        if (fs.getFluid().getRegistryName() != null &&
                                fs.getFluid().getRegistryName().equals(rl)) {
                            return fs;
                        }
                    }
                    FluidStack newFs = new FluidStack(fluid, 1000);
                    String newName = newFs.getDisplayName().getString()
                            .replace("Molten ", "")
                            .replace("熔融", "")
                            .trim();
                    if (!displayName.isEmpty() && newName.equalsIgnoreCase(displayName.trim())) {
                        return newFs;
                    }
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isVisible) return false;

        // ===== 打印调试日志 =====
        // System.out.println("[Tinker's Search] keyPressed: " + keyCode + ", focused=" + interactionHandler.isSearchBoxFocused());

        if (interactionHandler.isSearchBoxFocused()) {
            // ===== 所有按键先交给 handleKeyPressed 处理 =====
            if (interactionHandler.handleKeyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }

            // ===== 如果 handleKeyPressed 返回 false，检查 Enter/Escape =====
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                interactionHandler.setSearchBoxFocused(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                interactionHandler.setSearchBoxFocused(false);
                return true;
            }

            // ===== 其他按键阻止传播 =====
            return true;
        }

        // ===== 搜索框未聚焦 =====
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return false;
        }

        if (interactionHandler.handleKeyPressedGlobal(keyCode, scanCode, modifiers)) {
            return true;
        }

        return interactionHandler.handleKeyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!isVisible) return false;
        return interactionHandler.handleCharTyped(codePoint, modifiers);
    }

    // ==================== 渲染 ====================

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderer.renderButton(poseStack, mouseX, mouseY, partialTick);
    }

    // ==================== 内部事件处理 ====================

    private void onFluidClicked(List<FluidStack> fluids) {
        new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
            Minecraft.getInstance().execute(() -> {
                refreshMoltenFluids();
            });
        }).start();
    }

    // 在 FloatingSearchPanel.java 中找到 handleCardClick 方法，修改如下：

    private boolean handleCardClick(double mouseX, double mouseY, int button) {
        if (!isVisible) return false;

        if (dataManager.isAlloyMode()) {
            return handleAlloyCardClick(mouseX, mouseY, button);
        }

        int px = getPanelX();
        int py = this.y;
        int pw = this.width;

        int favStartY = py + layoutCalculator.getFavoriteAreaStartY();
        int favEndY = favStartY + layoutCalculator.getFavoriteAreaHeight();
        int smelteryStartY = py + layoutCalculator.getSmelteryAreaStartY();
        int smelteryEndY = py + this.height - 4;

        boolean inFavArea = mouseY >= favStartY && mouseY <= favEndY;
        boolean inSmelteryArea = mouseY >= smelteryStartY && mouseY <= smelteryEndY;

        if (!inFavArea && !inSmelteryArea) return false;

        List<FluidStack> list = inFavArea ? dataManager.getDisplayedFavoriteFluids() : dataManager.getDisplayedFluids();
        int scrollOff = inFavArea ? dataManager.getFavScrollOffset() : dataManager.getScrollOffset();
        int areaStartY = inFavArea ? favStartY : smelteryStartY;

        if (list.isEmpty()) return false;

        int cardW = (pw - 10 - PanelConfig.CARD_SPACING - PanelConfig.SCROLL_BAR_WIDTH - PanelConfig.SCROLL_BAR_PADDING) / PanelConfig.ITEMS_PER_ROW;
        int cardH = PanelConfig.CARD_HEIGHT;

        int startY = areaStartY - scrollOff;

        for (int i = 0; i < list.size(); i++) {
            int row = i / PanelConfig.ITEMS_PER_ROW;
            int col = i % PanelConfig.ITEMS_PER_ROW;
            int cardX = px + 5 + col * (cardW + PanelConfig.CARD_SPACING);
            int cardY = startY + row * (cardH + PanelConfig.CARD_SPACING);

            if (cardY + cardH < areaStartY || cardY > (inFavArea ? favEndY : smelteryEndY)) continue;

            if (mouseX >= cardX && mouseX <= cardX + cardW &&
                    mouseY >= cardY && mouseY <= cardY + cardH) {

                FluidStack fluid = list.get(i);
                if (fluid == null || fluid.isEmpty()) return false;

                boolean existsInSmeltery = true;
                if (inFavArea) {
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

                // ===== 右键：打开详细信息浮窗 =====
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    openFluidDetailScreen(fluid);
                    return true;
                }

                // ===== 左键：原有逻辑 =====
                if (onIcon && jeiAvailable) {
                    return interactionHandler.handleJeiIconClick(fluid, button);
                }

                if (inFavArea && !existsInSmeltery) {
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

    /**
     * 打开流体详细信息浮窗
     */
    /**
     * 打开流体详细信息浮窗
     */
    private void openFluidDetailScreen(FluidStack fluid) {
        Minecraft mc = Minecraft.getInstance();
        SmelteryBlockEntity smeltery = null;

        // ===== 方式1：从 DataManager 获取 =====
        BlockEntity target = dataManager.getSmelteryTileEntity();
        if (target instanceof SmelteryBlockEntity) {
            smeltery = (SmelteryBlockEntity) target;
        }

        // ===== 方式2：如果获取不到，从屏幕反射获取 =====
        if (smeltery == null && mc.screen instanceof AbstractContainerScreen) {
            BlockEntity be = SmelteryClickHandler.getSmelteryFromScreen(
                    (AbstractContainerScreen<?>) mc.screen
            );
            if (be instanceof SmelteryBlockEntity) {
                smeltery = (SmelteryBlockEntity) be;
            }
        }

        // ===== 方式3：通过 SmelteryDataHelper 获取 =====
        if (smeltery == null) {
            BlockEntity be = dataManager.getCachedTileEntity();
            if (be instanceof SmelteryBlockEntity) {
                smeltery = (SmelteryBlockEntity) be;
            }
        }

        // ===== 打开浮窗（即使 smeltery 为 null 也可以打开，只是不显示容量信息） =====
        mc.setScreen(new FluidDetailScreen(fluid, smeltery));
    }

    private boolean handleAlloyCardClick(double mouseX, double mouseY, int button) {
        int px = getPanelX();
        int py = this.y;
        int pw = this.width;

        Font font = Minecraft.getInstance().font;

        if (alloyHandler.getSelectedMaterial() != null) {
            String backText = new TranslatableComponent("gui.tinkerssearch.alloy_back").getString();
            int backX = px + 5;
            int backY = py + PanelConfig.CARDS_START_Y + 16;
            int backW = font.width(backText);
            int backH = 12;
            if (mouseX >= backX && mouseX <= backX + backW &&
                    mouseY >= backY && mouseY <= backY + backH) {
                alloyHandler.backToMaterials();
                return true;
            }
        }

        if (alloyHandler.getSelectedMaterial() == null) {
            List<FluidStack> materials = alloyHandler.getFilteredMaterials();
            int startY = py + PanelConfig.CARDS_START_Y + 32;
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
        int startY = py + PanelConfig.CARDS_START_Y + 32;
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