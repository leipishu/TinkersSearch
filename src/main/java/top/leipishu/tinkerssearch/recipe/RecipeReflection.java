package top.leipishu.tinkerssearch.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fluids.FluidStack;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.recipe.casting.IDisplayableCastingRecipe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 配方读取的通用反射工具。
 *
 * <p>集中处理匠魂及各附属模组在字段/方法命名上的差异。所有方法都容忍
 * 反射失败，返回 null / {@code ItemStack.EMPTY} / 空列表而不抛异常。
 */
public class RecipeReflection {

    // ============================================================
    // ===== 字段查找 =============================================
    // ============================================================

    /** 沿类层次查找第一个非 null 的具名字段值；找不到返回 null。 */
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

    /** 尝试从 recipe 读取 ItemStack 输出；失败返回 {@code ItemStack.EMPTY}。 */
    public static ItemStack tryGetOutput(Recipe<?> recipe) {
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

    /** part_builder 配方的 result；失败返回 {@code ItemStack.EMPTY}。 */
    public static ItemStack tryGetPartBuilderResult(Recipe<?> recipe) {
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

    // ============================================================
    // ===== part_builder cost ===================================
    // ============================================================

    /** part_builder 配方的 cost 字段；找不到返回 null。 */
    public static Integer tryGetPartBuilderCost(Recipe<?> recipe) {
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

    /** 尝试从 recipe 读取第一个输入流体；失败返回 null。 */
    public static FluidStack getCastingFluid(Recipe<?> recipe) {
        // 1. 直接找 fluid 字段
        try {
            Object fluidIngredient = findFieldValue(recipe, "fluid", "fluidIngredient", "inputFluid");
            FluidStack got = fluidFromIngredient(fluidIngredient);
            if (got != null) return got;
        } catch (Exception ignored) {}

        // 2. 从 materialFluidRecipe 里读
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

        // 3. getFluid() / getFluids()
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

        return null;
    }

    /** 从 FluidIngredient / FluidStack / 包装对象中提取第一个 FluidStack。 */
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

    /** 尝试把 recipe 的输入转成 {@code List<FluidStack>}；失败返回空列表。 */
    public static List<FluidStack> extractFluids(Recipe<?> recipe) {
        List<FluidStack> result = new ArrayList<>();

        for (String mn : new String[]{"getFluid", "getFluidIngredient", "getInputFluid"}) {
            try {
                Method m = recipe.getClass().getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(recipe);
                List<FluidStack> got = toFluidList(v);
                if (!got.isEmpty()) return got;
            } catch (Exception ignored) {}
        }

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

        return result;
    }

    /** 把任意对象转成 {@code List<FluidStack>}；失败返回空列表。 */
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

    /** 尝试从 recipe 读取材料 ID 对应的 {@link ResourceLocation}；失败返回 null。 */
    public static ResourceLocation extractMaterialId(Recipe<?> recipe) {
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

    /** 把任意对象转成 {@link ResourceLocation}；失败返回 null。 */
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
        if (a.getFluid().getRegistryName() == null || b.getFluid().getRegistryName() == null) return false;
        return a.getFluid().getRegistryName().equals(b.getFluid().getRegistryName());
    }
}