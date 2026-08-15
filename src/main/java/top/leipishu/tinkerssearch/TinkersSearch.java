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
    // ===== 删除 panelVisible 字段，直接使用 searchPanel.isVisible() =====
    // private boolean panelVisible = false;
    private BlockEntity smelteryBlockEntity = null;

    private int lastScreenWidth = 0;
    private int lastScreenHeight = 0;
    private boolean hasInitialized = false;

    private static final String[] SMELTERY_FIELD_NAMES = {"te", "tileEntity", "blockEntity", "smeltery", "tile"};

    private long lastToggleTime = 0;
    private static final long TOGGLE_COOLDOWN = 200;

    private boolean jeiAvailable;

    // ===== 按钮尺寸 =====
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

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.InitScreenEvent.Post event) {
        Screen screen = event.getScreen();
        if (screen == null) return;

        boolean isSmeltery = isSmelteryScreen(screen);

        if (isSmeltery) {
            handleSmelteryOpen(screen);
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
        searchPanel.updatePanelPosition();

        // ===== 使用 searchPanel.isVisible() 判断 =====
        if (searchPanel.isVisible()) {
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

    private void handleSmelteryClose() {
        if (isSmelteryScreen) {
            isSmelteryScreen = false;
            // ===== 关键修改：关闭冶炼炉时不要强制关闭面板 =====
            // 只刷新 JEI 区域，面板状态保持不变
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

        // ===== 如果搜索框获得焦点，完全跳过 =====
        if (searchPanel.isVisible() && interactionHandler != null && interactionHandler.isSearchBoxFocused()) {
            return;
        }

        // ===== ESC 键：完全忽略 =====
        if (event.getKey() == GLFW.GLFW_KEY_ESCAPE && event.getAction() == GLFW.GLFW_PRESS) {
            return;
        }

        // ===== 搜索框按键处理（未获得焦点时） =====
        if (searchPanel.isVisible()) {
            PanelInteractionHandler handler = searchPanel.getInteractionHandler();
            if (handler != null) {
                if (event.getKey() == GLFW.GLFW_KEY_BACKSPACE && event.getAction() == GLFW.GLFW_PRESS) {
                    handler.handleKeyPressed(event.getKey(), event.getScanCode(), event.getModifiers());
                    return;
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyboardKeyPressedPre(ScreenEvent.KeyboardKeyPressedEvent.Pre event) {
        if (searchPanel == null) return;
        if (!searchPanel.isVisible()) return;
        if (!interactionHandler.isSearchBoxFocused()) return;

        int key = event.getKeyCode();
        int scanCode = event.getScanCode();
        int modifiers = event.getModifiers();

        // Backspace：删除字符
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            interactionHandler.handleKeyPressed(key, scanCode, modifiers);
            event.setCanceled(true);
            return;
        }

        // Enter / ESC：取消焦点
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
            interactionHandler.setSearchBoxFocused(false);
            event.setCanceled(true);
            return;
        }

        // 其他所有按键：完全拦截，不触发任何快捷键
        event.setCanceled(true);
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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseClick(ScreenEvent.MouseClickedEvent.Pre event) {
        if (!isSmelteryScreen || searchPanel == null) return;

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        // ===== 计算按钮位置 =====
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

        // ===== 检测按钮点击 =====
        if (mouseX >= btnX && mouseX <= btnX + TAB_BUTTON_WIDTH &&
                mouseY >= btnY && mouseY <= btnY + TAB_BUTTON_HEIGHT) {
            if (searchPanel.isAnimating()) {
                event.setCanceled(true);
                return;
            }
            togglePanel();
            event.setCanceled(true);
            System.out.println("Tinker's Search: Tab button clicked, panel visible: " + searchPanel.isVisible());
            return;
        }

        // ===== 面板内部点击 =====
        if (searchPanel.isVisible() || searchPanel.isAnimating()) {
            if (searchPanel.isPointInsidePanel(mouseX, mouseY)) {
                if (searchPanel.isAnimating()) {
                    event.setCanceled(true);
                    return;
                }
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
        // Pre 阶段不绘制
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onScreenDrawPost(ScreenEvent.DrawScreenEvent.Post event) {
        if (!isSmelteryScreen || searchPanel == null) return;

        PoseStack poseStack = event.getPoseStack();

        // ===== 1. 先绘制独立的按钮（始终绘制） =====
        drawTabButton(poseStack);

        // ===== 2. 再绘制面板（如果可见或动画中） =====
        if (searchPanel.isVisible() || searchPanel.isAnimating()) {
            try {
                searchPanel.render(poseStack, 0, 0, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }
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

        // 获取鼠标位置
        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        boolean hover = mouseX >= btnX && mouseX <= btnX + TAB_BUTTON_WIDTH &&
                mouseY >= btnY && mouseY <= btnY + TAB_BUTTON_HEIGHT;

        // ===== 按钮背景 =====
        int bgColor = hover ? 0xCC444444 : 0xCC1A1A1A;
        GuiComponent.fill(poseStack, btnX, btnY, btnX + TAB_BUTTON_WIDTH, btnY + TAB_BUTTON_HEIGHT, bgColor);

        // ===== 边框 =====
        int borderColor = 0x44FFFFFF;
        GuiComponent.fill(poseStack, btnX, btnY, btnX + TAB_BUTTON_WIDTH, btnY + 1, borderColor);
        GuiComponent.fill(poseStack, btnX, btnY + TAB_BUTTON_HEIGHT - 1, btnX + TAB_BUTTON_WIDTH, btnY + TAB_BUTTON_HEIGHT, borderColor);

        if (isExpanded) {
            GuiComponent.fill(poseStack, btnX + TAB_BUTTON_WIDTH - 1, btnY, btnX + TAB_BUTTON_WIDTH, btnY + TAB_BUTTON_HEIGHT, borderColor);
        } else {
            GuiComponent.fill(poseStack, btnX, btnY, btnX + 1, btnY + TAB_BUTTON_HEIGHT, borderColor);
            GuiComponent.fill(poseStack, btnX + TAB_BUTTON_WIDTH - 1, btnY, btnX + TAB_BUTTON_WIDTH, btnY + TAB_BUTTON_HEIGHT, borderColor);
        }

        // ===== 箭头 =====
        Font font = mc.font;
        String arrow = isExpanded ? "◀" : "▶";
        int textX = btnX + (TAB_BUTTON_WIDTH - font.width(arrow)) / 2;
        int textY = btnY + (TAB_BUTTON_HEIGHT - font.lineHeight) / 2 + 1;
        int textColor = hover ? 0xFFFFFFFF : 0xCCCCCCCC;
        font.draw(poseStack, arrow, textX, textY, textColor);
    }
}