/**
 * JEI 集成相关类
 *
 * <p>提供与 Just Enough Items (JEI) 的集成功能。</p>
 *
 * <h2>主要功能</h2>
 * <ul>
 *   <li>注册 JEI 插件</li>
 *   <li>面板区域排除 - JEI 不会覆盖面板</li>
 *   <li>鼠标悬停检测 - 检测面板上的流体</li>
 * </ul>
 *
 * <p>JEI 为可选前置，不安装时相关功能自动禁用。</p>
 *
 * @author Leipishu
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
package top.leipishu.tinkerssearch.jei;

import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;