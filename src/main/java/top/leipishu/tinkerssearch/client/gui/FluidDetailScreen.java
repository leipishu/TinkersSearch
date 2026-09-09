package top.leipishu.tinkerssearch.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import slimeknights.tconstruct.smeltery.block.entity.controller.SmelteryBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.tank.SmelteryTank;
import top.leipishu.tinkerssearch.utils.CastingRecipeHelper;
import top.leipishu.tinkerssearch.utils.PartPropertyHelper;
import top.leipishu.tinkerssearch.utils.ScissorHelper;
import top.leipishu.tinkerssearch.utils.SmelteryDataHelper;
import top.leipishu.tinkerssearch.utils.SmelteryTemperatureReader;

import java.util.ArrayList;
import java.util.List;

/**
 * 流体详细信息浮窗
 * 右键卡片打开，展示：
 * 1. 流体基础信息（名称、当前量、温度）+ 流体图标（左右排布，图标与整体区块居中对齐）
 * 2. 可浇铸的所有物品（卡片模式 + 物品图标）
 * 3. 可制作的部件及属性词条（卡片模式）
 *
 * 采用整体滚动方案，所有内容在一个滚动区域内
 * 自适应窗口大小
 * 无分割线
 */
public class FluidDetailScreen extends Screen {

    private final FluidStack fluidStack;
    private final SmelteryBlockEntity smeltery;
    private final int currentTemperature;

    // 窗口尺寸
    private int windowWidth = 380;
    private int windowHeight = 460;
    private int centerX;
    private int centerY;

    // ===== 整体滚动 =====
    private int totalScrollOffset = 0;
    private int maxTotalScrollOffset = 0;
    private static final int SCROLL_SPEED = 16;

    // 数据
    private List<CastingRecipeHelper.CastingInfo> castingInfos = new ArrayList<>();
    private List<PartPropertyHelper.PartInfo> partInfos = new ArrayList<>();

    // 布局常量
    private static final int CARD_WIDTH = 150;
    private static final int CARD_HEIGHT = 44;
    private static final int CARD_SPACING = 6;
    private static final int CARDS_PER_ROW = 2;
    private static final int PADDING = 10;
    private static final int ICON_SIZE = 36;
    private static final int ICON_TEXT_GAP = 8;
    private static final int SECTION_SPACING = 10;

    private boolean isLoading = true;
    private boolean dataLoaded = false;
    private boolean needsLayoutRecalc = true;

    // 缓存字体和渲染器
    private Font font;
    private ItemRenderer itemRenderer;

    // ===== 布局缓存（相对于窗口内容区域顶部） =====
    private int contentHeight = 0;

    // 头部区块（图标 + 标题 + 信息）
    private int headerStartY = 0;
    private int headerEndY = 0;
    private int headerHeight = 0;
    private int iconX = 0;
    private int iconY = 0;          // 图标垂直居中于整个头部区块
    private int textStartX = 0;     // 文本起始X（图标右侧）
    private int titleY = 0;
    private int infoStartY = 0;
    private int infoLineHeight = 0;

    // 铸造区域
    private int castingTitleY = 0;
    private int castingStartY = 0;
    private int castingEndY = 0;
    private int castingHeight = 0;

    // 部件区域
    private int partTitleY = 0;
    private int partStartY = 0;
    private int partEndY = 0;
    private int partHeight = 0;

    // 底部提示
    private int bottomHintY = 0;

    // 关闭按钮位置（固定在右上角）
    private int closeX = 0;
    private int closeY = 0;

    public FluidDetailScreen(FluidStack fluidStack, SmelteryBlockEntity smeltery) {
        super(new TextComponent("Fluid Details"));
        this.fluidStack = fluidStack;
        this.smeltery = smeltery;
        this.currentTemperature = new SmelteryTemperatureReader().getCurrentSmelteryTemperature();

        new Thread(() -> {
            try {
                castingInfos = CastingRecipeHelper.getCastingRecipesForFluid(fluidStack);
                partInfos = PartPropertyHelper.getPartsForFluid(fluidStack);
            } catch (Exception e) {
                System.err.println("Tinker's Search: Failed to load detail data: " + e.getMessage());
                e.printStackTrace();
            }
            Minecraft.getInstance().execute(() -> {
                isLoading = false;
                dataLoaded = true;
                needsLayoutRecalc = true;
            });
        }).start();
    }

    // ==================== 布局计算 ====================

