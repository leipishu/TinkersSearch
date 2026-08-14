package top.leipishu.tinkerssearch.client.gui;

import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import top.leipishu.tinkerssearch.TinkersSearch;

import java.util.Collections;
import java.util.List;

/**
 * JEI 排除区域处理器 - 阻止 JEI 在面板区域渲染
 */
public class PanelExclusionHandler implements IGuiContainerHandler<AbstractContainerScreen<?>> {

    @Override
    public List<Rect2i> getGuiExtraAreas(AbstractContainerScreen<?> screen) {
        FloatingSearchPanel panel = TinkersSearch.getSearchPanel();
        if (panel == null || !panel.isVisible() && !panel.isAnimating()) {
            return Collections.emptyList();
        }

        int x = panel.getPanelX();
        int y = panel.getPanelY();
        int w = panel.getPanelWidth();
        int h = panel.getPanelHeight();

        return Collections.singletonList(new Rect2i(x, y, w, h));
    }
}