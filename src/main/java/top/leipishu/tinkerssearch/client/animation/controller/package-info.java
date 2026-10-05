/**
 * 动画控制器层。
 *
 * <p>按 UI 模块划分，每个控制器封装"该模块需要哪些动画"的 key 命名与
 * 读写语义。渲染层只调用控制器方法，不直接触碰 {@code AnimationManager}
 * 的字符串 key。
 *
 * <h2>划分</h2>
 * <ul>
 *   <li>{@link top.leipishu.tinkerssearch.client.animation.controller.PanelAnimations} —
 *       浮动面板：卡片 hover、Tab hover、Tab 指示器、空状态、刷新按钮</li>
 *   <li>{@link top.leipishu.tinkerssearch.client.animation.controller.WidgetAnimations} —
 *       通用组件：SearchBox focus、ScrollBar thumb 位置与 hover</li>
 * </ul>
 *
 * <h2>key 命名</h2>
 * <pre>
 *   panel.card.hover:&lt;fluidId&gt;
 *   panel.tab.hover:&lt;index&gt;
 *   panel.tab.indicator
 *   panel.empty:&lt;areaKey&gt;
 *   panel.refresh.hover
 *   widget.searchbox.focus:&lt;boxId&gt;
 *   widget.scrollbar.thumb:&lt;barId&gt;
 *   widget.scrollbar.hover:&lt;barId&gt;
 * </pre>
 *
 * <p>所有实例 id（{@code boxId} / {@code barId}）由组件在创建时显式指定，
 * 避免用 {@code identityHashCode} 导致跨会话累积泄漏。
 */
package top.leipishu.tinkerssearch.client.animation.controller;