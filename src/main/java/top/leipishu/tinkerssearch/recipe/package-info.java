/**
 * 浇筑配方读取与匹配。
 *
 * <p>与匠魂浇筑台/浇筑盆相关的配方解析逻辑集中在此包，不依赖
 * {@link slimeknights.tconstruct.library.materials.MaterialRegistry}
 * 的完整状态，KubeJS 等外部改动导致材料注册残缺时仍能正确匹配。
 *
 * <p>类职责划分：
 * <ul>
 *   <li>{@link top.leipishu.tinkerssearch.recipe.CastingRecipeHelper} —
 *       对外入口，遍历配方并分发处理</li>
 *   <li>{@link top.leipishu.tinkerssearch.recipe.MaterialResolver} —
 *       流体 → 材料 ID 解析</li>
 *   <li>{@link top.leipishu.tinkerssearch.recipe.MaterialCompatibility} —
 *       部件与材料的兼容性判断</li>
 *   <li>{@link top.leipishu.tinkerssearch.recipe.MaterialCastingCost} —
 *       itemCost 读取与 mB 换算</li>
 *   <li>{@link top.leipishu.tinkerssearch.recipe.PartRequirementsCache} —
 *       部件需求量缓存</li>
 *   <li>{@link top.leipishu.tinkerssearch.recipe.RecipeReflection} —
 *       通用反射工具</li>
 * </ul>
 */
package top.leipishu.tinkerssearch.recipe;
