/**
 * 动画系统核心。
 *
 * <p>本包不感知任何具体 UI，只提供动画所需的基本构件：
 * <ul>
 *   <li>{@link top.leipishu.tinkerssearch.client.animation.core.Easing} —
 *       缓动函数枚举</li>
 *   <li>{@link top.leipishu.tinkerssearch.client.animation.core.ColorUtil} —
 *       ARGB 插值与缩放</li>
 *   <li>{@link top.leipishu.tinkerssearch.client.animation.core.Animation} —
 *       单次触发型动画</li>
 *   <li>{@link top.leipishu.tinkerssearch.client.animation.core.Animator} —
 *       持久型（趋近目标）动画器</li>
 *   <li>{@link top.leipishu.tinkerssearch.client.animation.core.AnimationManager} —
 *       全局单例，注册 / 驱动 / 清理</li>
 * </ul>
 *
 * <p>线程约束：所有操作应在渲染主线程进行。
 */
package top.leipishu.tinkerssearch.client.animation.core;