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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import top.leipishu.tinkerssearch.config.PanelConfig;
import top.leipishu.tinkerssearch.utils.SearchHelper;
import top.leipishu.tinkerssearch.utils.SmelteryClickHandler;
import top.leipishu.tinkerssearch.utils.SmelteryDataHelper;

import java.util.ArrayList;
import java.util.List;
import org.lwjgl.glfw.GLFW;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

public class FloatingSearchPanel extends AbstractWidget {

    // ===== 状态 =====
    private boolean isVisible = false;
    private boolean isAnimating = false;

    // ===== 数据 =====
    private List<FluidStack> allFluids = new ArrayList<>();
    private List<FluidStack> displayedFluids = new ArrayList<>();
    private BlockEntity smelteryTileEntity = null;
    private BlockEntity cachedTileEntity = null;

    // ===== 最下方流体名称（用于高亮） =====
    private String bottomFluidName = null;

    // ===== 滚动 =====
    private int scrollOffset = 0;
    private int maxScrollOffset = 0;

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
        if (displayedFluids == null || displayedFluids.isEmpty()) return null;

        int px = this.x + animationOffset;
        int py = this.y;
        int pw = this.width;

        int cardW = (pw - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
        int cardH = CARD_HEIGHT;
        int startY = py + CARDS_START_Y - scrollOffset;

        for (int i = 0; i < displayedFluids.size(); i++) {
            int row = i / ITEMS_PER_ROW;
            int col = i % ITEMS_PER_ROW;
            int cardX = px + 5 + col * (cardW + CARD_SPACING);
            int cardY = startY + row * (cardH + CARD_SPACING);

            if (mouseX >= cardX && mouseX <= cardX + cardW &&
                    mouseY >= cardY && mouseY <= cardY + cardH) {
                return displayedFluids.get(i);
            }
        }
        return null;
    }

    // ==================== 可见性控制 ====================

    public void setVisible(boolean visible) {
        if (this.isVisible == visible && !isAnimating) return;
        if (!visible && !this.isVisible && !isAnimating) return;

        if (!visible) {
            interactionHandler.setSearchBoxFocused(false);
            scrollOffset = 0;
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
        if (displayedFluids.isEmpty()) {
            maxScrollOffset = 0;
            return;
        }

        int py = this.y;
        int pw = this.width;
        int ph = this.height;

        int startY = py + CARDS_START_Y;
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

    public void setScrollOffset(int offset) {
        scrollOffset = Math.max(0, Math.min(offset, maxScrollOffset));
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isVisible || displayedFluids.isEmpty()) return false;

        int actualX = this.x + animationOffset;
        if (mouseX < actualX || mouseX > actualX + this.width ||
                mouseY < this.y || mouseY > this.y + this.height) {
            return false;
        }

        int newOffset = scrollOffset - (int) (delta * SCROLL_SPEED);
        setScrollOffset(newOffset);
        return true;
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
        bottomFluidName = null;

        BlockEntity target = smelteryTileEntity != null ? smelteryTileEntity : cachedTileEntity;
        if (target == null) return;

        allFluids = SmelteryDataHelper.getMoltenFluids(target);

        FluidStack bottomFluid = SmelteryDataHelper.getBottomFluid(target);
        if (bottomFluid != null) {
            bottomFluidName = bottomFluid.getDisplayName().getString();
        }

        String keyword = interactionHandler.getSearchKeyword();
        if (keyword == null || keyword.trim().isEmpty()) {
            displayedFluids = new ArrayList<>(allFluids);
        } else {
            displayedFluids = SearchHelper.filterFluids(allFluids, keyword);
        }

        interactionHandler.setDataRefs(allFluids, displayedFluids);

        updateMaxScrollOffset();
        scrollOffset = 0;
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
        boolean success = SmelteryClickHandler.clickFluidByStack(fluidStack);
        if (success) {
            scheduleHighlightUpdate();
        }
        return success;
    }

    // ==================== 鼠标和键盘事件 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible && !isAnimating) return false;
        int actualX = this.x + animationOffset;
        if (mouseX >= actualX && mouseX <= actualX + this.width &&
                mouseY >= this.y && mouseY <= this.y + this.height) {
            return interactionHandler.handleMouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isVisible) return false;

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

        // ===== 保存并重置 OpenGL 状态 =====
        // 使用 GlStateManager 保存深度测试状态
        // 注意：GlStateManager 没有直接的 isEnabled 方法，我们直接禁用然后恢复
        // 因为无法可靠地检测当前状态，我们强制禁用，最后再启用
        // 这样其他模组如果依赖深度测试，可能会受影响，但这是解决覆盖问题的最可靠方式

        // 先保存当前 blend 状态（如果可以的话）
        // 对于深度测试，我们直接禁用
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

        // ===== 背景（完全不透明） =====
        GuiComponent.fill(poseStack, px, py, px + pw, py + ph, 0xFF1A1A1A);

        // ===== 边框 =====
        GuiComponent.fill(poseStack, px, py, px + 1, py + ph, 0x33FFFFFF);
        GuiComponent.fill(poseStack, px + pw - 1, py, px + pw, py + ph, 0x22FFFFFF);
        GuiComponent.fill(poseStack, px, py, px + pw, py + 1, 0x22FFFFFF);
        GuiComponent.fill(poseStack, px, py + ph - 1, px + pw, py + ph, 0x22FFFFFF);

        renderTitleBar(poseStack, px, py, pw, font);
        renderRefreshButton(poseStack, px, py, mouseX, mouseY, font);
        renderSearchBox(poseStack, px, py, pw, font);

        // ===== 卡片裁剪区域 =====
        int clipStartY = py + CARDS_START_Y;
        int clipEndY = py + ph - 4;
        int clipWidth = pw - 10 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING;
        int clipHeight = clipEndY - clipStartY;

        GlStateManager._enableScissorTest();
        enableScissor(px + 5, clipStartY, clipWidth, clipHeight);

        // 再次确保深度测试禁用
        RenderSystem.disableDepthTest();

        renderCards(poseStack, px, py, pw, ph, mouseX, mouseY, font);

        GlStateManager._disableScissorTest();

        renderScrollBar(poseStack, px, py, pw, ph);

        // ===== 恢复 OpenGL 状态 =====
        RenderSystem.enableDepthTest();
    }

