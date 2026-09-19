package top.leipishu.tinkerssearch.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingRecipe;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;
import slimeknights.tconstruct.tools.stats.LimbMaterialStats;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * 部件与材料的兼容性判断（1.20.1）。
 *
 * <p><b>1.19.2 逻辑恢复</b>：{@link #readFluidRecipe} 优先尝试<b>有参</b>
 * {@code getFluidRecipe(ICastingContainer)}（1.19.2 新签名），再回退到
 * <b>无参</b> {@code getFluidRecipe()}（1.18.2 兼容），最后扫描字段。
 */
public class MaterialCompatibility {

    public static boolean canUseMaterial(IMaterialItem mi, MaterialId targetMat) {
        if (mi == null || targetMat == null) return false;

        try {
            if (mi.canUseMaterial(targetMat)) return true;
        } catch (Throwable ignored) {}

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
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item.asItem());
            String path = itemId != null ? itemId.getPath().toLowerCase() : "";
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
        if (fluidRecipeOpt == null) return null;
        if (!fluidRecipeOpt.isPresent()) return null;

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
     *
     * <p>查找顺序（1.19.2 → 1.18.2 → 字段兜底）：
     * <ol>
     *   <li>★ 有参 {@code getFluidRecipe(ICastingContainer)} — 1.19.2 新签名</li>
     *   <li>无参 {@code getFluidRecipe()} — 1.18.2 兼容</li>
     *   <li>字段扫描：{@code cachedFluidRecipe} / {@code cachedFluid} / {@code fluidRecipe} /
     *       {@code materialFluidRecipe} / {@code materialRecipe}</li>
     * </ol>
     */
    private static Optional<?> readFluidRecipe(MaterialCastingRecipe recipe) {
        if (recipe == null) return null;

        // ===== 1. 有参 getFluidRecipe(ICastingContainer) — 1.19.2 新签名 =====
        try {
            for (Method m : recipe.getClass().getMethods()) {
                if (!m.getName().equals("getFluidRecipe")) continue;
                if (m.getParameterCount() != 1) continue;

                Class<?> pType = m.getParameterTypes()[0];
                Object arg = pType.isInstance(recipe) ? recipe : null;

                m.setAccessible(true);
                try {
                    Object v = m.invoke(recipe, arg);
                    if (v instanceof Optional) return (Optional<?>) v;
                    if (v != null) return Optional.of(v);
                } catch (Throwable ignored) {}
            }
        } catch (Exception ignored) {}

        // ===== 2. 无参 getFluidRecipe() — 1.18.2 兼容 =====
        try {
            Method m = recipe.getClass().getMethod("getFluidRecipe");
            m.setAccessible(true);
            Object v = m.invoke(recipe);
            if (v instanceof Optional) return (Optional<?>) v;
            if (v != null) return Optional.of(v);
        } catch (Exception ignored) {}

        // ===== 3. 字段扫描（含父类）=====
        try {
            Class<?> c = recipe.getClass();
            while (c != null && c != Object.class) {
                for (String fn : new String[]{
                        "cachedFluidRecipe", "cachedFluid", "fluidRecipe",
                        "materialFluidRecipe", "materialRecipe"}) {
                    try {
                        Field f = c.getDeclaredField(fn);
                        f.setAccessible(true);
                        Object v = f.get(recipe);
                        if (v instanceof Optional) return (Optional<?>) v;
                        if (v != null) return Optional.of(v);
                    } catch (NoSuchFieldException ignored) {}
                }
                c = c.getSuperclass();
            }
        } catch (Exception ignored) {}

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
        ResourceLocation aId = ForgeRegistries.FLUIDS.getKey(a.getFluid());
        ResourceLocation bId = ForgeRegistries.FLUIDS.getKey(b.getFluid());
        if (aId == null || bId == null) return false;
        return aId.equals(bId);
    }
}