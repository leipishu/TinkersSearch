package top.leipishu.tinkerssearch.utils;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class SearchHelper {

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

    private static boolean matches(FluidStack fluid, String keyword) {
        if (fluid == null || fluid.isEmpty()) {
            return false;
        }

        String displayName = fluid.getDisplayName().getString();
        String cleanName = displayName.replace("Molten ", "").replace("熔融", "");
        String lowerName = cleanName.toLowerCase();

        if (lowerName.contains(keyword)) {
            return true;
        }

        ResourceLocation rl = ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
        String registryName = rl != null ? rl.getPath().toLowerCase() : "";
        if (registryName.contains(keyword)) {
            return true;
        }

        return false;
    }

    public static boolean hasMatches(List<FluidStack> fluids, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return !fluids.isEmpty();
        }
        return !filterFluids(fluids, keyword).isEmpty();
    }

    public static int getMatchCount(List<FluidStack> fluids, String keyword) {
        return filterFluids(fluids, keyword).size();
    }
}