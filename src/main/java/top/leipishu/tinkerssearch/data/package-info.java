/**
 * 数据模型与缓存。
 *
 * <p>存放流体 → 部件的数据结构、构建缓存与持久化数据，不涉及任何 UI 或
 * 渲染逻辑。所有数据在此层完成"从游戏运行时状态到可展示模型"的转换。
 *
 * <p>关键类：
 * <ul>
 *   <li>{@link top.leipishu.tinkerssearch.data.FluidPartData} —
 *       一个流体的全部部件数据；结构为
 *       {@code FluidPartData → MaterialEntry → PartInfo}，
 *       以 {@code MaterialId} 为主键分页</li>
 *   <li>{@link top.leipishu.tinkerssearch.data.FluidPartDataCache} —
 *       构建与缓存 {@code FluidPartData}；优先从浇筑配方反查部件，
 *       失败时回退到遍历 {@code IMaterialItem}</li>
 *   <li>{@link top.leipishu.tinkerssearch.data.FavoritesManager} —
 *       收藏流体的 JSON 持久化，存于 {@code config/tinkerssearch_favorites.json}</li>
 * </ul>
 */
package top.leipishu.tinkerssearch.data;
