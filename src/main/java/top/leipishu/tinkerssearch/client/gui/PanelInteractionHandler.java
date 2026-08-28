package top.leipishu.tinkerssearch.client.gui;

import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

public class PanelInteractionHandler {

    private final FloatingSearchPanel panel;
    private final Runnable onRefresh;
    private final Consumer<List<FluidStack>> onFluidClick;

    private boolean isSearchBoxFocused = false;
    private String searchKeyword = "";
    private int cursorPosition = 0;
    private long lastClickTime = 0;

    private List<FluidStack> allFluids;
    private List<FluidStack> displayedFluids;

    private boolean jeiAvailable;
    private IJeiRuntime jeiRuntime;

    // 缓存反射方法
    private Method jeiGetJeiHelpers;
    private Method jeiGetFocusFactory;
    private Method jeiGetRecipesGui;
    private Method jeiShow;
    private Method jeiCreateFocus;
    private Object fluidStackType;
    private boolean methodsCached = false;

    public PanelInteractionHandler(FloatingSearchPanel panel,
                                   Runnable onRefresh,
                                   Consumer<List<FluidStack>> onFluidClick) {
        this.panel = panel;
        this.onRefresh = onRefresh;
        this.onFluidClick = onFluidClick;
        this.jeiAvailable = ModList.get().isLoaded("jei");
        if (jeiAvailable) {
            this.jeiRuntime = top.leipishu.tinkerssearch.jei.Jei.getJeiRuntime();
            cacheJeiMethods();
        }
        System.out.println("Tinker's Search: PanelInteractionHandler JEI available: " + jeiAvailable);
    }

