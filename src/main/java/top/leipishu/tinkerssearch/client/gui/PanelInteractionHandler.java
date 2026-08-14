package top.leipishu.tinkerssearch.client.gui;

import net.minecraft.client.Minecraft;
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
    private long lastClickTime = 0;

    private List<FluidStack> allFluids;
    private List<FluidStack> displayedFluids;

    private boolean jeiAvailable;
    private Object jeiRuntime;

    public PanelInteractionHandler(FloatingSearchPanel panel,
                                   Runnable onRefresh,
                                   Consumer<List<FluidStack>> onFluidClick) {
        this.panel = panel;
        this.onRefresh = onRefresh;
        this.onFluidClick = onFluidClick;
        this.jeiAvailable = ModList.get().isLoaded("jei");
        if (jeiAvailable) {
            this.jeiRuntime = top.leipishu.tinkerssearch.jei.Jei.getJeiRuntime();
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

    public void setSearchKeyword(String keyword) {
        this.searchKeyword = keyword;
    }

    public boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!panel.isVisible() || !isSearchBoxFocused) return false;

        switch (keyCode) {
            case 259:
                if (!searchKeyword.isEmpty()) {
                    searchKeyword = searchKeyword.substring(0, searchKeyword.length() - 1);
                    onRefresh.run();
                }
                return true;
            case 256:
            case 257:
            case 335:
                isSearchBoxFocused = false;
                return true;
            default:
                return false;
        }
    }

    public boolean handleCharTyped(char codePoint, int modifiers) {
        if (!panel.isVisible() || !isSearchBoxFocused) return false;
        if (Character.isISOControl(codePoint)) return false;

        searchKeyword += codePoint;
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
     * - 图标区域：交给 JEI 处理（左键用途、右键配方）
     * - 卡片主体：执行移动到最下面
     */
    private boolean handleCardClick(double mouseX, double mouseY, int px, int py, int pw, int button) {
        if (displayedFluids == null || displayedFluids.isEmpty()) return false;

        CardClickInfo info = getCardAndIconAt(mouseX, mouseY, px, py, pw);
        if (info == null) return false;

        FluidStack fluid = info.fluid;
        boolean isOnIcon = info.isOnIcon;

        if (isOnIcon) {
            // ===== 图标区域：交给 JEI 处理 =====
            return handleJeiInteraction(fluid, button);
        } else {
            // ===== 卡片主体：执行移动操作 =====
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return panel.moveFluidToBottom(fluid);
            }
        }

        return false;
    }

    /**
     * 处理 JEI 交互
     * 左键：显示用途 (Show Uses)
     * 右键：显示配方 (Show Recipes)
     */
    private boolean handleJeiInteraction(FluidStack fluid, int button) {
        if (!jeiAvailable || jeiRuntime == null) return false;

        try {
            // 获取 RecipeManager
            Class<?> runtimeClass = jeiRuntime.getClass();
            Method getRecipeManager = runtimeClass.getMethod("getRecipeManager");
            Object recipeManager = getRecipeManager.invoke(jeiRuntime);

            if (recipeManager == null) return false;

            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                // 左键：显示用途 (Show Uses)
                try {
                    Method showUses = recipeManager.getClass().getMethod("showUses", Object.class);
                    showUses.invoke(recipeManager, fluid);
                    System.out.println("Tinker's Search: JEI Show Uses for " + fluid.getDisplayName().getString());
                    return true;
                } catch (NoSuchMethodException e) {
                    // 尝试其他方法名
                    try {
                        Method showUses2 = recipeManager.getClass().getMethod("showUses", FluidStack.class);
                        showUses2.invoke(recipeManager, fluid);
                        System.out.println("Tinker's Search: JEI Show Uses (alt) for " + fluid.getDisplayName().getString());
                        return true;
                    } catch (NoSuchMethodException e2) {
                        System.err.println("Tinker's Search: showUses method not found");
                    }
                }
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                // 右键：显示配方 (Show Recipes)
                try {
                    Method showRecipes = recipeManager.getClass().getMethod("showRecipes", Object.class);
                    showRecipes.invoke(recipeManager, fluid);
                    System.out.println("Tinker's Search: JEI Show Recipes for " + fluid.getDisplayName().getString());
                    return true;
                } catch (NoSuchMethodException e) {
                    try {
                        Method showRecipes2 = recipeManager.getClass().getMethod("showRecipes", FluidStack.class);
                        showRecipes2.invoke(recipeManager, fluid);
                        System.out.println("Tinker's Search: JEI Show Recipes (alt) for " + fluid.getDisplayName().getString());
                        return true;
                    } catch (NoSuchMethodException e2) {
                        System.err.println("Tinker's Search: showRecipes method not found");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Tinker's Search: JEI interaction error: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * 处理键盘按键（用于 JEI 书签）
     */
    public boolean handleKeyPressedGlobal(int keyCode, int scanCode, int modifiers) {
        if (!panel.isVisible()) return false;

        // A键 (GLFW_KEY_A = 65)
        if (keyCode == 65) {
            // 检查鼠标是否在某个卡片上
            Minecraft mc = Minecraft.getInstance();
            double mouseX = mc.mouseHandler.xpos() / mc.getWindow().getGuiScale();
            double mouseY = mc.mouseHandler.ypos() / mc.getWindow().getGuiScale();

            int px = panel.getPanelX();
            int py = panel.getPanelY();
            int pw = panel.getPanelWidth();

            CardClickInfo info = getCardAndIconAt(mouseX, mouseY, px, py, pw);
            if (info != null && info.isOnIcon) {
                // 在图标上按 A 键，添加到 JEI 书签
                return addToJeiBookmark(info.fluid);
            }
        }

        return false;
    }

    /**
     * 添加到 JEI 书签
     */
    private boolean addToJeiBookmark(FluidStack fluid) {
        if (!jeiAvailable || jeiRuntime == null) return false;

        try {
            // 获取 BookmarkOverlay
            Class<?> runtimeClass = jeiRuntime.getClass();
            Method getBookmarkOverlay = runtimeClass.getMethod("getBookmarkOverlay");
            Object bookmarkOverlay = getBookmarkOverlay.invoke(jeiRuntime);

            if (bookmarkOverlay == null) return false;

            // 尝试添加书签
            Class<?> bookmarkClass = bookmarkOverlay.getClass();

            // 方法1: addIngredient
            try {
                Method addMethod = bookmarkClass.getMethod("addIngredient", Object.class);
                addMethod.invoke(bookmarkOverlay, fluid);
                System.out.println("Tinker's Search: Added to JEI bookmarks: " + fluid.getDisplayName().getString());
                return true;
            } catch (NoSuchMethodException e1) {
                // 方法2: addBookmark
                try {
                    Method addMethod = bookmarkClass.getMethod("addBookmark", Object.class);
                    addMethod.invoke(bookmarkOverlay, fluid);
                    System.out.println("Tinker's Search: Added to JEI bookmarks (alt): " + fluid.getDisplayName().getString());
                    return true;
                } catch (NoSuchMethodException e2) {
                    // 方法3: 通过 RecipeManager
                    try {
                        Method getRecipeManager = runtimeClass.getMethod("getRecipeManager");
                        Object recipeManager = getRecipeManager.invoke(jeiRuntime);
                        if (recipeManager != null) {
                            try {
                                Method addBookmark = recipeManager.getClass().getMethod("addBookmark", Object.class);
                                addBookmark.invoke(recipeManager, fluid);
                                System.out.println("Tinker's Search: Added to JEI bookmarks via RecipeManager");
                                return true;
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to add bookmark: " + e.getMessage());
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

                boolean isOnIcon = mouseX >= iconX && mouseX <= iconX + iconSize &&
                        mouseY >= iconY && mouseY <= iconY + iconSize;

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