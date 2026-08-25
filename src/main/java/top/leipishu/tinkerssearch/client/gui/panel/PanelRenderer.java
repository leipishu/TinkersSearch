package top.leipishu.tinkerssearch.client.gui.panel;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import top.leipishu.tinkerssearch.alloy.AlloyQueryHandler;
import top.leipishu.tinkerssearch.alloy.AlloyRecipeData;
import top.leipishu.tinkerssearch.alloy.AlloyResultCalculator;
import top.leipishu.tinkerssearch.client.gui.FloatingSearchPanel;
import top.leipishu.tinkerssearch.client.gui.PanelInteractionHandler;
import top.leipishu.tinkerssearch.utils.FavoritesManager;
import top.leipishu.tinkerssearch.utils.ScissorHelper;
import top.leipishu.tinkerssearch.utils.SmelteryDataHelper;

import java.util.List;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

/**
 * 面板渲染器 - 所有渲染逻辑
 */
public class PanelRenderer {

    private final FloatingSearchPanel panel;
    private final PanelDataManager dataManager;
    private final PanelLayoutCalculator layoutCalculator;
    private final PanelAnimationManager animationManager;
    private final PanelInteractionHandler interactionHandler;
    private final AlloyQueryHandler alloyHandler;

    private Font cachedFont = null;

    public PanelRenderer(FloatingSearchPanel panel, PanelDataManager dataManager,
                         PanelLayoutCalculator layoutCalculator, PanelAnimationManager animationManager,
                         PanelInteractionHandler interactionHandler, AlloyQueryHandler alloyHandler) {
        this.panel = panel;
        this.dataManager = dataManager;
        this.layoutCalculator = layoutCalculator;
        this.animationManager = animationManager;
        this.interactionHandler = interactionHandler;
        this.alloyHandler = alloyHandler;
    }

    // ==================== 主渲染 ====================

    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        ScissorHelper.reset();

        if (panel.isVisible() && !panel.isVisible()) {
            panel.setVisibleInternal(true);
        }

        dataManager.checkPendingHighlightUpdate();
        animationManager.updateAnimation();

        if (!panel.isVisible() && !animationManager.isAnimating()) return;

        layoutCalculator.checkWindowResize();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GlStateManager._disableScissorTest();

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (cachedFont == null) cachedFont = font;

        int px = panel.getPanelX() + animationManager.getAnimationOffset();
        int py = panel.getPanelY();
        int pw = panel.getPanelWidth();
        int ph = panel.getPanelHeight();

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

        if (dataManager.isAlloyMode()) {
            renderAlloySearchBox(poseStack, px, py, pw, font);
        } else {
            renderSearchBox(poseStack, px, py, pw, font);
        }

        if (dataManager.isAlloyMode()) {
            renderAlloyContent(poseStack, px, py, pw, ph, mouseX, mouseY, font);
        } else {
            renderNormalContent(poseStack, px, py, pw, ph, mouseX, mouseY, font);
        }

