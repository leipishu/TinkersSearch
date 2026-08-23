package top.leipishu.tinkerssearch.client.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;
import top.leipishu.tinkerssearch.config.PanelConfig;
import top.leipishu.tinkerssearch.utils.FavoritesManager;
import top.leipishu.tinkerssearch.utils.SearchHelper;
import top.leipishu.tinkerssearch.utils.SmelteryClickHandler;
import top.leipishu.tinkerssearch.utils.SmelteryDataHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    private static final int TITLE_CARD_SPACING = 4; // 标题与卡片之间的间距

    public FloatingSearchPanel() {
        super(0, 0, PANEL_WIDTH, 100, new TextComponent("Search Panel"));
        this.visible = false;
        this.isVisible = false;

        this.jeiAvailable = ModList.get().isLoaded("jei");
        System.out.println("Tinker's Search: JEI available in panel: " + jeiAvailable);

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
        if (displayedFluids.isEmpty() && displayedFavoriteFluids.isEmpty()) return null;

        int px = this.x + animationOffset;
        int py = this.y;
        int pw = this.width;

        int cardW = (pw - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
        int cardH = CARD_HEIGHT;

        // 检查冶炼炉区域
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

        // 检查收藏区域
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
            // 没有收藏时：从搜索框下方直接开始
            return this.y + CARDS_START_Y + SECTION_LABEL_HEIGHT + TITLE_CARD_SPACING;
        }
        return favEndY + SECTION_SPACING + SECTION_LABEL_HEIGHT + TITLE_CARD_SPACING;
    }

    // ==================== 可见性控制 ====================

    public void setVisible(boolean visible) {
        if (this.isVisible == visible && !isAnimating) return;
        if (!visible && !this.isVisible && !isAnimating) return;

        if (!visible) {
            interactionHandler.setSearchBoxFocused(false);
            scrollOffset = 0;
            favScrollOffset = 0;
            pendingHighlightUpdate = false;
            targetOffset = -this.width;
            isAnimating = true;
            animationStartTime = System.currentTimeMillis();
        } else {
            updatePanelPosition();
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
            }
            return;
        }

        float eased = 1.0f - (float) Math.pow(1.0f - progress, 3);
        int startOffset = targetOffset == 0 ? -this.width : 0;
        animationOffset = startOffset + (int) ((targetOffset - startOffset) * eased);
    }

    // ==================== 滚动 ====================

    public void updateMaxScrollOffset() {
        // 冶炼炉区域滚动
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

        // 收藏区域滚动
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

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isVisible || (displayedFluids.isEmpty() && displayedFavoriteFluids.isEmpty())) return false;

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

        // ===== 从 FavoritesManager 获取收藏列表 =====
        List<FluidStack> favList = FavoritesManager.getFavorites();
        for (FluidStack favFluid : favList) {
            if (favFluid == null || favFluid.isEmpty()) continue;

            ResourceLocation rl = favFluid.getFluid().getRegistryName();
            if (rl == null) continue;

            // 检查是否在冶炼炉中
            FluidStack matched = null;
            for (FluidStack fs : allFluids) {
                if (fs.getFluid().getRegistryName().equals(rl)) {
                    matched = fs;
                    break;
                }
            }

            if (matched != null) {
                // 在冶炼炉中：使用冶炼炉中的 FluidStack（包含正确数量）
                allFavoriteFluids.add(matched.copy());
            } else {
                // 不在冶炼炉中：使用收藏中保存的 FluidStack（数量为 1，但显示时用锁定文本）
                allFavoriteFluids.add(favFluid.copy());
            }
        }

        String keyword = interactionHandler.getSearchKeyword();
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

        // 乐观更新：立即将底部高亮设为当前流体
        this.bottomFluidName = fluidStack.getDisplayName().getString();

        boolean success = SmelteryClickHandler.clickFluidByStack(fluidStack);
        if (success) {
            scheduleHighlightUpdate();
        }
        return success;
    }

    /**
     * 处理卡片点击
     */
    private boolean handleCardClick(double mouseX, double mouseY, int button) {
        if (!isVisible) return false;

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

                // 判断是否在冶炼炉中存在
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

                // 判断是否点击了图标区域
                int iconSize = ICON_SIZE;
                int iconX = cardX + 3;
                int iconY = cardY + (cardH - iconSize) / 2;
                int padding = 2;
                boolean onIcon = mouseX >= iconX - padding && mouseX <= iconX + iconSize + padding &&
                        mouseY >= iconY - padding && mouseY <= iconY + iconSize + padding;

                // ===== 收藏按钮（星形）- 始终可点击 =====
                int starSize = 12;
                int starX = cardX + cardW - starSize - 4;
                int starY = cardY + 4;
                boolean onStar = mouseX >= starX && mouseX <= starX + starSize &&
                        mouseY >= starY && mouseY <= starY + starSize;
                if (onStar) {
                    // 使用 FluidStack 切换收藏
                    FavoritesManager.toggleFavorite(fluid);
                    refreshMoltenFluids();
                    return true;
                }

                // ===== 图标区域 -> JEI（始终可点击，无论是否在炉中） =====
                if (onIcon && jeiAvailable) {
                    return interactionHandler.handleJeiIconClick(fluid, button);
                }

                // ===== 收藏区域且不存在于冶炼炉：阻止卡片主体点击 =====
                if (inFavArea && !existsInSmeltery) {
                    // 只消费事件，不执行任何操作
                    return true;
                }

                // ===== 卡片主体 -> 移动到底部（仅左键，且必须在炉中） =====
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

        // 检查刷新按钮
        if (mouseX >= px + REFRESH_BTN_X && mouseX <= px + REFRESH_BTN_X + REFRESH_BTN_W &&
                mouseY >= py + REFRESH_BTN_Y && mouseY <= py + REFRESH_BTN_Y + REFRESH_BTN_H) {
            refreshMoltenFluids();
            return true;
        }

        // 检查搜索框
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

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (interactionHandler.isSearchBoxFocused()) {
                interactionHandler.setSearchBoxFocused(false);
                return true;
            }
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

        // ===== 背景 =====
        GuiComponent.fill(poseStack, px, py, px + pw, py + ph, 0xFF1A1A1A);

        // ===== 边框 =====
        GuiComponent.fill(poseStack, px, py, px + 1, py + ph, 0x33FFFFFF);
        GuiComponent.fill(poseStack, px + pw - 1, py, px + pw, py + ph, 0x22FFFFFF);
        GuiComponent.fill(poseStack, px, py, px + pw, py + 1, 0x22FFFFFF);
        GuiComponent.fill(poseStack, px, py + ph - 1, px + pw, py + ph, 0x22FFFFFF);

        renderTitleBar(poseStack, px, py, pw, font);
        renderRefreshButton(poseStack, px, py, mouseX, mouseY, font);
        renderSearchBox(poseStack, px, py, pw, font);

        // ===== 收藏区域 =====
        if (!displayedFavoriteFluids.isEmpty()) {
            // 收藏标签
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

            // ===== 分隔线（仅在收藏不为空时显示） =====
            int sepY = favStartY + favAreaHeight + SECTION_SPACING;
            GuiComponent.fill(poseStack, px + 5, sepY, px + pw - 5 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING, sepY + 1, 0xFF444444);

            // ===== 冶炼炉标签（有收藏时显示在分隔线下方） =====
            int smelterLabelY = sepY + SECTION_SPACING;
            font.draw(poseStack, "§e" + new TranslatableComponent("gui.tinkerssearch.smeltery").getString(), px + 5, smelterLabelY, 0xFFFFFF);
        } else {
            // ===== 没有收藏时：直接显示冶炼炉标签 =====
            int smelterLabelY = py + CARDS_START_Y;
            font.draw(poseStack, "§e" + new TranslatableComponent("gui.tinkerssearch.smeltery").getString(), px + 5, smelterLabelY, 0xFFFFFF);
        }

        // ===== 冶炼炉区域卡片 =====
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
        font.draw(poseStack, "§6Tinker's Search", px + 5, py + 5, 0xFFFFFF);
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

    private void renderCards(PoseStack poseStack, int px, int py, int pw, int ph, int mouseX, int mouseY, Font font) {
        int startY = py + getSmelteryAreaStartY() - scrollOffset;
        int endY = py + ph - 4;

        if (displayedFluids.isEmpty()) {
            String msg = interactionHandler.getSearchKeyword().isEmpty() ? "§7" + new TranslatableComponent("gui.tinkerssearch.no_fluids").getString() : "§7" + new TranslatableComponent("gui.tinkerssearch.no_match").getString();
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


    /**
     * 绘制单个卡片
     */
    /**
     * 绘制单个卡片
     */
    private void drawSingleCard(PoseStack poseStack, int x, int y, int w, int h, FluidStack fluid, boolean hover, Font font, boolean isFavorite) {
        // ===== 获取流体名称 =====
        String fluidName = fluid.getDisplayName().getString();

        // 如果名称异常，使用注册名
        ResourceLocation regName = fluid.getFluid().getRegistryName();
        if (fluidName == null || fluidName.isEmpty() || fluidName.equals("Air") || fluidName.equals("empty")) {
            if (regName != null) {
                fluidName = regName.getPath();
            }
        }

        // ===== 判断是否在冶炼炉中存在 =====
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

        // ===== 是否在底部（仅当存在于冶炼炉中） =====
        boolean isBottom = bottomFluidName != null && fluidName.equals(bottomFluidName) && existsInSmeltery;

        // ===== 卡片背景 =====
        int bg = hover ? 0xFF3A3A3A : 0xFF222222;
        GuiComponent.fill(poseStack, x, y, x + w, y + h, bg);

        // ===== 卡片边框 =====
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

        // ===== 流体图标 =====
        int iconSize = ICON_SIZE;
        int iconX = x + 3;
        int iconY = y + (h - iconSize) / 2;
        SmelteryDataHelper.drawFluidIcon(poseStack, iconX, iconY, fluid, iconSize);

        // ===== 流体名称 =====
        int textX = iconX + iconSize + ICON_TEXT_GAP;
        int maxTextW = w - iconSize - ICON_TEXT_GAP - 8 - 16;

        String displayName = fluidName.replace("Molten ", "").replace("熔融", "");
        String truncatedName = truncateTextWithEllipsis(font, displayName, maxTextW);

        int nameColor = isBottom ? 0xFF00FF00 : 0xFFFFFF;
        font.draw(poseStack, truncatedName, textX, y + 4, nameColor);

        // ===== 显示数量或锁定状态 =====
        if (isFavorite && !existsInSmeltery) {
            // 不在冶炼炉中：显示锁定文本
            String locked = new TranslatableComponent("gui.tinkerssearch.locked").getString();
            font.draw(poseStack, "§8" + locked, textX, y + 18, 0x888888);
        } else {
            // 在冶炼炉中：显示数量
            int amount = fluid.getAmount();
            String amtStr;
            if (amount >= 1000) {
                amtStr = String.format("%.1fB", amount / 1000.0);
            } else {
                amtStr = amount + "mB";
            }
            font.draw(poseStack, "§8" + amtStr, textX, y + 18, 0x888888);
        }

        // ===== 收藏按钮（星形） =====
        // 使用 isFavorite(fluid) 判断
        boolean isFav = FavoritesManager.isFavorite(fluid);
        int starSize = 12;
        int starX = x + w - starSize - 4;
        int starY = y + 4;
        String star = isFav ? "★" : "☆";
        int starColor = isFav ? 0xFFFFD700 : 0x666666;
        font.draw(poseStack, star, starX, starY, starColor);

        // ===== 悬停提示（仅当存在于冶炼炉中） =====
        if (hover && existsInSmeltery) {
            font.draw(poseStack, "§7左键: 移至底部", x + 4, y + h - 10, 0x666666);
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