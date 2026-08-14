package top.leipishu.tinkerssearch;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import top.leipishu.tinkerssearch.client.gui.FloatingSearchPanel;
import top.leipishu.tinkerssearch.client.gui.PanelInteractionHandler;

import java.lang.reflect.Field;

@Mod("tinkerssearch")
public class TinkersSearch {

    private static FloatingSearchPanel searchPanel;
    private PanelInteractionHandler interactionHandler;

    private boolean isSmelteryScreen = false;
    private boolean panelVisible = false;
    private BlockEntity smelteryBlockEntity = null;

    private int lastScreenWidth = 0;
    private int lastScreenHeight = 0;
    private boolean hasInitialized = false;

    private static final String[] SMELTERY_FIELD_NAMES = {"te", "tileEntity", "blockEntity", "smeltery", "tile"};

    // Ctrl+F 防抖
    private long lastToggleTime = 0;
    private static final long TOGGLE_COOLDOWN = 200;

    // ===== 按钮位置（强制固定在物品栏下方） =====
    private static final int BUTTON_X_OFFSET = 4;
    private static final int BUTTON_Y_OFFSET = 4;

    // ===== 缓存的 GUI 位置 =====
    private int cachedLeftPos = 0;
    private int cachedTopPos = 0;
    private int cachedImageWidth = 0;
    private int cachedImageHeight = 0;

    // ===== 按钮位置缓存（强制固定） =====
    private int buttonX = 0;
    private int buttonY = 0;
    private int buttonWidth = 0;
    private int buttonHeight = 14;

    public TinkersSearch() {
        System.out.println("Tinker's Search mod initialized!");
        MinecraftForge.EVENT_BUS.register(this);

        searchPanel = new FloatingSearchPanel();
        interactionHandler = searchPanel.getInteractionHandler();
        System.out.println("Tinker's Search: Panel created!");
    }

