/**
 * 合金查询相关类
 *
 * <p>提供合金配方的读取、查询和结果计算功能。</p>
 *
 * <h2>主要类</h2>
 * <ul>
 *   <li>{@link top.leipishu.tinkerssearch.alloy.AlloyQueryHandler} - 合金查询状态管理</li>
 *   <li>{@link top.leipishu.tinkerssearch.alloy.AlloyRecipeData} - 配方数据模型</li>
 *   <li>{@link top.leipishu.tinkerssearch.alloy.AlloyResultCalculator} - 合金链计算</li>
 *   <li>{@link top.leipishu.tinkerssearch.alloy.TinkersAlloyReader} - 配方读取器</li>
 * </ul>
 *
 * <h2>使用方式</h2>
 * <p>在搜索框中输入 <code>/a/ 材料名</code> 进入合金查询模式。</p>
 *
 * @author Leipishu
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
package top.leipishu.tinkerssearch.alloy;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;