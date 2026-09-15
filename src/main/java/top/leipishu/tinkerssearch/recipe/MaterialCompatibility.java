package top.leipishu.tinkerssearch.recipe;

import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;
import slimeknights.tconstruct.tools.stats.LimbMaterialStats;

import net.minecraftforge.fluids.FluidStack;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingRecipe;
import java.util.List;
import java.util.Optional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 部件与材料的兼容性判断。
 *
 * <p>两层判定：
 * <ol>
 *   <li>官方 {@link IMaterialItem#canUseMaterial}</li>
 *   <li>材料 stats 是否存在（覆盖 canUseMaterial 实现不完整的场景）</li>
 * </ol>
 *
 * <p>不做"材料存在就通过"的宽松兜底——那会让"已注册但无 stats"的材料
 * （如下界合金、黑曜石）显示出全部部件类型，包括该材料实际不能做的。
 */
public class MaterialCompatibility {

    /**
     * 判断部件能否使用指定材料。
     */
    public static boolean canUseMaterial(IMaterialItem mi, MaterialId targetMat) {
        // 1. 官方判断
        try {
            if (mi.canUseMaterial(targetMat)) return true;
        } catch (Throwable ignored) {}

        // 2. stats 检查
        MaterialStatsId statType = inferStatType(mi);
        if (statType != null) {
            try {
                if (MaterialRegistry.getInstance().getMaterialStats(targetMat, statType).isPresent()) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        return false;
    }

    /**
     * 推断部件的 statType。
     */
    public static MaterialStatsId inferStatType(IMaterialItem item) {
        if (item == null) return null;

        for (String mn : new String[]{"getStatType", "getStatsType", "getStatTypeId"}) {
            try {
                Method m = item.getClass().getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(item);
                if (v instanceof MaterialStatsId) return (MaterialStatsId) v;
            } catch (Exception ignored) {}
        }

        for (String fn : new String[]{"statType", "statsType", "statTypeId", "materialStatId"}) {
            try {
                Class<?> c = item.getClass();
                while (c != null && c != Object.class) {
                    try {
                        Field f = c.getDeclaredField(fn);
                        f.setAccessible(true);
                        Object v = f.get(item);
                        if (v instanceof MaterialStatsId) return (MaterialStatsId) v;
                    } catch (NoSuchFieldException ignored) {}
                    c = c.getSuperclass();
                }
            } catch (Exception ignored) {}
        }

        try {
            String path = item.asItem().getRegistryName() != null
                    ? item.asItem().getRegistryName().getPath().toLowerCase() : "";
            if (path.contains("head") || path.contains("blade") || path.contains("axe")
                    || path.contains("pick") || path.contains("sword") || path.contains("dagger")
                    || path.contains("hammer")) return HeadMaterialStats.ID;
            if (path.contains("handle") || path.contains("binding") || path.contains("grip")) return HandleMaterialStats.ID;
            if (path.contains("limb") || path.contains("bow") || path.contains("arm")) return LimbMaterialStats.ID;
        } catch (Exception ignored) {}

        return null;
    }

    // ============================================================
// ===== 通过 fluidRecipe 判定配方是否接受目标流体 ============
// ============================================================

    /**

     * <p>这是与 JEI 相同的读取路径：只看 recipe 自己绑定的流体列表，
     * 完全不依赖 {@link slimeknights.tconstruct.library.materials.MaterialRegistry}。
     *
     * @return
     *   <ul>
     *     <li>{@code TRUE}  配方明确接受该流体</li>
     *     <li>{@code FALSE} 配方明确不接受该流体</li>
     *     <li>{@code null}  未知（方法不存在 / fluidRecipe 为空 / 结构不识别）</li>
     *   </ul>
     */
    public static Boolean recipeAcceptsFluid(MaterialCastingRecipe recipe, FluidStack targetFluid) {
        if (recipe == null || targetFluid == null || targetFluid.isEmpty()) return Boolean.FALSE;

        Optional<?> fluidRecipeOpt = readFluidRecipe(recipe);
        if (fluidRecipeOpt == null) return null;         // 拿不到 → 未知
        if (!fluidRecipeOpt.isPresent()) return null;    // 缓存未构建 → 未知

        Object fluidRecipe = fluidRecipeOpt.get();
        Object inputsObj = invokeNoArg(fluidRecipe, "getInputs");
        if (!(inputsObj instanceof List)) return null;

        List<?> inputs = (List<?>) inputsObj;
        for (Object input : inputs) {
            if (ingredientAcceptsFluid(input, targetFluid)) return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    /**
     * 读取 {@code MaterialCastingRecipe} 的 fluidRecipe。
     * 优先调 {@code getFluidRecipe()}，失败则读字段 {@code cachedFluidRecipe}。
     */
    private static Optional<?> readFluidRecipe(MaterialCastingRecipe recipe) {
        // 1. 方法
        try {
            Method m = recipe.getClass().getMethod("getFluidRecipe");
            m.setAccessible(true);
            Object v = m.invoke(recipe);
            if (v instanceof Optional) return (Optional<?>) v;
        } catch (Exception ignored) {}

        // 2. 字段（含父类）
        try {
            Class<?> c = recipe.getClass();
            while (c != null && c != Object.class) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField("cachedFluidRecipe");
                    f.setAccessible(true);
                    Object v = f.get(recipe);
                    if (v instanceof Optional) return (Optional<?>) v;
                } catch (NoSuchFieldException ignored) {}
                c = c.getSuperclass();
            }
        } catch (Exception ignored) {}

        // 3. 兜底字段名
        for (String fn : new String[]{"fluidRecipe", "materialFluidRecipe", "materialRecipe"}) {
            try {
                Class<?> c = recipe.getClass();
                while (c != null && c != Object.class) {
                    try {
                        java.lang.reflect.Field f = c.getDeclaredField(fn);
                        f.setAccessible(true);
                        Object v = f.get(recipe);
                        if (v instanceof Optional) return (Optional<?>) v;
                    } catch (NoSuchFieldException ignored) {}
                    c = c.getSuperclass();
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private static Object invokeNoArg(Object obj, String name) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod(name);
            m.setAccessible(true);
            return m.invoke(obj);
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 判断单个 {@code FluidIngredient} 是否接受目标流体。
     */
    private static boolean ingredientAcceptsFluid(Object input, FluidStack targetFluid) {
        if (input == null) return false;

        if (input instanceof FluidStack) {
            return fluidIdsMatch((FluidStack) input, targetFluid);
        }

        Object fluidsObj = invokeNoArg(input, "getFluids");
        if (fluidsObj instanceof List) {
            for (Object o : (List<?>) fluidsObj) {
                if (o instanceof FluidStack && fluidIdsMatch((FluidStack) o, targetFluid)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean fluidIdsMatch(FluidStack a, FluidStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        if (a.getFluid().getRegistryName() == null || b.getFluid().getRegistryName() == null) return false;
        return a.getFluid().getRegistryName().equals(b.getFluid().getRegistryName());
    }
}