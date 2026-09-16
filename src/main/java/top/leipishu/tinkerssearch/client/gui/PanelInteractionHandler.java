package top.leipishu.tinkerssearch.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;
import top.leipishu.tinkerssearch.client.gui.components.SearchBox;
import top.leipishu.tinkerssearch.client.gui.components.SearchBoxStyle;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

import static top.leipishu.tinkerssearch.config.PanelConfig.*;

public class PanelInteractionHandler {

    private final FloatingSearchPanel panel;
    private final Runnable onRefresh;
    private final Consumer<List<FluidStack>> onFluidClick;

    private final SearchBox searchBox = new SearchBox(SearchBoxStyle.panel());

    private long lastClickTime = 0;

    private List<FluidStack> allFluids;
    private List<FluidStack> displayedFluids;

    private boolean jeiAvailable;
    private Object jeiRuntime;

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

        this.searchBox.setHintText(new TranslatableComponent("gui.tinkerssearch.search_hint"));
        this.searchBox.setOnTextChanged(s -> onRefresh.run());

        System.out.println("Tinker's Search: PanelInteractionHandler JEI available: " + jeiAvailable);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void cacheJeiMethods() {
        if (jeiRuntime == null || methodsCached) return;

        try {
            Class<?> runtimeClass = jeiRuntime.getClass();

            jeiGetJeiHelpers = runtimeClass.getMethod("getJeiHelpers");
            Object jeiHelpers = jeiGetJeiHelpers.invoke(jeiRuntime);

            if (jeiHelpers != null) {
                Class<?> helpersClass = jeiHelpers.getClass();
                jeiGetFocusFactory = helpersClass.getMethod("getFocusFactory");
                Object focusFactory = jeiGetFocusFactory.invoke(jeiHelpers);

                if (focusFactory != null) {
                    Class<?> focusFactoryClass = focusFactory.getClass();
                    Class<?> focusModeClass = Class.forName("mezz.jei.api.recipe.IFocus$Mode");
                    jeiCreateFocus = focusFactoryClass.getMethod("createFocus", focusModeClass, Object.class);
                }
            }

            jeiGetRecipesGui = runtimeClass.getMethod("getRecipesGui");
            jeiGetRecipeManager = runtimeClass.getMethod("getRecipeManager");

            Object recipesGui = jeiGetRecipesGui.invoke(jeiRuntime);
            if (recipesGui != null) {
                Class<?> focusClass = Class.forName("mezz.jei.api.recipe.IFocus");
                jeiShow = recipesGui.getClass().getMethod("show", focusClass);
            }

            Object recipeManager = jeiGetRecipeManager.invoke(jeiRuntime);
            if (recipeManager != null) {
                Class<?> focusClass = Class.forName("mezz.jei.api.recipe.IFocus");
                try {
                    jeiShowFocus = recipeManager.getClass().getMethod("showFocus", focusClass);
                } catch (NoSuchMethodException e) {}
            }

            Method getIngredientListOverlay = runtimeClass.getMethod("getIngredientListOverlay");
            Object listOverlay = getIngredientListOverlay.invoke(jeiRuntime);
            if (listOverlay != null) {
                try {
                    jeiGetIngredientUnderMouse = listOverlay.getClass().getMethod("getIngredientUnderMouse");
                } catch (NoSuchMethodException e) {}
            }

            methodsCached = true;
            System.out.println("Tinker's Search: JEI methods cached successfully");

        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to cache JEI methods: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ============================================================
    // ===== 数据引用 =============================================
    // ============================================================

    public void setDataRefs(List<FluidStack> allFluids, List<FluidStack> displayedFluids) {
        this.allFluids = allFluids;
        this.displayedFluids = displayedFluids;
    }

    // ============================================================
    // ===== 搜索框状态 ==========================================
    // ============================================================

    public boolean isSearchBoxFocused() { return searchBox.isFocused(); }
    public String getSearchKeyword() { return searchBox.getText(); }
    public int getCursorPosition() { return searchBox.getCursorPosition(); }
    public void setSearchKeyword(String keyword) { searchBox.setText(keyword); }
    public void setCursorPosition(int position) { searchBox.setCursorPosition(position); }
    public void setSearchBoxFocused(boolean focused) { searchBox.setFocused(focused); }
    public SearchBox getSearchBox() { return searchBox; }

    /** 根据当前 Tab 应用不同的搜索框配色。 */
    public void applyStyleForTab(PanelDataManager_TabStyle style) {
        switch (style) {
            case ALLOY: searchBox.setStyle(SearchBoxStyle.alloy()); break;
            case PANEL:
            default: searchBox.setStyle(SearchBoxStyle.panel()); break;
        }
    }

    /** 供外部调用，用枚举避免耦合到 PanelDataManager 内部 Tab。 */
    public enum PanelDataManager_TabStyle { PANEL, ALLOY }

    // ============================================================
    // ===== 键盘 / 字符输入 =====================================
    // ============================================================

    public boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!panel.isVisible()) return false;
        if (!searchBox.isFocused()) return false;
        return searchBox.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean handleCharTyped(char codePoint, int modifiers) {
        if (!panel.isVisible()) return false;
        if (!searchBox.isFocused()) return false;
        return searchBox.charTyped(codePoint, modifiers);
    }

    // ============================================================
    // ===== 鼠标事件 =============================================
    // ============================================================

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
            handleSearchBoxClick(mouseX, mouseY);
            return true;
        }