    private void recalculateLayout() {
        if (font == null) font = Minecraft.getInstance().font;

        int screenWidth = this.width;
        int screenHeight = this.height;

        windowWidth = Math.min(420, Math.max(320, screenWidth - 40));
        windowHeight = Math.min(520, Math.max(300, screenHeight - 40));
        centerX = (screenWidth - windowWidth) / 2;
        centerY = (screenHeight - windowHeight) / 2;

        // ===== 计算各元素位置 =====
        int currentY = PADDING;
        infoLineHeight = font.lineHeight + 2;

        // ---- 1. 头部区块（图标 + 标题 + 信息） ----
        headerStartY = currentY + 4;

        // 计算标题行高度
        int titleLineHeight = font.lineHeight;
        // 信息行高度：2行
        int infoLinesHeight = infoLineHeight * 2;
        // 头部区块总高度 = 标题行 + 信息行（标题和信息之间留一点间距）
        headerHeight = titleLineHeight + 2 + infoLinesHeight;

        // 图标垂直居中于整个头部区块
        iconY = headerStartY + (headerHeight - ICON_SIZE) / 2;
        iconX = PADDING;
        textStartX = iconX + ICON_SIZE + ICON_TEXT_GAP;

        // 标题Y = 头部区块顶部
        titleY = headerStartY;
        // 信息Y = 标题下方
        infoStartY = headerStartY + titleLineHeight + 2;

        headerEndY = headerStartY + headerHeight;
        currentY = headerEndY + SECTION_SPACING;

        // ---- 2. 铸造区域 ----
        castingTitleY = currentY;
        currentY += 16;
        castingStartY = currentY;
        castingHeight = calculateCastingTotalHeight();
        castingEndY = castingStartY + castingHeight;
        currentY = castingEndY + SECTION_SPACING;

        // ---- 3. 部件区域 ----
        partTitleY = currentY;
        currentY += 16;
        partStartY = currentY;
        partHeight = calculatePartTotalHeight();
        partEndY = partStartY + partHeight;
        currentY = partEndY + SECTION_SPACING;

        // ---- 4. 底部提示 ----
        bottomHintY = currentY;
        currentY += 14;

        // 总内容高度
        contentHeight = currentY + PADDING;

        // 计算最大滚动偏移
        int visibleHeight = windowHeight - PADDING * 2;
        maxTotalScrollOffset = Math.max(0, contentHeight - visibleHeight);
        if (totalScrollOffset > maxTotalScrollOffset) {
            totalScrollOffset = maxTotalScrollOffset;
        }

        // 关闭按钮位置
        closeX = windowWidth - 22 - PADDING;
        closeY = PADDING + 5;

        needsLayoutRecalc = false;
    }

    private int calculateCastingTotalHeight() {
        if (castingInfos == null || castingInfos.isEmpty()) return 24;
        int rows = (castingInfos.size() + CARDS_PER_ROW - 1) / CARDS_PER_ROW;
        return rows * (CARD_HEIGHT + CARD_SPACING) - CARD_SPACING + 4;
    }

    private int calculatePartTotalHeight() {
        if (partInfos == null || partInfos.isEmpty()) return 24;
        int rows = (partInfos.size() + CARDS_PER_ROW - 1) / CARDS_PER_ROW;
        return rows * (CARD_HEIGHT + CARD_SPACING) - CARD_SPACING + 4;
    }

    // ==================== 渲染 ====================

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        if (font == null) font = Minecraft.getInstance().font;
        if (itemRenderer == null) itemRenderer = Minecraft.getInstance().getItemRenderer();

        // 数据加载完成或窗口变化时重新计算布局
        if (dataLoaded && needsLayoutRecalc) {
            recalculateLayout();
        }
        // 窗口尺寸变化时重新计算
        int screenWidth = this.width;
        int screenHeight = this.height;
        int newWindowWidth = Math.min(420, Math.max(320, screenWidth - 40));
        int newWindowHeight = Math.min(520, Math.max(300, screenHeight - 40));
        if (newWindowWidth != windowWidth || newWindowHeight != windowHeight) {
            needsLayoutRecalc = true;
            recalculateLayout();
        }

        // ===== 背景遮罩 =====
        fill(poseStack, 0, 0, this.width, this.height, 0x80000000);

        // ===== 浮窗背景 =====
        fill(poseStack, centerX, centerY, centerX + windowWidth, centerY + windowHeight, 0xF01A1A1A);
        drawBorder(poseStack, centerX, centerY, windowWidth, windowHeight, 0xFF666666);