    // ==================== 新增：仅渲染文字（用于覆盖冶炼炉） ====================

    /**
     * 仅渲染面板中的文字内容
     * 用于在 onScreenDrawPost 中强制重绘文字，覆盖冶炼炉 UI
     */
    public void renderTextOnly(PoseStack poseStack) {
        if (!isVisible && !isAnimating) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int px = this.x + animationOffset;
        int py = this.y;
        int pw = this.width;
        int ph = this.height;

        // 强制重置渲染状态
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GlStateManager._disableScissorTest();
        RenderSystem.disableTexture();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // 1. 重新绘制标题
        font.draw(poseStack, "§6Tinker's Search", px + 5, py + 5, 0xFFFFFF);

        // 2. 重新绘制刷新按钮文字
        font.draw(poseStack, new TranslatableComponent("gui.tinkerssearch.refresh"),
                px + REFRESH_BTN_X + 4, py + REFRESH_BTN_Y + 3, 0xCCCCCC);

        // 3. 重新绘制搜索框文字
        int boxX = px + 5;
        int boxY = py + SEARCH_BOX_Y;
        String keyword = interactionHandler.getSearchKeyword();

        if (keyword.isEmpty()) {
            font.draw(poseStack, new TranslatableComponent("gui.tinkerssearch.search_hint"),
                    boxX + 4, boxY + 4, 0x666666);
        } else {
            font.draw(poseStack, keyword, boxX + 4, boxY + 4, 0xFFFFFF);
        }

        // 4. 重新绘制数量统计
        int total = allFluids.size();
        int matched = displayedFluids.size();
        String countStr = "§8" + matched + "/" + total;
        font.draw(poseStack, countStr, px + pw - 35, boxY + 4, 0x888888);

        // 5. 重新绘制所有卡片文字（核心：覆盖冶炼炉标尺）
        if (displayedFluids.isEmpty()) {
            String msg = interactionHandler.getSearchKeyword().isEmpty() ? "§7暂无熔融物" : "§7未找到匹配";
            font.draw(poseStack, msg, px + 5, py + CARDS_START_Y - scrollOffset + 10, 0x666666);
            RenderSystem.enableTexture();
            return;
        }

        int cardW = (pw - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
        int cardH = CARD_HEIGHT;
        int startY = py + CARDS_START_Y - scrollOffset;
        int endY = py + ph - 4;

        for (int i = 0; i < displayedFluids.size(); i++) {
            int row = i / ITEMS_PER_ROW;
            int col = i % ITEMS_PER_ROW;
            int cardX = px + 5 + col * (cardW + CARD_SPACING);
            int cardY = startY + row * (cardH + CARD_SPACING);

            if (cardY + cardH < py + CARDS_START_Y || cardY > endY) {
                continue;
            }

            FluidStack fluid = displayedFluids.get(i);
            String fluidName = fluid.getDisplayName().getString();
            boolean isBottom = bottomFluidName != null && fluidName.equals(bottomFluidName);

            // 流体名称
            String displayName = fluidName.replace("Molten ", "").replace("熔融", "");
            int textX = cardX + ICON_SIZE + ICON_TEXT_GAP + 3;
            int maxTextW = cardW - ICON_SIZE - ICON_TEXT_GAP - 8;
            String truncatedName = truncateTextWithEllipsis(font, displayName, maxTextW);
            int nameColor = isBottom ? 0xFF00FF00 : 0xFFFFFF;
            font.draw(poseStack, truncatedName, textX, cardY + 4, nameColor);

            // 流体量
            int amount = fluid.getAmount();
            String amtStr = amount >= 1000 ? String.format("%.1fB", amount / 1000.0) : amount + "mB";
            font.draw(poseStack, "§8" + amtStr, textX, cardY + 18, 0x888888);

            // 悬停提示（需要获取当前鼠标位置）
            Minecraft mc2 = Minecraft.getInstance();
            int mouseX = (int)(mc2.mouseHandler.xpos() * mc2.getWindow().getGuiScaledWidth() / mc2.getWindow().getScreenWidth());
            int mouseY2 = (int)(mc2.mouseHandler.ypos() * mc2.getWindow().getGuiScaledHeight() / mc2.getWindow().getScreenHeight());

            if (isHovered(cardX, cardY, cardW, cardH, mouseX, mouseY2)) {
                if (jeiAvailable) {
                    font.draw(poseStack, "§7左键: 配方 右键: 用途", cardX + 4, cardY + cardH - 10, 0x666666);
                    font.draw(poseStack, "§7A键: 加入书签", cardX + 4, cardY + cardH - 2, 0x666666);
                } else {
                    font.draw(poseStack, "§7左键卡片: 移至底部", cardX + 4, cardY + cardH - 6, 0x666666);
                }
            }
        }

        RenderSystem.enableTexture();
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
        int startY = py + CARDS_START_Y - scrollOffset;
        int endY = py + ph - 4;

        if (displayedFluids.isEmpty()) {
            String msg = interactionHandler.getSearchKeyword().isEmpty() ? "§7暂无熔融物" : "§7未找到匹配";
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

            if (cardY + cardH < py + CARDS_START_Y || cardY > endY) {
                continue;
            }

            FluidStack fluid = displayedFluids.get(i);
            boolean isHover = isHovered(cardX, cardY, cardW, cardH, mouseX, mouseY);
            drawSingleCard(poseStack, cardX, cardY, cardW, cardH, fluid, isHover, font);
        }
    }

    /**
     * 绘制单个卡片
     */
    private void drawSingleCard(PoseStack poseStack, int x, int y, int w, int h, FluidStack fluid, boolean hover, Font font) {
        String fluidName = fluid.getDisplayName().getString();
        boolean isBottom = bottomFluidName != null && fluidName.equals(bottomFluidName);

        // ===== 卡片背景（完全不透明，覆盖后面的冶炼炉 UI） =====
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

        // ===== 流体名称（带省略号） =====
        int textX = iconX + iconSize + ICON_TEXT_GAP;
        int maxTextW = w - iconSize - ICON_TEXT_GAP - 8;

        String displayName = fluidName.replace("Molten ", "").replace("熔融", "");
        String truncatedName = truncateTextWithEllipsis(font, displayName, maxTextW);

        int nameColor = isBottom ? 0xFF00FF00 : 0xFFFFFF;
        font.draw(poseStack, truncatedName, textX, y + 4, nameColor);

        // ===== 流体量 =====
        int amount = fluid.getAmount();
        String amtStr = amount >= 1000 ? String.format("%.1fB", amount / 1000.0) : amount + "mB";
        font.draw(poseStack, "§8" + amtStr, textX, y + 18, 0x888888);

        // ===== JEI 交互提示 =====
        if (jeiAvailable && hover) {
            font.draw(poseStack, "§7左键: 配方 右键: 用途", x + 4, y + h - 10, 0x666666);
            font.draw(poseStack, "§7A键: 加入书签", x + 4, y + h - 2, 0x666666);
        } else if (hover) {
            font.draw(poseStack, "§7左键卡片: 移至底部", x + 4, y + h - 6, 0x666666);
        }
    }

    /**
     * 带省略号的文本截断
     */
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

    private void renderScrollBar(PoseStack poseStack, int px, int py, int pw, int ph) {
        if (maxScrollOffset <= 0) return;

        int barX = px + pw - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING;
        int barY = py + CARDS_START_Y;
        int barH = ph - CARDS_START_Y - 4;

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

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {}
}