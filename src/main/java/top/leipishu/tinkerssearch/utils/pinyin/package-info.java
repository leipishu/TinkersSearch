/**
 * 拼音搜索工具包。
 *
 * <p>封装汉字 → 拼音的转换与匹配逻辑，供搜索过滤使用。
 *
 * <p>关键类：
 * <ul>
 *   <li>{@link top.leipishu.tinkerssearch.utils.pinyin.PinyinSearch} —
 *       汉字 → 拼音转换，带缓存；用全限定名调用 pinyin4j 的
 *       {@code net.sourceforge.pinyin4j.PinyinHelper}，避免与本类命名冲突</li>
 * </ul>
 */
package top.leipishu.tinkerssearch.utils.pinyin;
