package top.leipishu.tinkerssearch.utils.pinyin;

import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 汉字 → 拼音转换，带缓存。
 *
 * <p>用全限定名调用 pinyin4j 的 {@code net.sourceforge.pinyin4j.PinyinHelper}，
 * 避免与本类命名冲突。
 */
public class PinyinSearch {

    private static final Map<String, PinyinResult> CACHE = new ConcurrentHashMap<>();
    private static final HanyuPinyinOutputFormat FORMAT;

    static {
        FORMAT = new HanyuPinyinOutputFormat();
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    public static PinyinResult getPinyin(String text) {
        if (text == null) text = "";
        return CACHE.computeIfAbsent(text, PinyinSearch::generatePinyin);
    }

    private static PinyinResult generatePinyin(String text) {
        List<String> fullPinyins = new ArrayList<>();
        StringBuilder initials = new StringBuilder();
        StringBuilder cleanText = new StringBuilder();

        for (char c : text.toCharArray()) {
            if (isChinese(c)) {
                cleanText.append(c);
                try {
                    String[] pinyins = net.sourceforge.pinyin4j.PinyinHelper
                            .toHanyuPinyinStringArray(c, FORMAT);
                    if (pinyins != null && pinyins.length > 0) {
                        fullPinyins.add(pinyins[0]);
                        for (String p : pinyins) {
                            if (p != null && !p.isEmpty()) {
                                initials.append(p.charAt(0));
                            }
                        }
                    }
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    // 忽略异常
                }
            } else if (Character.isLetterOrDigit(c)) {
                cleanText.append(c);
                String lower = String.valueOf(Character.toLowerCase(c));
                fullPinyins.add(lower);
                initials.append(lower.charAt(0));
            }
        }

        return new PinyinResult(
                String.join("", fullPinyins),
                initials.toString(),
                cleanText.toString()
        );
    }

    private static boolean isChinese(char c) {
        return c >= 0x4E00 && c <= 0x9FA5;
    }

    public static class PinyinResult {
        public final String fullPinyin;
        public final String initials;
        public final String cleanText;

        public PinyinResult(String fullPinyin, String initials, String cleanText) {
            this.fullPinyin = fullPinyin;
            this.initials = initials;
            this.cleanText = cleanText;
        }
    }
}