        // ===== 关闭按钮（固定在右上角） =====
        int closeX = centerX + windowWidth - 22 - PADDING;
        int closeY = centerY + PADDING + 5;
        fill(poseStack, closeX, closeY, closeX + 16, closeY + 16, 0xCCFF4444);
        font.draw(poseStack, "✕", closeX + 4, closeY + 2, 0xFFFFFF);

        // ===== 裁剪区域 =====
        int clipX = centerX + PADDING;
        int clipY = centerY + PADDING;
        int clipW = windowWidth - PADDING * 2;
        int clipH = windowHeight - PADDING * 2;

        boolean scissorOk = ScissorHelper.enableScissor(clipX, clipY, clipW, clipH);
        if (scissorOk) {
            try {
                RenderSystem.disableDepthTest();
                renderContent(poseStack, mouseX, mouseY);
            } finally {
                ScissorHelper.disableScissor();
                RenderSystem.enableDepthTest();
            }
        } else {
            renderContent(poseStack, mouseX, mouseY);
        }

        // ===== 滚动条 =====
        if (maxTotalScrollOffset > 0) {
            renderScrollBar(poseStack);
        }

        // ===== 加载提示 =====
        if (isLoading) {
            String loadingText = "§e⏳ " + new TranslatableComponent("gui.tinkerssearch.detail.loading").getString();
            font.draw(poseStack, loadingText,
                    centerX + windowWidth / 2 - font.width(loadingText) / 2,
                    centerY + windowHeight / 2 - 4, 0xFFFF00);
        }

        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    /**
     * 渲染所有内容（应用滚动偏移）
     */
    private void renderContent(PoseStack poseStack, int mouseX, int mouseY) {
        int offsetY = -totalScrollOffset;
        int baseX = centerX;
        int baseY = centerY + offsetY;

        // ===== 1. 图标（左侧，垂直居中于整个头部区块） =====
        SmelteryDataHelper.drawFluidIcon(poseStack, baseX + iconX, baseY + iconY, fluidStack, ICON_SIZE);

        // ===== 2. 标题（图标右侧，头部区块顶部） =====
        String title = "§b" + new TranslatableComponent("gui.tinkerssearch.detail.title").getString() + ": " + fluidStack.getDisplayName().getString();
        font.draw(poseStack, title, baseX + textStartX, baseY + titleY, 0xFFFFFF);

        // ===== 3. 基础信息（图标右侧，标题下方） =====
        int infoY = baseY + infoStartY;
        String amount = "§7" + new TranslatableComponent("gui.tinkerssearch.detail.amount").getString() + ": §f" + fluidStack.getAmount() + " mB";
        font.draw(poseStack, amount, baseX + textStartX, infoY, 0xCCCCCC);

        String temp = "§7" + new TranslatableComponent("gui.tinkerssearch.detail.temperature").getString() + ": " +
                (currentTemperature > 0 ? "§e" + currentTemperature + "°C" : "§8" + new TranslatableComponent("gui.tinkerssearch.detail.unknown").getString());
        font.draw(poseStack, temp, baseX + textStartX + 150, infoY, 0xCCCCCC);
        infoY += infoLineHeight;

        if (smeltery != null) {
            SmelteryTank<?> tank = smeltery.getTank();
            if (tank != null) {
                int capacity = tank.getCapacity();
                String capacityStr = "§7" + new TranslatableComponent("gui.tinkerssearch.detail.capacity").getString() + ": §f" +
                        fluidStack.getAmount() + " / " + capacity + " mB";
                font.draw(poseStack, capacityStr, baseX + textStartX, infoY, 0xCCCCCC);
            }
        }

        // ===== 4. 铸造标题 =====
        String castingTitle = "§6📦 " + new TranslatableComponent("gui.tinkerssearch.detail.casting").getString() +
                " (§e" + castingInfos.size() + "§6)";
        font.draw(poseStack, castingTitle, baseX + PADDING, baseY + castingTitleY, 0xFFFFFF);

        // ===== 5. 铸造卡片 =====
        if (castingInfos.isEmpty()) {
            font.draw(poseStack, new TranslatableComponent("gui.tinkerssearch.detail.no_casting").getString(),
                    baseX + PADDING + 5, baseY + castingStartY + 10, 0x666666);
        } else {
            renderCastingCards(poseStack, baseX, baseY + castingStartY, mouseX, mouseY);
        }

        // ===== 6. 部件标题 =====
        String partTitle = "§d🔧 " + new TranslatableComponent("gui.tinkerssearch.detail.parts").getString() +
                " (§e" + partInfos.size() + "§d)";
        font.draw(poseStack, partTitle, baseX + PADDING, baseY + partTitleY, 0xFFFFFF);

        // ===== 7. 部件卡片 =====
        if (partInfos.isEmpty()) {
            font.draw(poseStack, new TranslatableComponent("gui.tinkerssearch.detail.no_parts").getString(),
                    baseX + PADDING + 5, baseY + partStartY + 10, 0x666666);
        } else {
            renderPartCards(poseStack, baseX, baseY + partStartY, mouseX, mouseY);
        }

        // ===== 8. 底部提示 =====
        font.draw(poseStack, "§8[右键/ESC " + new TranslatableComponent("gui.tinkerssearch.detail.close").getString() + "]",
                baseX + PADDING, baseY + bottomHintY, 0x444444);
    }

