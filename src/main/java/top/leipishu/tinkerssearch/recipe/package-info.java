/**
 * 浇筑配方读取与匹配。
 *
 * <p>与匠魂浇筑台/浇筑盆相关的配方解析逻辑集中在此包，不依赖
 * {@link slimeknights.tconstruct.library.materials.MaterialRegistry}
 * 的完整状态，KubeJS 等外部改动导致材料注册残缺时仍能正确匹配。
 *
 * <p>关键类：
 * <ul>
 *   <li>{@link top.leipishu.tinkerssearch.recipe.CastingRecipeHelper} —
 *       从 {@code RecipeManager} 提取浇筑配方，供 {@code FluidPartDataCache}
 *       构建部件列表</li>
 * </ul>
 */
package top.leipishu.tinkerssearch.recipe;