        if (handleCardClick(mouseX, mouseY, px, py, pw, button)) {
            lastClickTime = currentTime;
            return true;
        }

        if (isInPanel(mouseX, mouseY, px, py, pw)) {
            if (searchBox.isFocused()) {
                searchBox.setFocused(false);
                return true;
            }
        }

        return false;
    }

    public boolean handleSearchBoxClick(double mouseX, double mouseY) {
        if (!panel.isVisible()) return false;

        int px = panel.getPanelX();
        int py = panel.getPanelY();
        int pw = panel.getPanelWidth();

        searchBox.setBounds(px + 5, py + SEARCH_BOX_Y, pw - 10, SEARCH_BOX_H);
        return searchBox.mouseClicked(mouseX, mouseY, 0);
    }

    // ============================================================
    // ===== 命中检测 =============================================
    // ============================================================

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

    // ============================================================
    // ===== 卡片点击 =============================================
    // ============================================================

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
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                return false;
            }
        }

        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean handleJeiIconClick(FluidStack fluid, int button) {
        if (!jeiAvailable) return false;

        jeiRuntime = top.leipishu.tinkerssearch.jei.Jei.getJeiRuntime();
        if (jeiRuntime == null) return false;

        if (!methodsCached) cacheJeiMethods();

        if (jeiGetJeiHelpers != null && jeiGetFocusFactory != null && jeiCreateFocus != null && jeiShow != null) {
            try {
                Object jeiHelpers = jeiGetJeiHelpers.invoke(jeiRuntime);
                if (jeiHelpers == null) return tryFallback(fluid, button);

                Object focusFactory = jeiGetFocusFactory.invoke(jeiHelpers);
                if (focusFactory == null) return tryFallback(fluid, button);

                Class<?> focusModeClass = Class.forName("mezz.jei.api.recipe.IFocus$Mode");

                Object mode;
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    mode = Enum.valueOf((Class<Enum>) focusModeClass, "OUTPUT");
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    mode = Enum.valueOf((Class<Enum>) focusModeClass, "INPUT");
                } else {
                    return false;
                }

                Object focus = jeiCreateFocus.invoke(focusFactory, mode, fluid);

                Object recipesGui = jeiGetRecipesGui.invoke(jeiRuntime);
                if (recipesGui != null) {
                    jeiShow.invoke(recipesGui, focus);
                    return true;
                }

            } catch (Exception e) {
                System.err.println("Tinker's Search: Focus API failed: " + e.getMessage());
            }
        }

        if (jeiGetRecipeManager != null && jeiShowFocus != null) {
            try {
                Object recipeManager = jeiGetRecipeManager.invoke(jeiRuntime);
                if (recipeManager != null) {
                    Object jeiHelpers = jeiGetJeiHelpers.invoke(jeiRuntime);
                    Object focusFactory = jeiGetFocusFactory.invoke(jeiHelpers);
                    Class<?> focusModeClass = Class.forName("mezz.jei.api.recipe.IFocus$Mode");

                    Object mode;
                    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) mode = Enum.valueOf((Class<Enum>) focusModeClass, "INPUT");
                    else mode = Enum.valueOf((Class<Enum>) focusModeClass, "OUTPUT");

                    Object focus = jeiCreateFocus.invoke(focusFactory, mode, fluid);
                    jeiShowFocus.invoke(recipeManager, focus);
                    return true;
                }
            } catch (Exception e) {}
        }

        return tryFallback(fluid, button);
    }

    private boolean tryFallback(FluidStack fluid, int button) {
        try {
            Minecraft mc = Minecraft.getInstance();
            long windowHandle = mc.getWindow().getWindow();

            int key;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) key = GLFW.GLFW_KEY_R;
            else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) key = GLFW.GLFW_KEY_U;
            else return false;

            mc.keyboardHandler.keyPress(windowHandle, key, 0, 1, 0);
            mc.keyboardHandler.keyPress(windowHandle, key, 0, 0, 0);
            return true;
        } catch (Exception e) {}
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean handleKeyPressedGlobal(int keyCode, int scanCode, int modifiers) {
        if (!panel.isVisible()) return false;

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
                        return true;
                    } catch (Exception ignored) {}
                }
                return false;
            }

            Class<?> bookmarkClass = bookmarkOverlay.getClass();

            try {
                Method addMethod = bookmarkClass.getMethod("addIngredient", Object.class);
                addMethod.invoke(bookmarkOverlay, fluid);
                return true;
            } catch (NoSuchMethodException e1) {
                try {
                    Method addMethod = bookmarkClass.getMethod("addBookmark", Object.class);
                    addMethod.invoke(bookmarkOverlay, fluid);
                    return true;
                } catch (NoSuchMethodException e2) {}
            }
        } catch (Exception e) {}

        return false;
    }

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
        searchBox.clear();
        searchBox.setFocused(false);
        onRefresh.run();
    }

    public void refreshData() { onRefresh.run(); }

    private static class CardClickInfo {
        final FluidStack fluid;
        final boolean isOnIcon;

        CardClickInfo(FluidStack fluid, boolean isOnIcon) {
            this.fluid = fluid;
            this.isOnIcon = isOnIcon;
        }
    }
}