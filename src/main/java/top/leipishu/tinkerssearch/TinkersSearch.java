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
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;
import top.leipishu.tinkerssearch.client.gui.FloatingSearchPanel;
import top.leipishu.tinkerssearch.client.gui.PanelInteractionHandler;
import top.leipishu.tinkerssearch.jei.Jei;

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

    private long lastToggleTime = 0;
    private static final long TOGGLE_COOLDOWN = 200;

    private static final int BUTTON_X_OFFSET = 4;
    private static final int BUTTON_Y_OFFSET = 4;

    private int cachedGuiLeft = 0;
    private int cachedGuiTop = 0;
    private int cachedXSize = 0;
    private int cachedYSize = 0;

    private int buttonX = 0;
    private int buttonY = 0;
    private int buttonWidth = 0;
    private int buttonHeight = 14;

    private boolean jeiAvailable;

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
            if (jeiAvailable) {
                Jei.refreshExclusionAreas();
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
            int guiLeft = container.getGuiLeft();
            int guiTop = container.getGuiTop();
            int xSize = container.getXSize();
            int ySize = container.getYSize();

            searchPanel.updatePosition(guiLeft, guiTop, xSize, ySize);
        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to get GUI position: " + e.getMessage());
        }
    }

    private void updateCachedPosition(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen)) return;

        try {
            AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) screen;
            cachedGuiLeft = container.getGuiLeft();
            cachedGuiTop = container.getGuiTop();
            cachedXSize = container.getXSize();
            cachedYSize = container.getYSize();
        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to cache position: " + e.getMessage());
        }
    }

    private void forceUpdateButtonPosition(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen)) return;

        try {
            AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) screen;
            int guiLeft = container.getGuiLeft();
            int guiTop = container.getGuiTop();
            int xSize = container.getXSize();
            int ySize = container.getYSize();

            Font font = Minecraft.getInstance().font;
            String buttonTextKey = panelVisible ? "button.tinkerssearch.close" : "button.tinkerssearch.open";
            String buttonText = new TranslatableComponent(buttonTextKey).getString();
            int textWidth = font.width(buttonText);
            int padding = 8;
            int bWidth = textWidth + padding;
            int bHeight = 14;

            int inventoryBottomY = guiTop + ySize;
            int absX = guiLeft + BUTTON_X_OFFSET;
            int absY = inventoryBottomY + BUTTON_Y_OFFSET;

            int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            if (absY + bHeight > screenHeight - 4) {
                absY = screenHeight - bHeight - 4;
            }

            this.buttonX = absX;
            this.buttonY = absY;
            this.buttonWidth = bWidth;
            this.buttonHeight = bHeight;

            this.cachedGuiLeft = guiLeft;
            this.cachedGuiTop = guiTop;
            this.cachedXSize = xSize;
            this.cachedYSize = ySize;

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
                if (jeiAvailable) {
                    Jei.refreshExclusionAreas();
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
                if (jeiAvailable) {
                    Jei.refreshExclusionAreas();
                }
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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseClick(ScreenEvent.MouseClickedEvent.Pre event) {
        if (!isSmelteryScreen || searchPanel == null) return;

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        if (isButtonHovered(mouseX, mouseY)) {
            togglePanel();
            event.setCanceled(true);
            return;
        }

        // ===== 动画期间也拦截点击 =====
        if (searchPanel.isVisible() || searchPanel.isAnimating()) {
            if (searchPanel.isPointInsidePanel(mouseX, mouseY)) {
                boolean handled = interactionHandler.handleMouseClicked(mouseX, mouseY, event.getButton());
                event.setCanceled(true);
                return;
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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onScreenDrawPre(ScreenEvent.DrawScreenEvent.Pre event) {
        // Pre 阶段：不绘制任何东西，留给 Post 阶段统一绘制
        // 或者保留但会被 Post 覆盖，为了不重复绘制，这里留空
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onScreenDrawPost(ScreenEvent.DrawScreenEvent.Post event) {
        // ===== Post 阶段：绘制整个面板（背景 + 所有组件） =====
        // 这样就在 JEI 书签上面了
        if (isSmelteryScreen && searchPanel != null && searchPanel.isVisible()) {
            try {
                PoseStack poseStack = event.getPoseStack();
                searchPanel.render(poseStack, 0, 0, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 绘制按钮
        drawToggleButton(event);
    }

    /**
     * 绘制打开/关闭面板的按钮
     */
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

        // 如果按钮位置未初始化，计算默认位置
        if (absX == 0 && absY == 0) {
            int inventoryBottomY = cachedGuiTop + cachedYSize;
            absX = cachedGuiLeft + BUTTON_X_OFFSET;
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

        // 按钮背景颜色
        int bgColor = hover ? 0xFF4CAF50 : 0xFF2E7D32;
        int borderColor = 0xFFFFFFFF;

        // 绘制按钮背景
        GuiComponent.fill(poseStack, absX, absY, absX + bWidth, absY + bHeight, bgColor);
        // 绘制边框
        GuiComponent.fill(poseStack, absX, absY, absX + bWidth, absY + 1, borderColor);
        GuiComponent.fill(poseStack, absX, absY + bHeight - 1, absX + bWidth, absY + bHeight, borderColor);
        GuiComponent.fill(poseStack, absX, absY, absX + 1, absY + bHeight, borderColor);
        GuiComponent.fill(poseStack, absX + bWidth - 1, absY, absX + bWidth, absY + bHeight, borderColor);

        // 绘制按钮文字
        int textX = absX + (bWidth - textWidth) / 2;
        int textY = absY + (bHeight - font.lineHeight) / 2 + 1;
        font.draw(poseStack, buttonText, textX, textY, 0xFFFFFF);

        // 如果面板未打开，显示快捷键提示
        if (!panelVisible) {
            String hint = "§8[Ctrl+F]";
            int hintX = absX + bWidth + 4;
            int hintY = absY + (bHeight - font.lineHeight) / 2 + 1;
            font.draw(poseStack, hint, hintX, hintY, 0x666666);
        }
    }

    private boolean isButtonHovered(double mouseX, double mouseY) {
        int bWidth = this.buttonWidth;
        int bHeight = this.buttonHeight;

        // 如果宽度为0，重新计算
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
}