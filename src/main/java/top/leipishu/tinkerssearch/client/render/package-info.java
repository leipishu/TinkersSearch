/**
 * 客户端渲染辅助工具。
 *
 * <p>封装与具体 UI 组件无关、但涉及 OpenGL 状态管理的通用渲染逻辑，
 * 供面板、详情窗口等复用。
 *
 * <p>关键类：
 * <ul>
 *   <li>{@link top.leipishu.tinkerssearch.client.render.ScissorHelper} —
 *       安全启用/禁用 Scissor Test，处理 1.20.1 的 GL 状态兼容与越界裁剪，
 *       避免 {@code GL_INVALID_ENUM}</li>
 * </ul>
 */
package top.leipishu.tinkerssearch.client.render;
