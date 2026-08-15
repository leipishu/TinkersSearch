package top.leipishu.tinkerssearch.client.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
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

    // ==================== JEI 界面检测 ====================

    /**
     * 检测 JEI 配方/用途界面是否打开
     */
    public boolean isJeiRecipeGuiOpen() {
        if (!jeiAvailable) return false;

        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen == null) return false;

        String className = screen.getClass().getName();
        return className.contains("RecipesGui") ||
                className.contains("JeiRecipe") ||
                (className.contains("jei") && className.contains("Recipe"));
    }

    // ==================== 可见性控制 ====================

    public void setVisible(boolean visible) {
        // ===== 如果 JEI 界面打开，阻止关闭面板 =====
        if (!visible && isJeiRecipeGuiOpen()) {
            System.out.println("Tinker's Search: JEI recipe GUI open, preventing panel close");
            return;
        }

        if (this.isVisible == visible && !isAnimating) return;
        if (!visible && !this.isVisible && !isAnimating) return;

        if (!visible) {
            interactionHandler.setSearchBoxFocused(false);
            scrollOffset = 0;
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

    /**
     * 强制恢复面板可见性（供外部调用，防止被意外重置）
     */
    public void restoreVisibility() {
        if (isVisible && !visible) {
            System.out.println("Tinker's Search: Restoring panel visibility");
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

        BlockEntity target = smelteryTileEntity != null ? smelteryTileEntity : cachedTileEntity;
        if (target == null) return;

        allFluids = SmelteryDataHelper.getMoltenFluids(target);

        String keyword = interactionHandler.getSearchKeyword();
        if (keyword == null || keyword.trim().isEmpty()) {
            displayedFluids = new ArrayList<>(allFluids);
        } else {
            displayedFluids = SearchHelper.filterFluids(allFluids, keyword);
        }

        interactionHandler.setDataRefs(allFluids, displayedFluids);

        updateMaxScrollOffset();
        scrollOffset = 0;

        System.out.println("Tinker's Search: Refreshed - found " + allFluids.size() + " fluids, displayed " + displayedFluids.size());
    }

    private void onFluidClicked(List<FluidStack> fluids) {
        new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
            Minecraft.getInstance().execute(() -> {
                refreshMoltenFluids();
                System.out.println("Tinker's Search: Post-click sync refresh complete");
            });
        }).start();
    }

    // ==================== 卡片点击 ====================

    public boolean moveFluidToBottom(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.isEmpty()) return false;
        return SmelteryClickHandler.clickFluidByStack(fluidStack);
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

        // ===== ESC 键：完全忽略 =====
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return false;
        }

        // 先尝试 JEI 书签快捷键 (A键)
        if (interactionHandler.handleKeyPressedGlobal(keyCode, scanCode, modifiers)) {
            return true;
        }

        // 再尝试搜索框按键
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
        // ===== 关键：每次渲染时恢复面板状态，防止被外部事件重置 =====
        if (isVisible && !visible) {
            visible = true;
        }

        updateAnimation();

        if (!isVisible && !isAnimating) return;

        checkWindowResize();

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int px = this.x + animationOffset;
        int py = this.y;
        int pw = this.width;
        int ph = this.height;

        if (px + pw < 0) return;

        // ===== 背景 =====
        GuiComponent.fill(poseStack, px, py, px + pw, py + ph, 0xAA1A1A1A);

        // ===== 边框 =====
        GuiComponent.fill(poseStack, px, py, px + 1, py + ph, 0x33FFFFFF);
        GuiComponent.fill(poseStack, px + pw - 1, py, px + pw, py + ph, 0x22FFFFFF);
        GuiComponent.fill(poseStack, px, py, px + pw, py + 1, 0x22FFFFFF);
        GuiComponent.fill(poseStack, px, py + ph - 1, px + pw, py + ph, 0x22FFFFFF);

        renderTitleBar(poseStack, px, py, pw, font);
        renderRefreshButton(poseStack, px, py, mouseX, mouseY, font);
        renderSearchBox(poseStack, px, py, pw, font);

        int clipStartY = py + CARDS_START_Y;
        int clipEndY = py + ph - 4;
        enableScissor(px + 5, clipStartY, pw - 10 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING, clipEndY - clipStartY);

        renderCards(poseStack, px, py, pw, ph, mouseX, mouseY, font);

        disableScissor();

        renderScrollBar(poseStack, px, py, pw, ph);
    }

    // ==================== 渲染辅助方法 ====================

    private void enableScissor(int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        int scale = (int) mc.getWindow().getGuiScale();
        int screenX = x * scale;
        int screenY = mc.getWindow().getScreenHeight() - (y + height) * scale;
        int screenW = width * scale;
        int screenH = height * scale;
        GlStateManager._enableScissorTest();
        GlStateManager._scissorBox(screenX, screenY, screenW, screenH);
    }

    private void disableScissor() {
        GlStateManager._disableScissorTest();
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

            drawSingleCard(poseStack, cardX, cardY, cardW, cardH, displayedFluids.get(i), mouseX, mouseY, font);
        }
    }

    private void drawSingleCard(PoseStack poseStack, int x, int y, int w, int h, FluidStack fluid, int mouseX, int mouseY, Font font) {
        boolean hover = isHovered(x, y, w, h, mouseX, mouseY);

        // 卡片背景
        int bg = hover ? 0xFF3A3A3A : 0xFF222222;
        GuiComponent.fill(poseStack, x, y, x + w, y + h, bg);

        // 卡片边框
        int border = hover ? 0xFF888888 : 0xFF333333;
        GuiComponent.fill(poseStack, x, y, x + w, y + 1, border);
        GuiComponent.fill(poseStack, x, y + h - 1, x + w, y + h, border);
        GuiComponent.fill(poseStack, x, y, x + 1, y + h, border);
        GuiComponent.fill(poseStack, x + w - 1, y, x + w, y + h, border);

        // ===== 流体图标 =====
        int iconSize = ICON_SIZE;
        int iconX = x + 3;
        int iconY = y + (h - iconSize) / 2;

        // 直接使用 SmelteryDataHelper 渲染
        SmelteryDataHelper.drawFluidIcon(poseStack, iconX, iconY, fluid, iconSize);

        // 流体名称
        int textX = iconX + iconSize + ICON_TEXT_GAP;
        int maxTextW = w - iconSize - ICON_TEXT_GAP - 6;

        String name = fluid.getDisplayName().getString()
                .replace("Molten ", "").replace("熔融", "");
        name = truncateText(font, name, maxTextW);
        font.draw(poseStack, name, textX, y + 4, 0xFFFFFF);

        // 流体量
        int amount = fluid.getAmount();
        String amtStr = amount >= 1000 ? String.format("%.1fB", amount / 1000.0) : amount + "mB";
        font.draw(poseStack, "§8" + amtStr, textX, y + 18, 0x888888);

        // JEI 交互提示
        if (jeiAvailable && hover) {
            font.draw(poseStack, "§7左键: JEI配方 右键: JEI用途", x + 4, y + h - 10, 0x666666);
            font.draw(poseStack, "§7A键: 加入书签", x + 4, y + h - 2, 0x666666);
        } else if (hover) {
            font.draw(poseStack, "§7左键卡片: 移至底部", x + 4, y + h - 6, 0x666666);
        }
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

    private static String truncateText(Font font, String text, int maxWidth) {
        while (font.width(text) > maxWidth && text.length() > 1) {
            text = text.substring(0, text.length() - 1);
        }
        if (font.width(text) > maxWidth) {
            text = text.substring(0, Math.max(1, text.length() - 1)) + "...";
        }
        return text;
    }

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {}
}