        RenderSystem.enableDepthTest();
    }

    // ==================== 辅助渲染 ====================

    private void renderTitleBar(PoseStack poseStack, int px, int py, int pw, Font font) {
        GuiComponent.fill(poseStack, px + 1, py + 1, px + pw - 2, py + TITLE_BAR_HEIGHT, 0xFF2A2A2A);
        String title = dataManager.isAlloyMode() ?
                new TranslatableComponent("gui.tinkerssearch.alloy_query").getString() :
                "Tinker's Search";
        font.draw(poseStack, (dataManager.isAlloyMode() ? "§b" : "§6") + title, px + 5, py + 5, 0xFFFFFF);
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

        int total = dataManager.getAllFluids().size();
        int matched = dataManager.getDisplayedFluids().size();
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
        // ===== 收藏区域 =====
        if (!dataManager.getDisplayedFavoriteFluids().isEmpty()) {
            int favLabelY = py + CARDS_START_Y;
            font.draw(poseStack, "§6" + new TranslatableComponent("gui.tinkerssearch.favorites").getString(), px + 5, favLabelY, 0xFFFFFF);

            int favStartY = py + layoutCalculator.getFavoriteAreaStartY();
            int favAreaHeight = layoutCalculator.getFavoriteAreaHeight();

            if (favAreaHeight > 0) {
                boolean scissorOk = ScissorHelper.enableScissor(
                        px + 5,
                        favStartY,
                        pw - 10 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING,
                        favAreaHeight
                );
                if (scissorOk) {
                    try {
                        RenderSystem.disableDepthTest();
                        renderFavoriteCards(poseStack, px, py, pw, ph, mouseX, mouseY, font, favStartY, favAreaHeight);
                    } finally {
                        ScissorHelper.disableScissor();
                        RenderSystem.enableDepthTest();
                    }
                } else {
                    renderFavoriteCards(poseStack, px, py, pw, ph, mouseX, mouseY, font, favStartY, favAreaHeight);
                }
                renderScrollBar(poseStack, px, favStartY, favAreaHeight, pw, dataManager.getFavScrollOffset(), dataManager.getMaxFavScrollOffset());
            }

            int sepY = favStartY + favAreaHeight + SECTION_SPACING;
            GuiComponent.fill(poseStack, px + 5, sepY, px + pw - 5 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING, sepY + 1, 0xFF444444);

            int smelterLabelY = sepY + SECTION_SPACING;
            font.draw(poseStack, "§e" + new TranslatableComponent("gui.tinkerssearch.smeltery").getString(), px + 5, smelterLabelY, 0xFFFFFF);
        } else {
            int smelterLabelY = py + CARDS_START_Y;
            font.draw(poseStack, "§e" + new TranslatableComponent("gui.tinkerssearch.smeltery").getString(), px + 5, smelterLabelY, 0xFFFFFF);
        }

        // ===== 冶炼炉区域 =====
        int clipStartY = py + layoutCalculator.getSmelteryAreaStartY();
        int clipEndY = py + ph - 4;
        int clipWidth = pw - 10 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING;
        int clipHeight = clipEndY - clipStartY;

        if (clipHeight > 0) {
            boolean scissorOk = ScissorHelper.enableScissor(px + 5, clipStartY, clipWidth, clipHeight);
            if (scissorOk) {
                try {
                    RenderSystem.disableDepthTest();
                    renderCards(poseStack, px, py, pw, ph, mouseX, mouseY, font);
                } finally {
                    ScissorHelper.disableScissor();
                    RenderSystem.enableDepthTest();
                }
            } else {
                renderCards(poseStack, px, py, pw, ph, mouseX, mouseY, font);
            }
            renderScrollBar(poseStack, px, clipStartY, clipHeight, pw, dataManager.getScrollOffset(), dataManager.getMaxScrollOffset());
        }
    }

    private void renderCards(PoseStack poseStack, int px, int py, int pw, int ph, int mouseX, int mouseY, Font font) {
        int startY = py + layoutCalculator.getSmelteryAreaStartY() - dataManager.getScrollOffset();
        int endY = py + ph - 4;

        if (dataManager.getDisplayedFluids().isEmpty()) {
            String msg = interactionHandler.getSearchKeyword().isEmpty() ?
                    "§7" + new TranslatableComponent("gui.tinkerssearch.no_fluids").getString() :
                    "§7" + new TranslatableComponent("gui.tinkerssearch.no_match").getString();
            font.draw(poseStack, msg, px + 5, startY + dataManager.getScrollOffset() + 10, 0x666666);
            return;
        }

        int cardW = (pw - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
        int cardH = CARD_HEIGHT;

        for (int i = 0; i < dataManager.getDisplayedFluids().size(); i++) {
            int row = i / ITEMS_PER_ROW;
            int col = i % ITEMS_PER_ROW;

            int cardX = px + 5 + col * (cardW + CARD_SPACING);
            int cardY = startY + row * (cardH + CARD_SPACING);

            if (cardY + cardH < py + layoutCalculator.getSmelteryAreaStartY() || cardY > endY) {
                continue;
            }

            FluidStack fluid = dataManager.getDisplayedFluids().get(i);
            boolean isHover = isHovered(cardX, cardY, cardW, cardH, mouseX, mouseY);
            drawSingleCard(poseStack, cardX, cardY, cardW, cardH, fluid, isHover, font, false);
        }
    }

    private void renderFavoriteCards(PoseStack poseStack, int px, int py, int pw, int ph, int mouseX, int mouseY, Font font, int areaStartY, int areaHeight) {
        int startY = areaStartY - dataManager.getFavScrollOffset();
        int endY = areaStartY + areaHeight;

        if (dataManager.getDisplayedFavoriteFluids().isEmpty()) {
            font.draw(poseStack, "§7" + new TranslatableComponent("gui.tinkerssearch.no_favorites").getString(), px + 5, areaStartY + 10, 0x666666);
            return;
        }

        int cardW = (pw - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
        int cardH = CARD_HEIGHT;

        for (int i = 0; i < dataManager.getDisplayedFavoriteFluids().size(); i++) {
            int row = i / ITEMS_PER_ROW;
            int col = i % ITEMS_PER_ROW;

            int cardX = px + 5 + col * (cardW + CARD_SPACING);
            int cardY = startY + row * (cardH + CARD_SPACING);

            if (cardY + cardH < areaStartY || cardY > endY) {
                continue;
            }

            FluidStack fluid = dataManager.getDisplayedFavoriteFluids().get(i);
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
            for (FluidStack fs : dataManager.getAllFluids()) {
                if (fs.getFluid().getRegistryName().equals(regName)) {
                    existsInSmeltery = true;
                    break;
                }
            }
        }

        boolean isBottom = dataManager.getBottomFluidName() != null && fluidName.equals(dataManager.getBottomFluidName()) && existsInSmeltery;

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

        int currentTemp = panel.getCurrentSmelteryTemperature();

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
        int alloyScrollOffset = dataManager.getAlloyScrollOffset();
        alloyScrollOffset = Math.max(0, Math.min(alloyScrollOffset, maxOffset));
        dataManager.setAlloyScrollOffset(alloyScrollOffset);

        int clipX = px + 5;
        int clipWidth = pw - 10 - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING;

        boolean scissorOk = ScissorHelper.enableScissor(clipX, cardStartY, clipWidth, cardAreaHeight);
        if (scissorOk) {
            try {
                RenderSystem.disableDepthTest();
                int actualStartY = cardStartY - alloyScrollOffset;
                int cardY = actualStartY;
                for (AlloyResultCalculator.AlloyChainResult result : results) {
                    cardY = drawAlloyRecipeCard(poseStack, px, pw, cardY, result, currentTemp, mouseX, mouseY, font);
                    cardY += 6;
                    if (cardY > endY) break;
                }
            } finally {
                ScissorHelper.disableScissor();
                RenderSystem.enableDepthTest();
            }
        } else {
            int actualStartY = cardStartY - alloyScrollOffset;
            int cardY = actualStartY;
            for (AlloyResultCalculator.AlloyChainResult result : results) {
                cardY = drawAlloyRecipeCard(poseStack, px, pw, cardY, result, currentTemp, mouseX, mouseY, font);
                cardY += 6;
                if (cardY > endY) break;
            }
        }

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
        int alloyScrollOffset = dataManager.getAlloyScrollOffset();
        alloyScrollOffset = Math.max(0, Math.min(alloyScrollOffset, maxOffset));
        dataManager.setAlloyScrollOffset(alloyScrollOffset);

        boolean scissorOk = ScissorHelper.enableScissor(px + 5 + margin, cardStartY, availableWidth, cardAreaHeight);
        if (scissorOk) {
            try {
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
            } finally {
                ScissorHelper.disableScissor();
                RenderSystem.enableDepthTest();
            }
        } else {
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
        }

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
                String name = mf.fluid.getDisplayName().getString()
                        .replace("Molten ", "")
                        .replace("熔融", "");
                sb.append(name).append("(").append(mf.available).append("/").append(mf.needed).append("mB)");
            }
            String missingStr = sb.toString();
            int maxMissingWidth = w - 8;
            String truncatedMissing = truncateTextWithEllipsis(font, missingStr, maxMissingWidth);
            font.draw(poseStack, truncatedMissing, x + 4, lineY, 0xCCCCCC);
            lineY += 10;
        }

        if (result.getNext() != null) {
            String childName = result.getNext().getRecipe().getResult().getDisplayName().getString()
                    .replace("Molten ", "")
                    .replace("熔融", "");
            String childStr = "§e" + new TranslatableComponent("gui.tinkerssearch.alloy_child").getString().replace("%s", childName);
            int maxChildWidth = w - 8;
            String truncatedChild = truncateTextWithEllipsis(font, childStr, maxChildWidth);
            font.draw(poseStack, truncatedChild, x + 4, lineY, 0xCCCCCC);
            lineY += 10;
        }

        if (feasibility.isFeasible() && result.getNext() == null) {
            int maxTimes = feasibility.getMaxTimes();
            if (maxTimes > 0) {
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
}