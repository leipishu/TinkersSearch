package top.leipishu.tinkerssearch.client.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import slimeknights.tconstruct.smeltery.block.entity.controller.HeatingStructureBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.module.FuelModule;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import java.util.function.Supplier;
import net.minecraft.world.level.material.Fluid;
import org.lwjgl.glfw.GLFW;
import top.leipishu.tinkerssearch.alloy.AlloyQueryHandler;
import top.leipishu.tinkerssearch.alloy.AlloyRecipeData;
import top.leipishu.tinkerssearch.alloy.AlloyResultCalculator;
import top.leipishu.tinkerssearch.config.PanelConfig;
import top.leipishu.tinkerssearch.utils.FavoritesManager;
import top.leipishu.tinkerssearch.utils.SearchHelper;
import top.leipishu.tinkerssearch.utils.SmelteryClickHandler;
import top.leipishu.tinkerssearch.utils.SmelteryDataHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

public class FloatingSearchPanel extends AbstractWidget {

    // ===== 状态 =====
    private boolean isVisible = false;
    private boolean isAnimating = false;

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

    // ===== 交互 =====
    private PanelInteractionHandler interactionHandler;

    // ===== 屏幕尺寸 =====
    private int lastScreenWidth = 0;
    private int lastScreenHeight = 0;

    // ===== JEI =====
    private boolean jeiAvailable = false;

    // ===== 动画 =====
    private int animationOffset = 0;
    private int targetOffset = 0;
    private long animationStartTime = 0;
    private static final int ANIMATION_DURATION = 350;

    // ===== 按钮尺寸 =====
    public static final int TAB_BUTTON_WIDTH = 14;
    public static final int TAB_BUTTON_HEIGHT = 30;

    // ===== 移动后刷新标记 =====
    private boolean pendingHighlightUpdate = false;
    private long pendingHighlightTime = 0;

    // ===== 性能优化：缓存字体实例 =====
    private Font cachedFont = null;

    // ===== 收藏区域和冶炼炉区域分隔 =====
    private static final int SECTION_SPACING = 8;
    private static final int SECTION_LABEL_HEIGHT = 14;
    private static final int TITLE_CARD_SPACING = 4;

    // ===== 合金查询 =====
    private AlloyQueryHandler alloyHandler = AlloyQueryHandler.getInstance();
    private boolean isAlloyMode = false;
    private int alloyScrollOffset = 0;

    public FloatingSearchPanel() {
        super(0, 0, PANEL_WIDTH, 100, new TextComponent("Search Panel"));
        this.visible = false;
        this.isVisible = false;

        this.jeiAvailable = ModList.get().isLoaded("jei");

        this.interactionHandler = new PanelInteractionHandler(
                this,
                this::refreshMoltenFluids,
                this::onFluidClicked
        );

        updatePanelPosition();
    }

    // ==================== Getters ====================

    public int getPanelX() { return this.x + animationOffset; }
    public int getPanelY() { return this.y; }
    public int getPanelWidth() { return this.width; }
    public int getPanelHeight() { return this.height; }
    public boolean isVisible() { return isVisible; }
    public boolean isAnimating() { return isAnimating; }
    public int getScrollOffset() { return scrollOffset; }
    public int getMaxScrollOffset() { return maxScrollOffset; }
    public int getAnimationOffset() { return animationOffset; }
    public int getActualPanelX() { return this.x + animationOffset; }
    public boolean isJeiAvailable() { return jeiAvailable; }
    public boolean isAlloyMode() { return isAlloyMode; }

    public PanelInteractionHandler getInteractionHandler() {
        return interactionHandler;
    }

    // ==================== 位置更新 ====================

    public void updatePanelPosition() {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        this.x = 0;
        this.y = 0;
        this.width = PANEL_WIDTH;
        this.height = screenHeight;

        this.lastScreenWidth = screenWidth;
        this.lastScreenHeight = screenHeight;

        scrollOffset = 0;
        favScrollOffset = 0;
        alloyScrollOffset = 0;
        updateMaxScrollOffset();
        cachedFont = mc.font;
    }

