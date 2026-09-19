package top.leipishu.tinkerssearch;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.client.event.RenderTooltipEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import top.leipishu.tinkerssearch.client.gui.FloatingSearchPanel;
import top.leipishu.tinkerssearch.client.gui.FluidDetailScreen;
import top.leipishu.tinkerssearch.client.gui.PanelInteractionHandler;
import top.leipishu.tinkerssearch.jei.Jei;

import java.lang.reflect.Field;

@Mod("tinkerssearch")
public class TinkersSearch {

    private static FloatingSearchPanel searchPanel;
    private PanelInteractionHandler interactionHandler;

    private boolean isSmelteryScreen = false;
    private BlockEntity smelteryBlockEntity = null;
    private boolean hasInitialized = false;

    private static final String[] SMELTERY_FIELD_NAMES = {"te", "tileEntity", "blockEntity", "smeltery", "tile"};

    private long lastToggleTime = 0;
    private static final long TOGGLE_COOLDOWN = 200;

    private boolean jeiAvailable;

    private static final int TAB_BUTTON_WIDTH = 14;
    private static final int TAB_BUTTON_HEIGHT = 30;

    public TinkersSearch() {
        System.out.println("Tinker's Search mod initialized!");
        MinecraftForge.EVENT_BUS.register(this);

        searchPanel = new FloatingSearchPanel();
        interactionHandler = searchPanel.getInteractionHandler();

        jeiAvailable = ModList.get().isLoaded("jei");
        System.out.println("Tinker's Search: JEI available: " + jeiAvailable);
    }

    public static FloatingSearchPanel getSearchPanel() { return searchPanel; }

