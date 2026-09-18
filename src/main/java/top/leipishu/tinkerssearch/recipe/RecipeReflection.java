package top.leipishu.tinkerssearch.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.recipe.casting.IDisplayableCastingRecipe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 配方读取的通用反射工具。
 *
 * <p><b>1.19.2 修复</b>：{@link #extractFluids(Recipe)} 增加对
 * {@code MaterialFluidRecipe} 新结构的兜底扫描。
 */
public class RecipeReflection {

    // ============================================================
    // ===== 字段查找 =============================================
    // ============================================================

    public static Object findFieldValue(Object obj, String... names) {
        if (obj == null) return null;
        for (String name : names) {
            try {
                Class<?> c = obj.getClass();
                while (c != null && c != Object.class) {
                    try {
                        Field f = c.getDeclaredField(name);
                        f.setAccessible(true);
                        Object v = f.get(obj);
                        if (v != null) return v;
                    } catch (NoSuchFieldException ignored) {}
                    c = c.getSuperclass();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ============================================================
    // ===== ItemStack 输出 ======================================
    // ============================================================

    public static ItemStack tryGetOutput(Recipe<?> recipe) {
        if (recipe == null) return ItemStack.EMPTY;

        for (String name : new String[]{"getOutput", "getResult", "getResultItem"}) {
            try {
                Method m = recipe.getClass().getMethod(name);
                m.setAccessible(true);
                Object v = m.invoke(recipe);
                if (v instanceof ItemStack && !((ItemStack) v).isEmpty()) return (ItemStack) v;
            } catch (Exception ignored) {}
        }

        for (String name : new String[]{"output", "result"}) {
            try {
                Class<?> clazz = recipe.getClass();
                while (clazz != null && clazz != Object.class) {
                    try {
                        Field f = clazz.getDeclaredField(name);
                        f.setAccessible(true);
                        Object v = f.get(recipe);
                        if (v instanceof ItemStack && !((ItemStack) v).isEmpty()) return (ItemStack) v;
                        if (v instanceof Item) return new ItemStack((Item) v);
                    } catch (NoSuchFieldException ignored) {}
                    clazz = clazz.getSuperclass();
                }
            } catch (Exception ignored) {}
        }

        return ItemStack.EMPTY;
    }

    public static ItemStack tryGetPartBuilderResult(Recipe<?> recipe) {
        if (recipe == null) return ItemStack.EMPTY;

        for (String name : new String[]{"getResultItem", "getResult", "getOutput", "getRecipeOutput"}) {
            try {
                Method m = recipe.getClass().getMethod(name);
                Object v = m.invoke(recipe);
                if (v instanceof ItemStack && !((ItemStack) v).isEmpty()) return (ItemStack) v;
            } catch (Exception ignored) {}
        }

        for (String name : new String[]{"result", "output", "recipeOutput"}) {
            try {
                Class<?> clazz = recipe.getClass();
                while (clazz != null && clazz != Object.class) {
                    try {
                        Field f = clazz.getDeclaredField(name);
                        f.setAccessible(true);
                        Object v = f.get(recipe);
                        if (v instanceof ItemStack && !((ItemStack) v).isEmpty()) return (ItemStack) v;
                    } catch (NoSuchFieldException ignored) {}
                    clazz = clazz.getSuperclass();
                }
            } catch (Exception ignored) {}
        }

        return ItemStack.EMPTY;
    }

    public static Integer tryGetPartBuilderCost(Recipe<?> recipe) {
        if (recipe == null) return null;

        try {
            Method m = recipe.getClass().getMethod("getCost");
            Object v = m.invoke(recipe);
            if (v instanceof Integer) return (Integer) v;
        } catch (Exception ignored) {}

        try {
            Class<?> clazz = recipe.getClass();
            while (clazz != null && clazz != Object.class) {
                try {
                    Field f = clazz.getDeclaredField("cost");
                    f.setAccessible(true);
                    Object v = f.get(recipe);
                    if (v instanceof Integer) return (Integer) v;
                } catch (NoSuchFieldException ignored) {}
                clazz = clazz.getSuperclass();
            }
        } catch (Exception ignored) {}

        return null;
    }

    // ============================================================
    // ===== 输入流体 ============================================
    // ============================================================

    public static FluidStack getCastingFluid(Recipe<?> recipe) {
        if (recipe == null) return null;

        try {
            Object fluidIngredient = findFieldValue(recipe, "fluid", "fluidIngredient", "inputFluid");
            FluidStack got = fluidFromIngredient(fluidIngredient);
            if (got != null) return got;
        } catch (Exception ignored) {}

        try {
            Object mf = findFieldValue(recipe,
                    "materialFluid", "materialFluidRecipe", "materialRecipe", "fluidRecipe");
            if (mf != null) {
                FluidStack viaMf = fluidFromIngredient(mf);
                if (viaMf != null) return viaMf;
                Object inner = findFieldValue(mf, "fluid", "inputFluid");
                FluidStack viaInner = fluidFromIngredient(inner);
                if (viaInner != null) return viaInner;
            }
        } catch (Exception ignored) {}

        try {
            Method m = recipe.getClass().getMethod("getFluid");
            Object v = m.invoke(recipe);
            if (v instanceof FluidStack && !((FluidStack) v).isEmpty()) return (FluidStack) v;
        } catch (Exception ignored) {}

        try {
            Method m = recipe.getClass().getMethod("getFluids");
            Object v = m.invoke(recipe);
            if (v instanceof List) {
                List<?> list = (List<?>) v;
                if (!list.isEmpty() && list.get(0) instanceof FluidStack) {
                    FluidStack fs = (FluidStack) list.get(0);
                    if (!fs.isEmpty()) return fs;
                }
            }
        } catch (Exception ignored) {}

        try {
            Object cached = findFieldValue(recipe,
                    "cachedFluidRecipe", "fluidRecipe", "materialFluidRecipe", "materialRecipe");
            if (cached instanceof Optional) {
                Optional<?> opt = (Optional<?>) cached;
                if (opt.isPresent()) {
                    Object inner = opt.get();
                    Object inputsObj = invokeNoArg(inner, "getInputs");
                    if (inputsObj instanceof List) {
                        for (Object input : (List<?>) inputsObj) {
                            FluidStack fs = fluidFromIngredient(input);
                            if (fs != null) return fs;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    public static FluidStack fluidFromIngredient(Object fi) {
        if (fi == null) return null;
        if (fi instanceof FluidStack) {
            FluidStack fs = (FluidStack) fi;
            return fs.isEmpty() ? null : fs;
        }
        try {
            Method m = fi.getClass().getMethod("getFluids");
            m.setAccessible(true);
            Object fs = m.invoke(fi);
            if (fs instanceof List) {
                List<?> list = (List<?>) fs;
                for (Object o : list) {
                    if (o instanceof FluidStack && !((FluidStack) o).isEmpty()) {
                        return (FluidStack) o;
                    }
                }
            }
        } catch (Exception ignored) {}
        try {
            Method m = fi.getClass().getMethod("getFluid");
            m.setAccessible(true);
            Object fs = m.invoke(fi);
            if (fs instanceof FluidStack && !((FluidStack) fs).isEmpty()) {
                return (FluidStack) fs;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 尝试把 recipe 的输入转成 {@code List<FluidStack>}。
     *
     * <p><b>1.19.2 修复</b>：在原有方法/字段查找之后，增加一轮"暴力字段扫描"：
     * 遍历 recipe 及其父类的所有字段，寻找类型名含 {@code FluidIngredient} 的字段，
     * 或含 {@code MaterialFluidRecipe} 的字段（含 {@code Optional} 包装）。
     */
    public static List<FluidStack> extractFluids(Recipe<?> recipe) {
        List<FluidStack> result = new ArrayList<>();
        if (recipe == null) return result;

        // ===== 1. 直接方法 =====
        for (String mn : new String[]{"getFluid", "getFluidIngredient", "getInputFluid"}) {
            try {
                Method m = recipe.getClass().getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(recipe);
                List<FluidStack> got = toFluidList(v);
                if (!got.isEmpty()) return got;
            } catch (Exception ignored) {}
        }

        // ===== 2. 常见字段名 =====
        for (String fn : new String[]{"fluid", "input", "fluidInput", "fluidIngredient"}) {
            try {
                Class<?> clazz = recipe.getClass();
                while (clazz != null && clazz != Object.class) {
                    try {
                        Field f = clazz.getDeclaredField(fn);
                        f.setAccessible(true);
                        Object v = f.get(recipe);
                        List<FluidStack> got = toFluidList(v);
                        if (!got.isEmpty()) return got;
                    } catch (NoSuchFieldException ignored) {}
                    clazz = clazz.getSuperclass();
                }
            } catch (Exception ignored) {}
        }

        // ===== 3. cachedFluidRecipe 里挖 inputs =====
        Object cached = findFieldValue(recipe,
                "cachedFluidRecipe", "fluidRecipe", "materialFluidRecipe", "materialRecipe");
        if (cached instanceof Optional) {
            Optional<?> opt = (Optional<?>) cached;
            if (opt.isPresent()) {
                Object inner = opt.get();
                Object inputsObj = invokeNoArg(inner, "getInputs");
                if (inputsObj instanceof List) {
                    for (Object input : (List<?>) inputsObj) {
                        List<FluidStack> got = toFluidList(input);
                        result.addAll(got);
                    }
                    if (!result.isEmpty()) return result;
                }
            }
        }

        // ===== 4. 1.19.2 暴力扫描：找所有可能的 FluidIngredient / MaterialFluidRecipe 字段 =====
        try {
            Class<?> clazz = recipe.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(recipe);
                        if (v == null) continue;

                        // 包装在 Optional 里
                        if (v instanceof Optional) {
                            Optional<?> opt = (Optional<?>) v;
                            if (!opt.isPresent()) continue;
                            v = opt.get();
                        }

                        String typeName = v.getClass().getName().toLowerCase();

                        // 直接是 FluidIngredient
                        if (typeName.contains("fluidingredient")) {
                            List<FluidStack> got = toFluidList(v);
                            if (!got.isEmpty()) return got;
                        }

                        // MaterialFluidRecipe：从 getInputs() 挖
                        if (typeName.contains("materialfluid")) {
                            Object inputsObj = invokeNoArg(v, "getInputs");
                            if (inputsObj instanceof List) {
                                for (Object input : (List<?>) inputsObj) {
                                    List<FluidStack> got = toFluidList(input);
                                    result.addAll(got);
                                }
                                if (!result.isEmpty()) return result;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception ignored) {}

        return result;
    }

    public static List<FluidStack> toFluidList(Object v) {
        List<FluidStack> result = new ArrayList<>();
        if (v == null) return result;

        try {
            if (v instanceof FluidStack) {
                result.add((FluidStack) v);
                return result;
            }
            try {
                Method m = v.getClass().getMethod("getFluids");
                m.setAccessible(true);
                Object fs = m.invoke(v);
                if (fs instanceof List) {
                    for (Object o : (List<?>) fs) {
                        if (o instanceof FluidStack && !((FluidStack) o).isEmpty()) {
                            result.add((FluidStack) o);
                        }
                    }
                }
            } catch (Exception ignored) {}

            if (result.isEmpty()) {
                try {
                    Method m = v.getClass().getMethod("getFluid");
                    m.setAccessible(true);
                    Object fs = m.invoke(v);
                    if (fs instanceof FluidStack && !((FluidStack) fs).isEmpty()) {
                        result.add((FluidStack) fs);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        return result;
    }

    // ============================================================
    // ===== MaterialId / ResourceLocation =======================
    // ============================================================

    public static ResourceLocation extractMaterialId(Recipe<?> recipe) {
        if (recipe == null) return null;

        for (String mn : new String[]{"getMaterial", "getOutput", "getMaterialId"}) {
            try {
                Method m = recipe.getClass().getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(recipe);
                ResourceLocation id = toResourceLocation(v);
                if (id != null) return id;
            } catch (Exception ignored) {}
        }

        for (String fn : new String[]{"material", "output", "materialId", "outputMaterial"}) {
            try {
                Class<?> clazz = recipe.getClass();
                while (clazz != null && clazz != Object.class) {
                    try {
                        Field f = clazz.getDeclaredField(fn);
                        f.setAccessible(true);
                        Object v = f.get(recipe);
                        ResourceLocation id = toResourceLocation(v);
                        if (id != null) return id;
                    } catch (NoSuchFieldException ignored) {}
                    clazz = clazz.getSuperclass();
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    public static ResourceLocation toResourceLocation(Object v) {
        if (v == null) return null;
        try {
            if (v instanceof ResourceLocation) return (ResourceLocation) v;
            if (v instanceof MaterialId) {
                try { return new ResourceLocation(v.toString()); } catch (Exception ignored) {}
            }
            if (v instanceof IMaterial) {
                Object id = ((IMaterial) v).getIdentifier();
                if (id instanceof ResourceLocation) return (ResourceLocation) id;
                if (id != null) {
                    try { return new ResourceLocation(id.toString()); } catch (Exception ignored) {}
                }
            }
            for (String mn : new String[]{"getIdentifier", "getLocation", "getId"}) {
                try {
                    Method m = v.getClass().getMethod(mn);
                    m.setAccessible(true);
                    Object id = m.invoke(v);
                    if (id instanceof ResourceLocation) return (ResourceLocation) id;
                    if (id != null) {
                        try { return new ResourceLocation(id.toString()); } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }
            try { return new ResourceLocation(v.toString()); } catch (Exception ignored) {}
        } catch (Exception ignored) {}
        return null;
    }

    // ============================================================
    // ===== IDisplayableCastingRecipe 专用 ======================
    // ============================================================

    public static FluidStack getDisplayableCastingFluid(IDisplayableCastingRecipe recipe) {
        if (recipe == null) return null;

        try {
            Method method = recipe.getClass().getMethod("getFluid");
            return (FluidStack) method.invoke(recipe);
        } catch (Exception ignored) {}

        try {
            Method method = recipe.getClass().getMethod("getFluidIngredient");
            Object ingredient = method.invoke(recipe);
            if (ingredient != null) {
                try {
                    Method getFluids = ingredient.getClass().getMethod("getFluids");
                    @SuppressWarnings("unchecked")
                    List<FluidStack> fluids = (List<FluidStack>) getFluids.invoke(ingredient);
                    if (fluids != null && !fluids.isEmpty()) return fluids.get(0);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        return null;
    }

    public static ItemStack getDisplayableCastingOutput(IDisplayableCastingRecipe recipe) {
        if (recipe == null) return ItemStack.EMPTY;

        try {
            Method method = recipe.getClass().getMethod("getResult");
            Object result = method.invoke(recipe);
            if (result instanceof ItemStack) return (ItemStack) result;
        } catch (Exception ignored) {}

        try {
            Method method = recipe.getClass().getMethod("getOutput");
            Object result = method.invoke(recipe);
            if (result instanceof ItemStack) return (ItemStack) result;
        } catch (Exception ignored) {}

        return ItemStack.EMPTY;
    }

    public static boolean getDisplayableCastingHasCast(IDisplayableCastingRecipe recipe) {
        if (recipe == null) return false;

        try {
            Method method = recipe.getClass().getMethod("hasCast");
            return (boolean) method.invoke(recipe);
        } catch (Exception ignored) {}

        try {
            Method method = recipe.getClass().getMethod("getCast");
            Object cast = method.invoke(recipe);
            return cast != null;
        } catch (Exception ignored) {}

        return false;
    }

    // ============================================================
    // ===== FluidStack 比较 =====================================
    // ============================================================

    public static boolean matchesFluid(FluidStack a, FluidStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        ResourceLocation aId = ForgeRegistries.FLUIDS.getKey(a.getFluid());
        ResourceLocation bId = ForgeRegistries.FLUIDS.getKey(b.getFluid());
        if (aId == null || bId == null) return false;
        return aId.equals(bId);
    }

    // ============================================================
    // ===== 内部工具 =============================================
    // ============================================================

    private static Object invokeNoArg(Object obj, String name) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod(name);
            m.setAccessible(true);
            return m.invoke(obj);
        } catch (Exception ignored) {}
        return null;
    }
}