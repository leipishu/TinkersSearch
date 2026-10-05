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
 * 这里只保留：
 * <ul>
 *   <li>滑入滑出的时长、缓动常量</li>
 *   <li>{@link #targetVisible} — 动画结束后应保持的终态</li>
 *   <li>完成回调（切换 {@code actuallyVisible} 标志、重置 Tab）</li>
 * </ul>
 *
 * <p>对外接口保持不变：{@link #isAnimating()} / {@link #getAnimationOffset()} /
 * {@link #startHideAnimation()} / {@link #startShowAnimation()}，
 * 另新增 {@link #hideImmediate()} 用于父界面关闭时立即隐藏。
 */
public class PanelAnimationManager {

    /** 滑入滑出动画的 key。 */
    private static final String SLIDE_KEY = "panel.slide";

    /** 滑入滑出时长（毫秒）。 */
    private static final long SLIDE_DURATION = 350L;

    /** 滑入滑出缓动。 */
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

    /**
     * 当前水平偏移（负值 = 向左滑出屏幕）。
     *
     * <p>动画进行中返回插值；否则返回终态（隐藏 = -width，显示 = 0）。
     */
    public int getAnimationOffset() {
        Animation a = AnimationManager.get().get(SLIDE_KEY);
        if (a != null && !a.isComplete()) {
            return (int) a.getValue();
        }
        // 静止状态：根据目标态返回终值
        return targetVisible ? 0 : -panel.getPanelWidth();
    }

    // ============================================================
    // ===== 控制 ==================================================
    // ============================================================

    /**
     * 开始隐藏动画。
     *
     * <p>语义与旧版一致：
     * <ol>
     *   <li>立刻切回冶炼炉 Tab、清空搜索框、重置滚动</li>
     *   <li>面板 {@code actuallyVisible = false}（不再接收 JEI / 鼠标交互）</li>
     *   <li>滑出完成后 {@code isVisible = false}</li>
     * </ol>
     *
     * <p>★ 关键：在修改 {@link #targetVisible} 之前先读取当前 offset，
     * 否则 {@link #getAnimationOffset()} 的静止态回退会直接返回隐藏位置，
     * 导致动画 from == to（视觉上"没动画"）。
     */
    public void startHideAnimation() {
        resetToSmelteryTab();
        interactionHandler.setSearchBoxFocused(false);
        dataManager.resetScrollOffsets();

        float startOffset = getAnimationOffset();   // ★ 先读（此时 targetVisible 可能仍为 true）
        targetVisible = false;
        panel.setActuallyVisible(false);

        AnimationManager.get().play(
                SLIDE_KEY,
                startOffset,
                -panel.getPanelWidth(),
                SLIDE_DURATION,
                0L,
                SLIDE_EASING,
                () -> panel.setVisibleInternal(false)
        );
    }

    /**
     * 开始显示动画。
     *
     * <p>语义与旧版一致：
     * <ol>
     *   <li>重算布局、强制刷温度、刷新流体</li>
     *   <li>面板 {@code isVisible = true}（立即，以接收交互）</li>
     *   <li>滑入完成后 {@code actuallyVisible = true}（JEI 才开始占用空间）</li>
     * </ol>
     */
    public void startShowAnimation() {
        panel.updatePanelPosition();
        panel.forceRefreshTemperature();
        dataManager.refreshMoltenFluids();

        targetVisible = true;
        panel.setVisibleInternal(true);

        AnimationManager.get().play(
                SLIDE_KEY,
                -panel.getPanelWidth(),
                0f,
                SLIDE_DURATION,
                0L,
                SLIDE_EASING,
                () -> panel.setActuallyVisible(true)
        );
    }

    /**
     * 立即隐藏，不播放滑出动画。
     *
     * <p>用于父界面（冶炼炉界面）关闭的场景：此时渲染已经停止，
     * 播放滑出动画毫无意义，反而会让 {@code isVisible} 卡在 true。
     */
    public void hideImmediate() {
        AnimationManager.get().stop(SLIDE_KEY);
        targetVisible = false;
        panel.setVisibleInternal(false);
        panel.setActuallyVisible(false);
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