package top.leipishu.tinkerssearch.client.gui.panel;

import top.leipishu.tinkerssearch.alloy.AlloyQueryHandler;
import top.leipishu.tinkerssearch.client.gui.FloatingSearchPanel;
import top.leipishu.tinkerssearch.client.gui.PanelInteractionHandler;
import top.leipishu.tinkerssearch.client.gui.panel.PanelDataManager.Tab;

/**
 * 面板动画管理器 - 管理面板展开/收起动画
 */
public class PanelAnimationManager {

    private final FloatingSearchPanel panel;
    private final PanelInteractionHandler interactionHandler;
    private final AlloyQueryHandler alloyHandler;
    private final PanelDataManager dataManager;

    private boolean isAnimating = false;
    private int animationOffset = 0;
    private int targetOffset = 0;
    private long animationStartTime = 0;
    private static final int ANIMATION_DURATION = 350;

    public PanelAnimationManager(FloatingSearchPanel panel, PanelInteractionHandler interactionHandler,
                                 AlloyQueryHandler alloyHandler, PanelDataManager dataManager) {
        this.panel = panel;
        this.interactionHandler = interactionHandler;
        this.alloyHandler = alloyHandler;
        this.dataManager = dataManager;
    }

    public boolean isAnimating() { return isAnimating; }
    public int getAnimationOffset() { return animationOffset; }
    public int getTargetOffset() { return targetOffset; }

    public void setAnimationOffset(int offset) { this.animationOffset = offset; }
    public void setTargetOffset(int offset) { this.targetOffset = offset; }
    public void setAnimating(boolean animating) { this.isAnimating = animating; }
    public void setAnimationStartTime(long time) { this.animationStartTime = time; }

    public void updateAnimation() {
        if (!isAnimating) return;

        long currentTime = System.currentTimeMillis();
        float progress = (float) (currentTime - animationStartTime) / ANIMATION_DURATION;

        if (progress >= 1.0f) {
            animationOffset = targetOffset;
            isAnimating = false;
            if (targetOffset < 0) {
                panel.setVisibleInternal(false);
                panel.setActuallyVisible(false);
                resetToSmelteryTab();
            } else {
                panel.setActuallyVisible(true);
            }
            return;
        }

        float eased = 1.0f - (float) Math.pow(1.0f - progress, 3);
        int startOffset = targetOffset == 0 ? -panel.getPanelWidth() : 0;
        animationOffset = startOffset + (int) ((targetOffset - startOffset) * eased);
    }

    public void startHideAnimation() {
        resetToSmelteryTab();
        interactionHandler.setSearchBoxFocused(false);
        dataManager.resetScrollOffsets();
        targetOffset = -panel.getPanelWidth();
        isAnimating = true;
        animationStartTime = System.currentTimeMillis();
        panel.setActuallyVisible(false);
    }

    public void startShowAnimation() {
        panel.updatePanelPosition();
        panel.forceRefreshTemperature();
        dataManager.refreshMoltenFluids();
        panel.setVisibleInternal(true);
        animationOffset = -panel.getPanelWidth();
        targetOffset = 0;
        isAnimating = true;
        animationStartTime = System.currentTimeMillis();
    }

    /** 重置到冶炼炉 Tab 并清空搜索框。 */
    private void resetToSmelteryTab() {
        if (dataManager.getCurrentTab() == Tab.ALLOY) {
            alloyHandler.exitQueryMode();
        }
        dataManager.setCurrentTab(Tab.SMELTERY);
        interactionHandler.setSearchKeyword("");
        interactionHandler.setSearchBoxFocused(false);
    }
}
