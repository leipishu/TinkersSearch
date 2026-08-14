package top.leipishu.tinkerssearch.utils;

import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索帮助类 - 用于过滤流体列表
 */
public class SearchHelper {

    /**
     * 根据关键词过滤流体列表
     * @param fluids 原始流体列表
     * @param keyword 搜索关键词
     * @return 过滤后的流体列表
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
     * 检查单个流体是否匹配关键词
     */
    private static boolean matches(FluidStack fluid, String keyword) {
        if (fluid == null || fluid.isEmpty()) {
            return false;
        }

        // 获取显示名称并移除前缀
        String displayName = fluid.getDisplayName().getString();
        String cleanName = displayName.replace("Molten ", "").replace("熔融", "");
        String lowerName = cleanName.toLowerCase();

        // 检查是否包含关键词
        if (lowerName.contains(keyword)) {
            return true;
        }

        // 检查流体注册名是否包含关键词
        String registryName = fluid.getFluid().getRegistryName() != null
                ? fluid.getFluid().getRegistryName().getPath()
                : "";
        if (registryName.toLowerCase().contains(keyword)) {
            return true;
        }

        return false;
    }

    /**
     * 检查关键词是否匹配任何流体
     */
    public static boolean hasMatches(List<FluidStack> fluids, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return !fluids.isEmpty();
        }
        return !filterFluids(fluids, keyword).isEmpty();
    }

    /**
     * 获取匹配数量
     */
    public static int getMatchCount(List<FluidStack> fluids, String keyword) {
        return filterFluids(fluids, keyword).size();
    }
}