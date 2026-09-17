package top.leipishu.tinkerssearch.utils;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import top.leipishu.tinkerssearch.utils.pinyin.PinyinSearch;
import top.leipishu.tinkerssearch.utils.pinyin.PinyinSearch.PinyinResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索帮助类 - 用于过滤流体列表。
 *
 * <p>匹配路径（任一命中即通过）：
 * <ol>
 *   <li>显示名 contains（忽略 "Molten " / "熔融" 前缀）</li>
 *   <li>注册名 contains</li>
 *   <li>拼音全拼 contains</li>
 *   <li>拼音首字母 contains</li>
 * </ol>
 *
 * <p>传入的 {@code keyword} 已在 {@link #filterFluids} 里统一转小写。
 */
public class SearchHelper {

    /**
     * 根据关键词过滤流体列表。
     */
    public static List<FluidStack> filterFluids(List<FluidStack> fluids, String keyword) {
        List<FluidStack> result = new ArrayList<>();

        if (fluids == null || fluids.isEmpty()) {
            return result;
        }

        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>(fluids);
        }

        String lowerKeyword = keyword.trim().toLowerCase();

        for (FluidStack fluid : fluids) {
            if (matches(fluid, lowerKeyword)) {
                result.add(fluid);
            }
        }

        return result;
    }

    /**
     * 检查单个流体是否匹配关键词。
     *
     * @param keyword 已小写化、已 trim
     */
    private static boolean matches(FluidStack fluid, String keyword) {
        if (fluid == null || fluid.isEmpty()) {
            return false;
        }

        // ===== 1. 显示名（去掉 Molten / 熔融 前缀）=====
        String displayName = fluid.getDisplayName().getString();
        String cleanName = displayName.replace("Molten ", "").replace("熔融", "");
        String lowerName = cleanName.toLowerCase();

        if (lowerName.contains(keyword)) {
            return true;
        }

        // ===== 2. 注册名 =====
        // ✅ 1.20.1：通过 ForgeRegistries 获取注册名
        ResourceLocation rl = ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
        String registryName = rl != null ? rl.getPath() : "";
        if (registryName.toLowerCase().contains(keyword)) {
            return true;
        }

        // ===== 3. 拼音全拼 / 首字母 =====
        try {
            PinyinResult pinyin = PinyinSearch.getPinyin(cleanName);
            if (pinyin.fullPinyin.contains(keyword)) return true;
            if (pinyin.initials.contains(keyword)) return true;
        } catch (Throwable ignored) {
            // 拼音转换失败不影响字面匹配
        }

        return false;
    }

    /**
     * 检查关键词是否匹配任何流体。
     */
    public static boolean hasMatches(List<FluidStack> fluids, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return fluids != null && !fluids.isEmpty();
        }
        return !filterFluids(fluids, keyword).isEmpty();
    }

    /**
     * 获取匹配数量。
     */
    public static int getMatchCount(List<FluidStack> fluids, String keyword) {
        return filterFluids(fluids, keyword).size();
    }
}