    // ==================== 卡片渲染 ====================

    private void renderCastingCards(PoseStack poseStack, int baseX, int startY, int mouseX, int mouseY) {
        if (castingInfos.isEmpty()) return;

        int cardWidth = (windowWidth - PADDING * 2 - CARD_SPACING - 8) / CARDS_PER_ROW;

        for (int i = 0; i < castingInfos.size(); i++) {
            int row = i / CARDS_PER_ROW;
            int col = i % CARDS_PER_ROW;
            int cardX = baseX + PADDING + col * (cardWidth + CARD_SPACING);
            int cardY = startY + row * (CARD_HEIGHT + CARD_SPACING);

            CastingRecipeHelper.CastingInfo info = castingInfos.get(i);
            boolean hover = mouseX >= cardX && mouseX <= cardX + cardWidth &&
                    mouseY >= cardY && mouseY <= cardY + CARD_HEIGHT;
            drawCastingCard(poseStack, cardX, cardY, cardWidth, info, hover);
        }
    }

    private void drawCastingCard(PoseStack poseStack, int x, int y, int width,
                                 CastingRecipeHelper.CastingInfo info, boolean hover) {
        int bg = hover ? 0xFF3A3A3A : 0xFF222222;
        fill(poseStack, x, y, x + width, y + CARD_HEIGHT, bg);
        int border = hover ? 0xFF888888 : 0xFF333333;
        fill(poseStack, x, y, x + width, y + 1, border);
        fill(poseStack, x, y + CARD_HEIGHT - 1, x + width, y + CARD_HEIGHT, border);
        fill(poseStack, x, y, x + 1, y + CARD_HEIGHT, border);
        fill(poseStack, x + width - 1, y, x + width, y + CARD_HEIGHT, border);

        ItemStack stack = info.outputItem;
        int iconSize = 24;
        int iconX = x + 4;
        int iconY = y + (CARD_HEIGHT - iconSize) / 2;

        itemRenderer.renderGuiItem(stack, iconX, iconY);
        itemRenderer.renderGuiItemDecorations(font, stack, iconX, iconY, "");

        String name = stack.getHoverName().getString();
        int maxTextW = width - iconSize - 20;
        String displayName = font.width(name) > maxTextW ?
                font.plainSubstrByWidth(name, maxTextW - 6) + "..." : name;
        font.draw(poseStack, displayName, iconX + iconSize + 4, y + 4, 0xFFFFFF);

        if (info.requiresCast) {
            String castHint = "§8" + new TranslatableComponent("gui.tinkerssearch.detail.requires_cast").getString();
            int hintWidth = font.width(castHint);
            if (hintWidth > width - iconSize - 20) {
                castHint = font.plainSubstrByWidth(castHint, width - iconSize - 24) + "...";
            }
            font.draw(poseStack, castHint, iconX + iconSize + 4, y + 18, 0x666666);
        }

        int count = stack.getCount();
        if (count > 1) {
            font.draw(poseStack, "§8x" + count, iconX + iconSize + 4, y + 28, 0x888888);
        }
    }

    private void renderPartCards(PoseStack poseStack, int baseX, int startY, int mouseX, int mouseY) {
        if (partInfos.isEmpty()) return;

        int cardWidth = (windowWidth - PADDING * 2 - CARD_SPACING - 8) / CARDS_PER_ROW;

        for (int i = 0; i < partInfos.size(); i++) {
            int row = i / CARDS_PER_ROW;
            int col = i % CARDS_PER_ROW;
            int cardX = baseX + PADDING + col * (cardWidth + CARD_SPACING);
            int cardY = startY + row * (CARD_HEIGHT + CARD_SPACING);

            PartPropertyHelper.PartInfo info = partInfos.get(i);
            boolean hover = mouseX >= cardX && mouseX <= cardX + cardWidth &&
                    mouseY >= cardY && mouseY <= cardY + CARD_HEIGHT;
            drawPartCard(poseStack, cardX, cardY, cardWidth, info, hover);
        }
    }

