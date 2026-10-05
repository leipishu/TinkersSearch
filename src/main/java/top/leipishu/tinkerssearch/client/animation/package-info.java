/**
 * 客户端动画系统。
 *
 * <p>统一管理所有 UI 动画：缓动、时间、插值、生命周期。
 * 与具体渲染逻辑解耦——{@code core} 只负责"怎么动"，
 * 具体 UI 模块（panel / detail / widget）通过 key 引用动画状态。
 *
 * <h2>分层</h2>
 * <ul>
 *   <li>{@code core} — 缓动函数、颜色插值、动画实例、全局管理器</li>
 *   <li>{@code controller}（后续）— 按 UI 模块分组的动画定义与查询封装</li>
 * </ul>
 *
 * <h2>驱动</h2>
 * {@code TinkersSearch} 订阅 {@code TickEvent.RenderTickEvent.Phase.START}，
 * 每帧调用 {@code AnimationManager.get().update()}。
 *
 * <h2>使用方式</h2>
 * <pre>
 *   // 触发一次动画
 *   AnimationManager.get().play("panel.slide",
 *           -panelWidth, 0f, 350L, Easing.EASE_OUT_CUBIC);
 *
 *   // 查询当前值（未在播放时返回 fallback）
 *   float offset = AnimationManager.get().getValue("panel.slide", 0f);
 *
 *   // 持久型（hover、滚动等）
 *   Animator hover = AnimationManager.get().animator("card.hover:" + id, 0f, 60f);
 *   hover.setTarget(isHover ? 1f : 0f);
 *   float t = hover.getValue();
 * </pre>
 */
package top.leipishu.tinkerssearch.client.animation;