    private boolean isDetailScreenOpen() {
        return Minecraft.getInstance().screen instanceof FluidDetailScreen;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onScreenInit(ScreenEvent.Init event) {
        Screen screen = event.getScreen();
        if (screen == null) return;
        if (screen instanceof FluidDetailScreen) return;

        boolean isSmeltery = isSmelteryScreen(screen);
        if (isSmeltery) {
            handleSmelteryOpen(screen);
            addPanelToRenderables(screen);
        } else {
            handleSmelteryClose();
            removePanelFromRenderables(screen);
        }
    }

    private void addPanelToRenderables(Screen screen) {
        if (searchPanel == null) return;
        screen.renderables.remove(searchPanel);
        screen.renderables.add(searchPanel);
    }

    private void removePanelFromRenderables(Screen screen) {
        if (searchPanel == null) return;
        screen.renderables.remove(searchPanel);
    }

    private boolean isSmelteryScreen(Screen screen) {
        String className = screen.getClass().getName();
        return className.contains("SmelteryScreen") || className.contains("smeltery");
    }

    private void handleSmelteryOpen(Screen screen) {
        if (!isSmelteryScreen) {
            isSmelteryScreen = true;
            hasInitialized = false;
        }
        findSmelteryBlockEntity(screen);
        searchPanel.updatePanelPosition();

        if (searchPanel.isVisible()) {
            if (!hasInitialized) {
                searchPanel.refreshTemperature();
                searchPanel.refreshMoltenFluids();
                hasInitialized = true;
            }
            if (jeiAvailable) Jei.refreshExclusionAreas();
        }
    }

    private void findSmelteryBlockEntity(Screen screen) {
        for (String name : SMELTERY_FIELD_NAMES) {
            try {
                Field field = screen.getClass().getDeclaredField(name);
                field.setAccessible(true);
                Object obj = field.get(screen);
                if (obj instanceof BlockEntity) {
                    smelteryBlockEntity = (BlockEntity) obj;
                    searchPanel.setSmelteryBlockEntity(smelteryBlockEntity);
                    return;
                }
            } catch (Exception ignored) {}
        }
    }

    private void handleSmelteryClose() {
        if (isSmelteryScreen) {
            isSmelteryScreen = false;
            if (searchPanel != null && searchPanel.isVisible()) searchPanel.setVisible(false);
            if (jeiAvailable) Jei.refreshExclusionAreas();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (isDetailScreenOpen()) return;

        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen == null) return;

        if (KeyBindings.togglePanelKey.consumeClick()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastToggleTime < TOGGLE_COOLDOWN) return;
            lastToggleTime = currentTime;

            if (isSmelteryScreen(screen)) {
                if (!isSmelteryScreen) {
                    isSmelteryScreen = true;
                    findSmelteryBlockEntity(screen);
                    searchPanel.updatePanelPosition();
                    addPanelToRenderables(screen);
                }
                togglePanel();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyboardKeyPressedPre(ScreenEvent.KeyPressed event) {
        if (isDetailScreenOpen()) return;
        if (searchPanel == null || !searchPanel.isVisible()) return;
        if (!interactionHandler.isSearchBoxFocused()) return;

        int keyCode = event.getKeyCode();
        if (interactionHandler.handleKeyPressed(keyCode, event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER ||
                keyCode == GLFW.GLFW_KEY_ESCAPE) {
            interactionHandler.setSearchBoxFocused(false);
            event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
    }

    private void togglePanel() {
        searchPanel.toggleVisibility();

        if (searchPanel.isVisible()) {
            searchPanel.forceUpdatePosition();
            searchPanel.refreshTemperature();
            Screen screen = Minecraft.getInstance().screen;
            if (screen != null && isSmelteryScreen(screen)) {
                findSmelteryBlockEntity(screen);
                if (smelteryBlockEntity != null) {
                    searchPanel.setSmelteryBlockEntity(smelteryBlockEntity);
                    searchPanel.refreshMoltenFluids();
                }
            }
            hasInitialized = true;
        }
        if (jeiAvailable) Jei.refreshExclusionAreas();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onCharTyped(ScreenEvent.CharacterTyped event) {
        if (isDetailScreenOpen()) return;
        if (!isSmelteryScreen || searchPanel == null || !searchPanel.isVisible()) return;
        if (!interactionHandler.isSearchBoxFocused()) return;
        if (interactionHandler.handleCharTyped(event.getCodePoint(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseClickPre(ScreenEvent.MouseButtonPressed.Pre event) {
        if (isDetailScreenOpen()) return;
        if (!isSmelteryScreen || searchPanel == null) return;

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        if (searchPanel.isTabButtonClicked(mouseX, mouseY)) {
            if (searchPanel.isAnimating()) { event.setCanceled(true); return; }
            togglePanel();
            event.setCanceled(true);
            return;
        }

        if (searchPanel.isVisible() || searchPanel.isAnimating()) {
            if (searchPanel.isPointInsidePanel(mouseX, mouseY)) {
                if (searchPanel.isAnimating()) { event.setCanceled(true); return; }
                searchPanel.mouseClicked(mouseX, mouseY, event.getButton());
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderTooltipPre(RenderTooltipEvent.Pre event) {
        if (isDetailScreenOpen()) return;
        if (!isSmelteryScreen || searchPanel == null) return;

        if (searchPanel.isVisible() || searchPanel.isAnimating()) {
            Minecraft mc = Minecraft.getInstance();
            double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
            double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
            if (searchPanel.isPointInsidePanel(mouseX, mouseY)) event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (isDetailScreenOpen()) return;
        if (!isSmelteryScreen || searchPanel == null || !searchPanel.isVisible()) return;
        if (searchPanel.isPointInsidePanel(event.getMouseX(), event.getMouseY())) {
            searchPanel.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDelta());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseDragPre(ScreenEvent.MouseDragged.Pre event) {
        if (isDetailScreenOpen()) return;
        if (!isSmelteryScreen || searchPanel == null) return;
        if (!searchPanel.isDraggingScrollBar()) return;
        searchPanel.handleMouseDrag(event.getMouseX(), event.getMouseY());
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseReleasedPre(ScreenEvent.MouseButtonReleased.Pre event) {
        if (isDetailScreenOpen()) return;
        if (!isSmelteryScreen || searchPanel == null) return;
        if (!searchPanel.isDraggingScrollBar()) return;
        searchPanel.handleMouseRelease();
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onScreenDrawPost(ScreenEvent.Render event) {
        if (isDetailScreenOpen()) return;
        if (!isSmelteryScreen || searchPanel == null) return;

        GuiGraphics graphics = event.getGuiGraphics();

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);

        // ★ 1.20.1 正确清理：使用 GlStateManager._clear 而非 GL11.glClear
        try {
            GlStateManager._clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        } catch (Exception ignored) {}

        boolean depthTestWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try { GlStateManager._disableScissorTest(); } catch (Exception ignored) {}

        try {
            if (searchPanel.isVisible() || searchPanel.isAnimating()) {
                searchPanel.render(graphics, 0, 0, 0);
            }
            drawTabButton(graphics);
        } finally {
            if (depthTestWasEnabled) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blendWasEnabled) RenderSystem.enableBlend(); else RenderSystem.disableBlend();

            if (scissorWasEnabled) {
                try { GlStateManager._enableScissorTest(); } catch (Exception ignored) {}
            } else {
                try { GlStateManager._disableScissorTest(); } catch (Exception ignored) {}
            }
            graphics.pose().popPose();
        }
    }

    private void drawTabButton(GuiGraphics graphics) {
        if (searchPanel == null) return;

        int animationOffset = searchPanel.getAnimationOffset();
        boolean isExpanded = searchPanel.isExpanded();
        int panelWidth = searchPanel.getPanelWidth();
        int panelHeight = searchPanel.getPanelHeight();

        int btnX = isExpanded ? animationOffset + panelWidth - 1 : 0;
        int btnY = (panelHeight - TAB_BUTTON_HEIGHT) / 2;
        if (btnX < 0) btnX = 0;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        if (btnX + TAB_BUTTON_WIDTH < 0 || btnX > screenWidth) return;

        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        boolean hover = mouseX >= btnX && mouseX <= btnX + TAB_BUTTON_WIDTH &&
                mouseY >= btnY && mouseY <= btnY + TAB_BUTTON_HEIGHT;

        int bgColor = hover ? 0xCC444444 : 0xCC1A1A1A;
        graphics.fill(btnX, btnY, btnX + TAB_BUTTON_WIDTH, btnY + TAB_BUTTON_HEIGHT, bgColor);

        int borderColor = 0x44FFFFFF;
        graphics.fill(btnX, btnY, btnX + TAB_BUTTON_WIDTH, btnY + 1, borderColor);
        graphics.fill(btnX, btnY + TAB_BUTTON_HEIGHT - 1, btnX + TAB_BUTTON_WIDTH, btnY + TAB_BUTTON_HEIGHT, borderColor);
        if (isExpanded) {
            graphics.fill(btnX + TAB_BUTTON_WIDTH - 1, btnY, btnX + TAB_BUTTON_WIDTH, btnY + TAB_BUTTON_HEIGHT, borderColor);
        } else {
            graphics.fill(btnX, btnY, btnX + 1, btnY + TAB_BUTTON_HEIGHT, borderColor);
            graphics.fill(btnX + TAB_BUTTON_WIDTH - 1, btnY, btnX + TAB_BUTTON_WIDTH, btnY + TAB_BUTTON_HEIGHT, borderColor);
        }

        Font font = mc.font;
        String arrow = isExpanded ? "◀" : "▶";
        int textX = btnX + (TAB_BUTTON_WIDTH - font.width(arrow)) / 2;
        int textY = btnY + (TAB_BUTTON_HEIGHT - font.lineHeight) / 2 + 1;
        int textColor = hover ? 0xFFFFFFFF : 0xCCCCCCCC;
        graphics.drawString(font, arrow, textX, textY, textColor);
    }
}