    private void drawPartCard(PoseStack poseStack, int x, int y, int width,
                              PartPropertyHelper.PartInfo info, boolean hover) {
        int bg = hover ? 0xFF2A3A2A : 0xFF1A2A1A;
        fill(poseStack, x, y, x + width, y + CARD_HEIGHT, bg);
        int border = hover ? 0xFF66AA66 : 0xFF2A4A2A;
        fill(poseStack, x, y, x + width, y + 1, border);
        fill(poseStack, x, y + CARD_HEIGHT - 1, x + width, y + CARD_HEIGHT, border);
        fill(poseStack, x, y, x + 1, y + CARD_HEIGHT, border);
        fill(poseStack, x + width - 1, y, x + width, y + CARD_HEIGHT, border);

        String name = info.partName;
        if (name.contains("_")) name = name.replace("_", " ");
        font.draw(poseStack, "§f" + name, x + 6, y + 4, 0xFFFFFF);

        PartPropertyHelper.PartProperties props = info.properties;
        if (props != null && props.hasStats()) {
            String statsStr = props.formatStats();
            int maxWidth = width - 12;
            if (font.width(statsStr) > maxWidth) {
                statsStr = font.plainSubstrByWidth(statsStr, maxWidth - 6) + "...";
            }
            font.draw(poseStack, statsStr, x + 6, y + 18, 0x88CC88);
        } else {
            font.draw(poseStack, "§8" + new TranslatableComponent("gui.tinkerssearch.detail.unknown").getString(),
                    x + 6, y + 18, 0x666666);
        }

        if (props != null && props.modifiers != null && !props.modifiers.isEmpty()) {
            String modStr = "§b";
            for (int i = 0; i < Math.min(props.modifiers.size(), 2); i++) {
                if (i > 0) modStr += ", ";
                PartPropertyHelper.ModifierInfo mod = props.modifiers.get(i);
                modStr += mod.name + (mod.level > 1 ? " " + mod.level : "");
            }
            if (props.modifiers.size() > 2) modStr += "...";
            font.draw(poseStack, modStr, x + 6, y + 32, 0x88CCFF);
        }
    }

    // ==================== 辅助绘制 ====================

    private void drawBorder(PoseStack poseStack, int x, int y, int width, int height, int color) {
        fill(poseStack, x, y, x + width, y + 1, color);
        fill(poseStack, x, y + height - 1, x + width, y + height, color);
        fill(poseStack, x, y, x + 1, y + height, color);
        fill(poseStack, x + width - 1, y, x + width, y + height, color);
    }

    private void renderScrollBar(PoseStack poseStack) {
        int barX = centerX + windowWidth - 6;
        int barY = centerY + PADDING + 2;
        int barH = windowHeight - PADDING * 2 - 4;

        fill(poseStack, barX, barY, barX + 3, barY + barH, 0x33FFFFFF);

        float ratio = (float) totalScrollOffset / (float) maxTotalScrollOffset;
        int thumbH = Math.max(16, (int) (barH * 0.3f));
        int thumbY = barY + (int) (ratio * (barH - thumbH));
        fill(poseStack, barX, thumbY, barX + 3, thumbY + thumbH, 0x99FFFFFF);
    }

    // ==================== 鼠标事件 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 关闭按钮
        int closeX = centerX + windowWidth - 22 - PADDING;
        int closeY = centerY + PADDING + 5;
        if (mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 16) {
            this.onClose();
            return true;
        }

        // 点击外部关闭
        if (mouseX < centerX || mouseX > centerX + windowWidth ||
                mouseY < centerY || mouseY > centerY + windowHeight) {
            this.onClose();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < centerX || mouseX > centerX + windowWidth ||
                mouseY < centerY || mouseY > centerY + windowHeight) {
            return false;
        }

        int newOffset = totalScrollOffset - (int) (delta * SCROLL_SPEED);
        totalScrollOffset = Math.max(0, Math.min(newOffset, maxTotalScrollOffset));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        if (keyCode == 264) { // DOWN
            totalScrollOffset = Math.min(totalScrollOffset + SCROLL_SPEED, maxTotalScrollOffset);
            return true;
        }
        if (keyCode == 265) { // UP
            totalScrollOffset = Math.max(totalScrollOffset - SCROLL_SPEED, 0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
    }
}