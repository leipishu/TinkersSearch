package top.leipishu.tinkerssearch.client.gui.panel;

import top.leipishu.tinkerssearch.alloy.AlloyQueryHandler;
import top.leipishu.tinkerssearch.client.animation.core.Animation;
import top.leipishu.tinkerssearch.client.animation.core.AnimationManager;
import top.leipishu.tinkerssearch.client.animation.core.Easing;
import top.leipishu.tinkerssearch.client.gui.FloatingSearchPanel;
import top.leipishu.tinkerssearch.client.gui.PanelInteractionHandler;
import top.leipishu.tinkerssearch.client.gui.panel.PanelDataManager.Tab;

/**
 * 面板动画管理器。
 *
 * <p>迁移到统一动画系统后，本类不再自持 offset / target / startTime，
 * 全部状态由 {@link AnimationManager} 的 {@code "panel.slide"} 动画持有。
 */
public class PanelAnimationManager {

    /** 滑入滑出动画的 key。 */
    private static final String SLIDE_KEY = "panel.slide";
    private static final long SLIDE_DURATION = 350L;
    private static final Easing SLIDE_EASING = Easing.EASE_OUT_CUBIC;

    private final FloatingSearchPanel panel;
    private final PanelInteractionHandler interactionHandler;
    private final AlloyQueryHandler alloyHandler;
    private final PanelDataManager dataManager;

    /** 当前目标态：true = 应该显示（offset → 0），false = 应该隐藏（offset → -width）。 */
    private boolean targetVisible = false;

    public PanelAnimationManager(FloatingSearchPanel panel, PanelInteractionHandler interactionHandler,
                                 AlloyQueryHandler alloyHandler, PanelDataManager dataManager) {
        this.panel = panel;
        this.interactionHandler = interactionHandler;
        this.alloyHandler = alloyHandler;
        this.dataManager = dataManager;
    }

    // ============================================================
    // ===== 查询 ==================================================
    // ============================================================

    public boolean isAnimating() {
        return AnimationManager.get().isRunning(SLIDE_KEY);
    }

    public int getAnimationOffset() {
        Animation a = AnimationManager.get().get(SLIDE_KEY);
        if (a != null && !a.isComplete()) {
            return (int) a.getValue();
        }
        return targetVisible ? 0 : -panel.getPanelWidth();
    }

    public boolean isTargetVisible() { return targetVisible; }

    // ============================================================
    // ===== 控制 ==================================================
    // ============================================================

    public void startHideAnimation() {
        resetToSmelteryTab();
        interactionHandler.setSearchBoxFocused(false);
        dataManager.resetScrollOffsets();

        float startOffset = getAnimationOffset();   // ★ 先读
        targetVisible = false;
        panel.setActuallyVisible(false);

        AnimationManager.get().play(
                SLIDE_KEY, startOffset, -panel.getPanelWidth(),
                SLIDE_DURATION, 0L, SLIDE_EASING,
                () -> panel.setVisibleInternal(false));
    }

    public void startShowAnimation() {
        panel.updatePanelPosition();
        panel.forceRefreshTemperature();
        dataManager.refreshMoltenFluids();

        targetVisible = true;
        panel.setVisibleInternal(true);

        AnimationManager.get().play(
                SLIDE_KEY, -panel.getPanelWidth(), 0f,
                SLIDE_DURATION, 0L, SLIDE_EASING,
                () -> panel.setActuallyVisible(true));
    }

    /**
     * 立即隐藏，不播放滑出动画。
     *
     * <p>用于父界面关闭的场景。
     */
    public void hideImmediate() {
        AnimationManager.get().stop(SLIDE_KEY);
        targetVisible = false;
        panel.setVisibleInternal(false);
        panel.setActuallyVisible(false);
    }

    /**
     * @deprecated 动画驱动已迁移到 {@link AnimationManager#update()}。
     */
    @Deprecated
    public void updateAnimation() {
        // no-op
    }

    // ============================================================
    // ===== 内部 ==================================================
    // ============================================================

    private void resetToSmelteryTab() {
        if (dataManager.getCurrentTab() == Tab.ALLOY) {
            alloyHandler.exitQueryMode();
        }
        dataManager.setCurrentTab(Tab.SMELTERY);
        interactionHandler.setSearchKeyword("");
        interactionHandler.setSearchBoxFocused(false);
    }
}