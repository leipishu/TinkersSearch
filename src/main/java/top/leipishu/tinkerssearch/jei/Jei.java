package top.leipishu.tinkerssearch.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import top.leipishu.tinkerssearch.TinkersSearch;
import top.leipishu.tinkerssearch.client.gui.FloatingSearchPanel;

import java.util.Collections;
import java.util.List;

@JeiPlugin
public class Jei implements IModPlugin {

    private static final ResourceLocation ID = new ResourceLocation("tinkerssearch", "jei_plugin");
    private static IJeiRuntime jeiRuntime;
    private static boolean jeiAvailable = false;

    static {
        jeiAvailable = ModList.get().isLoaded("jei");
        System.out.println("Tinker's Search: JEI available: " + jeiAvailable);
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        if (!jeiAvailable) return;

        registration.addGuiContainerHandler((Class) AbstractContainerScreen.class,
                new IGuiContainerHandler<AbstractContainerScreen<?>>() {
                    @Override
                    public List<Rect2i> getGuiExtraAreas(AbstractContainerScreen<?> screen) {
                        FloatingSearchPanel panel = TinkersSearch.getSearchPanel();
                        if (panel == null) return Collections.emptyList();

                        // 只有完全展开的面板才占用空间
                        if (!panel.isVisible() || panel.isAnimating()) {
                            return Collections.emptyList();
                        }

                        int x = panel.getPanelX();
                        int y = panel.getPanelY();
                        int w = panel.getPanelWidth();
                        int h = panel.getPanelHeight();

                        // 确保面板在屏幕范围内
                        Minecraft mc = Minecraft.getInstance();
                        if (mc == null || mc.getWindow() == null) {
                            return Collections.emptyList();
                        }
                        int screenWidth = mc.getWindow().getGuiScaledWidth();
                        int screenHeight = mc.getWindow().getGuiScaledHeight();

                        if (x >= screenWidth || y >= screenHeight || x + w <= 0 || y + h <= 0) {
                            return Collections.emptyList();
                        }

                        return Collections.singletonList(new Rect2i(x, y, w, h));
                    }

                    @Override
                    public Object getIngredientUnderMouse(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
                        String className = screen.getClass().getName();
                        if (!className.contains("SmelteryScreen") && !className.contains("smeltery")) {
                            return null;
                        }

                        FloatingSearchPanel panel = TinkersSearch.getSearchPanel();
                        if (panel == null) return null;

                        if (!panel.isVisible() && !panel.isAnimating()) return null;
                        if (!panel.isPointInsidePanel(mouseX, mouseY)) return null;

                        net.minecraftforge.fluids.FluidStack fluid = panel.getFluidAt(mouseX, mouseY);
                        if (fluid == null || fluid.isEmpty()) return null;

                        System.out.println("Tinker's Search: JEI detected fluid: " + fluid.getDisplayName().getString());

                        return fluid;
                    }
                });
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!jeiAvailable) return;
        System.out.println("Tinker's Search: Registering recipes");
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (!jeiAvailable) return;
        System.out.println("Tinker's Search: Registering recipe catalysts");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        Jei.jeiRuntime = jeiRuntime;
        System.out.println("Tinker's Search: JEI runtime available");
    }

    public static IJeiRuntime getJeiRuntime() {
        return jeiRuntime;
    }

    public static boolean isJeiAvailable() {
        return jeiAvailable && jeiRuntime != null;
    }

    public static void refreshExclusionAreas() {
        if (jeiRuntime != null) {
            System.out.println("Tinker's Search: Refreshing JEI exclusion areas");
        }
    }
}
