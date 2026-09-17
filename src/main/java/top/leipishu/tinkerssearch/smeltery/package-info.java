/**
 * 冶炼炉（Smeltery）交互。
 *
 * <p>封装对冶炼炉方块实体的读取与操作，避免直接在 GUI 层反射字段或
 * 调用匠魂内部 API。
 *
 * <p>关键类：
 * <ul>
 *   <li>{@link top.leipishu.tinkerssearch.smeltery.SmelteryDataHelper} —
 *       读取炉内流体、最下方流体、绘制流体图标</li>
 *   <li>{@link top.leipishu.tinkerssearch.smeltery.SmelteryClickHandler} —
 *       向服务端发送流体点击网络包</li>
 *   <li>{@link top.leipishu.tinkerssearch.smeltery.SmelteryTemperatureReader} —
 *       从 FuelModule 读取当前炉温</li>
 * </ul>
 */
package top.leipishu.tinkerssearch.smeltery;
