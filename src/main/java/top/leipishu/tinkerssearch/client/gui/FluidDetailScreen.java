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
import org.lwjgl.glfw.GLFW;
import slimeknights.tconstruct.smeltery.block.entity.controller.SmelteryBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.tank.SmelteryTank;
import top.leipishu.tinkerssearch.utils.CastingRecipeHelper;
import top.leipishu.tinkerssearch.utils.PartPropertyHelper;
import top.leipishu.tinkerssearch.utils.ScissorHelper;
import top.leipishu.tinkerssearch.utils.SmelteryDataHelper;
import top.leipishu.tinkerssearch.utils.SmelteryTemperatureReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FluidDetailScreen extends Screen {

    private final FluidStack fluidStack;
    private final SmelteryBlockEntity smeltery;
    private final int currentTemperature;

    private int windowWidth = 400;
    private int windowHeight = 480;
    private int centerX;
    private int centerY;

    private int totalScrollOffset = 0;
    private int maxTotalScrollOffset = 0;
    private static final int SCROLL_SPEED = 16;

    private List<CastingRecipeHelper.CastingInfo> allCastingInfos = new ArrayList<>();
    private List<PartPropertyHelper.PartInfo> allPartInfos = new ArrayList<>();

    private List<CastingRecipeHelper.CastingInfo> filteredCastingInfos = new ArrayList<>();
    private List<PartPropertyHelper.PartInfo> filteredPartInfos = new ArrayList<>();

    private static final int CARD_HEIGHT = 52;
    private static final int PART_CARD_HEIGHT = 60;
    private static final int CARD_SPACING = 6;
    private static final int CARDS_PER_ROW = 2;
    private static final int PADDING = 10;
    private static final int ICON_SIZE = 36;
    private static final int ICON_TEXT_GAP = 8;
    private static final int SECTION_SPACING = 10;
    private static final int SEARCH_BOX_HEIGHT = 18;
    private static final int CLEAR_BUTTON_SIZE = 10;

    private boolean isLoading = true;
    private boolean dataLoaded = false;
    private boolean needsLayoutRecalc = true;

    private Font font;
    private ItemRenderer itemRenderer;

    private boolean searchFocused = false;
    private String searchText = "";
    private int searchCursor = 0;

    private int contentHeight = 0;

    private int headerStartY = 0;
    private int headerHeight = 0;
    private int iconX = 0;
    private int iconY = 0;
    private int textStartX = 0;
    private int titleY = 0;
    private int infoStartY = 0;
    private int infoLineHeight = 0;

    private int searchBoxY = 0;
    private int searchBoxX = 0;
    private int searchBoxW = 0;

    private int castingTitleY = 0;
    private int castingStartY = 0;
    private int castingHeight = 0;

    private int partTitleY = 0;
    private int partStartY = 0;
    private int partHeight = 0;

    private int bottomHintY = 0;

    public FluidDetailScreen(FluidStack fluidStack, SmelteryBlockEntity smeltery) {
        super(new TextComponent("Fluid Details"));
        this.fluidStack = fluidStack;
        this.smeltery = smeltery;
        this.currentTemperature = new SmelteryTemperatureReader().getCurrentSmelteryTemperature();

        CastingRecipeHelper.prewarmPartRequirements();

        new Thread(() -> {
            try {
                allCastingInfos = CastingRecipeHelper.getCastingRecipesForFluid(fluidStack);
                allPartInfos = PartPropertyHelper.getPartsForFluid(fluidStack);
                filteredCastingInfos = new ArrayList<>(allCastingInfos);
                filteredPartInfos = new ArrayList<>(allPartInfos);
            } catch (Exception e) {
                System.err.println("Tinker's Search: Failed to load detail data: " + e.getMessage());
            }
            Minecraft.getInstance().execute(() -> {
                isLoading = false;
                dataLoaded = true;
                needsLayoutRecalc = true;
            });
        }).start();
    }

    // ==================== 过滤 ====================

    private void applyFilter() {
        String kw = searchText.trim().toLowerCase(Locale.ROOT);
        if (kw.isEmpty()) {
            filteredCastingInfos = new ArrayList<>(allCastingInfos);
            filteredPartInfos = new ArrayList<>(allPartInfos);
        } else {
            filteredCastingInfos = new ArrayList<>();
            for (CastingRecipeHelper.CastingInfo info : allCastingInfos) {
                String name = info.outputItem.getHoverName().getString().toLowerCase(Locale.ROOT);
                if (name.contains(kw)) filteredCastingInfos.add(info);
            }
            filteredPartInfos = new ArrayList<>();
            for (PartPropertyHelper.PartInfo info : allPartInfos) {
                String name = info.partName.toLowerCase(Locale.ROOT);
                if (name.contains(kw)) filteredPartInfos.add(info);
            }
        }
        needsLayoutRecalc = true;
    }

    // ==================== 布局 ====================

    private void recalculateLayout() {
        if (font == null) font = Minecraft.getInstance().font;

        int screenWidth = this.width;
        int screenHeight = this.height;
        windowWidth = Math.min(440, Math.max(340, screenWidth - 40));
        windowHeight = Math.min(540, Math.max(300, screenHeight - 40));
        centerX = (screenWidth - windowWidth) / 2;
        centerY = (screenHeight - windowHeight) / 2;

        int currentY = PADDING + 2;
        infoLineHeight = font.lineHeight + 2;

        headerStartY = currentY;
        int titleLineHeight = font.lineHeight;
        int infoLinesHeight = infoLineHeight * 2;
        headerHeight = titleLineHeight + 2 + infoLinesHeight;

        iconY = headerStartY + (headerHeight - ICON_SIZE) / 2;
        iconX = PADDING;
        textStartX = iconX + ICON_SIZE + ICON_TEXT_GAP;

        titleY = headerStartY;
        infoStartY = headerStartY + titleLineHeight + 2;

        currentY = headerStartY + headerHeight + SECTION_SPACING;

        searchBoxY = currentY;
        searchBoxX = PADDING;
        searchBoxW = windowWidth - PADDING * 2;
        currentY += SEARCH_BOX_HEIGHT + SECTION_SPACING;

        castingTitleY = currentY;
        currentY += 16;
        castingStartY = currentY;
        castingHeight = calculateCastingTotalHeight();
        currentY = castingStartY + castingHeight + SECTION_SPACING;

        partTitleY = currentY;
        currentY += 16;
        partStartY = currentY;
        partHeight = calculatePartTotalHeight();
        currentY = partStartY + partHeight + SECTION_SPACING;

        bottomHintY = currentY;
        currentY += 14;

        contentHeight = currentY + PADDING;

        int visibleHeight = windowHeight - PADDING * 2;
        maxTotalScrollOffset = Math.max(0, contentHeight - visibleHeight);
        if (totalScrollOffset > maxTotalScrollOffset) {
            totalScrollOffset = maxTotalScrollOffset;
        }

        needsLayoutRecalc = false;
    }

    private int calculateCastingTotalHeight() {
        if (filteredCastingInfos == null || filteredCastingInfos.isEmpty()) return 24;
        int rows = (filteredCastingInfos.size() + CARDS_PER_ROW - 1) / CARDS_PER_ROW;
        return rows * (CARD_HEIGHT + CARD_SPACING) - CARD_SPACING + 4;
    }

    private int calculatePartTotalHeight() {
        if (filteredPartInfos == null || filteredPartInfos.isEmpty()) return 24;
        int rows = (filteredPartInfos.size() + CARDS_PER_ROW - 1) / CARDS_PER_ROW;
        return rows * (PART_CARD_HEIGHT + CARD_SPACING) - CARD_SPACING + 4;
    }

    // ==================== 渲染 ====================

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        if (font == null) font = Minecraft.getInstance().font;
        if (itemRenderer == null) itemRenderer = Minecraft.getInstance().getItemRenderer();

        int newW = Math.min(440, Math.max(340, this.width - 40));
        int newH = Math.min(540, Math.max(300, this.height - 40));
        if (newW != windowWidth || newH != windowHeight || needsLayoutRecalc) {
            recalculateLayout();
        }

        fill(poseStack, 0, 0, this.width, this.height, 0x80000000);

        fill(poseStack, centerX + 2, centerY + 2, centerX + windowWidth + 2, centerY + windowHeight + 2, 0x40000000);
        fill(poseStack, centerX, centerY, centerX + windowWidth, centerY + windowHeight, 0xF0181818);
        drawBorder(poseStack, centerX, centerY, windowWidth, windowHeight, 0xFF555555);

        int closeX = centerX + windowWidth - 22 - PADDING;
        int closeY = centerY + PADDING + 3;
        fill(poseStack, closeX, closeY, closeX + 16, closeY + 16, 0xCCDD4444);
        font.draw(poseStack, "✕", closeX + 4, closeY + 2, 0xFFFFFF);

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

        if (maxTotalScrollOffset > 0) {
            renderScrollBar(poseStack);
        }

        if (isLoading) {
            String loadingText = "§e" + new TranslatableComponent("gui.tinkerssearch.detail.loading").getString();
            font.draw(poseStack, loadingText,
                    centerX + windowWidth / 2 - font.width(loadingText) / 2,
                    centerY + windowHeight / 2 - 4, 0xFFFF00);
        }

        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    private void renderContent(PoseStack poseStack, int mouseX, int mouseY) {
        int offsetY = -totalScrollOffset;
        int baseX = centerX;
        int baseY = centerY + offsetY;

        SmelteryDataHelper.drawFluidIcon(poseStack, baseX + iconX, baseY + iconY, fluidStack, ICON_SIZE);

        String title = "§b" + new TranslatableComponent("gui.tinkerssearch.detail.title").getString() + ": §f" + fluidStack.getDisplayName().getString();
        font.draw(poseStack, title, baseX + textStartX, baseY + titleY, 0xFFFFFF);

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

        renderSearchBox(poseStack, baseX, baseY + searchBoxY, mouseX, mouseY);

        String castingTitle = "§6" + new TranslatableComponent("gui.tinkerssearch.detail.casting").getString() +
                " §7(§e" + filteredCastingInfos.size() + "§7/§8" + allCastingInfos.size() + "§7)";
        font.draw(poseStack, castingTitle, baseX + PADDING, baseY + castingTitleY, 0xFFFFFF);

        if (filteredCastingInfos.isEmpty()) {
            font.draw(poseStack, new TranslatableComponent("gui.tinkerssearch.detail.no_casting").getString(),
                    baseX + PADDING + 5, baseY + castingStartY + 10, 0x666666);
        } else {
            renderCastingCards(poseStack, baseX, baseY + castingStartY, mouseX, mouseY);
        }

        String partTitle = "§d" + new TranslatableComponent("gui.tinkerssearch.detail.parts").getString() +
                " §7(§e" + filteredPartInfos.size() + "§7/§8" + allPartInfos.size() + "§7)";
        font.draw(poseStack, partTitle, baseX + PADDING, baseY + partTitleY, 0xFFFFFF);

        if (filteredPartInfos.isEmpty()) {
            font.draw(poseStack, new TranslatableComponent("gui.tinkerssearch.detail.no_parts").getString(),
                    baseX + PADDING + 5, baseY + partStartY + 10, 0x666666);
        } else {
            renderPartCards(poseStack, baseX, baseY + partStartY, mouseX, mouseY);
        }

        font.draw(poseStack, "§8[右键/ESC " + new TranslatableComponent("gui.tinkerssearch.detail.close").getString() + "]",
                baseX + PADDING, baseY + bottomHintY, 0x444444);
    }

    // ==================== 搜索框 ====================

    private void renderSearchBox(PoseStack poseStack, int baseX, int y, int mouseX, int mouseY) {
        int x = baseX + searchBoxX;
        int w = searchBoxW;
        int h = SEARCH_BOX_HEIGHT;

        int bg = searchFocused ? 0xFF3A3A3A : 0xFF222222;
        fill(poseStack, x, y, x + w, y + h, bg);

        int border = searchFocused ? 0xFF888888 : 0xFF444444;
        fill(poseStack, x, y, x + w, y + 1, border);
        fill(poseStack, x, y + h - 1, x + w, y + h, border);
        fill(poseStack, x, y, x + 1, y + h, border);
        fill(poseStack, x + w - 1, y, x + w, y + h, border);

        int textX = x + 4;
        int textY = y + 4;

        if (searchText.isEmpty()) {
            font.draw(poseStack, new TranslatableComponent("gui.tinkerssearch.detail.search_hint"),
                    textX, textY, 0x666666);
        } else {
            int cursorPos = Math.max(0, Math.min(searchCursor, searchText.length()));
            String beforeCursor = searchText.substring(0, cursorPos);
            String afterCursor = searchText.substring(cursorPos);
            int beforeWidth = font.width(beforeCursor);

            font.draw(poseStack, beforeCursor, textX, textY, 0xFFFFFF);
            font.draw(poseStack, afterCursor, textX + beforeWidth, textY, 0xFFFFFF);

            if (searchFocused && (System.currentTimeMillis() / 500 % 2 == 0)) {
                int cursorX = textX + beforeWidth;
                if (cursorX < x + w - 2) {
                    fill(poseStack, cursorX, y + 2, cursorX + 1, y + h - 2, 0xFFFFFFFF);
                }
            }
        }

        if (!searchText.isEmpty()) {
            int clearX = x + w - 14;
            int clearY = y + (h - CLEAR_BUTTON_SIZE) / 2;
            fill(poseStack, clearX, clearY, clearX + CLEAR_BUTTON_SIZE, clearY + CLEAR_BUTTON_SIZE, 0x88AA4444);
            font.draw(poseStack, "§f✕", clearX + 2, clearY + 1, 0xFFFFFF);
        }
    }

    // ==================== 铸造卡片 ====================

    private void renderCastingCards(PoseStack poseStack, int baseX, int startY, int mouseX, int mouseY) {
        if (filteredCastingInfos.isEmpty()) return;
        int cardWidth = (windowWidth - PADDING * 2 - CARD_SPACING) / CARDS_PER_ROW;

        for (int i = 0; i < filteredCastingInfos.size(); i++) {
            int row = i / CARDS_PER_ROW;
            int col = i % CARDS_PER_ROW;
            int cardX = baseX + PADDING + col * (cardWidth + CARD_SPACING);
            int cardY = startY + row * (CARD_HEIGHT + CARD_SPACING);

            CastingRecipeHelper.CastingInfo info = filteredCastingInfos.get(i);
            boolean hover = mouseX >= cardX && mouseX <= cardX + cardWidth &&
                    mouseY >= cardY && mouseY <= cardY + CARD_HEIGHT;
            drawCastingCard(poseStack, cardX, cardY, cardWidth, info, hover);
        }
    }

    private void drawCastingCard(PoseStack poseStack, int x, int y, int width,
                                 CastingRecipeHelper.CastingInfo info, boolean hover) {
        fill(poseStack, x + 1, y + CARD_HEIGHT, x + width + 1, y + CARD_HEIGHT + 1, 0x40000000);
        int bg = hover ? 0xFF333A44 : 0xFF1E2228;
        fill(poseStack, x, y, x + width, y + CARD_HEIGHT, bg);
        int border = hover ? 0xFF66AAFF : 0xFF3A4250;
        fill(poseStack, x, y, x + width, y + 1, border);
        fill(poseStack, x, y + CARD_HEIGHT - 1, x + width, y + CARD_HEIGHT, border);
        fill(poseStack, x, y, x + 1, y + CARD_HEIGHT, border);
        fill(poseStack, x + width - 1, y, x + width, y + CARD_HEIGHT, border);

        ItemStack stack = info.outputItem;
        int iconSize = 28;
        int iconX = x + 6;
        int iconY = y + (CARD_HEIGHT - iconSize) / 2;

        itemRenderer.renderGuiItem(stack, iconX, iconY);
        itemRenderer.renderGuiItemDecorations(font, stack, iconX, iconY, "");

        int textX = iconX + iconSize + 4;
        int maxTextW = width - iconSize - 18;
        int textY = y + 5;

        String name = stack.getHoverName().getString();
        int count = stack.getCount();
        String nameLine = (count > 1) ? name + " §8×" + count : name;
        String displayName = font.width(nameLine) > maxTextW
                ? font.plainSubstrByWidth(nameLine, maxTextW - 6) + "..." : nameLine;
        font.draw(poseStack, "§f" + displayName, textX, textY, 0xFFFFFF);
        textY += 13;

        int required = info.requiredAmount;
        int available = fluidStack.getAmount();
        int canCast = required > 0 ? available / required : 0;

        String meltLine;
        if (canCast >= 1) {
            meltLine = "§7需§f" + required + "mB §7| §a×" + canCast;
        } else {
            int lack = required - available;
            meltLine = "§7需§f" + required + "mB §7| §c缺§f" + lack + "mB";
        }
        if (font.width(meltLine) > maxTextW) {
            meltLine = font.plainSubstrByWidth(meltLine, maxTextW - 6) + "...";
        }
        font.draw(poseStack, meltLine, textX, textY, 0xFFFFFF);
        textY += 13;

        if (info.requiresCast) {
            String castLine = "§8" + new TranslatableComponent("gui.tinkerssearch.detail.requires_cast").getString();
            if (font.width(castLine) > maxTextW) {
                castLine = font.plainSubstrByWidth(castLine, maxTextW - 6) + "...";
            }
            font.draw(poseStack, castLine, textX, textY, 0x888888);
        }
    }

    // ==================== 部件卡片 ====================

    private void renderPartCards(PoseStack poseStack, int baseX, int startY, int mouseX, int mouseY) {
        if (filteredPartInfos.isEmpty()) return;
        int cardWidth = (windowWidth - PADDING * 2 - CARD_SPACING) / CARDS_PER_ROW;

        for (int i = 0; i < filteredPartInfos.size(); i++) {
            int row = i / CARDS_PER_ROW;
            int col = i % CARDS_PER_ROW;
            int cardX = baseX + PADDING + col * (cardWidth + CARD_SPACING);
            int cardY = startY + row * (PART_CARD_HEIGHT + CARD_SPACING);

            PartPropertyHelper.PartInfo info = filteredPartInfos.get(i);
            boolean hover = mouseX >= cardX && mouseX <= cardX + cardWidth &&
                    mouseY >= cardY && mouseY <= cardY + PART_CARD_HEIGHT;
            drawPartCard(poseStack, cardX, cardY, cardWidth, info, hover);
        }
    }

    /**
     * 绘制单个部件卡片（动态行位置 + 压缩宽度）
     *
     * 布局：
     *   行1：部件名
     *   行2：熔炼信息
     *   行3：数值型属性（有）| 倍率型（无数值型时）
     *   行4：倍率型属性（有数值型时）| 修饰语 | 空
     */
    private void drawPartCard(PoseStack poseStack, int x, int y, int width,
                              PartPropertyHelper.PartInfo info, boolean hover) {
        // 阴影 + 背景 + 边框
        fill(poseStack, x + 1, y + PART_CARD_HEIGHT, x + width + 1, y + PART_CARD_HEIGHT + 1, 0x40000000);
        int bg = hover ? 0xFF2A3A2E : 0xFF1A2A1E;
        fill(poseStack, x, y, x + width, y + PART_CARD_HEIGHT, bg);
        int border = hover ? 0xFF66BB66 : 0xFF2E4A32;
        fill(poseStack, x, y, x + width, y + 1, border);
        fill(poseStack, x, y + PART_CARD_HEIGHT - 1, x + width, y + PART_CARD_HEIGHT, border);
        fill(poseStack, x, y, x + 1, y + PART_CARD_HEIGHT, border);
        fill(poseStack, x + width - 1, y, x + width, y + PART_CARD_HEIGHT, border);

        // ============ 图标（左侧，22px）============
        int iconSize = 22;
        int iconX = x + 4;
        int iconY = y + (PART_CARD_HEIGHT - iconSize) / 2;
        if (info.displayStack != null && !info.displayStack.isEmpty()) {
            itemRenderer.renderGuiItem(info.displayStack, iconX, iconY);
        }

        // ============ 文本区 ============
        int textX = iconX + iconSize + 3;
        int maxW = width - iconSize - 12;

        // ============ 行1：部件名 ============
        String name = info.partName;
        if (name.contains("_")) name = name.replace("_", " ");
        String displayName = font.width(name) > maxW
                ? font.plainSubstrByWidth(name, maxW - 4) + "..." : name;
        font.draw(poseStack, "§f" + displayName, textX, y + 4, 0xFFFFFF);

        // ============ 行2：熔炼信息 ============
        int line2Y = y + 17;
        if (info.requiredAmount > 0) {
            int available = fluidStack.getAmount();
            int canMake = available / info.requiredAmount;
            String meltLine;
            if (canMake >= 1) {
                meltLine = "§7需§f" + info.requiredAmount + "mB §7| §a×" + canMake;
            } else {
                int lack = info.requiredAmount - available;
                meltLine = "§7需§f" + info.requiredAmount + "mB §7| §c缺§f" + lack + "mB";
            }
            if (font.width(meltLine) > maxW) {
                meltLine = font.plainSubstrByWidth(meltLine, maxW - 6) + "...";
            }
            font.draw(poseStack, meltLine, textX, line2Y, 0xFFFFFF);
        } else {
            font.draw(poseStack, "§8无配方", textX, line2Y, 0x666666);
        }

        // ============ 行3、行4：动态属性 ============
        PartPropertyHelper.PartProperties props = info.properties;

        String numericText = "";
        String modifierText = "";
        String traitsText = "";

        if (props != null && props.hasStats()) {
            numericText = props.formatNumericStats();
            modifierText = props.formatModifierStats();
            traitsText = props.formatModifiers();
        }

        boolean hasNumeric = !numericText.isEmpty();
        boolean hasModifier = !modifierText.isEmpty();
        boolean hasTraits = !traitsText.isEmpty();

        String line3Text;
        String line4Text;

        if (hasNumeric) {
            // 有数值型：行3=数值型，行4=倍率型（优先）或修饰语
            line3Text = numericText;
            line4Text = hasModifier ? modifierText : (hasTraits ? traitsText : "");
        } else if (hasModifier) {
            // 无数值型但有倍率型：行3=倍率型，行4=修饰语
            line3Text = modifierText;
            line4Text = hasTraits ? traitsText : "";
        } else if (hasTraits) {
            // 只有修饰语
            line3Text = traitsText;
            line4Text = "";
        } else {
            // 什么都没有
            line3Text = "§8未知";
            line4Text = "";
        }

        // 行3
        int line3Y = y + 30;
        if (!line3Text.isEmpty()) {
            String text = line3Text;
            if (font.width(text) > maxW) {
                text = font.plainSubstrByWidth(text, maxW - 6) + "...";
            }
            font.draw(poseStack, text, textX, line3Y, 0xFFFFFF);
        }

        // 行4
        int line4Y = y + 43;
        if (!line4Text.isEmpty()) {
            String text = line4Text;
            if (font.width(text) > maxW) {
                text = font.plainSubstrByWidth(text, maxW - 6) + "...";
            }
            font.draw(poseStack, text, textX, line4Y, 0xFFFFFF);
        }
    }

    // ==================== 辅助 ====================

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

    // ==================== 事件 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int closeX = centerX + windowWidth - 22 - PADDING;
        int closeY = centerY + PADDING + 3;
        if (mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 16) {
            this.onClose();
            return true;
        }

        if (mouseX < centerX || mouseX > centerX + windowWidth ||
                mouseY < centerY || mouseY > centerY + windowHeight) {
            if (searchFocused) {
                searchFocused = false;
                return true;
            }
            this.onClose();
            return true;
        }

        int sx = centerX + searchBoxX;
        int sy = centerY + searchBoxY - totalScrollOffset;
        int sw = searchBoxW;
        int sh = SEARCH_BOX_HEIGHT;

        if (mouseX >= sx && mouseX <= sx + sw && mouseY >= sy && mouseY <= sy + sh) {
            if (!searchText.isEmpty()) {
                int clearX = sx + sw - 14;
                int clearY = sy + (sh - CLEAR_BUTTON_SIZE) / 2;
                if (mouseX >= clearX && mouseX <= clearX + CLEAR_BUTTON_SIZE
                        && mouseY >= clearY && mouseY <= clearY + CLEAR_BUTTON_SIZE) {
                    searchText = "";
                    searchCursor = 0;
                    applyFilter();
                    searchFocused = true;
                    return true;
                }
            }
            searchFocused = true;
            searchCursor = PanelInteractionHandler.calculateCursorFromMouse(
                    font, searchText, mouseX, sx + 4
            );
            return true;
        }

        searchFocused = false;
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
        if (searchFocused) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE:
                    if (searchCursor > 0 && !searchText.isEmpty()) {
                        String before = searchText.substring(0, searchCursor - 1);
                        String after = searchText.substring(searchCursor);
                        searchText = before + after;
                        searchCursor--;
                        applyFilter();
                    }
                    return true;
                case GLFW.GLFW_KEY_DELETE:
                    if (searchCursor < searchText.length()) {
                        String before = searchText.substring(0, searchCursor);
                        String after = searchText.substring(searchCursor + 1);
                        searchText = before + after;
                        applyFilter();
                    }
                    return true;
                case GLFW.GLFW_KEY_LEFT:
                    if (searchCursor > 0) searchCursor--;
                    return true;
                case GLFW.GLFW_KEY_RIGHT:
                    if (searchCursor < searchText.length()) searchCursor++;
                    return true;
                case GLFW.GLFW_KEY_HOME:
                    searchCursor = 0;
                    return true;
                case GLFW.GLFW_KEY_END:
                    searchCursor = searchText.length();
                    return true;
                case GLFW.GLFW_KEY_ESCAPE:
                    searchFocused = false;
                    return true;
                case GLFW.GLFW_KEY_ENTER:
                case GLFW.GLFW_KEY_KP_ENTER:
                    searchFocused = false;
                    return true;
            }
        } else {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.onClose();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                totalScrollOffset = Math.min(totalScrollOffset + SCROLL_SPEED, maxTotalScrollOffset);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                totalScrollOffset = Math.max(totalScrollOffset - SCROLL_SPEED, 0);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchFocused) {
            if (Character.isISOControl(codePoint)) return false;
            String before = searchText.substring(0, searchCursor);
            String after = searchText.substring(searchCursor);
            searchText = before + codePoint + after;
            searchCursor++;
            applyFilter();
            return true;
        }
        return super.charTyped(codePoint, modifiers);
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