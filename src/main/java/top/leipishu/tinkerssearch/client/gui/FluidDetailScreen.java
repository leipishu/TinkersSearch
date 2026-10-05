package top.leipishu.tinkerssearch.client.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import slimeknights.tconstruct.smeltery.block.entity.controller.SmelteryBlockEntity;
import slimeknights.tconstruct.smeltery.block.entity.tank.SmelteryTank;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;

import top.leipishu.tinkerssearch.client.animation.controller.DetailAnimations;
import top.leipishu.tinkerssearch.client.animation.controller.DetailPageAnimations;
import top.leipishu.tinkerssearch.client.animation.core.Animation;
import top.leipishu.tinkerssearch.client.animation.core.AnimationManager;
import top.leipishu.tinkerssearch.client.animation.core.Animator;
import top.leipishu.tinkerssearch.client.animation.core.Easing;
import top.leipishu.tinkerssearch.client.gui.components.CardBackground;
import top.leipishu.tinkerssearch.client.gui.components.ScrollBar;
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
    private final int displayAmount;
    private final Screen savedScreen;

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

    // ===== 部件展开 / 淡入淡出（加速版）=====
    private static final long EXPAND_DURATION = 180;
    private static final long CONTENT_FADE_DURATION = 80;
    private static final Easing EXPAND_EASING = Easing.EASE_OUT_CUBIC;

    private static final String PART_EXPAND_PREFIX = "detail.part.expand:";
    private static final String PART_FADE_PREFIX   = "detail.part.fade:";
    private static final String PART_ANY_PREFIX    = "detail.part.";

    // 位置动画
    private static final String PART_POS_ROW_PREFIX = "detail.part.pos.row:";
    private static final String PART_POS_COL_PREFIX = "detail.part.pos.col:";
    private static final float  PART_POS_TAU        = 90f;

    private static final int EXPAND_W_COLS = 2;
    private static final int EXPAND_H_ROWS = 3;

    private static final int PAGE_BTN_W = 20;
    private static final int PAGE_BTN_H = 16;
    private static final int CLOSE_BTN_SIZE = 12;

    private final ScrollBar totalScrollBar = new ScrollBar();

    private boolean isLoading = true;
    private boolean dataLoaded = false;
    private boolean needsLayoutRecalc = true;
    private boolean closing = false;

    /** 翻页期间卡片区的整体 alpha。 */
    private float currentPageAlpha = 1f;

    private Font font;

    private final SearchBox searchBox = new SearchBox(SearchBoxStyle.detail());

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
    private int scrollStartY = 0;

    private int castingTitleY = 0;
    private int castingStartY = 0;

    private int partTitleY = 0;
    private int partStartY = 0;
    private int bottomHintY = 0;

    private int lastScreenWidth = 0;
    private int lastScreenHeight = 0;

    private final Set<String> expandedKeys = new HashSet<>();
    private final Map<String, Integer> knownRow = new HashMap<>();
    private final Map<String, Integer> knownCol = new HashMap<>();

    private List<PartLayout> partLayouts = new ArrayList<>();
    private List<Component> pendingTooltip = null;
    private final List<int[]> pageButtonRects = new ArrayList<>();

    private static class PartLayout {
        final PartInfo info;
        final String key;
        final int x, y, w, h;
        final boolean expanded;
        PartLayout(PartInfo info, String key, int x, int y, int w, int h, boolean expanded) {
            this.info = info; this.key = key;
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.expanded = expanded;
        }
    }

    public FluidDetailScreen(FluidStack fluidStack, SmelteryBlockEntity smeltery, Screen savedScreen) {
        super(Component.literal("Fluid Details"));
        this.fluidStack = fluidStack;
        this.smeltery = smeltery;
        this.savedScreen = savedScreen;
        this.currentTemperature = new SmelteryTemperatureReader().getCurrentSmelteryTemperature();
        this.displayAmount = resolveActualAmount(fluidStack, smeltery);

        CastingRecipeHelper.prewarmPartRequirements();

        searchBox.setHintText(Component.translatable("gui.tinkerssearch.detail.search_hint"));
        searchBox.setOnTextChanged(s -> applyFilter());
        searchBox.setAnimationId("detail.searchbox");

        totalScrollBar.setOnOffsetChanged(v -> this.totalScrollOffset = v);
        totalScrollBar.setThumbRatio(0.3f);
        totalScrollBar.setThumbMinHeight(16);
        totalScrollBar.setHoverExpandX(6);
        totalScrollBar.setAnimationId("detail.scrollbar");

        new Thread(() -> {
            try {
                allCastingInfos = CastingRecipeHelper.getCastingRecipesForFluid(fluidStack);
                allCastingInfos.removeIf(info -> info.outputItem.getItem() instanceof IMaterialItem);

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

        DetailAnimations.startOpenAnimation();
    }

    private static int resolveActualAmount(FluidStack target, SmelteryBlockEntity smeltery) {
        if (target == null || target.isEmpty() || smeltery == null) return 0;
        ResourceLocation targetId = ForgeRegistries.FLUIDS.getKey(target.getFluid());
        if (targetId == null) return 0;

        SmelteryTank<?> tank = smeltery.getTank();
        if (tank == null) return 0;

        int count = tank.getTanks();
        int total = 0;
        for (int i = 0; i < count; i++) {
            FluidStack fs = tank.getFluidInTank(i);
            if (fs == null || fs.isEmpty()) continue;
            ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fs.getFluid());
            if (targetId.equals(id)) total += fs.getAmount();
        }
        return total;
    }

    private static String buildKey(PartInfo info) { return info.itemId + "|" + info.materialId; }

    private MaterialEntry currentEntry() {
        if (data == null || data.entries.isEmpty()) return null;
        if (currentPageIndex < 0 || currentPageIndex >= data.entries.size()) return null;
        return data.entries.get(currentPageIndex);
    }

    // ============================================================
    // ===== 部件动画状态 =========================================
    // ============================================================

    private float getAnimProgress(String key) {
        Animation a = AnimationManager.get().get(PART_EXPAND_PREFIX + key);
        if (a != null) return a.getValue();
        return expandedKeys.contains(key) ? 1f : 0f;
    }

    private float getContentAlpha(String key) {
        Animation a = AnimationManager.get().get(PART_FADE_PREFIX + key);
        if (a != null) return a.getValue();
        return expandedKeys.contains(key) ? 1f : 0f;
    }

    private boolean useExpandedLayout(String key) {
        if (expandedKeys.contains(key)) return true;
        return getAnimProgress(key) > 0.01f || getContentAlpha(key) > 0.01f;
    }

    // ============================================================
    // ===== 位置动画 =============================================
    // ============================================================

    private float animRow(String key, int targetRow) {
        Integer prev = knownRow.get(key);
        Animator a = AnimationManager.get().animator(PART_POS_ROW_PREFIX + key, targetRow, PART_POS_TAU);
        if (prev == null) { a.snap(targetRow); knownRow.put(key, targetRow); }
        else { a.setTarget(targetRow); }
        return a.getValue();
    }

    private float animCol(String key, int targetCol) {
        Integer prev = knownCol.get(key);
        Animator a = AnimationManager.get().animator(PART_POS_COL_PREFIX + key, targetCol, PART_POS_TAU);
        if (prev == null) { a.snap(targetCol); knownCol.put(key, targetCol); }
        else { a.setTarget(targetCol); }
        return a.getValue();
    }

    private void clearPartAnimForKey(String key) {
        AnimationManager.get().stop(PART_EXPAND_PREFIX + key);
        AnimationManager.get().stop(PART_FADE_PREFIX + key);
        AnimationManager.get().stopAnimator(PART_POS_ROW_PREFIX + key);
        AnimationManager.get().stopAnimator(PART_POS_COL_PREFIX + key);
        knownRow.remove(key);
        knownCol.remove(key);
    }

    // ============================================================
    // ===== 展开 / 收起 ==========================================
    // ============================================================

    private void toggleExpand(String key) {
        float currentProgress = getAnimProgress(key);
        boolean nowExpand = !expandedKeys.contains(key);

        if (nowExpand) {
            expandedKeys.add(key);
            AnimationManager.get().play(
                    PART_EXPAND_PREFIX + key, currentProgress, 1f,
                    EXPAND_DURATION, 0L, EXPAND_EASING, null);

            long fadeDelay = (long) (EXPAND_DURATION * (1f - currentProgress));
            AnimationManager.get().play(
                    PART_FADE_PREFIX + key, 0f, 1f,
                    CONTENT_FADE_DURATION, fadeDelay, Easing.LINEAR, null);
        } else {
            expandedKeys.remove(key);
            float startFade = getContentAlpha(key);

            AnimationManager.get().play(
                    PART_EXPAND_PREFIX + key, currentProgress, currentProgress,
                    CONTENT_FADE_DURATION, 0L, Easing.LINEAR, null);

            AnimationManager.get().play(
                    PART_FADE_PREFIX + key, startFade, 0f,
                    CONTENT_FADE_DURATION, 0L, Easing.LINEAR,
                    () -> {
                        AnimationManager.get().play(
                                PART_EXPAND_PREFIX + key, currentProgress, 0f,
                                EXPAND_DURATION, 0L, EXPAND_EASING, null);
                        needsLayoutRecalc = true;
                    });
        }
        needsLayoutRecalc = true;
    }

    private void clearPartAnimations() {
        expandedKeys.clear();
        AnimationManager.get().stopPrefix(PART_ANY_PREFIX);
        knownRow.clear();
        knownCol.clear();
    }

    private void switchPage(int delta) {
        if (data == null || data.entries.isEmpty()) return;
        int n = data.entries.size();
        if (n <= 1) return;

        // 只触发动画；数据切换延迟到淡出完成的回调里
        DetailPageAnimations.startTurn(delta, () -> {
            currentPageIndex = ((currentPageIndex + delta) % n + n) % n;
            filteredPartInfos = new ArrayList<>(data.entries.get(currentPageIndex).parts);
            clearPartAnimations();
            partLayouts.clear();
            needsLayoutRecalc = true;
        });
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
        Set<String> removed = new HashSet<>(expandedKeys);
        removed.removeAll(visibleKeys);
        for (String key : removed) clearPartAnimForKey(key);
        expandedKeys.retainAll(visibleKeys);

        needsLayoutRecalc = true;
    }

    // ============================================================
    // ===== 布局 ==================================================
    // ============================================================

    private void recalculateLayout() {
        if (font == null) font = Minecraft.getInstance().font;

        int screenWidth = this.width;
        int screenHeight = this.height;

        windowWidth = computeWindowWidth(screenWidth);
        windowHeight = computeWindowHeight(screenHeight);

        centerX = Math.max(0, (screenWidth - windowWidth) / 2);
        centerY = Math.max(0, (screenHeight - windowHeight) / 2);

        lastScreenWidth = screenWidth;
        lastScreenHeight = screenHeight;
        infoLineHeight = font.lineHeight + 2;

        int y = PADDING + 2;
        headerStartY = y;
        int titleLineHeight = font.lineHeight;
        int infoLinesHeight = infoLineHeight * 2;
        headerHeight = titleLineHeight + 2 + infoLinesHeight;

        iconY = headerStartY + (headerHeight - ICON_SIZE) / 2;
        iconX = PADDING;
        textStartX = iconX + ICON_SIZE + ICON_TEXT_GAP;

        titleY = headerStartY;
        infoStartY = headerStartY + titleLineHeight + 2;

        y = headerStartY + headerHeight + SECTION_SPACING;
        searchBoxY = y;
        searchBoxX = PADDING;
        searchBoxW = Math.max(20, windowWidth - PADDING * 2);
        y += SEARCH_BOX_HEIGHT;

        scrollStartY = y + SECTION_SPACING;

        castingTitleY = scrollStartY;
        castingStartY = castingTitleY + 16;
        int castingHeight = calculateCastingTotalHeight();
        y = castingStartY + castingHeight + SECTION_SPACING;

        partTitleY = y;
        partStartY = partTitleY + 16;
        int partHeight = calculatePartTotalHeight();
        y = partStartY + partHeight + SECTION_SPACING;

        bottomHintY = y;
        y += font.lineHeight + 4;

        int scrollContentHeight = y - scrollStartY + PADDING;
        int scrollAreaHeight = Math.max(0, windowHeight - PADDING - scrollStartY);
        maxTotalScrollOffset = Math.max(0, scrollContentHeight - scrollAreaHeight);
        if (totalScrollOffset > maxTotalScrollOffset) totalScrollOffset = maxTotalScrollOffset;
        if (totalScrollOffset < 0) totalScrollOffset = 0;
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
            boolean expanded = useExpandedLayout(key);
            int sizeW = expanded ? EXPAND_W_COLS : 1;
            int sizeH = expanded ? EXPAND_H_ROWS : 1;

            int[] pos = findPosition(occupied, cols, sizeW, sizeH);
            if (pos == null) continue;

            int row = pos[0], col = pos[1];
            for (int r = row; r < row + sizeH && r < occupied.length; r++) {
                for (int c = col; c < col + sizeW && c < cols; c++) occupied[r][c] = true;
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

    // ============================================================
    // ===== 渲染 ==================================================
    // ============================================================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1) 渲染背景容器屏幕
        if (savedScreen != null) {
            PoseStack ps = graphics.pose();
            ps.pushPose();
            savedScreen.render(graphics, -1, -1, partialTick);
            ps.popPose();
        }

        Minecraft mc = Minecraft.getInstance();

        // 2) 关键修复：flush 背景 buffered 物品 + 清 depth buffer
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(515);

        try { mc.renderBuffers().bufferSource().endBatch(); } catch (Throwable ignored) {}
        try { GlStateManager._clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX); } catch (Throwable ignored) {}
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // 3) 窗口开关动画状态
        float windowScale = DetailAnimations.getWindowScale();
        float maskProgress = DetailAnimations.getMaskProgress();

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(0, 0, 500);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        // 遮罩：alpha 通过颜色分量控制
        int maskAlpha = (int) (0x80 * maskProgress);
        if (maskAlpha > 0) {
            graphics.fill(0, 0, this.width, this.height, maskAlpha << 24);
        }

        // 窗口内容缩放（以窗口中心为原点）
        boolean identityScale = Math.abs(windowScale - 1f) < 0.001f;
        if (!identityScale) {
            float cx = centerX + windowWidth / 2f;
            float cy = centerY + windowHeight / 2f;
            poseStack.pushPose();
            poseStack.translate(cx, cy, 0);
            poseStack.scale(windowScale, windowScale, 1f);
            poseStack.translate(-cx, -cy, 0);
        }

        pendingTooltip = null;
        pageButtonRects.clear();

        if (font == null) font = Minecraft.getInstance().font;

        int newW = computeWindowWidth(this.width);
        int newH = computeWindowHeight(this.height);

        if (newW != windowWidth || newH != windowHeight
                || this.width != lastScreenWidth || this.height != lastScreenHeight
                || needsLayoutRecalc || anyPartAnimating() || DetailPageAnimations.isTurning()) {
            recalculateLayout();
        }

        graphics.fill(centerX + 2, centerY + 2, centerX + windowWidth + 2, centerY + windowHeight + 2, 0x40000000);
        graphics.fill(centerX, centerY, centerX + windowWidth, centerY + windowHeight, 0xF0181818);
        drawBorder(graphics, centerX, centerY, windowWidth, windowHeight, 0xFF555555);

        int closeX = centerX + windowWidth - PADDING - CLOSE_BTN_SIZE;
        int closeY = centerY + PADDING + 2;
        graphics.fill(closeX, closeY, closeX + CLOSE_BTN_SIZE, closeY + CLOSE_BTN_SIZE, 0x88AA4444);
        graphics.drawString(font, "\u00a7f\u2715", closeX + 2, closeY + 2, 0xFFFFFF);

        int clipX = centerX + PADDING;
        int clipW = windowWidth - PADDING * 2;

        boolean useScissor = identityScale;

        int fixedClipTop = centerY + PADDING;
        int fixedClipHeight = centerY + scrollStartY - fixedClipTop;
        if (fixedClipHeight > 0) {
            boolean ok = useScissor && ScissorHelper.enableScissor(clipX, fixedClipTop, clipW, fixedClipHeight);
            try { renderFixedContent(graphics, mouseX, mouseY); }
            finally { if (ok) ScissorHelper.disableScissor(); }
        }

        int scrollClipTop = centerY + scrollStartY;
        int scrollClipHeight = centerY + windowHeight - PADDING - scrollClipTop;
        if (scrollClipHeight > 0) {
            boolean ok = useScissor && ScissorHelper.enableScissor(clipX, scrollClipTop, clipW, scrollClipHeight);
            try { renderScrolledContent(graphics, mouseX, mouseY); }
            finally { if (ok) ScissorHelper.disableScissor(); }
        }

        renderScrollBar(graphics, mouseX, mouseY);

        if (isLoading) {
            String loadingText = "\u00a7e" + Component.translatable("gui.tinkerssearch.detail.loading").getString();
            graphics.drawString(font, loadingText,
                    centerX + windowWidth / 2 - font.width(loadingText) / 2,
                    centerY + windowHeight / 2 - 4, 0xFFFF00);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (!identityScale) poseStack.popPose();

        // Tooltip：正常渲染，无额外 GL 状态改动
        if (pendingTooltip != null) {
            graphics.renderComponentTooltip(font, pendingTooltip, mouseX, mouseY);
            pendingTooltip = null;
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        poseStack.popPose();

        try { mc.renderBuffers().bufferSource().endBatch(); } catch (Throwable ignored) {}
    }

    private static int computeWindowWidth(int screenWidth) {
        int maxAllowed = Math.max(50, screenWidth - 20);
        return Math.min(440, maxAllowed);
    }

    private static int computeWindowHeight(int screenHeight) {
        int maxAllowed = Math.max(50, screenHeight - 20);
        return Math.min(540, maxAllowed);
    }

    private void renderFixedContent(GuiGraphics graphics, int mouseX, int mouseY) {
        int baseX = centerX;
        int baseY = centerY;

        SmelteryDataHelper.drawFluidIcon(graphics, baseX + iconX, baseY + iconY, fluidStack, ICON_SIZE);

        String title = "\u00a7b" + Component.translatable("gui.tinkerssearch.detail.title").getString()
                + ": \u00a7f" + fluidStack.getDisplayName().getString();
        graphics.drawString(font, title, baseX + textStartX, baseY + titleY, 0xFFFFFF);

        int infoY = baseY + infoStartY;
        String amount = "\u00a77" + Component.translatable("gui.tinkerssearch.detail.amount").getString()
                + ": \u00a7f" + displayAmount + " mB";
        graphics.drawString(font, amount, baseX + textStartX, infoY, 0xCCCCCC);

        String temp = "\u00a77" + Component.translatable("gui.tinkerssearch.detail.temperature").getString() + ": " +
                (currentTemperature > 0 ? "\u00a7e" + currentTemperature + "\u00b0C"
                        : "\u00a78" + Component.translatable("gui.tinkerssearch.detail.unknown").getString());
        graphics.drawString(font, temp, baseX + textStartX + 150, infoY, 0xCCCCCC);
        infoY += infoLineHeight;

        if (smeltery != null) {
            SmelteryTank<?> tank = smeltery.getTank();
            if (tank != null) {
                int capacity = tank.getCapacity();
                String capacityStr = "\u00a77" + Component.translatable("gui.tinkerssearch.detail.capacity").getString()
                        + ": \u00a7f" + displayAmount + " / " + capacity + " mB";
                graphics.drawString(font, capacityStr, baseX + textStartX, infoY, 0xCCCCCC);
            }
        }

        searchBox.setBounds(baseX + searchBoxX, baseY + searchBoxY, searchBoxW, SEARCH_BOX_HEIGHT);
        searchBox.render(graphics, mouseX, mouseY, font);
    }

    private void renderScrolledContent(GuiGraphics graphics, int mouseX, int mouseY) {
        int baseX = centerX;
        int baseY = centerY - totalScrollOffset;

        // ===== 铸造部分（不参与翻页动画）=====
        String castingTitle = "\u00a76" + Component.translatable("gui.tinkerssearch.detail.casting").getString() +
                " \u00a77(\u00a7e" + filteredCastingInfos.size() + "\u00a77/\u00a78" + allCastingInfos.size() + "\u00a77)";
        graphics.drawString(font, castingTitle, baseX + PADDING, baseY + castingTitleY, 0xFFFFFF);

        if (filteredCastingInfos.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.tinkerssearch.detail.no_casting").getString(),
                    baseX + PADDING + 5, baseY + castingStartY + 10, 0x666666);
        } else {
            renderCastingCards(graphics, baseX, baseY + castingStartY, mouseX, mouseY);
        }

        // ===== 部件标题（不参与翻页动画）=====
        int totalPages = data.entries.size();
        String pageInfo = totalPages > 1 ? " \u00a77[" + (currentPageIndex + 1) + "/" + totalPages + "]" : "";
        MaterialEntry curEntry = currentEntry();
        int currentPageTotal = (curEntry != null) ? curEntry.parts.size() : 0;
        String partTitle = "\u00a7d" + Component.translatable("gui.tinkerssearch.detail.parts").getString()
                + pageInfo
                + " \u00a77(\u00a7e" + filteredPartInfos.size() + "\u00a77/\u00a78" + currentPageTotal + "\u00a77)";
        graphics.drawString(font, partTitle, baseX + PADDING, baseY + partTitleY, 0xFFFFFF);

        // ★ 当前页浇筑模式标识（与部件标题、翻页按钮同一行）
        //   材料名用 MaterialRegistry 动态查询 → 跟随语言切换
        if (curEntry != null) {
            String matLabel;
            if (curEntry.kind == MaterialEntry.SourceKind.COMPOSITE
                    && curEntry.compositeInput != null) {
                String inputName = resolveMaterialDisplayName(curEntry.compositeInput);
                if (!inputName.isEmpty()) {
                    matLabel = Component.translatable(
                            "gui.tinkerssearch.detail.casting.on", inputName).getString();
                } else {
                    matLabel = Component.translatable(
                            "gui.tinkerssearch.detail.casting.direct").getString();
                }
            } else {
                matLabel = Component.translatable(
                        "gui.tinkerssearch.detail.casting.direct").getString();
            }

            String matDisplay = "§7[" + matLabel + "]";
            int matLabelX = baseX + PADDING + font.width(partTitle) + 6;

            // 翻页按钮占位（只在多页时预留）
            int rightReserve = totalPages > 1 ? (PAGE_BTN_W * 2 + 40 + 8) : 0;
            int rightLimit = baseX + windowWidth - PADDING - rightReserve;

            if (matLabelX < rightLimit - 10) {
                int availW = rightLimit - matLabelX;
                String display = font.width(matDisplay) <= availW
                        ? matDisplay
                        : font.plainSubstrByWidth(matDisplay, Math.max(10, availW - 6)) + "…";
                graphics.drawString(font, display, matLabelX, baseY + partTitleY, 0xAAAAAA);
            }
        }

        // ===== 翻页按钮（不参与翻页动画）=====
        if (totalPages > 1) {
            int barY = baseY + partTitleY - 4;
            int rightX = baseX + windowWidth - PADDING;
            int prevX = rightX - PAGE_BTN_W * 2 - 40;
            drawPageButton(graphics, prevX, barY, "\u25c0", mouseX, mouseY, -1);
            String pageStr = "\u00a7f" + (currentPageIndex + 1) + "\u00a77/\u00a7f" + totalPages;
            graphics.drawString(font, pageStr, prevX + PAGE_BTN_W + 4, barY + 4, 0xFFFFFF);
            int nextX = prevX + PAGE_BTN_W + 4 + font.width(pageStr) + 4;
            drawPageButton(graphics, nextX, barY, "\u25b6", mouseX, mouseY, 1);
        }

        // ===== 卡片网格（唯一参与翻页动画的部分）=====
        float pageSlideX = DetailPageAnimations.getSlideX();
        boolean applySlide = Math.abs(pageSlideX) > 0.5f;

        if (applySlide) {
            graphics.pose().pushPose();
            graphics.pose().translate(pageSlideX, 0, 0);
        }
        try {
            if (filteredPartInfos.isEmpty()) {
                graphics.drawString(font, Component.translatable("gui.tinkerssearch.detail.no_parts").getString(),
                        baseX + PADDING + 5, baseY + partStartY + 10, 0x666666);
            } else {
                renderPartCards(graphics, baseX, baseY + partStartY, mouseX, mouseY);
            }
        } finally {
            if (applySlide) graphics.pose().popPose();
        }

        // ===== 页脚（不参与翻页动画）=====
        String footer = "\u00a78[\u53f3\u952e/ESC " + Component.translatable("gui.tinkerssearch.detail.close").getString() + "]";
        graphics.drawString(font, footer, baseX + PADDING, baseY + bottomHintY, 0x444444);
    }

    /**
     * 从 {@link MaterialId} 解析当前语言的显示名。
     *
     * <p>不使用 {@link IMaterial} 接口的任何方法，直接按 TConstruct 的
     * 翻译键约定 {@code material.<namespace>.<path>} 构造，
     * 交给 MC I18n 解析——与流体卡片使用同一套本地化机制。
     *
     * <p>如果材料没有对应翻译（如某些附属模组未提供 lang 条目），
     * 自动退化为 {@code mat.getPath()}。
     */
    private static String resolveMaterialDisplayName(MaterialId mat) {
        if (mat == null) return "";

        // 1. 直接构造翻译键
        String key = "material." + mat.getNamespace() + "." + mat.getPath();
        String localized = Component.translatable(key).getString();

        // 2. 若翻译不存在（Component.translatable 会返回键本身作为 fallback），退化为 path
        if (localized != null && !localized.isEmpty() && !localized.equals(key)) {
            return localized;
        }

        return mat.getPath();
    }

    private void drawPageButton(GuiGraphics graphics, int x, int y, String arrow,
                                int mouseX, int mouseY, int delta) {
        boolean hover = mouseX >= x && mouseX <= x + PAGE_BTN_W
                && mouseY >= y && mouseY <= y + PAGE_BTN_H;
        int bg = hover ? 0xFF555555 : 0xFF333333;
        int border = hover ? 0xFF999999 : 0xFF555555;
        CardBackground.draw(graphics, x, y, PAGE_BTN_W, PAGE_BTN_H, bg, border);

        int textX = x + (PAGE_BTN_W - font.width(arrow)) / 2;
        int textY = y + (PAGE_BTN_H - font.lineHeight) / 2 + 1;
        graphics.drawString(font, "\u00a7f" + arrow, textX, textY, 0xFFFFFF);
        pageButtonRects.add(new int[]{x, y, delta});
    }

    // ==================== 铸造卡片 ====================

    private void renderCastingCards(GuiGraphics graphics, int baseX, int startY, int mouseX, int mouseY) {
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
            drawCastingCard(graphics, cardX, cardY, cardWidth, info, hover);
        }
    }

    private void drawCastingCard(GuiGraphics graphics, int x, int y, int width,
                                 CastingRecipeHelper.CastingInfo info, boolean hover) {
        int bg = hover ? 0xFF333A44 : 0xFF1E2228;
        int border = hover ? 0xFF66AAFF : 0xFF3A4250;
        CardBackground.drawWithShadow(graphics, x, y, width, CARD_HEIGHT, bg, border, 0x40000000);

        ItemStack stack = info.outputItem;

        int iconAreaSize = 28;
        int iconAreaX = x + 6;
        int iconAreaY = y + (CARD_HEIGHT - iconAreaSize) / 2;
        int itemX = iconAreaX + (iconAreaSize - 16) / 2;
        int itemY = iconAreaY + (iconAreaSize - 16) / 2;

        graphics.renderItem(stack, itemX, itemY);
        graphics.renderItemDecorations(font, stack, itemX, itemY, "");

        int textX = iconAreaX + iconAreaSize + 4;
        int maxTextW = width - iconAreaSize - 18;
        int textY = y + 5;

        String name = stack.getHoverName().getString();
        int count = stack.getCount();
        String nameLine = (count > 1) ? name + " \u00a78\u00d7" + count : name;
        String displayName = font.width(nameLine) > maxTextW
                ? font.plainSubstrByWidth(nameLine, maxTextW - 6) + "..." : nameLine;
        graphics.drawString(font, "\u00a7f" + displayName, textX, textY, 0xFFFFFF);
        textY += 13;

        int required = info.requiredAmount;
        int available = displayAmount;
        int canCast = required > 0 ? available / required : 0;

        String meltLine;
        if (canCast >= 1) meltLine = "\u00a7f" + required + "mB \u00a77| \u00a7a\u00d7" + canCast;
        else meltLine = "\u00a7f" + required + "mB \u00a77| \u00a7c-" + (required - available) + "mB";

        if (font.width(meltLine) > maxTextW) meltLine = font.plainSubstrByWidth(meltLine, maxTextW - 6) + "...";
        graphics.drawString(font, meltLine, textX, textY, 0xFFFFFF);
        textY += 13;

        if (info.requiresCast) {
            String castLine = "\u00a78" + Component.translatable("gui.tinkerssearch.detail.requires_cast").getString();
            if (font.width(castLine) > maxTextW) castLine = font.plainSubstrByWidth(castLine, maxTextW - 6) + "...";
            graphics.drawString(font, castLine, textX, textY, 0x888888);
        }
    }

    // ==================== 部件网格 ====================

    private void renderPartCards(GuiGraphics graphics, int baseX, int startY, int mouseX, int mouseY) {
        if (filteredPartInfos.isEmpty()) return;

        // 翻页 alpha
        currentPageAlpha = DetailPageAnimations.getContentAlpha();

        if (currentPageAlpha <= 0.01f) {
            partLayouts.clear();
            return;
        }

        int contentW = windowWidth - PADDING * 2;
        int cols = Math.max(1, (contentW + BLOCK_SPACING) / (BLOCK_SIZE + BLOCK_SPACING));
        int gridW = cols * BLOCK_SIZE + (cols - 1) * BLOCK_SPACING;
        int padding = (contentW - gridW) / 2;

        partLayouts.clear();
        boolean[][] occupied = new boolean[512][cols];
        int step = BLOCK_SIZE + BLOCK_SPACING;

        for (PartInfo info : filteredPartInfos) {
            String key = buildKey(info);
            boolean expanded = useExpandedLayout(key);
            int sizeW = expanded ? EXPAND_W_COLS : 1;
            int sizeH = expanded ? EXPAND_H_ROWS : 1;

            int[] pos = findPosition(occupied, cols, sizeW, sizeH);
            if (pos == null) continue;

            int row = pos[0], col = pos[1];
            for (int r = row; r < row + sizeH && r < occupied.length; r++) {
                for (int c = col; c < col + sizeW && c < cols; c++) occupied[r][c] = true;
            }

            float animR = animRow(key, row);
            float animC = animCol(key, col);

            int visualX = baseX + PADDING + padding + Math.round(animC * step);
            int visualY = startY + Math.round(animR * step);

            float progress = getAnimProgress(key);
            float contentAlpha = getContentAlpha(key);

            int targetSlotW = sizeW * BLOCK_SIZE + (sizeW - 1) * BLOCK_SPACING;
            int targetSlotH = sizeH * BLOCK_SIZE + (sizeH - 1) * BLOCK_SPACING;
            int visualW = (int) (BLOCK_SIZE + (targetSlotW - BLOCK_SIZE) * progress);
            int visualH = (int) (BLOCK_SIZE + (targetSlotH - BLOCK_SIZE) * progress);

            PartLayout layout = new PartLayout(info, key, visualX, visualY, visualW, visualH, expanded);
            partLayouts.add(layout);

            boolean hover = mouseX >= visualX && mouseX <= visualX + visualW
                    && mouseY >= visualY && mouseY <= visualY + visualH;

            if (expanded) {
                drawExpandedCard(graphics, layout, hover, contentAlpha);
            } else {
                drawPartBlock(graphics, layout, hover);
            }
        }
    }

    private void drawPartBlock(GuiGraphics graphics, PartLayout layout, boolean hover) {
        int x = layout.x, y = layout.y, w = layout.w, h = layout.h;
        int bg = hover ? 0xFF2A3A2E : 0xFF1A2A1E;
        int border = hover ? 0xFF66BB66 : 0xFF2E4A32;

        boolean needAlpha = currentPageAlpha < 0.99f;
        if (needAlpha) RenderSystem.setShaderColor(1f, 1f, 1f, currentPageAlpha);
        try {
            CardBackground.drawWithShadow(graphics, x, y, w, h, bg, border, 0x40000000);

            int iconX = x + (w - 16) / 2;
            int iconY = y + (h - 16 - 14) / 2;

            if (layout.info.displayStack != null && !layout.info.displayStack.isEmpty()) {
                graphics.renderItem(layout.info.displayStack, iconX, iconY);
            }

            String name = layout.info.getDisplayName();
            int maxW = w - 6;
            String displayName = font.width(name) > maxW
                    ? font.plainSubstrByWidth(name, maxW - 4) + "..." : name;
            int nameX = x + (w - font.width(displayName)) / 2;
            int nameY = y + h - 14;
            graphics.drawString(font, "\u00a7f" + displayName, nameX, nameY, 0xFFFFFF);
        } finally {
            if (needAlpha) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    private void drawExpandedCard(GuiGraphics graphics, PartLayout layout, boolean hover, float contentAlpha) {
        int x = layout.x, y = layout.y, w = layout.w, h = layout.h;
        int bg = hover ? 0xFF2A3A2E : 0xFF1A2A1E;
        int border = hover ? 0xFF66BB66 : 0xFF2E4A32;

        boolean needBgAlpha = currentPageAlpha < 0.99f;
        if (needBgAlpha) RenderSystem.setShaderColor(1f, 1f, 1f, currentPageAlpha);
        try {
            CardBackground.drawWithShadow(graphics, x, y, w, h, bg, border, 0x40000000);
        } finally {
            if (needBgAlpha) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        float combined = contentAlpha * currentPageAlpha;
        if (combined <= 0.01f) return;

        RenderSystem.setShaderColor(1f, 1f, 1f, combined);
        try {
            drawExpandedHeader(graphics, layout.info, x, y, w);
            drawExpandedContent(graphics, layout.info, x, y, w, h);
        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    private void drawExpandedHeader(GuiGraphics graphics, PartInfo info, int slotX, int slotY, int slotW) {
        int iconAreaSize = 22;
        int iconAreaX = slotX + 6;
        int iconAreaY = slotY + 6;
        int itemX = iconAreaX + (iconAreaSize - 16) / 2;
        int itemY = iconAreaY + (iconAreaSize - 16) / 2;

        if (info.displayStack != null && !info.displayStack.isEmpty()) {
            graphics.renderItem(info.displayStack, itemX, itemY);
        }

        String name = info.getDisplayName();
        int nameMaxW = slotW - iconAreaSize - 20;
        if (nameMaxW > 20) {
            String displayName = font.width(name) > nameMaxW
                    ? font.plainSubstrByWidth(name, nameMaxW - 4) + "..." : name;
            graphics.drawString(font, "\u00a7f" + displayName, iconAreaX + iconAreaSize + 4, slotY + 10, 0xFFFFFF);
        }
    }

    private void drawExpandedContent(GuiGraphics graphics, PartInfo info,
                                     int slotX, int slotY, int slotW, int slotH) {
        int textX = slotX + 6;
        int textY = slotY + 32;
        int maxW = slotW - 12;

        if (info.requiredAmount > 0) {
            int canMake = displayAmount / info.requiredAmount;
            String meltLine;
            if (canMake >= 1) meltLine = "\u00a7f" + info.requiredAmount + "mB \u00a77| \u00a7a\u00d7" + canMake;
            else meltLine = "\u00a7f" + info.requiredAmount + "mB \u00a77| \u00a7c-"
                    + (info.requiredAmount - displayAmount) + "mB";
            if (font.width(meltLine) > maxW) meltLine = font.plainSubstrByWidth(meltLine, maxW - 6) + "...";
            graphics.drawString(font, meltLine, textX, textY, 0xFFFFFF);
        } else {
            String noRecipe = "\u00a78" + Component.translatable("gui.tinkerssearch.detail.no_recipe").getString();
            graphics.drawString(font, noRecipe, textX, textY, 0x666666);
        }
        textY += 12;

        for (Component line : info.properties.statLines) {
            if (textY > slotY + slotH - 14) break;
            graphics.drawString(font, line, textX, textY, 0xFFFFFF);
            textY += 12;
        }

        if (!info.properties.modifiers.isEmpty()) {
            if (textY > slotY + slotH - 14) return;
            textY += 2;
            String traitsLabel = "\u00a7b" + Component.translatable("gui.tinkerssearch.detail.traits").getString();
            graphics.drawString(font, traitsLabel, textX, textY, 0xFFFFFF);
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

                if (tagX + tagW > textX + maxW) { tagX = textX; tagY += tagH + tagGap; }
                if (tagY + tagH > slotY + slotH - 4) break;

                int bgColor = 0xFF000000 | (color & 0x303030);
                int borderColor = 0xFF000000 | color;

                graphics.fill(tagX, tagY, tagX + tagW, tagY + tagH, bgColor);
                graphics.fill(tagX, tagY, tagX + tagW, tagY + 1, borderColor);
                graphics.fill(tagX, tagY + tagH - 1, tagX + tagW, tagY + tagH, borderColor);
                graphics.fill(tagX, tagY, tagX + 1, tagY + tagH, borderColor);
                graphics.fill(tagX + tagW - 1, tagY, tagX + tagW, tagY + tagH, borderColor);

                String displayText = font.width(text) > tagW - 6
                        ? font.plainSubstrByWidth(text, tagW - 10) + "..." : text;

                Component textComp = Component.literal(displayText)
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
                graphics.drawString(font, textComp, tagX + 4, tagY + 3, color);

                boolean tagHover = mx >= tagX && mx <= tagX + tagW && my >= tagY && my <= tagY + tagH;
                if (tagHover) {
                    List<Component> tooltip = new ArrayList<>();
                    Component titleComp = mod.displayName.copy();
                    if (mod.level > 1) {
                        titleComp = Component.literal("").append(mod.displayName)
                                .append(Component.literal(" " + mod.level));
                    }
                    tooltip.add(titleComp);

                    if (mod.descriptionLines.isEmpty()) {
                        tooltip.add(Component.literal("\u00a77("
                                + Component.translatable("gui.tinkerssearch.detail.no_desc").getString() + ")"));
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
                String noTraits = "\u00a78" + Component.translatable("gui.tinkerssearch.detail.no_traits").getString();
                graphics.drawString(font, noTraits, textX, textY, 0x666666);
            }
        }
    }

    // ==================== 辅助 ====================

    private void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private void renderScrollBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int barX = centerX + windowWidth - 6;
        int barY = centerY + scrollStartY;
        int barH = (centerY + windowHeight - PADDING) - barY;
        if (barH <= 0) return;

        totalScrollBar.setBounds(barX, barY, 3, barH);
        totalScrollBar.setRange(totalScrollOffset, maxTotalScrollOffset);
        totalScrollBar.render(graphics, mouseX, mouseY);
    }

    private boolean anyPartAnimating() {
        return AnimationManager.get().isAnyRunning(PART_ANY_PREFIX);
    }

    // ==================== 事件 ====================

    private boolean shouldBlockInput() {
        return closing || DetailAnimations.isClosing();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (shouldBlockInput()) return true;

        int closeX = centerX + windowWidth - PADDING - CLOSE_BTN_SIZE;
        int closeY = centerY + PADDING + 2;
        if (mouseX >= closeX && mouseX <= closeX + CLOSE_BTN_SIZE
                && mouseY >= closeY && mouseY <= closeY + CLOSE_BTN_SIZE) {
            this.onClose();
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && totalScrollBar.tryBeginDrag(mouseX, mouseY)) {
            return true;
        }

        if (mouseX < centerX || mouseX > centerX + windowWidth ||
                mouseY < centerY || mouseY > centerY + windowHeight) {
            if (searchBox.isFocused()) { searchBox.setFocused(false); return true; }
            this.onClose();
            return true;
        }

        for (int[] rect : pageButtonRects) {
            if (mouseX >= rect[0] && mouseX <= rect[0] + PAGE_BTN_W
                    && mouseY >= rect[1] && mouseY <= rect[1] + PAGE_BTN_H) {
                if (DetailPageAnimations.isTurning()) return true;
                switchPage(rect[2]);
                return true;
            }
        }

        for (PartLayout layout : partLayouts) {
            if (mouseX >= layout.x && mouseX <= layout.x + layout.w
                    && mouseY >= layout.y && mouseY <= layout.y + layout.h) {
                if (DetailPageAnimations.isTurning()) return true;
                toggleExpand(layout.key);
                return true;
            }
        }

        searchBox.setBounds(centerX + searchBoxX, centerY + searchBoxY, searchBoxW, SEARCH_BOX_HEIGHT);
        if (searchBox.mouseClicked(mouseX, mouseY, button)) return true;

        searchBox.setFocused(false);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (shouldBlockInput()) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && totalScrollBar.isDragging()) {
            totalScrollBar.updateDrag(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (shouldBlockInput()) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && totalScrollBar.isDragging()) {
            totalScrollBar.endDrag();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (shouldBlockInput()) return true;
        if (mouseX < centerX || mouseX > centerX + windowWidth ||
                mouseY < centerY || mouseY > centerY + windowHeight) return false;
        int newOffset = totalScrollOffset - (int) (delta * SCROLL_SPEED);
        totalScrollOffset = Math.max(0, Math.min(newOffset, maxTotalScrollOffset));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (shouldBlockInput()) return true;

        if (searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { this.onClose(); return true; }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            totalScrollOffset = Math.min(totalScrollOffset + SCROLL_SPEED, maxTotalScrollOffset);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            totalScrollOffset = Math.max(totalScrollOffset - SCROLL_SPEED, 0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) { switchPage(-1); return true; }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) { switchPage(1); return true; }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (shouldBlockInput()) return true;
        if (searchBox.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (closing) return;

        AnimationManager.get().stopPrefix(PART_ANY_PREFIX);
        AnimationManager.get().stopPrefix("widget.");
        DetailPageAnimations.clear();

        knownRow.clear();
        knownCol.clear();

        closing = true;
        DetailAnimations.startCloseAnimation(this::finishClose);
    }

    private void finishClose() {
        DetailAnimations.clear();

        Minecraft mc = Minecraft.getInstance();
        if (savedScreen != null) mc.setScreen(savedScreen);
        else super.onClose();
    }
}