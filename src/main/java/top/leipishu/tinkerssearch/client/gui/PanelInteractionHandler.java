package top.leipishu.tinkerssearch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;
import top.leipishu.tinkerssearch.config.PanelConfig;

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
    private Object jeiRuntime;

    // 缓存 JEI 方法
    private Method jeiGetJeiHelpers = null;
    private Method jeiGetFocusFactory = null;
    private Method jeiGetRecipesGui = null;
    private Method jeiGetRecipeManager = null;
    private Method jeiCreateFocus = null;
    private Method jeiShowFocus = null;
    private Method jeiShow = null;
    private Method jeiGetIngredientUnderMouse = null;
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
     * 缓存 JEI 方法以提高性能
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void cacheJeiMethods() {
        if (jeiRuntime == null || methodsCached) return;

        try {
            Class<?> runtimeClass = jeiRuntime.getClass();

            // 1. IJeiRuntime.getJeiHelpers()
            jeiGetJeiHelpers = runtimeClass.getMethod("getJeiHelpers");
            Object jeiHelpers = jeiGetJeiHelpers.invoke(jeiRuntime);

            if (jeiHelpers != null) {
                Class<?> helpersClass = jeiHelpers.getClass();
                // 2. IJeiHelpers.getFocusFactory()
                jeiGetFocusFactory = helpersClass.getMethod("getFocusFactory");
                Object focusFactory = jeiGetFocusFactory.invoke(jeiHelpers);

                if (focusFactory != null) {
                    Class<?> focusFactoryClass = focusFactory.getClass();
                    Class<?> focusModeClass = Class.forName("mezz.jei.api.recipe.IFocus$Mode");
                    // 3. IFocusFactory.createFocus()
                    jeiCreateFocus = focusFactoryClass.getMethod("createFocus", focusModeClass, Object.class);
                }
            }

            // 4. IJeiRuntime.getRecipesGui()
            jeiGetRecipesGui = runtimeClass.getMethod("getRecipesGui");

            // 5. IJeiRuntime.getRecipeManager() (备用)
            jeiGetRecipeManager = runtimeClass.getMethod("getRecipeManager");

            // 6. 获取 show 方法
            Object recipesGui = jeiGetRecipesGui.invoke(jeiRuntime);
            if (recipesGui != null) {
                Class<?> focusClass = Class.forName("mezz.jei.api.recipe.IFocus");
                jeiShow = recipesGui.getClass().getMethod("show", focusClass);
            }

            // 7. 备用：RecipeManager.showFocus()
            Object recipeManager = jeiGetRecipeManager.invoke(jeiRuntime);
            if (recipeManager != null) {
                Class<?> focusClass = Class.forName("mezz.jei.api.recipe.IFocus");
                try {
                    jeiShowFocus = recipeManager.getClass().getMethod("showFocus", focusClass);
                } catch (NoSuchMethodException e) {
                    // 可能没有这个方法
                }
            }

            // 8. IIngredientListOverlay.getIngredientUnderMouse() - 获取鼠标下的物品
            Method getIngredientListOverlay = runtimeClass.getMethod("getIngredientListOverlay");
            Object listOverlay = getIngredientListOverlay.invoke(jeiRuntime);
            if (listOverlay != null) {
                Class<?> listOverlayClass = listOverlay.getClass();
                // 无参数版本
                try {
                    jeiGetIngredientUnderMouse = listOverlayClass.getMethod("getIngredientUnderMouse");
                } catch (NoSuchMethodException e) {
                    // 可能参数不同
                }
            }

            methodsCached = true;
            System.out.println("Tinker's Search: JEI methods cached successfully");
            System.out.println("  jeiGetJeiHelpers: " + (jeiGetJeiHelpers != null));
            System.out.println("  jeiGetFocusFactory: " + (jeiGetFocusFactory != null));
            System.out.println("  jeiCreateFocus: " + (jeiCreateFocus != null));
            System.out.println("  jeiGetRecipesGui: " + (jeiGetRecipesGui != null));
            System.out.println("  jeiShow: " + (jeiShow != null));
            System.out.println("  jeiShowFocus: " + (jeiShowFocus != null));

        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to cache JEI methods: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void setDataRefs(List<FluidStack> allFluids, List<FluidStack> displayedFluids) {
        this.allFluids = allFluids;
        this.displayedFluids = displayedFluids;
    }

    public boolean isSearchBoxFocused() {
        return isSearchBoxFocused;
    }

    public String getSearchKeyword() {
        return searchKeyword;
    }

    public int getCursorPosition() {
        return cursorPosition;
    }

    public void setSearchKeyword(String keyword) {
        this.searchKeyword = keyword;
        this.cursorPosition = keyword.length();
    }

    public void setCursorPosition(int position) {
        this.cursorPosition = Math.max(0, Math.min(position, searchKeyword.length()));
    }

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

    /**
     * 处理卡片点击
     * - 图标区域：交给 JEI 处理（左键配方、右键用途）
     * - 卡片主体：执行移动到最下面
     */
    // 在 PanelInteractionHandler.handleCardClick 方法中修改
    private boolean handleCardClick(double mouseX, double mouseY, int px, int py, int pw, int button) {
        if (displayedFluids == null || displayedFluids.isEmpty()) return false;

        CardClickInfo info = getCardAndIconAt(mouseX, mouseY, px, py, pw);
        if (info == null) return false;

        FluidStack fluid = info.fluid;
        boolean isOnIcon = info.isOnIcon;

        if (isOnIcon) {
            // ===== 图标区域：使用 JEI Focus API =====
            return handleJeiIconClick(fluid, button);
        } else {
            // ===== 卡片主体：左键移动，右键交给上层处理 =====
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return panel.moveFluidToBottom(fluid);
            }
            // 右键返回 false，让上层处理（打开详情）
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                return false;
            }
        }

        return false;
    }

    /**
     * 处理 JEI 图标点击
     * 使用 JEI Focus API（无按键冲突）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean handleJeiIconClick(FluidStack fluid, int button) {
        if (!jeiAvailable) return false;

        // 重新获取 runtime
        jeiRuntime = top.leipishu.tinkerssearch.jei.Jei.getJeiRuntime();
        if (jeiRuntime == null) {
            System.out.println("Tinker's Search: JEI runtime is null");
            return false;
        }

        // 如果方法没缓存，重新缓存
        if (!methodsCached) {
            cacheJeiMethods();
        }

        // ===== 方法1：使用 IFocusFactory + RecipesGui.show() =====
        if (jeiGetJeiHelpers != null && jeiGetFocusFactory != null && jeiCreateFocus != null && jeiShow != null) {
            try {
                // 获取 IJeiHelpers
                Object jeiHelpers = jeiGetJeiHelpers.invoke(jeiRuntime);
                if (jeiHelpers == null) {
                    System.out.println("Tinker's Search: IJeiHelpers is null");
                    return tryFallback(fluid, button);
                }

                // 获取 IFocusFactory
                Object focusFactory = jeiGetFocusFactory.invoke(jeiHelpers);
                if (focusFactory == null) {
                    System.out.println("Tinker's Search: IFocusFactory is null");
                    return tryFallback(fluid, button);
                }

                // 获取 IFocus 相关的类
                Class<?> focusModeClass = Class.forName("mezz.jei.api.recipe.IFocus$Mode");

                // 根据按钮选择模式
                Object mode;
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    // ===== 左键：试试 OUTPUT（显示配方） =====
                    mode = Enum.valueOf((Class<Enum>) focusModeClass, "OUTPUT");
                    System.out.println("Tinker's Search: 左键 → OUTPUT for " + fluid.getDisplayName().getString());
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    // ===== 右键：试试 INPUT（显示用途） =====
                    mode = Enum.valueOf((Class<Enum>) focusModeClass, "INPUT");
                    System.out.println("Tinker's Search: 右键 → INPUT for " + fluid.getDisplayName().getString());
                } else {
                    return false;
                }

                // 创建 IFocus
                Object focus = jeiCreateFocus.invoke(focusFactory, mode, fluid);

                // 获取 RecipesGui 并显示
                Object recipesGui = jeiGetRecipesGui.invoke(jeiRuntime);
                if (recipesGui != null) {
                    jeiShow.invoke(recipesGui, focus);
                    System.out.println("Tinker's Search: JEI Focus API succeeded (RecipesGui)");
                    return true;
                }

            } catch (Exception e) {
                System.err.println("Tinker's Search: Focus API failed: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // ===== 方法2：使用 RecipeManager.showFocus() 备用 =====
        if (jeiGetRecipeManager != null && jeiShowFocus != null) {
            try {
                Object recipeManager = jeiGetRecipeManager.invoke(jeiRuntime);
                if (recipeManager != null) {
                    // 需要创建 Focus
                    Object jeiHelpers = jeiGetJeiHelpers.invoke(jeiRuntime);
                    Object focusFactory = jeiGetFocusFactory.invoke(jeiHelpers);
                    Class<?> focusModeClass = Class.forName("mezz.jei.api.recipe.IFocus$Mode");

                    Object mode;
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                        mode = Enum.valueOf((Class<Enum>) focusModeClass, "INPUT");
                    } else {
                        mode = Enum.valueOf((Class<Enum>) focusModeClass, "OUTPUT");
                    }

                    Object focus = jeiCreateFocus.invoke(focusFactory, mode, fluid);
                    jeiShowFocus.invoke(recipeManager, focus);
                    System.out.println("Tinker's Search: JEI Focus API 成功 (RecipeManager)");
                    return true;
                }
            } catch (Exception e) {
                System.out.println("Tinker's Search: RecipeManager fallback failed: " + e.getMessage());
            }
        }

        // ===== 方法3：模拟按键（最终回退） =====
        return tryFallback(fluid, button);
    }

    /**
     * 回退方案：模拟按键
     */
    private boolean tryFallback(FluidStack fluid, int button) {
        try {
            Minecraft mc = Minecraft.getInstance();
            long windowHandle = mc.getWindow().getWindow();

            int key;
            String keyName;
            String action;

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                // ===== 左键：R 键 = 显示配方 =====
                key = GLFW.GLFW_KEY_R;
                keyName = "R";
                action = "配方";
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                // ===== 右键：U 键 = 显示用途 =====
                key = GLFW.GLFW_KEY_U;
                keyName = "U";
                action = "用途";
            } else {
                return false;
            }

            mc.keyboardHandler.keyPress(windowHandle, key, 0, 1, 0);
            mc.keyboardHandler.keyPress(windowHandle, key, 0, 0, 0);

            System.out.println("Tinker's Search: 模拟 " + action + " (" + keyName + "键)");
            return true;

        } catch (Exception e) {
            System.err.println("Tinker's Search: 模拟按键失败: " + e.getMessage());
        }
        return false;
    }

    /**
     * 处理键盘按键（用于 JEI 书签）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean handleKeyPressedGlobal(int keyCode, int scanCode, int modifiers) {
        if (!panel.isVisible()) return false;

        // A键 (GLFW_KEY_A = 65) - 添加到 JEI 书签
        if (keyCode == 65) {
            Minecraft mc = Minecraft.getInstance();
            double mouseX = mc.mouseHandler.xpos() / mc.getWindow().getGuiScale();
            double mouseY = mc.mouseHandler.ypos() / mc.getWindow().getGuiScale();

            int px = panel.getPanelX();
            int py = panel.getPanelY();
            int pw = panel.getPanelWidth();

            CardClickInfo info = getCardAndIconAt(mouseX, mouseY, px, py, pw);
            if (info != null && info.isOnIcon) {
                return addToJeiBookmark(info.fluid);
            }
        }

        return false;
    }

    /**
     * 添加到 JEI 书签
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean addToJeiBookmark(FluidStack fluid) {
        if (!jeiAvailable) return false;

        jeiRuntime = top.leipishu.tinkerssearch.jei.Jei.getJeiRuntime();
        if (jeiRuntime == null) return false;

        try {
            Class<?> runtimeClass = jeiRuntime.getClass();

            Method getBookmarkOverlay = runtimeClass.getMethod("getBookmarkOverlay");
            Object bookmarkOverlay = getBookmarkOverlay.invoke(jeiRuntime);

            if (bookmarkOverlay == null) {
                Method getIngredientListOverlay = runtimeClass.getMethod("getIngredientListOverlay");
                Object listOverlay = getIngredientListOverlay.invoke(jeiRuntime);

                if (listOverlay != null) {
                    try {
                        Method addBookmark = listOverlay.getClass().getMethod("addBookmark", Object.class);
                        addBookmark.invoke(listOverlay, fluid);
                        System.out.println("Tinker's Search: 已添加到 JEI 书签 (列表): " + fluid.getDisplayName().getString());
                        return true;
                    } catch (Exception ignored) {}
                }
                return false;
            }

            Class<?> bookmarkClass = bookmarkOverlay.getClass();

            try {
                Method addMethod = bookmarkClass.getMethod("addIngredient", Object.class);
                addMethod.invoke(bookmarkOverlay, fluid);
                System.out.println("Tinker's Search: 已添加到 JEI 书签: " + fluid.getDisplayName().getString());
                return true;
            } catch (NoSuchMethodException e1) {
                try {
                    Method addMethod = bookmarkClass.getMethod("addBookmark", Object.class);
                    addMethod.invoke(bookmarkOverlay, fluid);
                    System.out.println("Tinker's Search: 已添加到 JEI 书签 (备用): " + fluid.getDisplayName().getString());
                    return true;
                } catch (NoSuchMethodException e2) {
                    System.err.println("Tinker's Search: 未找到书签添加方法");
                }
            }
        } catch (Exception e) {
            System.err.println("Tinker's Search: 添加书签失败: " + e.getMessage());
        }

        return false;
    }

    /**
     * 获取点击位置的卡片和图标信息
     */
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

    public void setSearchBoxFocused(boolean focused) {
        this.isSearchBoxFocused = focused;
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