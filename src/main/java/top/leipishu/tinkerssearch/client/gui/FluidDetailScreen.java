package top.leipishu.tinkerssearch.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.glfw.GLFW;
import slimeknights.tconstruct.smeltery.block.entity.controller.SmelteryBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.tank.SmelteryTank;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

import top.leipishu.tinkerssearch.client.gui.components.CardBackground;
import top.leipishu.tinkerssearch.client.gui.components.SearchBox;
import top.leipishu.tinkerssearch.client.gui.components.SearchBoxStyle;
import top.leipishu.tinkerssearch.data.FluidPartData;
import top.leipishu.tinkerssearch.data.FluidPartData.MaterialEntry;
import top.leipishu.tinkerssearch.data.FluidPartData.ModifierInfo;
import top.leipishu.tinkerssearch.data.FluidPartData.PartInfo;
import top.leipishu.tinkerssearch.data.FluidPartDataCache;
import top.leipishu.tinkerssearch.recipe.CastingRecipeHelper;
import top.leipishu.tinkerssearch.client.render.ScissorHelper;
import top.leipishu.tinkerssearch.smeltery.SmelteryDataHelper;
import top.leipishu.tinkerssearch.smeltery.SmelteryTemperatureReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    private FluidPartData data = new FluidPartData(null, new ArrayList<>(), 0L);
    private int currentPageIndex = 0;

    private List<CastingRecipeHelper.CastingInfo> filteredCastingInfos = new ArrayList<>();
    private List<PartInfo> filteredPartInfos = new ArrayList<>();

    private static final int CARD_HEIGHT = 52;
    private static final int CARD_SPACING = 6;
    private static final int CARDS_PER_ROW = 2;
    private static final int PADDING = 10;
    private static final int ICON_SIZE = 36;
    private static final int ICON_TEXT_GAP = 8;
    private static final int SECTION_SPACING = 10;
    private static final int SEARCH_BOX_HEIGHT = 18;

    private static final int BLOCK_SIZE = 56;
    private static final int BLOCK_SPACING = 6;
    private static final long EXPAND_DURATION = 250;
    private static final long CONTENT_FADE_DURATION = 150;

    private static final int EXPAND_W_COLS = 2;
    private static final int EXPAND_H_ROWS = 3;

    private static final int PAGE_BTN_W = 20;
    private static final int PAGE_BTN_H = 16;

    private boolean isLoading = true;
    private boolean dataLoaded = false;
    private boolean needsLayoutRecalc = true;

    private Font font;
    private ItemRenderer itemRenderer;

    private final SearchBox searchBox = new SearchBox(SearchBoxStyle.detail());

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

    private final Set<String> expandedKeys = new HashSet<>();
    private final Map<String, Long> animStartTimes = new HashMap<>();
    private List<PartLayout> partLayouts = new ArrayList<>();
    private List<Component> pendingTooltip = null;

    private final List<int[]> pageButtonRects = new ArrayList<>();

    private static class PartLayout {
        PartInfo info;
        String key;
        int x, y, w, h;
        boolean expanded;
        PartLayout(PartInfo info, String key, int x, int y, int w, int h, boolean expanded) {
            this.info = info;
            this.key = key;
            this.x = x; this.y = y; this.w = w; this.h = h; this.expanded = expanded;
        }
    }

    public FluidDetailScreen(FluidStack fluidStack, SmelteryBlockEntity smeltery) {
        super(new TextComponent("Fluid Details"));
        this.fluidStack = fluidStack;
        this.smeltery = smeltery;
        this.currentTemperature = new SmelteryTemperatureReader().getCurrentSmelteryTemperature();

        CastingRecipeHelper.prewarmPartRequirements();

        searchBox.setHintText(new TranslatableComponent("gui.tinkerssearch.detail.search_hint"));
        searchBox.setOnTextChanged(s -> applyFilter());

        new Thread(() -> {
            try {
                allCastingInfos = CastingRecipeHelper.getCastingRecipesForFluid(fluidStack);
                allCastingInfos.removeIf(info ->
                        info.outputItem.getItem() instanceof IMaterialItem);

                data = FluidPartDataCache.get(fluidStack);
                filteredCastingInfos = new ArrayList<>(allCastingInfos);
                filteredPartInfos = data.entries.isEmpty()
                        ? new ArrayList<>()
                        : new ArrayList<>(data.entries.get(0).parts);
                currentPageIndex = 0;
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

    private static String buildKey(PartInfo info) {
        return info.itemId + "|" + info.materialId;
    }

    private MaterialEntry currentEntry() {
        if (data == null || data.entries.isEmpty()) return null;
        if (currentPageIndex < 0 || currentPageIndex >= data.entries.size()) return null;
        return data.entries.get(currentPageIndex);
    }

    private void switchPage(int delta) {
        if (data == null || data.entries.isEmpty()) return;
        int n = data.entries.size();
        currentPageIndex = ((currentPageIndex + delta) % n + n) % n;

        filteredPartInfos = new ArrayList<>(data.entries.get(currentPageIndex).parts);

        expandedKeys.clear();
        animStartTimes.clear();
        partLayouts.clear();

        needsLayoutRecalc = true;
    }

    private void applyFilter() {
        String kw = searchBox.getText().trim().toLowerCase(Locale.ROOT);

        if (kw.isEmpty()) {
            filteredCastingInfos = new ArrayList<>(allCastingInfos);
        } else {
            filteredCastingInfos = new ArrayList<>();
            for (CastingRecipeHelper.CastingInfo info : allCastingInfos) {
                String name = info.outputItem.getHoverName().getString().toLowerCase(Locale.ROOT);
                if (name.contains(kw)) filteredCastingInfos.add(info);
            }
        }

        MaterialEntry entry = currentEntry();
        if (entry == null) {
            filteredPartInfos = new ArrayList<>();
        } else {
            List<PartInfo> currentPageParts = entry.parts;
            if (kw.isEmpty()) {
                filteredPartInfos = new ArrayList<>(currentPageParts);
            } else {
                filteredPartInfos = new ArrayList<>();
                for (PartInfo info : currentPageParts) {
                    String name = info.getDisplayName().toLowerCase(Locale.ROOT);
                    if (name.contains(kw)) filteredPartInfos.add(info);
                }
            }
        }

        Set<String> visibleKeys = new HashSet<>();
        for (PartInfo info : filteredPartInfos) visibleKeys.add(buildKey(info));
        expandedKeys.retainAll(visibleKeys);
        animStartTimes.keySet().retainAll(visibleKeys);

        needsLayoutRecalc = true;
    }

    // ==================== 动画 ====================

    private float getAnimProgress(String key) {
        boolean expanded = expandedKeys.contains(key);
        Long t = animStartTimes.get(key);
        if (t == null) return expanded ? 1f : 0f;
        long elapsed = System.currentTimeMillis() - t;
        if (elapsed >= EXPAND_DURATION) return expanded ? 1f : 0f;
        float p = (float) elapsed / EXPAND_DURATION;
        float eased = 1f - (float) Math.pow(1f - p, 3);
        return expanded ? eased : (1f - eased);
    }

    private float getContentAlpha(String key) {
        if (!expandedKeys.contains(key)) return 0f;
        Long t = animStartTimes.get(key);
        if (t == null) return 1f;
        long elapsed = System.currentTimeMillis() - t;
        if (elapsed < EXPAND_DURATION) return 0f;
        long fadeElapsed = elapsed - EXPAND_DURATION;
        if (fadeElapsed >= CONTENT_FADE_DURATION) return 1f;
        return (float) fadeElapsed / CONTENT_FADE_DURATION;
    }

    private boolean isAnimating(String key) {
        Long t = animStartTimes.get(key);
        if (t == null) return false;
        long elapsed = System.currentTimeMillis() - t;
        return elapsed < (EXPAND_DURATION + CONTENT_FADE_DURATION);
    }

    private boolean anyAnimating() {
        long now = System.currentTimeMillis();
        long total = EXPAND_DURATION + CONTENT_FADE_DURATION;
        for (Long t : animStartTimes.values()) {
            if (now - t < total) return true;
        }
        return false;
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

        int contentW = windowWidth - PADDING * 2;
        int cols = Math.max(1, (contentW + BLOCK_SPACING) / (BLOCK_SIZE + BLOCK_SPACING));

        boolean[][] occupied = new boolean[512][cols];
        int maxRow = 0;

        for (PartInfo info : filteredPartInfos) {
            String key = buildKey(info);
            boolean expanded = expandedKeys.contains(key);
            int sizeW = expanded ? EXPAND_W_COLS : 1;
            int sizeH = expanded ? EXPAND_H_ROWS : 1;

            int[] pos = findPosition(occupied, cols, sizeW, sizeH);
            if (pos == null) continue;

            int row = pos[0], col = pos[1];
            for (int r = row; r < row + sizeH && r < occupied.length; r++) {
                for (int c = col; c < col + sizeW && c < cols; c++) {
                    occupied[r][c] = true;
                }
            }
            maxRow = Math.max(maxRow, row + sizeH);
        }
        return maxRow * (BLOCK_SIZE + BLOCK_SPACING) + 4;
    }

    private int[] findPosition(boolean[][] occupied, int cols, int sizeW, int sizeH) {
        for (int row = 0; row < occupied.length; row++) {
            for (int col = 0; col + sizeW <= cols; col++) {
                boolean free = true;
                for (int r = 0; r < sizeH && free; r++) {
                    for (int c = 0; c < sizeW && free; c++) {
                        int rr = row + r;
                        int cc = col + c;
                        if (rr >= occupied.length || cc >= cols || occupied[rr][cc]) { free = false; break; }
                    }
                }
                if (free) return new int[]{row, col};
            }
        }
        return null;
    }

    // ==================== 渲染 ====================

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        pendingTooltip = null;
        pageButtonRects.clear();

        if (font == null) font = Minecraft.getInstance().font;
        if (itemRenderer == null) itemRenderer = Minecraft.getInstance().getItemRenderer();

        int newW = Math.min(440, Math.max(340, this.width - 40));
        int newH = Math.min(540, Math.max(300, this.height - 40));

        if (newW != windowWidth || newH != windowHeight || needsLayoutRecalc || anyAnimating()) {
            recalculateLayout();
        }

        fill(poseStack, 0, 0, this.width, this.height, 0x80000000);
        fill(poseStack, centerX + 2, centerY + 2, centerX + windowWidth + 2, centerY + windowHeight + 2, 0x40000000);
        fill(poseStack, centerX, centerY, centerX + windowWidth, centerY + windowHeight, 0xF0181818);
        drawBorder(poseStack, centerX, centerY, windowWidth, windowHeight, 0xFF555555);

        int closeX = centerX + windowWidth - 22 - PADDING;
        int closeY = centerY + PADDING + 3;
        fill(poseStack, closeX, closeY, closeX + 16, closeY + 16, 0xCCDD4444);
        font.draw(poseStack, "\u2715", closeX + 4, closeY + 2, 0xFFFFFF);

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

        if (maxTotalScrollOffset > 0) renderScrollBar(poseStack);

        if (isLoading) {
            String loadingText = "\u00a7e" + new TranslatableComponent("gui.tinkerssearch.detail.loading").getString();
            font.draw(poseStack, loadingText,
                    centerX + windowWidth / 2 - font.width(loadingText) / 2,
                    centerY + windowHeight / 2 - 4, 0xFFFF00);
        }

        super.render(poseStack, mouseX, mouseY, partialTick);

        if (pendingTooltip != null) {
            renderComponentTooltip(poseStack, pendingTooltip, mouseX, mouseY);
            pendingTooltip = null;
        }
    }

    private void renderContent(PoseStack poseStack, int mouseX, int mouseY) {
        int offsetY = -totalScrollOffset;
        int baseX = centerX;
        int baseY = centerY + offsetY;

        SmelteryDataHelper.drawFluidIcon(poseStack, baseX + iconX, baseY + iconY, fluidStack, ICON_SIZE);

        String title = "\u00a7b" + new TranslatableComponent("gui.tinkerssearch.detail.title").getString()
                + ": \u00a7f" + fluidStack.getDisplayName().getString();
        font.draw(poseStack, title, baseX + textStartX, baseY + titleY, 0xFFFFFF);

        int infoY = baseY + infoStartY;
        String amount = "\u00a77" + new TranslatableComponent("gui.tinkerssearch.detail.amount").getString()
                + ": \u00a7f" + fluidStack.getAmount() + " mB";
        font.draw(poseStack, amount, baseX + textStartX, infoY, 0xCCCCCC);

        String temp = "\u00a77" + new TranslatableComponent("gui.tinkerssearch.detail.temperature").getString() + ": " +
                (currentTemperature > 0 ? "\u00a7e" + currentTemperature + "\u00b0C"
                        : "\u00a78" + new TranslatableComponent("gui.tinkerssearch.detail.unknown").getString());
        font.draw(poseStack, temp, baseX + textStartX + 150, infoY, 0xCCCCCC);
        infoY += infoLineHeight;

        if (smeltery != null) {
            SmelteryTank<?> tank = smeltery.getTank();
            if (tank != null) {
                int capacity = tank.getCapacity();
                String capacityStr = "\u00a77" + new TranslatableComponent("gui.tinkerssearch.detail.capacity").getString()
                        + ": \u00a7f" + fluidStack.getAmount() + " / " + capacity + " mB";
                font.draw(poseStack, capacityStr, baseX + textStartX, infoY, 0xCCCCCC);
            }
        }

        searchBox.setBounds(baseX + searchBoxX, baseY + searchBoxY, searchBoxW, SEARCH_BOX_HEIGHT);
        searchBox.render(poseStack, mouseX, mouseY, font);

        String castingTitle = "\u00a76" + new TranslatableComponent("gui.tinkerssearch.detail.casting").getString() +
                " \u00a77(\u00a7e" + filteredCastingInfos.size() + "\u00a77/\u00a78" + allCastingInfos.size() + "\u00a77)";
        font.draw(poseStack, castingTitle, baseX + PADDING, baseY + castingTitleY, 0xFFFFFF);

        if (filteredCastingInfos.isEmpty()) {
            font.draw(poseStack, new TranslatableComponent("gui.tinkerssearch.detail.no_casting").getString(),
                    baseX + PADDING + 5, baseY + castingStartY + 10, 0x666666);
        } else {
            renderCastingCards(poseStack, baseX, baseY + castingStartY, mouseX, mouseY);
        }

        int totalPages = data.entries.size();
        String pageInfo = totalPages > 1 ? " \u00a77[" + (currentPageIndex + 1) + "/" + totalPages + "]" : "";
        int currentPageTotal = (currentEntry() != null) ? currentEntry().parts.size() : 0;
        String partTitle = "\u00a7d" + new TranslatableComponent("gui.tinkerssearch.detail.parts").getString()
                + pageInfo
                + " \u00a77(\u00a7e" + filteredPartInfos.size() + "\u00a77/\u00a78" + currentPageTotal + "\u00a77)";
        font.draw(poseStack, partTitle, baseX + PADDING, baseY + partTitleY, 0xFFFFFF);

        if (totalPages > 1) {
            int barY = baseY + partTitleY - 4;
            int rightX = baseX + windowWidth - PADDING;
            int prevX = rightX - PAGE_BTN_W * 2 - 40;
            drawPageButton(poseStack, prevX, barY, "\u25c0", mouseX, mouseY, -1);
            String pageStr = "\u00a7f" + (currentPageIndex + 1) + "\u00a77/\u00a7f" + totalPages;
            font.draw(poseStack, pageStr, prevX + PAGE_BTN_W + 4, barY + 4, 0xFFFFFF);
            int nextX = prevX + PAGE_BTN_W + 4 + font.width(pageStr) + 4;
            drawPageButton(poseStack, nextX, barY, "\u25b6", mouseX, mouseY, 1);
        }

        if (filteredPartInfos.isEmpty()) {
            font.draw(poseStack, new TranslatableComponent("gui.tinkerssearch.detail.no_parts").getString(),
                    baseX + PADDING + 5, baseY + partStartY + 10, 0x666666);
        } else {
            renderPartCards(poseStack, baseX, baseY + partStartY, mouseX, mouseY);
        }

        String footer = "\u00a78[\u53f3\u952e/ESC " + new TranslatableComponent("gui.tinkerssearch.detail.close").getString() + "]";
        font.draw(poseStack, footer, baseX + PADDING, baseY + bottomHintY, 0x444444);
    }

    private void drawPageButton(PoseStack ps, int x, int y, String arrow,
                                int mouseX, int mouseY, int delta) {
        boolean hover = mouseX >= x && mouseX <= x + PAGE_BTN_W
                && mouseY >= y && mouseY <= y + PAGE_BTN_H;

        int bg = hover ? 0xFF555555 : 0xFF333333;
        int border = hover ? 0xFF999999 : 0xFF555555;
        CardBackground.draw(ps, x, y, PAGE_BTN_W, PAGE_BTN_H, bg, border);

        int textX = x + (PAGE_BTN_W - font.width(arrow)) / 2;
        int textY = y + (PAGE_BTN_H - font.lineHeight) / 2 + 1;
        font.draw(ps, "\u00a7f" + arrow, textX, textY, 0xFFFFFF);

        pageButtonRects.add(new int[]{x, y, delta});
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
        int bg = hover ? 0xFF333A44 : 0xFF1E2228;
        int border = hover ? 0xFF66AAFF : 0xFF3A4250;
        CardBackground.drawWithShadow(poseStack, x, y, width, CARD_HEIGHT, bg, border, 0x40000000);

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
        String nameLine = (count > 1) ? name + " \u00a78\u00d7" + count : name;
        String displayName = font.width(nameLine) > maxTextW
                ? font.plainSubstrByWidth(nameLine, maxTextW - 6) + "..." : nameLine;
        font.draw(poseStack, "\u00a7f" + displayName, textX, textY, 0xFFFFFF);
        textY += 13;

        int required = info.requiredAmount;
        int available = fluidStack.getAmount();
        int canCast = required > 0 ? available / required : 0;

        String meltLine;
        if (canCast >= 1) {
            meltLine = "\u00a7f" + required + "mB \u00a77| \u00a7a\u00d7" + canCast;
        } else {
            int lack = required - available;
            meltLine = "\u00a7f" + required + "mB \u00a77| \u00a7c-" + lack + "mB";
        }
        if (font.width(meltLine) > maxTextW) {
            meltLine = font.plainSubstrByWidth(meltLine, maxTextW - 6) + "...";
        }
        font.draw(poseStack, meltLine, textX, textY, 0xFFFFFF);
        textY += 13;

        if (info.requiresCast) {
            String castLine = "\u00a78" + new TranslatableComponent("gui.tinkerssearch.detail.requires_cast").getString();
            if (font.width(castLine) > maxTextW) {
                castLine = font.plainSubstrByWidth(castLine, maxTextW - 6) + "...";
            }
            font.draw(poseStack, castLine, textX, textY, 0x888888);
        }
    }

    // ==================== 部件网格 ====================

    private void renderPartCards(PoseStack poseStack, int baseX, int startY, int mouseX, int mouseY) {
        if (filteredPartInfos.isEmpty()) return;

        int contentW = windowWidth - PADDING * 2;
        int cols = Math.max(1, (contentW + BLOCK_SPACING) / (BLOCK_SIZE + BLOCK_SPACING));
        int gridW = cols * BLOCK_SIZE + (cols - 1) * BLOCK_SPACING;
        int padding = (contentW - gridW) / 2;

        partLayouts.clear();
        boolean[][] occupied = new boolean[512][cols];

        for (PartInfo info : filteredPartInfos) {
            String key = buildKey(info);
            boolean expanded = expandedKeys.contains(key);
            int sizeW = expanded ? EXPAND_W_COLS : 1;
            int sizeH = expanded ? EXPAND_H_ROWS : 1;

            int[] pos = findPosition(occupied, cols, sizeW, sizeH);
            if (pos == null) continue;

            int row = pos[0], col = pos[1];
            for (int r = row; r < row + sizeH && r < occupied.length; r++) {
                for (int c = col; c < col + sizeW && c < cols; c++) {
                    occupied[r][c] = true;
                }
            }

            int targetX = baseX + PADDING + padding + col * (BLOCK_SIZE + BLOCK_SPACING);
            int targetY = startY + row * (BLOCK_SIZE + BLOCK_SPACING);
            int targetW = sizeW * BLOCK_SIZE + (sizeW - 1) * BLOCK_SPACING;
            int targetH = sizeH * BLOCK_SIZE + (sizeH - 1) * BLOCK_SPACING;

            PartLayout layout = new PartLayout(info, key, targetX, targetY, targetW, targetH, expanded);
            partLayouts.add(layout);

            boolean hover = mouseX >= targetX && mouseX <= targetX + targetW
                    && mouseY >= targetY && mouseY <= targetY + targetH;

            boolean hasAnim = isAnimating(key);
            if (expanded) {
                drawExpandedCardAnimated(poseStack, layout, hover, getAnimProgress(key));
            } else if (hasAnim) {
                drawCollapsingCardAnimated(poseStack, layout, hover, getAnimProgress(key));
            } else {
                drawPartBlock(poseStack, layout, hover);
            }
        }
    }

    private void drawPartBlock(PoseStack ps, PartLayout layout, boolean hover) {
        int x = layout.x, y = layout.y, w = layout.w, h = layout.h;
        int bg = hover ? 0xFF2A3A2E : 0xFF1A2A1E;
        int border = hover ? 0xFF66BB66 : 0xFF2E4A32;
        CardBackground.drawWithShadow(ps, x, y, w, h, bg, border, 0x40000000);

        int iconSize = 24;
        int iconX = x + (w - iconSize) / 2;
        int iconY = y + 6;
        if (layout.info.displayStack != null && !layout.info.displayStack.isEmpty()) {
            itemRenderer.renderGuiItem(layout.info.displayStack, iconX, iconY);
        }

        String name = layout.info.getDisplayName();
        int maxW = w - 6;
        String displayName = font.width(name) > maxW
                ? font.plainSubstrByWidth(name, maxW - 4) + "..." : name;
        int nameX = x + (w - font.width(displayName)) / 2;
        int nameY = y + h - 14;
        font.draw(ps, "\u00a7f" + displayName, nameX, nameY, 0xFFFFFF);
    }

    private void drawExpandedCardAnimated(PoseStack ps, PartLayout layout, boolean hover, float progress) {
        int slotX = layout.x, slotY = layout.y, slotW = layout.w, slotH = layout.h;
        int animW = (int) (BLOCK_SIZE + (slotW - BLOCK_SIZE) * progress);
        int animH = (int) (BLOCK_SIZE + (slotH - BLOCK_SIZE) * progress);
        int animX = slotX + (slotW - animW) / 2;
        int animY = slotY + (slotH - animH) / 2;

        int bg = hover ? 0xFF2A3A2E : 0xFF1A2A1E;
        int border = hover ? 0xFF66BB66 : 0xFF2E4A32;
        CardBackground.drawWithShadow(ps, animX, animY, animW, animH, bg, border, 0x40000000);

        if (progress < 1.0f) return;

        PartInfo info = layout.info;
        float contentAlpha = getContentAlpha(layout.key);
        if (contentAlpha <= 0.01f) return;

        RenderSystem.setShaderColor(1f, 1f, 1f, contentAlpha);
        try {
            drawExpandedHeader(ps, info, slotX, slotY, slotW);
            drawExpandedContent(ps, info, slotX, slotY, slotW, slotH);
        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    private void drawCollapsingCardAnimated(PoseStack ps, PartLayout layout, boolean hover, float progress) {
        int slotX = layout.x, slotY = layout.y, slotW = layout.w, slotH = layout.h;
        int bigW = EXPAND_W_COLS * BLOCK_SIZE + (EXPAND_W_COLS - 1) * BLOCK_SPACING;
        int bigH = EXPAND_H_ROWS * BLOCK_SIZE + (EXPAND_H_ROWS - 1) * BLOCK_SPACING;

        int animW = (int) (BLOCK_SIZE + (bigW - BLOCK_SIZE) * progress);
        int animH = (int) (BLOCK_SIZE + (bigH - BLOCK_SIZE) * progress);
        int animX = slotX + (slotW - animW) / 2;
        int animY = slotY + (slotH - animH) / 2;

        int bg = hover ? 0xFF2A3A2E : 0xFF1A2A1E;
        int border = hover ? 0xFF66BB66 : 0xFF2E4A32;
        CardBackground.drawWithShadow(ps, animX, animY, animW, animH, bg, border, 0x40000000);
    }

    private void drawExpandedHeader(PoseStack ps, PartInfo info, int slotX, int slotY, int slotW) {
        int iconSize = 22;
        int iconX = slotX + 6;
        int iconY = slotY + 6;
        if (info.displayStack != null && !info.displayStack.isEmpty()) {
            itemRenderer.renderGuiItem(info.displayStack, iconX, iconY);
        }

        String name = info.getDisplayName();
        int nameMaxW = slotW - iconSize - 20;
        if (nameMaxW > 20) {
            String displayName = font.width(name) > nameMaxW
                    ? font.plainSubstrByWidth(name, nameMaxW - 4) + "..." : name;
            font.draw(ps, "\u00a7f" + displayName, iconX + iconSize + 4, slotY + 10, 0xFFFFFF);
        }
    }

    private void drawExpandedContent(PoseStack ps, PartInfo info,
                                     int slotX, int slotY, int slotW, int slotH) {
        int textX = slotX + 6;
        int textY = slotY + 32;
        int maxW = slotW - 12;

        if (info.requiredAmount > 0) {
            int available = fluidStack.getAmount();
            int canMake = available / info.requiredAmount;
            String meltLine;
            if (canMake >= 1) {
                meltLine = "\u00a7f" + info.requiredAmount + "mB \u00a77| \u00a7a\u00d7" + canMake;
            } else {
                meltLine = "\u00a7f" + info.requiredAmount + "mB \u00a77| \u00a7c-"
                        + (info.requiredAmount - available) + "mB";
            }
            if (font.width(meltLine) > maxW) meltLine = font.plainSubstrByWidth(meltLine, maxW - 6) + "...";
            font.draw(ps, meltLine, textX, textY, 0xFFFFFF);
        } else {
            String noRecipe = "\u00a78" + new TranslatableComponent("gui.tinkerssearch.detail.no_recipe").getString();
            font.draw(ps, noRecipe, textX, textY, 0x666666);
        }
        textY += 12;

        for (Component line : info.properties.statLines) {
            if (textY > slotY + slotH - 14) break;
            font.draw(ps, line, textX, textY, 0xFFFFFF);
            textY += 12;
        }

        if (!info.properties.modifiers.isEmpty()) {
            if (textY > slotY + slotH - 14) return;
            textY += 2;
            String traitsLabel = "\u00a7b" + new TranslatableComponent("gui.tinkerssearch.detail.traits").getString();
            font.draw(ps, traitsLabel, textX, textY, 0xFFFFFF);
            textY += 12;

            int tagH = 14;
            int tagGap = 3;
            int tagX = textX;
            int tagY = textY;

            Minecraft mc = Minecraft.getInstance();
            double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

            for (ModifierInfo mod : info.properties.modifiers) {
                int color = 0xFFFFFF;
                try {
                    Style style = mod.displayName.getStyle();
                    TextColor tc = style.getColor();
                    if (tc != null) color = tc.getValue();
                } catch (Exception ignored) {}

                String text = mod.displayName.getString();
                if (mod.level > 1) text = text + " " + mod.level;

                int tagW = font.width(text) + 8;
                if (tagW > maxW) tagW = maxW;

                if (tagX + tagW > textX + maxW) {
                    tagX = textX;
                    tagY += tagH + tagGap;
                }
                if (tagY + tagH > slotY + slotH - 4) break;

                int bgColor = 0xFF000000 | (color & 0x303030);
                int borderColor = 0xFF000000 | color;

                fill(ps, tagX, tagY, tagX + tagW, tagY + tagH, bgColor);
                fill(ps, tagX, tagY, tagX + tagW, tagY + 1, borderColor);
                fill(ps, tagX, tagY + tagH - 1, tagX + tagW, tagY + tagH, borderColor);
                fill(ps, tagX, tagY, tagX + 1, tagY + tagH, borderColor);
                fill(ps, tagX + tagW - 1, tagY, tagX + tagW, tagY + tagH, borderColor);

                String displayText = font.width(text) > tagW - 6
                        ? font.plainSubstrByWidth(text, tagW - 10) + "..." : text;

                Component textComp = new TextComponent(displayText)
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
                font.draw(ps, textComp, tagX + 4, tagY + 3, color);

                boolean tagHover = mx >= tagX && mx <= tagX + tagW && my >= tagY && my <= tagY + tagH;
                if (tagHover) {
                    List<Component> tooltip = new ArrayList<>();
                    Component titleComp = mod.displayName.copy();
                    if (mod.level > 1) {
                        titleComp = new TextComponent("").append(mod.displayName)
                                .append(new TextComponent(" " + mod.level));
                    }
                    tooltip.add(titleComp);

                    if (mod.descriptionLines.isEmpty()) {
                        tooltip.add(new TextComponent("\u00a77("
                                + new TranslatableComponent("gui.tinkerssearch.detail.no_desc").getString() + ")"));
                    } else {
                        for (Component line : mod.descriptionLines) tooltip.add(line);
                    }
                    pendingTooltip = tooltip;
                }

                tagX += tagW + tagGap;
            }
        } else {
            if (textY <= slotY + slotH - 14) {
                textY += 2;
                String noTraits = "\u00a78" + new TranslatableComponent("gui.tinkerssearch.detail.no_traits").getString();
                font.draw(ps, noTraits, textX, textY, 0x666666);
            }
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
            if (searchBox.isFocused()) {
                searchBox.setFocused(false);
                return true;
            }
            this.onClose();
            return true;
        }

        for (int[] rect : pageButtonRects) {
            if (mouseX >= rect[0] && mouseX <= rect[0] + PAGE_BTN_W
                    && mouseY >= rect[1] && mouseY <= rect[1] + PAGE_BTN_H) {
                switchPage(rect[2]);
                return true;
            }
        }

        for (PartLayout layout : partLayouts) {
            if (mouseX >= layout.x && mouseX <= layout.x + layout.w
                    && mouseY >= layout.y && mouseY <= layout.y + layout.h) {
                if (expandedKeys.contains(layout.key)) {
                    expandedKeys.remove(layout.key);
                } else {
                    expandedKeys.add(layout.key);
                }
                animStartTimes.put(layout.key, System.currentTimeMillis());
                needsLayoutRecalc = true;
                return true;
            }
        }

        searchBox.setBounds(centerX + searchBoxX, centerY + searchBoxY - totalScrollOffset,
                searchBoxW, SEARCH_BOX_HEIGHT);
        if (searchBox.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        searchBox.setFocused(false);
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
        if (searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

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
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            switchPage(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            switchPage(1);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox.charTyped(codePoint, modifiers)) {
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