    /**
     * 缓存 JEI 方法（使用反射，兼容不同版本）
     */
    private void cacheJeiMethods() {
        if (jeiRuntime == null || methodsCached) return;

        try {
            Class<?> runtimeClass = jeiRuntime.getClass();

            // 1. 获取 IJeiHelpers
            jeiGetJeiHelpers = runtimeClass.getMethod("getJeiHelpers");
            Object jeiHelpers = jeiGetJeiHelpers.invoke(jeiRuntime);

            if (jeiHelpers != null) {
                Class<?> helpersClass = jeiHelpers.getClass();
                // 2. 获取 IFocusFactory
                jeiGetFocusFactory = helpersClass.getMethod("getFocusFactory");
                Object focusFactory = jeiGetFocusFactory.invoke(jeiHelpers);

                if (focusFactory != null) {
                    Class<?> focusFactoryClass = focusFactory.getClass();
                    // 3. 获取 createFocus 方法
                    try {
                        jeiCreateFocus = focusFactoryClass.getMethod("createFocus",
                                Class.forName("mezz.jei.api.recipe.RecipeIngredientRole"),
                                Class.forName("mezz.jei.api.ingredients.IIngredientType"),
                                Object.class);
                        System.out.println("Tinker's Search: Using new createFocus API");
                    } catch (NoSuchMethodException e) {
                        try {
                            jeiCreateFocus = focusFactoryClass.getMethod("createFocus",
                                    Class.forName("mezz.jei.api.recipe.IFocus$Mode"),
                                    Object.class);
                            System.out.println("Tinker's Search: Using old createFocus API");
                        } catch (Exception ex) {
                            System.err.println("Tinker's Search: No createFocus method found");
                        }
                    }
                }
            }

            // 4. 获取 IRecipesGui.show 方法
            jeiGetRecipesGui = runtimeClass.getMethod("getRecipesGui");
            Object recipesGui = jeiGetRecipesGui.invoke(jeiRuntime);
            if (recipesGui != null) {
                Class<?> focusClass = Class.forName("mezz.jei.api.recipe.IFocus");
                jeiShow = recipesGui.getClass().getMethod("show", focusClass);
            }

            // 5. 获取 ForgeTypes.FLUID_STACK
            try {
                Class<?> forgeTypesClass = Class.forName("mezz.jei.api.forge.ForgeTypes");
                fluidStackType = forgeTypesClass.getField("FLUID_STACK").get(null);
                System.out.println("Tinker's Search: ForgeTypes.FLUID_STACK found");
            } catch (ClassNotFoundException e) {
                try {
                    Class<?> vanillaTypesClass = Class.forName("mezz.jei.api.ingredients.VanillaTypes");
                    fluidStackType = vanillaTypesClass.getField("FLUID_STACK").get(null);
                    System.out.println("Tinker's Search: VanillaTypes.FLUID_STACK found");
                } catch (Exception ex) {
                    System.err.println("Tinker's Search: FLUID_STACK not found");
                }
            }

            methodsCached = true;
            System.out.println("Tinker's Search: JEI methods cached");

        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to cache JEI methods: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== 数据管理 ====================

    public void setDataRefs(List<FluidStack> allFluids, List<FluidStack> displayedFluids) {
        this.allFluids = allFluids;
        this.displayedFluids = displayedFluids;
    }

    // ==================== 搜索框状态 ====================

    public boolean isSearchBoxFocused() {
        return isSearchBoxFocused;
    }

    public void setSearchBoxFocused(boolean focused) {
        this.isSearchBoxFocused = focused;
    }

    public String getSearchKeyword() {
        return searchKeyword;
    }

    public void setSearchKeyword(String keyword) {
        this.searchKeyword = keyword;
        this.cursorPosition = keyword.length();
    }

    public int getCursorPosition() {
        return cursorPosition;
    }

    public void setCursorPosition(int position) {
        this.cursorPosition = Math.max(0, Math.min(position, searchKeyword.length()));
    }

    // ==================== 键盘输入处理 ====================

    public boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!panel.isVisible() || !isSearchBoxFocused) return false;

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (!searchKeyword.isEmpty() && cursorPosition > 0) {
                    String before = searchKeyword.substring(0, cursorPosition - 1);
                    String after = searchKeyword.substring(cursorPosition);
                    searchKeyword = before + after;
                    cursorPosition--;
                    onRefresh.run();
                }
                return true;

            case GLFW.GLFW_KEY_DELETE:
                if (!searchKeyword.isEmpty() && cursorPosition < searchKeyword.length()) {
                    String before = searchKeyword.substring(0, cursorPosition);
                    String after = searchKeyword.substring(cursorPosition + 1);
                    searchKeyword = before + after;
                    onRefresh.run();
                }
                return true;

            case GLFW.GLFW_KEY_LEFT:
                if (cursorPosition > 0) {
                    cursorPosition--;
                }
                return true;

            case GLFW.GLFW_KEY_RIGHT:
                if (cursorPosition < searchKeyword.length()) {
                    cursorPosition++;
                }
                return true;

            case GLFW.GLFW_KEY_HOME:
                cursorPosition = 0;
                return true;

            case GLFW.GLFW_KEY_END:
                cursorPosition = searchKeyword.length();
                return true;

            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
            case GLFW.GLFW_KEY_ESCAPE:
                isSearchBoxFocused = false;
                return true;

            default:
                return false;
        }
    }

    public boolean handleCharTyped(char codePoint, int modifiers) {
        if (!panel.isVisible() || !isSearchBoxFocused) return false;
        if (Character.isISOControl(codePoint)) return false;

        String before = searchKeyword.substring(0, cursorPosition);
        String after = searchKeyword.substring(cursorPosition);
        searchKeyword = before + codePoint + after;
        cursorPosition++;
        onRefresh.run();
        return true;
    }

    // ==================== 鼠标点击处理 ====================

    public boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        if (!panel.isVisible()) return false;

        int px = panel.getPanelX();
        int py = panel.getPanelY();
        int pw = panel.getPanelWidth();

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < CLICK_COOLDOWN) {
            return false;
        }

        if (isInRefreshButton(mouseX, mouseY, px, py)) {
            onRefresh.run();
            return true;
        }

        if (isInSearchBox(mouseX, mouseY, px, py, pw)) {
            isSearchBoxFocused = true;
            handleMouseClickSetCursor(mouseX, mouseY, px, py, pw);
            return true;
        }

        if (handleCardClick(mouseX, mouseY, px, py, pw, button)) {
            lastClickTime = currentTime;
            return true;
        }

        if (isInPanel(mouseX, mouseY, px, py, pw)) {
            if (isSearchBoxFocused) {
                isSearchBoxFocused = false;
                return true;
            }
        }

        return false;
    }

    public boolean handleMouseClickSetCursor(double mouseX, double mouseY, int px, int py, int pw) {
        if (!panel.isVisible() || !isSearchBoxFocused) return false;

        int boxX = px + 5;
        int boxY = py + SEARCH_BOX_Y;
        int boxW = pw - 10;
        int boxH = SEARCH_BOX_H;

        if (mouseX >= boxX && mouseX <= boxX + boxW &&
                mouseY >= boxY && mouseY <= boxY + boxH) {

            Font font = Minecraft.getInstance().font;
            int clickX = (int) mouseX - boxX - 4;
            int charIndex = 0;
            int currentX = 0;

            for (int i = 0; i < searchKeyword.length(); i++) {
                String subText = searchKeyword.substring(0, i + 1);
                int charWidth = font.width(subText) - font.width(searchKeyword.substring(0, i));
                if (currentX + charWidth / 2 > clickX) {
                    break;
                }
                currentX += charWidth;
                charIndex = i + 1;
            }

            cursorPosition = Math.max(0, Math.min(charIndex, searchKeyword.length()));
            return true;
        }
        return false;
    }

    // ==================== 区域检测 ====================

    private boolean isInRefreshButton(double mouseX, double mouseY, int px, int py) {
        return mouseX >= px + REFRESH_BTN_X && mouseX <= px + REFRESH_BTN_X + REFRESH_BTN_W &&
                mouseY >= py + REFRESH_BTN_Y && mouseY <= py + REFRESH_BTN_Y + REFRESH_BTN_H;
    }

    private boolean isInSearchBox(double mouseX, double mouseY, int px, int py, int pw) {
        return mouseX >= px + 5 && mouseX <= px + 5 + pw - 10 &&
                mouseY >= py + SEARCH_BOX_Y && mouseY <= py + SEARCH_BOX_Y + SEARCH_BOX_H;
    }

    private boolean isInPanel(double mouseX, double mouseY, int px, int py, int pw) {
        return mouseX >= px && mouseX <= px + pw &&
                mouseY >= py && mouseY <= py + panel.getPanelHeight();
    }

    // ==================== 卡片点击 ====================

    private boolean handleCardClick(double mouseX, double mouseY, int px, int py, int pw, int button) {
        if (displayedFluids == null || displayedFluids.isEmpty()) return false;

        CardClickInfo info = getCardAndIconAt(mouseX, mouseY, px, py, pw);
        if (info == null) return false;

        FluidStack fluid = info.fluid;
        boolean isOnIcon = info.isOnIcon;

        if (isOnIcon) {
            return handleJeiIconClick(fluid, button);
        } else {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return panel.moveFluidToBottom(fluid);
            }
        }

        return false;
    }

    // ==================== JEI 交互 ====================

    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean handleJeiIconClick(FluidStack fluid, int button) {
        if (!jeiAvailable) return false;

        jeiRuntime = top.leipishu.tinkerssearch.jei.Jei.getJeiRuntime();
        if (jeiRuntime == null) {
            System.out.println("Tinker's Search: JEI runtime is null");
            return false;
        }

        if (!methodsCached) {
            cacheJeiMethods();
        }

        try {
            Class<?> roleClass = Class.forName("mezz.jei.api.recipe.RecipeIngredientRole");
            Object role;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                role = Enum.valueOf((Class<Enum>) roleClass, "OUTPUT");
                System.out.println("Tinker's Search: left click → OUTPUT for " + fluid.getDisplayName().getString());
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                role = Enum.valueOf((Class<Enum>) roleClass, "INPUT");
                System.out.println("Tinker's Search: right click → INPUT for " + fluid.getDisplayName().getString());
            } else {
                return false;
            }

            Object jeiHelpers = jeiGetJeiHelpers.invoke(jeiRuntime);
            if (jeiHelpers == null) {
                return tryFallback(fluid, button);
            }

            Object focusFactory = jeiGetFocusFactory.invoke(jeiHelpers);
            if (focusFactory == null) {
                return tryFallback(fluid, button);
            }

            Object recipesGui = jeiGetRecipesGui.invoke(jeiRuntime);
            if (recipesGui == null) {
                return tryFallback(fluid, button);
            }

            Object focus;
            if (jeiCreateFocus.getParameterCount() == 3) {
                if (fluidStackType == null) {
                    fluidStackType = FluidStack.class;
                }
                focus = jeiCreateFocus.invoke(focusFactory, role, fluidStackType, fluid);
            } else {
                Object mode;
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    Class<?> modeClass = Class.forName("mezz.jei.api.recipe.IFocus$Mode");
                    mode = Enum.valueOf((Class<Enum>) modeClass, "OUTPUT");
                } else {
                    Class<?> modeClass = Class.forName("mezz.jei.api.recipe.IFocus$Mode");
                    mode = Enum.valueOf((Class<Enum>) modeClass, "INPUT");
                }
                focus = jeiCreateFocus.invoke(focusFactory, mode, fluid);
            }

            jeiShow.invoke(recipesGui, focus);
            System.out.println("Tinker's Search: JEI Focus API succeeded");
            return true;

        } catch (ClassNotFoundException e) {
            System.out.println("Tinker's Search: Class not found: " + e.getMessage());
            return tryFallback(fluid, button);
        } catch (Exception e) {
            System.err.println("Tinker's Search: Focus API failed: " + e.getMessage());
            e.printStackTrace();
            return tryFallback(fluid, button);
        }
    }

    /**
     * 回退方案：模拟按键
     */
    private boolean tryFallback(FluidStack fluid, int button) {
        try {
            Minecraft mc = Minecraft.getInstance();
            long windowHandle = mc.getWindow().getWindow();

            int key;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                key = GLFW.GLFW_KEY_R;
                System.out.println("Tinker's Search: simulate recipe (R Key)");
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                key = GLFW.GLFW_KEY_U;
                System.out.println("Tinker's Search: simulate usage (U Key)");
            } else {
                return false;
            }

            mc.keyboardHandler.keyPress(windowHandle, key, 0, 1, 0);
            mc.keyboardHandler.keyPress(windowHandle, key, 0, 0, 0);
            return true;

        } catch (Exception e) {
            System.err.println("Tinker's Search: key simulation failed: " + e.getMessage());
        }
        return false;
    }

    // ==================== 卡片检测 ====================

    private CardClickInfo getCardAndIconAt(double mouseX, double mouseY, int px, int py, int pw) {
        if (displayedFluids == null || displayedFluids.isEmpty()) return null;

        int cardW = (pw - 10 - CARD_SPACING - SCROLL_BAR_WIDTH - SCROLL_BAR_PADDING) / ITEMS_PER_ROW;
        int cardH = CARD_HEIGHT;

        int scrollOffset = panel.getScrollOffset();
        int startY = py + CARDS_START_Y - scrollOffset;

        for (int i = 0; i < displayedFluids.size(); i++) {
            int row = i / ITEMS_PER_ROW;
            int col = i % ITEMS_PER_ROW;
            int cardX = px + 5 + col * (cardW + CARD_SPACING);
            int cardY = startY + row * (cardH + CARD_SPACING);

            if (mouseX >= cardX && mouseX <= cardX + cardW &&
                    mouseY >= cardY && mouseY <= cardY + cardH) {

                FluidStack fluid = displayedFluids.get(i);

                int iconSize = ICON_SIZE;
                int iconX = cardX + 3;
                int iconY = cardY + (cardH - iconSize) / 2;

                int padding = 2;
                boolean isOnIcon = mouseX >= iconX - padding && mouseX <= iconX + iconSize + padding &&
                        mouseY >= iconY - padding && mouseY <= iconY + iconSize + padding;

                return new CardClickInfo(fluid, isOnIcon);
            }
        }
        return null;
    }

    public void clearSearch() {
        this.searchKeyword = "";
        this.cursorPosition = 0;
        this.isSearchBoxFocused = false;
        onRefresh.run();
    }

    public void refreshData() {
        onRefresh.run();
    }

    private static class CardClickInfo {
        final FluidStack fluid;
        final boolean isOnIcon;

        CardClickInfo(FluidStack fluid, boolean isOnIcon) {
            this.fluid = fluid;
            this.isOnIcon = isOnIcon;
        }
    }
}