    public void forceUpdatePosition() {
        updatePanelPosition();
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

    public void updatePosition(int leftPos, int topPos, int imageWidth, int imageHeight) {
        updatePanelPosition();
    }

    // ==================== 碰撞检测 ====================

    public boolean isPointInsidePanel(double mouseX, double mouseY) {
        if (!isVisible && !isAnimating) return false;
        int actualX = this.x + animationOffset;
        return mouseX >= actualX && mouseX <= actualX + this.width &&
                mouseY >= this.y && mouseY <= this.y + this.height;
    }

    public FluidStack getFluidAt(double mouseX, double mouseY) {
        if (!isVisible && !isAnimating) return null;

        if (isAlloyMode) {
            return getAlloyFluidAt(mouseX, mouseY);
        }

        if (displayedFluids.isEmpty() && displayedFavoriteFluids.isEmpty()) return null;

        int px = this.x + animationOffset;
        int py = this.y;
        int pw = this.width;

        int cardW = (pw - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
        int cardH = CARD_HEIGHT;

        int smelteryStartY = py + getSmelteryAreaStartY();
        for (int i = 0; i < displayedFluids.size(); i++) {
            int row = i / ITEMS_PER_ROW;
            int col = i % ITEMS_PER_ROW;
            int cardX = px + 5 + col * (cardW + CARD_SPACING);
            int cardY = smelteryStartY - scrollOffset + row * (cardH + CARD_SPACING);
            if (mouseX >= cardX && mouseX <= cardX + cardW &&
                    mouseY >= cardY && mouseY <= cardY + cardH) {
                return displayedFluids.get(i);
            }
        }

        int favStartY = py + getFavoriteAreaStartY();
        for (int i = 0; i < displayedFavoriteFluids.size(); i++) {
            int row = i / ITEMS_PER_ROW;
            int col = i % ITEMS_PER_ROW;
            int cardX = px + 5 + col * (cardW + CARD_SPACING);
            int cardY = favStartY - favScrollOffset + row * (cardH + CARD_SPACING);
            if (mouseX >= cardX && mouseX <= cardX + cardW &&
                    mouseY >= cardY && mouseY <= cardY + cardH) {
                return displayedFavoriteFluids.get(i);
            }
        }
        return null;
    }

    // ==================== 区域位置计算 ====================

    private int getFavoriteAreaStartY() {
        return CARDS_START_Y + SECTION_LABEL_HEIGHT + TITLE_CARD_SPACING;
    }

    private int getFavoriteAreaHeight() {
        if (displayedFavoriteFluids.isEmpty()) return 0;
        int cardW = (this.width - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
        int totalRows = (displayedFavoriteFluids.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
        int contentHeight = totalRows * (CARD_HEIGHT + CARD_SPACING) - CARD_SPACING;
        return Math.min(contentHeight, (int)(this.height * 0.35));
    }

    private int getSmelteryAreaStartY() {
        int favEndY = this.y + getFavoriteAreaStartY() + getFavoriteAreaHeight();
        if (displayedFavoriteFluids.isEmpty()) {
            return this.y + CARDS_START_Y + SECTION_LABEL_HEIGHT + TITLE_CARD_SPACING;
        }
        return favEndY + SECTION_SPACING + SECTION_LABEL_HEIGHT + TITLE_CARD_SPACING;
    }

    // ==================== 可见性控制 ====================

    public void setVisible(boolean visible) {
        if (this.isVisible == visible && !isAnimating) return;
        if (!visible && !this.isVisible && !isAnimating) return;

        if (!visible) {
            if (isAlloyMode) {
                isAlloyMode = false;
                alloyHandler.exitQueryMode();
                interactionHandler.setSearchKeyword("");
                interactionHandler.setSearchBoxFocused(false);
            }

            interactionHandler.setSearchBoxFocused(false);
            scrollOffset = 0;
            favScrollOffset = 0;
            alloyScrollOffset = 0;
            pendingHighlightUpdate = false;
            // 清理冶炼炉实体缓存，避免下次打开时读到旧的 BlockEntity
            this.smelteryTileEntity = null;
            targetOffset = -this.width;
            isAnimating = true;
            animationStartTime = System.currentTimeMillis();
        } else {
            updatePanelPosition();

            // ===== 关键：打开面板时立即刷新温度 =====
            refreshSmelteryTemperature();

            refreshMoltenFluids();
            this.isVisible = true;
            this.visible = true;
            animationOffset = -this.width;
            targetOffset = 0;
            isAnimating = true;
            animationStartTime = System.currentTimeMillis();
        }
    }

    public void toggleVisibility() {
        setVisible(!this.isVisible);
    }

    public void restoreVisibility() {
        if (isVisible && !visible) {
            visible = true;
        }
    }

    // ==================== 动画 ====================

    private void updateAnimation() {
        if (!isAnimating) return;

        long currentTime = System.currentTimeMillis();
        float progress = (float) (currentTime - animationStartTime) / ANIMATION_DURATION;

        if (progress >= 1.0f) {
            animationOffset = targetOffset;
            isAnimating = false;
            if (targetOffset < 0) {
                this.isVisible = false;
                this.visible = false;
                if (isAlloyMode) {
                    isAlloyMode = false;
                    alloyHandler.exitQueryMode();
                    interactionHandler.setSearchKeyword("");
                }
            }
            return;
        }

        float eased = 1.0f - (float) Math.pow(1.0f - progress, 3);
        int startOffset = targetOffset == 0 ? -this.width : 0;
        animationOffset = startOffset + (int) ((targetOffset - startOffset) * eased);
    }

    // ==================== 滚动 ====================

    public void updateMaxScrollOffset() {
        if (displayedFluids.isEmpty()) {
            maxScrollOffset = 0;
        } else {
            int py = this.y;
            int pw = this.width;
            int ph = this.height;

            int startY = py + getSmelteryAreaStartY();
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
            int favStartY = this.y + getFavoriteAreaStartY();
            int favEndY = favStartY + getFavoriteAreaHeight();

            int cardW = (this.width - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
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

    public void setScrollOffset(int offset) {
        scrollOffset = Math.max(0, Math.min(offset, maxScrollOffset));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isVisible) return false;

        if (isAlloyMode) {
            int maxOffset = Math.max(0, getAlloyContentHeight() - getAlloyVisibleHeight());
            int newOffset = alloyScrollOffset - (int) (delta * SCROLL_SPEED);
            alloyScrollOffset = Math.max(0, Math.min(newOffset, maxOffset));
            return true;
        }

        if (displayedFluids.isEmpty() && displayedFavoriteFluids.isEmpty()) return false;

        int actualX = this.x + animationOffset;
        if (mouseX < actualX || mouseX > actualX + this.width ||
                mouseY < this.y || mouseY > this.y + this.height) {
            return false;
        }

        int favStartY = this.y + getFavoriteAreaStartY();
        int favEndY = favStartY + getFavoriteAreaHeight();
        int smelteryStartY = this.y + getSmelteryAreaStartY();
        int smelteryEndY = this.y + this.height - 4;

        if (mouseY >= favStartY && mouseY <= favEndY) {
            int newOffset = favScrollOffset - (int) (delta * SCROLL_SPEED);
            favScrollOffset = Math.max(0, Math.min(newOffset, maxFavScrollOffset));
            return true;
        } else if (mouseY >= smelteryStartY && mouseY <= smelteryEndY) {
            int newOffset = scrollOffset - (int) (delta * SCROLL_SPEED);
            setScrollOffset(newOffset);
            return true;
        }
        return false;
    }

    // ==================== 数据刷新 ====================

    public void setSmelteryBlockEntity(BlockEntity tileEntity) {
        this.smelteryTileEntity = tileEntity;
        this.cachedTileEntity = tileEntity;
        if (tileEntity != null) {
            refreshMoltenFluids();
        }
    }

    public void refreshMoltenFluids() {
        String keyword = interactionHandler.getSearchKeyword();

        // 在 refreshMoltenFluids() 中
        if (keyword != null && keyword.startsWith("/a/")) {
            // 先刷新 allFluids
            BlockEntity target = smelteryTileEntity != null ? smelteryTileEntity : cachedTileEntity;
            if (target != null) {
                allFluids = SmelteryDataHelper.getMoltenFluids(target);
            }

            String searchTerm = keyword.substring(3).trim();
            int currentTemp = getCurrentSmelteryTemperature();
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

    public void scheduleHighlightUpdate() {
        pendingHighlightUpdate = true;
        pendingHighlightTime = System.currentTimeMillis();
    }

    private void checkPendingHighlightUpdate() {
        if (!pendingHighlightUpdate) return;

        long elapsed = System.currentTimeMillis() - pendingHighlightTime;
        if (elapsed >= 500) {
            pendingHighlightUpdate = false;
            updateBottomFluidHighlight();
        }
    }

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

    // ==================== 卡片点击 ====================

    public boolean moveFluidToBottom(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.isEmpty()) return false;

        this.bottomFluidName = fluidStack.getDisplayName().getString();

        boolean success = SmelteryClickHandler.clickFluidByStack(fluidStack);
        if (success) {
            scheduleHighlightUpdate();
        }
        return success;
    }

    private boolean handleCardClick(double mouseX, double mouseY, int button) {
        if (!isVisible) return false;

        if (isAlloyMode) {
            return handleAlloyCardClick(mouseX, mouseY, button);
        }

        int px = this.x + animationOffset;
        int py = this.y;
        int pw = this.width;

        int favStartY = py + getFavoriteAreaStartY();
        int favEndY = favStartY + getFavoriteAreaHeight();
        int smelteryStartY = py + getSmelteryAreaStartY();
        int smelteryEndY = py + this.height - 4;

        boolean inFavArea = mouseY >= favStartY && mouseY <= favEndY;
        boolean inSmelteryArea = mouseY >= smelteryStartY && mouseY <= smelteryEndY;

        if (!inFavArea && !inSmelteryArea) return false;

        List<FluidStack> list = inFavArea ? displayedFavoriteFluids : displayedFluids;
        int scrollOff = inFavArea ? favScrollOffset : scrollOffset;
        int areaStartY = inFavArea ? favStartY : smelteryStartY;

        if (list.isEmpty()) return false;

        int cardW = (pw - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
        int cardH = CARD_HEIGHT;

        int startY = areaStartY - scrollOff;

        for (int i = 0; i < list.size(); i++) {
            int row = i / ITEMS_PER_ROW;
            int col = i % ITEMS_PER_ROW;
            int cardX = px + 5 + col * (cardW + CARD_SPACING);
            int cardY = startY + row * (cardH + CARD_SPACING);

            if (cardY + cardH < areaStartY || cardY > (inFavArea ? favEndY : smelteryEndY)) continue;

            if (mouseX >= cardX && mouseX <= cardX + cardW &&
                    mouseY >= cardY && mouseY <= cardY + cardH) {

                FluidStack fluid = list.get(i);
                if (fluid == null || fluid.isEmpty()) return false;

                boolean existsInSmeltery = true;
                if (inFavArea) {
                    existsInSmeltery = false;
                    for (FluidStack fs : allFluids) {
                        if (fs.getFluid().getRegistryName().equals(fluid.getFluid().getRegistryName())) {
                            existsInSmeltery = true;
                            break;
                        }
                    }
                }

                int iconSize = ICON_SIZE;
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

    // ==================== 鼠标和键盘事件 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible && !isAnimating) return false;

        int px = this.x + animationOffset;
        int py = this.y;
        int pw = this.width;

        if (!(mouseX >= px && mouseX <= px + pw && mouseY >= py && mouseY <= py + this.height)) {
            return false;
        }

        Font font = Minecraft.getInstance().font;

        if (isAlloyMode && alloyHandler.getSelectedMaterial() != null) {
            String backText = new TranslatableComponent("gui.tinkerssearch.alloy_back").getString();
            int backX = px + 5;
            int backY = py + CARDS_START_Y + 16;
            int backW = font.width(backText);
            int backH = 12;
            if (mouseX >= backX && mouseX <= backX + backW &&
                    mouseY >= backY && mouseY <= backY + backH) {
                alloyHandler.backToMaterials();
                return true;
            }
        }

        if (mouseX >= px + REFRESH_BTN_X && mouseX <= px + REFRESH_BTN_X + REFRESH_BTN_W &&
                mouseY >= py + REFRESH_BTN_Y && mouseY <= py + REFRESH_BTN_Y + REFRESH_BTN_H) {
            refreshMoltenFluids();
            return true;
        }

        if (mouseX >= px + 5 && mouseX <= px + 5 + pw - 10 &&
                mouseY >= py + SEARCH_BOX_Y && mouseY <= py + SEARCH_BOX_Y + SEARCH_BOX_H) {
            interactionHandler.setSearchBoxFocused(true);
            return true;
        }

        if (interactionHandler.isSearchBoxFocused()) {
            interactionHandler.setSearchBoxFocused(false);
        }

        return handleCardClick(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isVisible) return false;

        if (interactionHandler.isSearchBoxFocused()) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                return interactionHandler.handleKeyPressed(keyCode, scanCode, modifiers);
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                interactionHandler.setSearchBoxFocused(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                interactionHandler.setSearchBoxFocused(false);
                return true;
            }
            // 其他所有按键：返回 true 阻止传播，但不调用 event.setCanceled
            return true;
        }

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
        if (isVisible && !visible) {
            visible = true;
        }

        checkPendingHighlightUpdate();
        updateAnimation();

        if (!isVisible && !isAnimating) return;

        checkWindowResize();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GlStateManager._disableScissorTest();

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (cachedFont == null) cachedFont = font;

        int px = this.x + animationOffset;
        int py = this.y;
        int pw = this.width;
        int ph = this.height;

        if (px + pw < 0) {
            RenderSystem.enableDepthTest();
            return;
        }

        GuiComponent.fill(poseStack, px, py, px + pw, py + ph, 0xFF1A1A1A);

        GuiComponent.fill(poseStack, px, py, px + 1, py + ph, 0x33FFFFFF);
        GuiComponent.fill(poseStack, px + pw - 1, py, px + pw, py + ph, 0x22FFFFFF);
        GuiComponent.fill(poseStack, px, py, px + pw, py + 1, 0x22FFFFFF);
        GuiComponent.fill(poseStack, px, py + ph - 1, px + pw, py + ph, 0x22FFFFFF);

        renderTitleBar(poseStack, px, py, pw, font);
        renderRefreshButton(poseStack, px, py, mouseX, mouseY, font);

        if (isAlloyMode) {
            renderAlloySearchBox(poseStack, px, py, pw, font);
        } else {
            renderSearchBox(poseStack, px, py, pw, font);
        }

        if (isAlloyMode) {
            renderAlloyContent(poseStack, px, py, pw, ph, mouseX, mouseY, font);
        } else {
            renderNormalContent(poseStack, px, py, pw, ph, mouseX, mouseY, font);
        }

        RenderSystem.enableDepthTest();
    }

    // ==================== 渲染辅助方法 ====================

    private void enableScissor(int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        int scale = (int) mc.getWindow().getGuiScale();
        int screenX = x * scale;
        int screenY = mc.getWindow().getScreenHeight() - (y + height) * scale;
        int screenW = Math.max(0, width * scale);
        int screenH = Math.max(0, height * scale);
        GlStateManager._scissorBox(screenX, screenY, screenW, screenH);
    }

    private void renderTitleBar(PoseStack poseStack, int px, int py, int pw, Font font) {
        GuiComponent.fill(poseStack, px + 1, py + 1, px + pw - 2, py + TITLE_BAR_HEIGHT, 0xFF2A2A2A);
        String title = isAlloyMode ?
                new TranslatableComponent("gui.tinkerssearch.alloy_query").getString() :
                "Tinker's Search";
        font.draw(poseStack, (isAlloyMode ? "§b" : "§6") + title, px + 5, py + 5, 0xFFFFFF);
    }

    private void renderRefreshButton(PoseStack poseStack, int px, int py, int mouseX, int mouseY, Font font) {
        int btnX = px + REFRESH_BTN_X;
        int btnY = py + REFRESH_BTN_Y;
        boolean hover = isHovered(btnX, btnY, REFRESH_BTN_W, REFRESH_BTN_H, mouseX, mouseY);

        int color = hover ? 0xFF555555 : 0xFF333333;
        GuiComponent.fill(poseStack, btnX, btnY, btnX + REFRESH_BTN_W, btnY + REFRESH_BTN_H, color);
        GuiComponent.fill(poseStack, btnX, btnY, btnX + REFRESH_BTN_W, btnY + 1, 0xFF666666);
        GuiComponent.fill(poseStack, btnX, btnY + REFRESH_BTN_H - 1, btnX + REFRESH_BTN_W, btnY + REFRESH_BTN_H, 0xFF666666);
        GuiComponent.fill(poseStack, btnX, btnY, btnX + 1, btnY + REFRESH_BTN_H, 0xFF666666);
        GuiComponent.fill(poseStack, btnX + REFRESH_BTN_W - 1, btnY, btnX + REFRESH_BTN_W, btnY + REFRESH_BTN_H, 0xFF666666);

        font.draw(poseStack, new TranslatableComponent("gui.tinkerssearch.refresh"), btnX + 4, btnY + 3, 0xCCCCCC);
    }

    private void renderSearchBox(PoseStack poseStack, int px, int py, int pw, Font font) {
        boolean focused = interactionHandler.isSearchBoxFocused();
        String keyword = interactionHandler.getSearchKeyword();

        int boxX = px + 5;
        int boxY = py + SEARCH_BOX_Y;
        int boxW = pw - 10;
        int boxH = SEARCH_BOX_H;

        int bg = focused ? 0xFF3A3A3A : 0xFF222222;
        GuiComponent.fill(poseStack, boxX, boxY, boxX + boxW, boxY + boxH, bg);

        int border = focused ? 0xFF888888 : 0xFF444444;
        GuiComponent.fill(poseStack, boxX, boxY, boxX + boxW, boxY + 1, border);
        GuiComponent.fill(poseStack, boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, border);
        GuiComponent.fill(poseStack, boxX, boxY, boxX + 1, boxY + boxH, border);
        GuiComponent.fill(poseStack, boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, border);

        if (keyword.isEmpty()) {
            font.draw(poseStack, new TranslatableComponent("gui.tinkerssearch.search_hint"), boxX + 4, boxY + 4, 0x666666);
        } else {
            font.draw(poseStack, keyword, boxX + 4, boxY + 4, 0xFFFFFF);
        }

        if (focused && (System.currentTimeMillis() / 500 % 2 == 0)) {
            int cursorX = boxX + 4 + font.width(keyword);
            if (cursorX < boxX + boxW - 2) {
                GuiComponent.fill(poseStack, cursorX, boxY + 2, cursorX + 1, boxY + boxH - 2, 0xFFFFFFFF);
            }
        }

        int total = allFluids.size();
        int matched = displayedFluids.size();
        String countStr = "§8" + matched + "/" + total;
        font.draw(poseStack, countStr, px + pw - 35, boxY + 4, 0x888888);

        int lineY = boxY + boxH + 4;
        GuiComponent.fill(poseStack, px + 5, lineY, px + pw - 5 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING, lineY + 1, 0xFF333333);
    }

    private void renderAlloySearchBox(PoseStack poseStack, int px, int py, int pw, Font font) {
        boolean focused = interactionHandler.isSearchBoxFocused();
        String keyword = interactionHandler.getSearchKeyword();

        int boxX = px + 5;
        int boxY = py + SEARCH_BOX_Y;
        int boxW = pw - 10;
        int boxH = SEARCH_BOX_H;

        int bg = focused ? 0xFF2A2A3A : 0xFF1A1A2A;
        GuiComponent.fill(poseStack, boxX, boxY, boxX + boxW, boxY + boxH, bg);

        int border = focused ? 0xFF6688FF : 0xFF4466AA;
        GuiComponent.fill(poseStack, boxX, boxY, boxX + boxW, boxY + 1, border);
        GuiComponent.fill(poseStack, boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, border);
        GuiComponent.fill(poseStack, boxX, boxY, boxX + 1, boxY + boxH, border);
        GuiComponent.fill(poseStack, boxX + boxW - 1, boxY, boxX + boxW, boxY + boxH, border);

        String displayText = keyword.isEmpty() ? "§7" + new TranslatableComponent("gui.tinkerssearch.alloy_search_hint").getString() : keyword;
        font.draw(poseStack, displayText, boxX + 4, boxY + 4, 0xFFFFFF);

        if (focused && (System.currentTimeMillis() / 500 % 2 == 0)) {
            int cursorX = boxX + 4 + font.width(keyword);
            if (cursorX < boxX + boxW - 2) {
                GuiComponent.fill(poseStack, cursorX, boxY + 2, cursorX + 1, boxY + boxH - 2, 0xFFFFFFFF);
            }
        }

        int lineY = boxY + boxH + 4;
        GuiComponent.fill(poseStack, px + 5, lineY, px + pw - 5 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING, lineY + 1, 0xFF333366);
    }

    private void renderNormalContent(PoseStack poseStack, int px, int py, int pw, int ph, int mouseX, int mouseY, Font font) {
        if (!displayedFavoriteFluids.isEmpty()) {
            int favLabelY = py + CARDS_START_Y;
            font.draw(poseStack, "§6" + new TranslatableComponent("gui.tinkerssearch.favorites").getString(), px + 5, favLabelY, 0xFFFFFF);

            int favStartY = py + getFavoriteAreaStartY();
            int favAreaHeight = getFavoriteAreaHeight();

            if (favAreaHeight > 0) {
                GlStateManager._enableScissorTest();
                enableScissor(px + 5, favStartY, pw - 10 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING, favAreaHeight);
                RenderSystem.disableDepthTest();
                renderFavoriteCards(poseStack, px, py, pw, ph, mouseX, mouseY, font, favStartY, favAreaHeight);
                GlStateManager._disableScissorTest();
                renderScrollBar(poseStack, px, favStartY, favAreaHeight, pw, favScrollOffset, maxFavScrollOffset);
            }

            int sepY = favStartY + favAreaHeight + SECTION_SPACING;
            GuiComponent.fill(poseStack, px + 5, sepY, px + pw - 5 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING, sepY + 1, 0xFF444444);

            int smelterLabelY = sepY + SECTION_SPACING;
            font.draw(poseStack, "§e" + new TranslatableComponent("gui.tinkerssearch.smeltery").getString(), px + 5, smelterLabelY, 0xFFFFFF);
        } else {
            int smelterLabelY = py + CARDS_START_Y;
            font.draw(poseStack, "§e" + new TranslatableComponent("gui.tinkerssearch.smeltery").getString(), px + 5, smelterLabelY, 0xFFFFFF);
        }

        int clipStartY = py + getSmelteryAreaStartY();
        int clipEndY = py + ph - 4;
        int clipWidth = pw - 10 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING;
        int clipHeight = clipEndY - clipStartY;

        if (clipHeight > 0) {
            GlStateManager._enableScissorTest();
            enableScissor(px + 5, clipStartY, clipWidth, clipHeight);
            RenderSystem.disableDepthTest();
            renderCards(poseStack, px, py, pw, ph, mouseX, mouseY, font);
            GlStateManager._disableScissorTest();
            renderScrollBar(poseStack, px, clipStartY, clipHeight, pw, scrollOffset, maxScrollOffset);
        }
    }

    // ==================== 温度获取 ====================

    public int getCurrentSmelteryTemperature() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;

        if (screen == null) {
            return 0;
        }

        BlockEntity target = null;
        String[] fieldNames = {"te", "tileEntity", "blockEntity", "smeltery", "tile"};
        for (String name : fieldNames) {
            try {
                Field field = screen.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object obj = field.get(screen);
                if (obj instanceof BlockEntity) {
                    target = (BlockEntity) obj;
                    this.smelteryTileEntity = target;
                    this.cachedTileEntity = target;
                    break;
                }
            } catch (Exception ignored) {}
        }

        if (target == null) {
            return 0;
        }

        try {
            if (target instanceof HeatingStructureBlockEntity) {
                HeatingStructureBlockEntity controller = (HeatingStructureBlockEntity) target;

                // ===== 获取燃料模块 =====
                FuelModule fuelModule = controller.getFuelModule();
                if (fuelModule != null) {
                    // 方法1: 正在燃烧的温度
                    int temp = fuelModule.getTemperature();
                    if (temp > 0) {
                        System.out.println("Tinker's Search: Burning temperature = " + temp + "°C");
                        return temp;
                    }

                    // 方法2: 获取燃料槽里的燃料温度
                    // FuelModule 里有 tankSupplier，获取燃料槽位置
                    Field tankSupplierField = FuelModule.class.getDeclaredField("tankSupplier");
                    tankSupplierField.setAccessible(true);
                    Supplier<List<BlockPos>> tankSupplier = (Supplier<List<BlockPos>>) tankSupplierField.get(fuelModule);
                    List<BlockPos> tankPositions = tankSupplier.get();

                    if (tankPositions != null && !tankPositions.isEmpty()) {
                        Level level = controller.getLevel();
                        for (BlockPos pos : tankPositions) {
                            BlockEntity te = level.getBlockEntity(pos);
                            if (te != null) {
                                IFluidHandler fluidHandler = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)
                                        .orElse(null);
                                if (fluidHandler != null) {
                                    FluidStack fluid = fluidHandler.getFluidInTank(0);
                                    if (!fluid.isEmpty()) {
                                        // 从燃料流体获取温度
                                        try {
                                            Class<?> fuelLookupClass = Class.forName("slimeknights.tconstruct.library.recipe.fuel.MeltingFuelLookup");
                                            Method findFuelMethod = fuelLookupClass.getMethod("findFuel", Fluid.class);
                                            Object fuel = findFuelMethod.invoke(null, fluid.getFluid());

                                            if (fuel != null) {
                                                Method getTempMethod = fuel.getClass().getMethod("getTemperature");
                                                int fuelTemp = (int) getTempMethod.invoke(fuel);
                                                if (fuelTemp > 0) {
                                                    System.out.println("Tinker's Search: Fuel tank temperature = " + fuelTemp + "°C");
                                                    return fuelTemp;
                                                }
                                            }
                                        } catch (Exception ignored) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Tinker's Search: Temperature read failed: " + e.getMessage());
            e.printStackTrace();
        }

        return 0;
    }

    /**
     * 刷新冶炼炉温度并更新到 alloyHandler
     */
    private void refreshSmelteryTemperature() {
        int currentTemp = getCurrentSmelteryTemperature();
        alloyHandler.refreshTemperature(currentTemp);
        System.out.println("Tinker's Search: Refreshed temperature: " + currentTemp + "°C");
    }

    /**
     * 外部调用刷新温度（用于 TinkersSearch 切换面板时）
     */
    public void refreshTemperature() {
        refreshSmelteryTemperature();
    }

    // ==================== 合金模式渲染 ====================

    private void renderAlloyContent(PoseStack poseStack, int px, int py, int pw, int ph, int mouseX, int mouseY, Font font) {
        int startY = py + CARDS_START_Y + 18;
        int endY = py + ph - 4;

        if (alloyHandler.getSelectedMaterial() == null) {
            renderAlloyMaterials(poseStack, px, py, pw, mouseX, mouseY, font, startY, endY);
            return;
        }

        int contentY = startY;

        String backText = new TranslatableComponent("gui.tinkerssearch.alloy_back").getString();
        font.draw(poseStack, "§7" + backText, px + 5, contentY, 0xCCCCCC);
        contentY += 14;

        FluidStack selected = alloyHandler.getSelectedMaterial();
        String selectedName = selected.getDisplayName().getString().replace("Molten ", "");
        font.draw(poseStack, "§b" + selectedName + " §7" + new TranslatableComponent("gui.tinkerssearch.alloy_results").getString() + ":", px + 5, contentY, 0xFFFFFF);
        contentY += 12;

        int currentTemp = getCurrentSmelteryTemperature();

        List<AlloyResultCalculator.AlloyChainResult> results = alloyHandler.getCurrentResults();
        if (results.isEmpty()) {
            font.draw(poseStack, "§7" + new TranslatableComponent("gui.tinkerssearch.alloy_no_recipes").getString(), px + 5, contentY, 0x666666);
            return;
        }

        int cardStartY = contentY;
        int cardAreaHeight = endY - cardStartY;

        int cardH = 44;
        for (AlloyResultCalculator.AlloyChainResult result : results) {
            AlloyRecipeData.AlloyFeasibility feasibility = result.getFeasibility();
            if (!feasibility.isFeasible() || result.getNext() != null) {
                cardH = 54;
                break;
            }
        }
        int totalContentHeight = results.size() * (cardH + 6) - 6;
        int maxOffset = Math.max(0, totalContentHeight - cardAreaHeight);
        alloyScrollOffset = Math.max(0, Math.min(alloyScrollOffset, maxOffset));

        int clipX = px + 5;
        int clipWidth = pw - 10 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING;

        GlStateManager._enableScissorTest();
        enableScissor(clipX, cardStartY, clipWidth, cardAreaHeight);
        RenderSystem.disableDepthTest();

        int actualStartY = cardStartY - alloyScrollOffset;
        int cardY = actualStartY;
        for (AlloyResultCalculator.AlloyChainResult result : results) {
            cardY = drawAlloyRecipeCard(poseStack, px, pw, cardY, result, currentTemp, mouseX, mouseY, font);
            cardY += 6;
            if (cardY > endY) break;
        }

        GlStateManager._disableScissorTest();

        if (maxOffset > 0) {
            renderScrollBar(poseStack, px, cardStartY, cardAreaHeight, pw, alloyScrollOffset, maxOffset);
        }
    }

    private void renderAlloyMaterials(PoseStack poseStack, int px, int py, int pw, int mouseX, int mouseY, Font font, int startY, int endY) {
        List<FluidStack> materials = alloyHandler.getFilteredMaterials();

        String title = new TranslatableComponent("gui.tinkerssearch.alloy_materials").getString();
        font.draw(poseStack, "§7" + title + " §8(" + materials.size() + ")", px + 5, startY, 0xCCCCCC);

        int cardStartY = startY + 18;

        int cardAreaHeight = endY - cardStartY;

        if (materials.isEmpty()) {
            font.draw(poseStack, "§7" + new TranslatableComponent("gui.tinkerssearch.no_match").getString(), px + 5, cardStartY + 10, 0x666666);
            return;
        }

        int margin = 4;
        int availableWidth = pw - 10 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING - margin * 2;
        int cardW = (availableWidth - CARD_SPACING * (ITEMS_PER_ROW - 1)) / ITEMS_PER_ROW;
        int cardH = CARD_HEIGHT;

        int totalRows = (materials.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
        int totalContentHeight = totalRows * (cardH + CARD_SPACING) - CARD_SPACING;

        int maxOffset = Math.max(0, totalContentHeight - cardAreaHeight);
        alloyScrollOffset = Math.max(0, Math.min(alloyScrollOffset, maxOffset));

        GlStateManager._enableScissorTest();
        enableScissor(px + 5 + margin, cardStartY, availableWidth, cardAreaHeight);
        RenderSystem.disableDepthTest();

        int actualStartY = cardStartY - alloyScrollOffset;

        for (int i = 0; i < materials.size(); i++) {
            int row = i / ITEMS_PER_ROW;
            int col = i % ITEMS_PER_ROW;
            int cardX = px + 5 + margin + col * (cardW + CARD_SPACING);
            int cardY = actualStartY + row * (cardH + CARD_SPACING);

            if (cardY + cardH < cardStartY || cardY > endY) continue;

            FluidStack fluid = materials.get(i);
            boolean hover = isHovered(cardX, cardY, cardW, cardH, mouseX, mouseY);
            boolean selected = alloyHandler.getSelectedMaterial() != null &&
                    alloyHandler.getSelectedMaterial().getFluid().getRegistryName()
                            .equals(fluid.getFluid().getRegistryName());

            drawAlloyMaterialCard(poseStack, cardX, cardY, cardW, cardH, fluid, hover, selected, font);
        }

        GlStateManager._disableScissorTest();

        if (maxOffset > 0) {
            renderScrollBar(poseStack, px, cardStartY, cardAreaHeight, pw, alloyScrollOffset, maxOffset);
        }
    }

    private void drawAlloyMaterialCard(PoseStack poseStack, int x, int y, int w, int h,
                                       FluidStack fluid, boolean hover, boolean selected, Font font) {
        String name = fluid.getDisplayName().getString().replace("Molten ", "");

        int bg = selected ? 0xFF1A3A6A : (hover ? 0xFF3A3A3A : 0xFF222222);
        GuiComponent.fill(poseStack, x, y, x + w, y + h, bg);

        int border = selected ? 0xFF4488FF : (hover ? 0xFF888888 : 0xFF333333);
        GuiComponent.fill(poseStack, x, y, x + w, y + 1, border);
        GuiComponent.fill(poseStack, x, y + h - 1, x + w, y + h, border);
        GuiComponent.fill(poseStack, x, y, x + 1, y + h, border);
        GuiComponent.fill(poseStack, x + w - 1, y, x + w, y + h, border);

        int iconSize = ICON_SIZE;
        int iconX = x + 3;
        int iconY = y + (h - iconSize) / 2;
        SmelteryDataHelper.drawFluidIcon(poseStack, iconX, iconY, fluid, iconSize);

        int textX = iconX + iconSize + ICON_TEXT_GAP;
        int maxTextW = w - iconSize - ICON_TEXT_GAP - 8;
        String truncated = truncateTextWithEllipsis(font, name, maxTextW);
        font.draw(poseStack, truncated, textX, y + (h - font.lineHeight) / 2 + 1, 0xFFFFFF);
    }

    private int drawAlloyRecipeCard(PoseStack poseStack, int px, int pw, int y,
                                    AlloyResultCalculator.AlloyChainResult result,
                                    int currentTemp, int mouseX, int mouseY, Font font) {
        AlloyRecipeData recipe = result.getRecipe();
        AlloyRecipeData.AlloyFeasibility feasibility = result.getFeasibility();

        int cardH = 44;
        if (!feasibility.isFeasible() || result.getNext() != null) {
            cardH = 54;
        }
        if (feasibility.isFeasible() && result.getNext() == null && feasibility.getMaxTimes() > 0) {
            cardH = 66;
        }

        int margin = 6;
        int x = px + margin;
        int w = pw - margin * 2;

        int maxW = pw - 10 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING - 2;
        if (w > maxW) {
            w = maxW;
        }

        boolean feasible = result.isFullyFeasible();
        int bg = feasible ? 0xFF1A3A1A : 0xFF3A2A1A;
        GuiComponent.fill(poseStack, x, y, x + w, y + cardH, bg);

        int border = feasible ? 0xFF00FF00 : 0xFFFF6600;
        GuiComponent.fill(poseStack, x, y, x + w, y + 1, border);
        GuiComponent.fill(poseStack, x, y + cardH - 1, x + w, y + cardH, border);
        GuiComponent.fill(poseStack, x, y, x + 1, y + cardH, border);
        GuiComponent.fill(poseStack, x + w - 1, y, x + w, y + cardH, border);

        String status = feasible ?
                "§a" + new TranslatableComponent("gui.tinkerssearch.alloy_feasible").getString() :
                "§c" + new TranslatableComponent("gui.tinkerssearch.alloy_infeasible").getString();
        font.draw(poseStack, status, x + 4, y + 2, 0xFFFFFF);

        String tempStr = feasibility.isTemperatureOk() ?
                "§a" + currentTemp + "°C" :
                "§c" + currentTemp + "°C §7/ §e" + feasibility.getRequiredTemp() + "°C";
        font.draw(poseStack, tempStr, x + w - font.width(tempStr) - 4, y + 2, 0xFFFFFF);

        String chainStr = result.formatChain();
        int maxChainWidth = w - 8;
        String truncatedChain = truncateTextWithEllipsis(font, chainStr, maxChainWidth);
        font.draw(poseStack, "§7" + truncatedChain, x + 4, y + 14, 0xCCCCCC);

        int lineY = y + 26;

        List<AlloyRecipeData.AlloyFeasibility.MissingFluid> missing = feasibility.getMissingFluids();
        if (!missing.isEmpty()) {
            StringBuilder sb = new StringBuilder("§c");
            sb.append(new TranslatableComponent("gui.tinkerssearch.alloy_missing_prefix").getString());
            sb.append(": ");
            for (int i = 0; i < missing.size(); i++) {
                if (i > 0) sb.append(", ");
                AlloyRecipeData.AlloyFeasibility.MissingFluid mf = missing.get(i);
                String name = mf.fluid.getDisplayName().getString().replace("Molten ", "");
                sb.append(name).append("(").append(mf.available).append("/").append(mf.needed).append("mB)");
            }
            String missingStr = sb.toString();
            int maxMissingWidth = w - 8;
            String truncatedMissing = truncateTextWithEllipsis(font, missingStr, maxMissingWidth);
            font.draw(poseStack, truncatedMissing, x + 4, lineY, 0xCCCCCC);
            lineY += 10;
        }

        if (result.getNext() != null) {
            String childName = result.getNext().getRecipe().getResult().getDisplayName().getString().replace("Molten ", "");
            String childStr = "§e" + new TranslatableComponent("gui.tinkerssearch.alloy_child").getString().replace("%s", childName);
            int maxChildWidth = w - 8;
            String truncatedChild = truncateTextWithEllipsis(font, childStr, maxChildWidth);
            font.draw(poseStack, truncatedChild, x + 4, lineY, 0xCCCCCC);
            lineY += 10;
        }

        // ===== 显示可执行次数（修复版） =====
        if (feasibility.isFeasible() && result.getNext() == null) {
            int maxTimes = feasibility.getMaxTimes();
            if (maxTimes > 0) {
                // 使用 TranslatableComponent 的参数功能
                String timesText = new TranslatableComponent("gui.tinkerssearch.alloy_times", maxTimes).getString();
                String timesStr = "§e" + timesText;
                String truncatedTimes = truncateTextWithEllipsis(font, timesStr, w - 8);
                font.draw(poseStack, truncatedTimes, x + 4, lineY, 0xCCCCCC);
            } else if (maxTimes == 0 && !feasibility.getMissingFluids().isEmpty()) {
                String timesText = new TranslatableComponent("gui.tinkerssearch.alloy_times", 0).getString();
                String timesStr = "§c" + timesText;
                String truncatedTimes = truncateTextWithEllipsis(font, timesStr, w - 8);
                font.draw(poseStack, truncatedTimes, x + 4, lineY, 0xCCCCCC);
            }
            lineY += 10;
        }

        return y + cardH;
    }

    private void renderCards(PoseStack poseStack, int px, int py, int pw, int ph, int mouseX, int mouseY, Font font) {
        int startY = py + getSmelteryAreaStartY() - scrollOffset;
        int endY = py + ph - 4;

        if (displayedFluids.isEmpty()) {
            String msg = interactionHandler.getSearchKeyword().isEmpty() ?
                    "§7" + new TranslatableComponent("gui.tinkerssearch.no_fluids").getString() :
                    "§7" + new TranslatableComponent("gui.tinkerssearch.no_match").getString();
            font.draw(poseStack, msg, px + 5, startY + scrollOffset + 10, 0x666666);
            return;
        }

        int cardW = (pw - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
        int cardH = CARD_HEIGHT;

        for (int i = 0; i < displayedFluids.size(); i++) {
            int row = i / ITEMS_PER_ROW;
            int col = i % ITEMS_PER_ROW;

            int cardX = px + 5 + col * (cardW + CARD_SPACING);
            int cardY = startY + row * (cardH + CARD_SPACING);

            if (cardY + cardH < py + getSmelteryAreaStartY() || cardY > endY) {
                continue;
            }

            FluidStack fluid = displayedFluids.get(i);
            boolean isHover = isHovered(cardX, cardY, cardW, cardH, mouseX, mouseY);
            drawSingleCard(poseStack, cardX, cardY, cardW, cardH, fluid, isHover, font, false);
        }
    }

    private void renderFavoriteCards(PoseStack poseStack, int px, int py, int pw, int ph, int mouseX, int mouseY, Font font, int areaStartY, int areaHeight) {
        int startY = areaStartY - favScrollOffset;
        int endY = areaStartY + areaHeight;

        if (displayedFavoriteFluids.isEmpty()) {
            font.draw(poseStack, "§7" + new TranslatableComponent("gui.tinkerssearch.no_favorites").getString(), px + 5, areaStartY + 10, 0x666666);
            return;
        }

        int cardW = (pw - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
        int cardH = CARD_HEIGHT;

        for (int i = 0; i < displayedFavoriteFluids.size(); i++) {
            int row = i / ITEMS_PER_ROW;
            int col = i % ITEMS_PER_ROW;

            int cardX = px + 5 + col * (cardW + CARD_SPACING);
            int cardY = startY + row * (cardH + CARD_SPACING);

            if (cardY + cardH < areaStartY || cardY > endY) {
                continue;
            }

            FluidStack fluid = displayedFavoriteFluids.get(i);
            boolean isHover = isHovered(cardX, cardY, cardW, cardH, mouseX, mouseY);
            drawSingleCard(poseStack, cardX, cardY, cardW, cardH, fluid, isHover, font, true);
        }
    }

    private void drawSingleCard(PoseStack poseStack, int x, int y, int w, int h, FluidStack fluid, boolean hover, Font font, boolean isFavorite) {
        String fluidName = fluid.getDisplayName().getString();

        ResourceLocation regName = fluid.getFluid().getRegistryName();
        if (fluidName == null || fluidName.isEmpty() || fluidName.equals("Air") || fluidName.equals("empty")) {
            if (regName != null) {
                fluidName = regName.getPath();
            }
        }

        boolean existsInSmeltery = true;
        if (isFavorite) {
            existsInSmeltery = false;
            for (FluidStack fs : allFluids) {
                if (fs.getFluid().getRegistryName().equals(regName)) {
                    existsInSmeltery = true;
                    break;
                }
            }
        }

        boolean isBottom = bottomFluidName != null && fluidName.equals(bottomFluidName) && existsInSmeltery;

        int bg = hover ? 0xFF3A3A3A : 0xFF222222;
        GuiComponent.fill(poseStack, x, y, x + w, y + h, bg);

        if (isBottom) {
            int green = 0xFF00FF00;
            GuiComponent.fill(poseStack, x, y, x + w, y + 1, green);
            GuiComponent.fill(poseStack, x, y + h - 1, x + w, y + h, green);
            GuiComponent.fill(poseStack, x, y, x + 1, y + h, green);
            GuiComponent.fill(poseStack, x + w - 1, y, x + w, y + h, green);
        } else {
            int border = hover ? 0xFF888888 : 0xFF333333;
            GuiComponent.fill(poseStack, x, y, x + w, y + 1, border);
            GuiComponent.fill(poseStack, x, y + h - 1, x + w, y + h, border);
            GuiComponent.fill(poseStack, x, y, x + 1, y + h, border);
            GuiComponent.fill(poseStack, x + w - 1, y, x + w, y + h, border);
        }

        int iconSize = ICON_SIZE;
        int iconX = x + 3;
        int iconY = y + (h - iconSize) / 2;
        SmelteryDataHelper.drawFluidIcon(poseStack, iconX, iconY, fluid, iconSize);

        int textX = iconX + iconSize + ICON_TEXT_GAP;
        int maxTextW = w - iconSize - ICON_TEXT_GAP - 8 - 16;

        String displayName = fluidName.replace("Molten ", "").replace("熔融", "");
        String truncatedName = truncateTextWithEllipsis(font, displayName, maxTextW);

        int nameColor = isBottom ? 0xFF00FF00 : 0xFFFFFF;
        font.draw(poseStack, truncatedName, textX, y + 4, nameColor);

        if (isFavorite && !existsInSmeltery) {
            String locked = new TranslatableComponent("gui.tinkerssearch.locked").getString();
            font.draw(poseStack, "§8" + locked, textX, y + 18, 0x888888);
        } else {
            int amount = fluid.getAmount();
            String amtStr;
            if (amount >= 1000) {
                amtStr = String.format("%.1fB", amount / 1000.0);
            } else {
                amtStr = amount + "mB";
            }
            font.draw(poseStack, "§8" + amtStr, textX, y + 18, 0x888888);
        }

        boolean isFav = FavoritesManager.isFavorite(fluid);
        int starSize = 12;
        int starX = x + w - starSize - 4;
        int starY = y + 4;
        String star = isFav ? "★" : "☆";
        int starColor = isFav ? 0xFFFFD700 : 0x666666;
        font.draw(poseStack, star, starX, starY, starColor);

        if (hover && existsInSmeltery) {
            font.draw(poseStack, "§7" + new TranslatableComponent("gui.tinkerssearch.click_move").getString(), x + 4, y + h - 10, 0x666666);
        }
    }

    private void renderScrollBar(PoseStack poseStack, int px, int areaStartY, int areaHeight, int panelWidth,
                                 int scrollOffset, int maxScrollOffset) {
        if (maxScrollOffset <= 0) return;

        int barX = px + panelWidth - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING;
        int barY = areaStartY;
        int barH = areaHeight;

        GuiComponent.fill(poseStack, barX, barY, barX + SCROLL_BAR_WIDTH, barY + barH, 0x33FFFFFF);

        float ratio = (float) scrollOffset / (float) maxScrollOffset;
        int thumbH = Math.max(16, (int) (barH * 0.3f));
        int thumbY = barY + (int) (ratio * (barH - thumbH));
        GuiComponent.fill(poseStack, barX, thumbY, barX + SCROLL_BAR_WIDTH, thumbY + thumbH, 0x99FFFFFF);
    }

    // ==================== 合金模式辅助方法 ====================

    private int getAlloyContentHeight() {
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

    private int getAlloyVisibleHeight() {
        int startY = this.y + CARDS_START_Y + 18;
        if (alloyHandler.getSelectedMaterial() != null) {
            startY = this.y + CARDS_START_Y + 18 + 14 + 12 + 12;
        } else {
            startY = this.y + CARDS_START_Y + 18 + 14 + 4;
        }
        int endY = this.y + this.height - 4;
        return Math.max(0, endY - startY);
    }

    private boolean handleAlloyCardClick(double mouseX, double mouseY, int button) {
        int px = this.x + animationOffset;
        int py = this.y;
        int pw = this.width;

        Font font = Minecraft.getInstance().font;

        if (alloyHandler.getSelectedMaterial() != null) {
            String backText = new TranslatableComponent("gui.tinkerssearch.alloy_back").getString();
            int backX = px + 5;
            int backY = py + CARDS_START_Y + 16;
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
            int startY = py + CARDS_START_Y + 32;
            int endY = py + this.height - 4;

            if (materials.isEmpty()) return false;

            int cardW = (pw - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
            int cardH = CARD_HEIGHT;

            int actualStartY = startY - alloyScrollOffset;

            for (int i = 0; i < materials.size(); i++) {
                int row = i / ITEMS_PER_ROW;
                int col = i % ITEMS_PER_ROW;
                int cardX = px + 5 + col * (cardW + CARD_SPACING);
                int cardY = actualStartY + row * (cardH + CARD_SPACING);

                if (cardY + cardH < startY || cardY > endY) continue;

                if (mouseX >= cardX && mouseX <= cardX + cardW &&
                        mouseY >= cardY && mouseY <= cardY + cardH) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        int currentTemp = getCurrentSmelteryTemperature();
                        alloyHandler.refreshTemperature(currentTemp);
                        alloyHandler.selectMaterial(materials.get(i), allFluids, currentTemp);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private FluidStack getAlloyFluidAt(double mouseX, double mouseY) {
        if (alloyHandler.getSelectedMaterial() != null) return null;

        int px = this.x + animationOffset;
        int py = this.y;
        int pw = this.width;

        List<FluidStack> materials = alloyHandler.getFilteredMaterials();
        int startY = py + CARDS_START_Y + 32;
        int endY = py + this.height - 4;

        if (materials.isEmpty()) return null;

        int cardW = (pw - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
        int cardH = CARD_HEIGHT;

        int actualStartY = startY - alloyScrollOffset;

        for (int i = 0; i < materials.size(); i++) {
            int row = i / ITEMS_PER_ROW;
            int col = i % ITEMS_PER_ROW;
            int cardX = px + 5 + col * (cardW + CARD_SPACING);
            int cardY = actualStartY + row * (cardH + CARD_SPACING);

            if (cardY + cardH < startY || cardY > endY) continue;

            if (mouseX >= cardX && mouseX <= cardX + cardW &&
                    mouseY >= cardY && mouseY <= cardY + cardH) {
                return materials.get(i);
            }
        }
        return null;
    }

    // ==================== 工具方法 ====================

    private static boolean isHovered(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private String truncateTextWithEllipsis(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) return "";

        int textWidth = font.width(text);
        if (textWidth <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return "";
        }

        int left = 0;
        int right = text.length();
        int bestLength = 0;

        while (left <= right) {
            int mid = (left + right) / 2;
            String testStr = text.substring(0, mid) + ellipsis;
            int testWidth = font.width(testStr);

            if (testWidth <= maxWidth) {
                bestLength = mid;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        if (bestLength <= 0) {
            return ellipsis;
        }

        return text.substring(0, bestLength) + ellipsis;
    }

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {}
}