    // ==================== 屏幕初始化 ====================

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.InitScreenEvent.Post event) {
        Screen screen = event.getScreen();
        if (screen == null) return;

        boolean isSmeltery = isSmelteryScreen(screen);

        if (isSmeltery) {
            handleSmelteryOpen(screen);
            forceUpdateButtonPosition(screen);
        } else {
            handleSmelteryClose();
        }
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
        updatePanelPosition(screen);
        updateCachedPosition(screen);
        forceUpdateButtonPosition(screen);

        if (panelVisible) {
            searchPanel.setVisible(true);
            if (!hasInitialized) {
                searchPanel.refreshMoltenFluids();
                hasInitialized = true;
            }
        } else {
            searchPanel.setVisible(false);
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

    private void updatePanelPosition(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen)) return;

        try {
            AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) screen;
            Field leftPos = AbstractContainerScreen.class.getDeclaredField("leftPos");
            Field topPos = AbstractContainerScreen.class.getDeclaredField("topPos");
            Field imageWidth = AbstractContainerScreen.class.getDeclaredField("imageWidth");
            Field imageHeight = AbstractContainerScreen.class.getDeclaredField("imageHeight");

            leftPos.setAccessible(true);
            topPos.setAccessible(true);
            imageWidth.setAccessible(true);
            imageHeight.setAccessible(true);

            searchPanel.updatePosition(
                    leftPos.getInt(container),
                    topPos.getInt(container),
                    imageWidth.getInt(container),
                    imageHeight.getInt(container)
            );
        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to get GUI position: " + e.getMessage());
        }
    }

    private void updateCachedPosition(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen)) return;

        try {
            AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) screen;
            Field leftPos = AbstractContainerScreen.class.getDeclaredField("leftPos");
            Field topPos = AbstractContainerScreen.class.getDeclaredField("topPos");
            Field imageWidth = AbstractContainerScreen.class.getDeclaredField("imageWidth");
            Field imageHeight = AbstractContainerScreen.class.getDeclaredField("imageHeight");

            leftPos.setAccessible(true);
            topPos.setAccessible(true);
            imageWidth.setAccessible(true);
            imageHeight.setAccessible(true);

            cachedLeftPos = leftPos.getInt(container);
            cachedTopPos = topPos.getInt(container);
            cachedImageWidth = imageWidth.getInt(container);
            cachedImageHeight = imageHeight.getInt(container);
        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to cache position: " + e.getMessage());
        }
    }

    /**
     * 强制更新按钮位置
     */
    private void forceUpdateButtonPosition(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen)) return;

        try {
            AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) screen;
            Field leftPos = AbstractContainerScreen.class.getDeclaredField("leftPos");
            Field topPos = AbstractContainerScreen.class.getDeclaredField("topPos");
            Field imageWidth = AbstractContainerScreen.class.getDeclaredField("imageWidth");
            Field imageHeight = AbstractContainerScreen.class.getDeclaredField("imageHeight");

            leftPos.setAccessible(true);
            topPos.setAccessible(true);
            imageWidth.setAccessible(true);
            imageHeight.setAccessible(true);

            int lPos = leftPos.getInt(container);
            int tPos = topPos.getInt(container);
            int iWidth = imageWidth.getInt(container);
            int iHeight = imageHeight.getInt(container);

            Font font = Minecraft.getInstance().font;
            String buttonTextKey = panelVisible ? "button.tinkerssearch.close" : "button.tinkerssearch.open";
            String buttonText = new TranslatableComponent(buttonTextKey).getString();
            int textWidth = font.width(buttonText);
            int padding = 8;
            int bWidth = textWidth + padding;
            int bHeight = 14;

            int inventoryBottomY = tPos + iHeight;
            int absX = lPos + BUTTON_X_OFFSET;
            int absY = inventoryBottomY + BUTTON_Y_OFFSET;

            int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            if (absY + bHeight > screenHeight - 4) {
                absY = screenHeight - bHeight - 4;
            }

            this.buttonX = absX;
            this.buttonY = absY;
            this.buttonWidth = bWidth;
            this.buttonHeight = bHeight;

            this.cachedLeftPos = lPos;
            this.cachedTopPos = tPos;
            this.cachedImageWidth = iWidth;
            this.cachedImageHeight = iHeight;

        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to force update button position: " + e.getMessage());
        }
    }

    private void handleSmelteryClose() {
        if (isSmelteryScreen) {
            isSmelteryScreen = false;
            panelVisible = false;
            searchPanel.setVisible(false);
            smelteryBlockEntity = null;
            hasInitialized = false;
            System.out.println("Tinker's Search: Smeltery closed");
        }
    }

    // ==================== 窗口尺寸变化 ====================

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen == null) return;

        int currentWidth = mc.getWindow().getGuiScaledWidth();
        int currentHeight = mc.getWindow().getGuiScaledHeight();

        if (currentWidth != lastScreenWidth || currentHeight != lastScreenHeight) {
            lastScreenWidth = currentWidth;
            lastScreenHeight = currentHeight;

            if (isSmelteryScreen && screen instanceof AbstractContainerScreen) {
                updatePanelPosition(screen);
                updateCachedPosition(screen);
                forceUpdateButtonPosition(screen);
                if (searchPanel != null) {
                    searchPanel.forceUpdatePosition();
                }
                System.out.println("Tinker's Search: Window resized, position updated");
            }
        }

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
                    updatePanelPosition(screen);
                    updateCachedPosition(screen);
                    forceUpdateButtonPosition(screen);
                }
                togglePanel();
            } else {
                System.out.println("Tinker's Search: KeyBinding pressed but not in smeltery screen");
            }
        }
    }

    // ==================== 键盘事件 ====================

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (searchPanel == null) return;

        if (searchPanel.isVisible()) {
            if (event.getKey() == GLFW.GLFW_KEY_BACKSPACE && event.getAction() == GLFW.GLFW_PRESS) {
                if (interactionHandler.handleKeyPressed(event.getKey(), event.getScanCode(), event.getModifiers())) {
                    return;
                }
            }
            if (event.getKey() == GLFW.GLFW_KEY_ESCAPE && event.getAction() == GLFW.GLFW_PRESS) {
                if (interactionHandler.handleKeyPressed(event.getKey(), event.getScanCode(), event.getModifiers())) {
                    return;
                }
            }
        }

        if (event.getKey() == GLFW.GLFW_KEY_ESCAPE && event.getAction() == GLFW.GLFW_PRESS) {
            if (panelVisible) {
                panelVisible = false;
                searchPanel.setVisible(false);
                System.out.println("Tinker's Search: ESC pressed, hiding panel");
            }
        }
    }

    private void togglePanel() {
        System.out.println("Tinker's Search: Toggling panel!");
        panelVisible = !panelVisible;
        searchPanel.toggleVisibility();

        if (panelVisible) {
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
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onCharTyped(ScreenEvent.KeyboardCharTypedEvent.Pre event) {
        if (!isSmelteryScreen || searchPanel == null || !searchPanel.isVisible()) return;
        if (!interactionHandler.isSearchBoxFocused()) return;

        if (interactionHandler.handleCharTyped(event.getCodePoint(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    // ==================== 鼠标事件 ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseClick(ScreenEvent.MouseClickedEvent.Pre event) {
        if (!isSmelteryScreen || searchPanel == null) return;

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        if (isButtonHovered(mouseX, mouseY)) {
            togglePanel();
            event.setCanceled(true);
            System.out.println("Tinker's Search: Button clicked, panel visible: " + panelVisible);
            return;
        }

        if (searchPanel.isVisible() && searchPanel.isPointInsidePanel(mouseX, mouseY)) {
            interactionHandler.handleMouseClicked(mouseX, mouseY, event.getButton());
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseScroll(ScreenEvent.MouseScrollEvent.Pre event) {
        if (!isSmelteryScreen || searchPanel == null || !searchPanel.isVisible()) {
            return;
        }

        if (searchPanel.isPointInsidePanel(event.getMouseX(), event.getMouseY())) {
            if (searchPanel.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDelta())) {
                event.setCanceled(true);
            } else {
                event.setCanceled(true);
            }
        }
    }

    // ==================== 渲染 ====================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onScreenDraw(ScreenEvent.DrawScreenEvent.Post event) {
        // 1. 绘制面板
        if (isSmelteryScreen && searchPanel != null && searchPanel.isVisible()) {
            try {
                PoseStack poseStack = event.getPoseStack();
                searchPanel.render(poseStack, 0, 0, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 2. 绘制按钮
        drawToggleButton(event);
    }

    // ==================== 按钮绘制 ====================

    private void drawToggleButton(ScreenEvent.DrawScreenEvent.Post event) {
        if (!isSmelteryScreen) return;

        PoseStack poseStack = event.getPoseStack();
        Font font = Minecraft.getInstance().font;

        String buttonTextKey = panelVisible ? "button.tinkerssearch.close" : "button.tinkerssearch.open";
        String buttonText = new TranslatableComponent(buttonTextKey).getString();

        int textWidth = font.width(buttonText);
        int padding = 8;
        int bWidth = textWidth + padding;
        int bHeight = 14;

        int absX = this.buttonX;
        int absY = this.buttonY;

        if (absX == 0 && absY == 0) {
            int inventoryBottomY = cachedTopPos + cachedImageHeight;
            absX = cachedLeftPos + BUTTON_X_OFFSET;
            absY = inventoryBottomY + BUTTON_Y_OFFSET;

            int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            if (absY + bHeight > screenHeight - 4) {
                absY = screenHeight - bHeight - 4;
            }

            this.buttonX = absX;
            this.buttonY = absY;
            this.buttonWidth = bWidth;
            this.buttonHeight = bHeight;
        }

        boolean hover = isButtonHovered(event.getMouseX(), event.getMouseY());

        int bgColor = hover ? 0xFF4CAF50 : 0xFF2E7D32;
        int borderColor = 0xFFFFFFFF;

        GuiComponent.fill(poseStack, absX, absY, absX + bWidth, absY + bHeight, bgColor);
        GuiComponent.fill(poseStack, absX, absY, absX + bWidth, absY + 1, borderColor);
        GuiComponent.fill(poseStack, absX, absY + bHeight - 1, absX + bWidth, absY + bHeight, borderColor);
        GuiComponent.fill(poseStack, absX, absY, absX + 1, absY + bHeight, borderColor);
        GuiComponent.fill(poseStack, absX + bWidth - 1, absY, absX + bWidth, absY + bHeight, borderColor);

        int textX = absX + (bWidth - textWidth) / 2;
        int textY = absY + (bHeight - font.lineHeight) / 2 + 1;
        font.draw(poseStack, buttonText, textX, textY, 0xFFFFFF);

        if (!panelVisible) {
            String hint = "§8[Ctrl+F]";
            int hintX = absX + bWidth + 4;
            int hintY = absY + (bHeight - font.lineHeight) / 2 + 1;
            font.draw(poseStack, hint, hintX, hintY, 0x666666);
        }
    }

    // ==================== 辅助方法 ====================

    private boolean isButtonHovered(double mouseX, double mouseY) {
        int bWidth = this.buttonWidth;
        int bHeight = this.buttonHeight;

        if (bWidth == 0) {
            Font font = Minecraft.getInstance().font;
            String buttonTextKey = panelVisible ? "button.tinkerssearch.close" : "button.tinkerssearch.open";
            String buttonText = new TranslatableComponent(buttonTextKey).getString();
            int textWidth = font.width(buttonText);
            int padding = 8;
            bWidth = textWidth + padding;
            bHeight = 14;
        }

        return mouseX >= this.buttonX && mouseX <= this.buttonX + bWidth &&
                mouseY >= this.buttonY && mouseY <= this.buttonY + bHeight;
    }

    private static boolean isKeyDown(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }
}