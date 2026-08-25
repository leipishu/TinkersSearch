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
import top.leipishu.tinkerssearch.alloy.TinkersAlloyReader;
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

    // TinkersAlloyReader.setDebugMode(true);

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
            handleSmelteryOpen(screen);  // 调用唯一的方法
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
                // ===== 刷新温度（现在可以访问） =====
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

            // ===== 关闭搜索面板 =====
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

    // 在 TinkersSearch.java 中
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyboardKeyPressedPre(ScreenEvent.KeyboardKeyPressedEvent.Pre event) {
        if (searchPanel == null) return;
        if (!searchPanel.isVisible()) return;
        if (!interactionHandler.isSearchBoxFocused()) return;

        int keyCode = event.getKeyCode();

        // Backspace：让 PanelInteractionHandler 处理删除字符
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (interactionHandler.handleKeyPressed(keyCode, event.getScanCode(), event.getModifiers())) {
                event.setCanceled(true);
            }
            return;
        }

        // Enter / Escape：取消搜索框焦点
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            interactionHandler.setSearchBoxFocused(false);
            event.setCanceled(true);
            return;
        }

        // 其他所有按键：在这里直接拦截，阻止触发任何快捷键
        event.setCanceled(true);
    }

    private void togglePanel() {
        System.out.println("Tinker's Search: Toggling panel!");
        searchPanel.toggleVisibility();

        if (searchPanel.isVisible()) {
            searchPanel.forceUpdatePosition();

            // ===== 刷新温度（现在可以访问） =====
            searchPanel.refreshTemperature();

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
                // ===== 直接调用面板的鼠标点击方法 =====
                searchPanel.mouseClicked(mouseX, mouseY, event.getButton());
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderTooltipPre(RenderTooltipEvent.Pre event) {
        // 仅当处于冶炼炉屏幕且面板存在
        if (!isSmelteryScreen || searchPanel == null) return;

        // 面板可见或动画中
        if (searchPanel.isVisible() || searchPanel.isAnimating()) {
            // 获取当前鼠标位置（屏幕坐标）
            Minecraft mc = Minecraft.getInstance();
            double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
            double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();

            if (searchPanel.isPointInsidePanel(mouseX, mouseY)) {
                event.setCanceled(true);  // 阻止 Tooltip 渲染
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

    @SubscribeEvent
    public void onWorldLoad(net.minecraftforge.event.world.WorldEvent.Load event) {
        if (event.getWorld().isClientSide()) {
            TinkersAlloyReader.clearCache();
            System.out.println("Tinker's Search: Cache cleared on world load");
        }
    }

    @SubscribeEvent
    public void onRecipesUpdated(net.minecraftforge.event.OnDatapackSyncEvent event) {
        TinkersAlloyReader.clearCache();
        System.out.println("Tinker's Search: Cache cleared on datapack sync");
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

        poseStack.pushPose();
        poseStack.translate(0, 0, 500);

        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        boolean depthTestWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean textureWasEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableTexture();

        // ===== 使用 GlStateManager 禁用 Scissor（安全方式） =====
        try {
            GlStateManager._disableScissorTest();
        } catch (Exception ignored) {}

        try {
            // ===== Tab 按钮始终绘制 =====
            drawTabButton(poseStack);

            // ===== 面板内容只在可见或动画中绘制 =====
            if (searchPanel.isVisible() || searchPanel.isAnimating()) {
                searchPanel.render(poseStack, 0, 0, 0);
            }
        } finally {
            // ===== 恢复状态 =====
            if (depthTestWasEnabled) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();

            if (blendWasEnabled) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();

            if (textureWasEnabled) RenderSystem.enableTexture();
            else RenderSystem.disableTexture();

            // ===== 恢复 Scissor 状态 =====
            if (scissorWasEnabled) {
                try {
                    GlStateManager._enableScissorTest();
                } catch (Exception ignored) {}
            } else {
                try {
                    GlStateManager._disableScissorTest();
                } catch (Exception ignored) {}
            }

            poseStack.popPose();
        }
    }

    private void drawTabButton(PoseStack poseStack) {
        // ===== 面板对象不存在时不绘制 =====
        if (searchPanel == null) return;

        int panelHeight = searchPanel.getPanelHeight();
        int panelX = searchPanel.getActualPanelX();
        boolean isExpanded = searchPanel.isVisible() || searchPanel.isAnimating();

        int btnX, btnY;
        if (isExpanded) {
            btnX = panelX + searchPanel.getPanelWidth() - 1;
        } else {
            // ===== 收起状态：按钮在屏幕左侧（x=0） =====
            btnX = 0;
        }
        btnY = (panelHeight - TAB_BUTTON_HEIGHT) / 2;

        // ===== 确保按钮不超出屏幕左侧 =====
        if (btnX < 0) {
            btnX = 0;
        }

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        // ===== 如果按钮完全在屏幕外，不绘制 =====
        if (btnX + TAB_BUTTON_WIDTH < 0 || btnX > screenWidth) {
            return;
        }

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