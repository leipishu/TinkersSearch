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
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (searchPanel == null) return;

        if (searchPanel.isVisible()) {
            if (event.getKey() == GLFW.GLFW_KEY_ESCAPE && event.getAction() == GLFW.GLFW_PRESS) {
                if (interactionHandler.isSearchBoxFocused()) {
                    interactionHandler.setSearchBoxFocused(false);
                }
                return;
            }

            PanelInteractionHandler handler = searchPanel.getInteractionHandler();
            if (handler != null) {
                if (event.getAction() == GLFW.GLFW_PRESS) {
                    if (handler.handleKeyPressed(event.getKey(), event.getScanCode(), event.getModifiers())) {
                        return;
                    }
                }
            }
        }
    }

    private void togglePanel() {
        System.out.println("Tinker's Search: Toggling panel!");
        searchPanel.toggleVisibility();

        if (searchPanel.isVisible()) {
            searchPanel.forceUpdatePosition();
            if (smelteryBlockEntity != null) {
                searchPanel.refreshMoltenFluids();
            } else {
                Screen screen = Minecraft.getInstance().screen;
                if (screen != null && isSmelteryScreen(screen)) {
                    findSmelteryBlockEntity(screen);
                    if (smelteryBlockEntity != null) {
                        searchPanel.refreshMoltenFluids();
                    }
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

    /**
     * 拦截所有鼠标点击事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseClickPre(ScreenEvent.MouseClickedEvent.Pre event) {
        if (!isSmelteryScreen || searchPanel == null) return;

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        if (checkTabButtonClick(event)) {
            return;
        }

        if (searchPanel.isVisible() || searchPanel.isAnimating()) {
            if (searchPanel.isPointInsidePanel(mouseX, mouseY)) {
                if (searchPanel.isAnimating()) {
                    event.setCanceled(true);
                    return;
                }
                interactionHandler.handleMouseClicked(mouseX, mouseY, event.getButton());
                event.setCanceled(true);
                return;
            }
        }
    }


    /**
     * 检测 Tab 按钮点击
     */
    private boolean checkTabButtonClick(ScreenEvent.MouseClickedEvent.Pre event) {
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        int panelHeight = searchPanel.getPanelHeight();
        int panelX = searchPanel.getActualPanelX();
        boolean isExpanded = searchPanel.isVisible() || searchPanel.isAnimating();

        int btnX, btnY;
        if (isExpanded) {
            btnX = panelX + searchPanel.getPanelWidth() - 1;
        } else {
            btnX = 0;
        }
        btnY = (panelHeight - TAB_BUTTON_HEIGHT) / 2;

        if (mouseX >= btnX && mouseX <= btnX + TAB_BUTTON_WIDTH &&
                mouseY >= btnY && mouseY <= btnY + TAB_BUTTON_HEIGHT) {
            if (searchPanel.isAnimating()) {
                event.setCanceled(true);
                return true;
            }
            togglePanel();
            event.setCanceled(true);
            System.out.println("Tinker's Search: Tab button clicked, panel visible: " + searchPanel.isVisible());
            return true;
        }
        return false;
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

    // ========================================================
    // ===== 核心修复：最终渲染层 =============================
    // ========================================================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onScreenDrawPost(ScreenEvent.DrawScreenEvent.Post event) {
        if (!isSmelteryScreen || searchPanel == null) return;

        PoseStack poseStack = event.getPoseStack();

        // ===== 1. 保存当前 PoseStack =====
        poseStack.pushPose();

        // ===== 2. 提升 Z 层级到最前 =====
        // Minecraft GUI 默认使用 0 作为基准，tooltip 通常用 400
        // 这里用 500 确保在所有 vanilla 元素之上
        poseStack.translate(0, 0, 500);

        // ===== 3. 清除深度缓冲（关键！）=====
        // 这样之前屏幕渲染留下的深度值不会干扰我们的覆盖层
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        // ===== 4. 保存 OpenGL 状态并设置安全状态 =====
        boolean depthTestWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean textureWasEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableTexture();  // ← 必须启用纹理！
        GlStateManager._disableScissorTest();

        try {
            // ===== 5. 绘制 Tab 按钮 =====
            drawTabButton(poseStack);

            // ===== 6. 绘制面板 =====
            if (searchPanel.isVisible() || searchPanel.isAnimating()) {
                searchPanel.render(poseStack, 0, 0, 0);
            }

        } finally {
            // ===== 7. 恢复 OpenGL 状态 =====
            if (depthTestWasEnabled) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();

            if (blendWasEnabled) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();

            if (textureWasEnabled) RenderSystem.enableTexture();
            else RenderSystem.disableTexture();

            if (scissorWasEnabled) GlStateManager._enableScissorTest();
            else GlStateManager._disableScissorTest();

            // ===== 8. 恢复 PoseStack =====
            poseStack.popPose();
        }
    }

    /**
     * 独立绘制展开/收起按钮
     */
    private void drawTabButton(PoseStack poseStack) {
        int panelHeight = searchPanel.getPanelHeight();
        int panelX = searchPanel.getActualPanelX();
        boolean isExpanded = searchPanel.isVisible() || searchPanel.isAnimating();

        int btnX, btnY;
        if (isExpanded) {
            btnX = panelX + searchPanel.getPanelWidth() - 1;
        } else {
            btnX = 0;
        }
        btnY = (panelHeight - TAB_BUTTON_HEIGHT) / 2;

        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        boolean hover = mouseX >= btnX && mouseX <= btnX + TAB_BUTTON_WIDTH &&
                mouseY >= btnY && mouseY <= btnY + TAB_BUTTON_HEIGHT;

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