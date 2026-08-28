package top.leipishu.tinkerssearch;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.client.event.InputEvent;
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

    // Tab按钮尺寸（与FloatingSearchPanel保持一致）
    private static final int TAB_BUTTON_WIDTH = 14;
    private static final int TAB_BUTTON_HEIGHT = 30;

    public TinkersSearch() {
        System.out.println("Tinker's Search mod initialized!");
        MinecraftForge.EVENT_BUS.register(this);

        searchPanel = new FloatingSearchPanel();
        interactionHandler = searchPanel.getInteractionHandler();

        jeiAvailable = ModList.get().isLoaded("jei");
        System.out.println("Tinker's Search: JEI available: " + jeiAvailable);
        System.out.println("Tinker's Search: Panel created!");
    }

    public static FloatingSearchPanel getSearchPanel() {
        return searchPanel;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onScreenInit(ScreenEvent.InitScreenEvent.Post event) {
        Screen screen = event.getScreen();
        if (screen == null) return;

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
        System.out.println("Tinker's Search: Panel added to renderables list");
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
            System.out.println("Tinker's Search: SmelteryScreen detected!");
        }

        findSmelteryBlockEntity(screen);
        searchPanel.updatePanelPosition();

        if (searchPanel.isVisible()) {
            if (!hasInitialized) {
                searchPanel.refreshTemperature();
                searchPanel.refreshMoltenFluids();
                hasInitialized = true;
            }
            if (jeiAvailable) {
                Jei.refreshExclusionAreas();
            }
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
                    System.out.println("Tinker's Search: Found via '" + name + "'");
                    return;
                }
            } catch (Exception ignored) {}
        }
        System.out.println("Tinker's Search: Could not find smeltery BlockEntity");
    }

    private void handleSmelteryClose() {
        if (isSmelteryScreen) {
            isSmelteryScreen = false;

            if (searchPanel != null && searchPanel.isVisible()) {
                searchPanel.setVisible(false);
                System.out.println("Tinker's Search: Panel closed due to smeltery screen closing");
            }

            if (jeiAvailable) {
                Jei.refreshExclusionAreas();
            }
            System.out.println("Tinker's Search: Smeltery closed");
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen == null) return;

        if (KeyBindings.togglePanelKey.consumeClick()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastToggleTime < TOGGLE_COOLDOWN) {
                return;
            }
            lastToggleTime = currentTime;

            if (screen != null && isSmelteryScreen(screen)) {
                if (!isSmelteryScreen) {
                    isSmelteryScreen = true;
                    findSmelteryBlockEntity(screen);
                    searchPanel.updatePanelPosition();
                    addPanelToRenderables(screen);
                }
                togglePanel();
            } else {
                System.out.println("Tinker's Search: KeyBinding pressed but not in smeltery screen");
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyboardKeyPressedPre(ScreenEvent.KeyboardKeyPressedEvent.Pre event) {
        if (searchPanel == null) return;
        if (!searchPanel.isVisible()) return;
        if (!interactionHandler.isSearchBoxFocused()) return;

        int keyCode = event.getKeyCode();

        // ===== 所有按键交给 PanelInteractionHandler 处理 =====
        if (interactionHandler.handleKeyPressed(keyCode, event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
            return;
        }

        // ===== Backspace、Delete 等特殊键（实际上已被 handleKeyPressed 处理） =====
        // 这里只处理 handleKeyPressed 没有处理的情况

        // ===== Enter 和 Escape =====
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER ||
                keyCode == GLFW.GLFW_KEY_ESCAPE) {
            interactionHandler.setSearchBoxFocused(false);
            event.setCanceled(true);
            return;
        }

        // ===== 其他按键阻止传播 =====
        event.setCanceled(true);
    }

    private void togglePanel() {
        System.out.println("Tinker's Search: Toggling panel!");
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

        if (jeiAvailable) {
            Jei.refreshExclusionAreas();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onCharTyped(ScreenEvent.KeyboardCharTypedEvent.Pre event) {
        if (!isSmelteryScreen || searchPanel == null || !searchPanel.isVisible()) return;
        if (!interactionHandler.isSearchBoxFocused()) return;

        if (interactionHandler.handleCharTyped(event.getCodePoint(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    // ============================================================
    // ===== 鼠标事件 - Tab按钮由面板自己检测 =====================
    // ============================================================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseClickPre(ScreenEvent.MouseClickedEvent.Pre event) {
        if (!isSmelteryScreen || searchPanel == null) return;

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        // ===== 委托给面板检测Tab按钮 =====
        if (searchPanel.isTabButtonClicked(mouseX, mouseY)) {
            if (searchPanel.isAnimating()) {
                event.setCanceled(true);
                return;
            }
            togglePanel();
            event.setCanceled(true);
            return;
        }

        if (searchPanel.isVisible() || searchPanel.isAnimating()) {
            if (searchPanel.isPointInsidePanel(mouseX, mouseY)) {
                if (searchPanel.isAnimating()) {
                    event.setCanceled(true);
                    return;
                }
                searchPanel.mouseClicked(mouseX, mouseY, event.getButton());
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderTooltipPre(RenderTooltipEvent.Pre event) {
        if (!isSmelteryScreen || searchPanel == null) return;

        if (searchPanel.isVisible() || searchPanel.isAnimating()) {
            Minecraft mc = Minecraft.getInstance();
            double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
            double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

            if (searchPanel.isPointInsidePanel(mouseX, mouseY)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseScroll(ScreenEvent.MouseScrollEvent.Pre event) {
        if (!isSmelteryScreen || searchPanel == null || !searchPanel.isVisible()) {
            return;
        }

        if (searchPanel.isPointInsidePanel(event.getMouseX(), event.getMouseY())) {
            searchPanel.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDelta());
            event.setCanceled(true);
        }
    }

    // ============================================================
    // ===== 最终渲染层 - Tab按钮独立绘制（使用面板的动画偏移） =====
    // ============================================================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onScreenDrawPost(ScreenEvent.DrawScreenEvent.Post event) {
        if (!isSmelteryScreen || searchPanel == null) return;

        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(0, 0, 500);

        try {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        } catch (Exception ignored) {}

        boolean depthTestWasEnabled = false;
        boolean blendWasEnabled = false;
        boolean textureWasEnabled = false;
        boolean scissorWasEnabled = false;

        try {
            depthTestWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
            textureWasEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        } catch (Exception ignored) {}

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableTexture();

        try {
            GlStateManager._disableScissorTest();
        } catch (Exception ignored) {}

        try {
            // ===== 渲染面板（面板内部会绘制内容，但不绘制Tab按钮） =====
            if (searchPanel.isVisible() || searchPanel.isAnimating()) {
                searchPanel.render(poseStack, 0, 0, 0);
            }

            // ===== 独立绘制Tab按钮（使用面板的动画偏移量） =====
            drawTabButton(poseStack);

        } finally {
            try {
                if (depthTestWasEnabled) RenderSystem.enableDepthTest();
                else RenderSystem.disableDepthTest();

                if (blendWasEnabled) RenderSystem.enableBlend();
                else RenderSystem.disableBlend();

                if (textureWasEnabled) RenderSystem.enableTexture();
                else RenderSystem.disableTexture();

                if (scissorWasEnabled) {
                    try {
                        GlStateManager._enableScissorTest();
                    } catch (Exception ignored) {}
                } else {
                    try {
                        GlStateManager._disableScissorTest();
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            poseStack.popPose();
        }
    }

    // ============================================================
    // ===== Tab按钮绘制（使用面板的动画偏移量） ==================
    // ============================================================

    private void drawTabButton(PoseStack poseStack) {
        if (searchPanel == null) return;

        // ===== 从面板获取所有状态 =====
        int animationOffset = searchPanel.getAnimationOffset();
        boolean isExpanded = searchPanel.isExpanded();
        int panelWidth = searchPanel.getPanelWidth();
        int panelHeight = searchPanel.getPanelHeight();

        // ===== 计算按钮位置（使用面板的动画偏移） =====
        int btnX, btnY;
        if (isExpanded) {
            // 展开时：面板右边缘 + 动画偏移
            btnX = animationOffset + panelWidth - 1;
        } else {
            // 收起时：屏幕左侧
            btnX = 0;
        }
        btnY = (panelHeight - TAB_BUTTON_HEIGHT) / 2;

        if (btnX < 0) {
            btnX = 0;
        }

        // ===== 边界检查 =====
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        if (btnX + TAB_BUTTON_WIDTH < 0 || btnX > screenWidth) {
            return;
        }

        // ===== 鼠标悬停检测 =====
        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        boolean hover = mouseX >= btnX && mouseX <= btnX + TAB_BUTTON_WIDTH &&
                mouseY >= btnY && mouseY <= btnY + TAB_BUTTON_HEIGHT;

        // ===== 绘制 =====
        int bgColor = hover ? 0xCC444444 : 0xCC1A1A1A;
        GuiComponent.fill(poseStack, btnX, btnY, btnX + TAB_BUTTON_WIDTH, btnY + TAB_BUTTON_HEIGHT, bgColor);

        int borderColor = 0x44FFFFFF;
        GuiComponent.fill(poseStack, btnX, btnY, btnX + TAB_BUTTON_WIDTH, btnY + 1, borderColor);
        GuiComponent.fill(poseStack, btnX, btnY + TAB_BUTTON_HEIGHT - 1, btnX + TAB_BUTTON_WIDTH, btnY + TAB_BUTTON_HEIGHT, borderColor);

        if (isExpanded) {
            GuiComponent.fill(poseStack, btnX + TAB_BUTTON_WIDTH - 1, btnY, btnX + TAB_BUTTON_WIDTH, btnY + TAB_BUTTON_HEIGHT, borderColor);
        } else {
            GuiComponent.fill(poseStack, btnX, btnY, btnX + 1, btnY + TAB_BUTTON_HEIGHT, borderColor);
            GuiComponent.fill(poseStack, btnX + TAB_BUTTON_WIDTH - 1, btnY, btnX + TAB_BUTTON_WIDTH, btnY + TAB_BUTTON_HEIGHT, borderColor);
        }

        Font font = mc.font;
        String arrow = isExpanded ? "◀" : "▶";
        int textX = btnX + (TAB_BUTTON_WIDTH - font.width(arrow)) / 2;
        int textY = btnY + (TAB_BUTTON_HEIGHT - font.lineHeight) / 2 + 1;
        int textColor = hover ? 0xFFFFFFFF : 0xCCCCCCCC;
        font.draw(poseStack, arrow, textX, textY, textColor);
    }
}