package top.leipishu.tinkerssearch.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fluids.FluidStack;
import slimeknights.tconstruct.library.recipe.casting.IDisplayableCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingRecipe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 铸造配方辅助类
 * 用于获取流体对应的铸造配方
 * 兼容 1.18.2 匠魂 API
 */
public class CastingRecipeHelper {

    public static class CastingInfo {
        public final ItemStack outputItem;
        public final boolean requiresCast;

        public CastingInfo(ItemStack output, boolean requiresCast) {
            this.outputItem = output;
            this.requiresCast = requiresCast;
        }
    }

    /**
     * 获取指定流体的所有铸造配方
     */
    public static List<CastingInfo> getCastingRecipesForFluid(FluidStack fluidStack) {
        List<CastingInfo> result = new ArrayList<>();

        if (fluidStack == null || fluidStack.isEmpty()) {
            return result;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return result;
        }

        RecipeManager recipeManager = mc.getConnection().getRecipeManager();

        try {
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                // ===== 1. ItemCastingRecipe 处理 =====
                if (recipe instanceof ItemCastingRecipe) {
                    ItemCastingRecipe castingRecipe = (ItemCastingRecipe) recipe;
                    processItemCastingRecipe(castingRecipe, fluidStack, result);
                    continue;
                }

                // ===== 2. MaterialCastingRecipe 处理（完全通过反射） =====
                if (recipe instanceof MaterialCastingRecipe) {
                    MaterialCastingRecipe castingRecipe = (MaterialCastingRecipe) recipe;
                    processMaterialCastingRecipe(castingRecipe, fluidStack, result);
                    continue;
                }

                // ===== 3. 其他 IDisplayableCastingRecipe 实现 =====
                if (recipe instanceof IDisplayableCastingRecipe) {
                    IDisplayableCastingRecipe castingRecipe = (IDisplayableCastingRecipe) recipe;
                    processDisplayableCastingRecipe(castingRecipe, fluidStack, result);
                }
            }
        } catch (Exception e) {
            System.err.println("Tinker's Search: Error loading casting recipes: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * 处理 ItemCastingRecipe
     */
    private static void processItemCastingRecipe(ItemCastingRecipe recipe, FluidStack targetFluid, List<CastingInfo> result) {
        try {
            // ItemCastingRecipe 有 getFluids() 方法
            List<FluidStack> recipeFluids = recipe.getFluids();
            if (recipeFluids == null || recipeFluids.isEmpty()) {
                return;
            }

            FluidStack recipeFluid = recipeFluids.get(0);
            if (!matchesFluid(recipeFluid, targetFluid)) {
                return;
            }

            // ItemCastingRecipe 有 getOutput() 方法
            ItemStack output = recipe.getOutput();
            if (output == null || output.isEmpty()) {
                return;
            }

            // ItemCastingRecipe 有 hasCast() 方法
            boolean requiresCast = recipe.hasCast();
            result.add(new CastingInfo(output.copy(), requiresCast));

        } catch (Exception e) {
            // 忽略单个配方的解析错误
        }
    }

    /**
     * 处理 MaterialCastingRecipe（完全通过反射）
     *
     * MaterialCastingRecipe 继承自 ItemCastingRecipe，
     * 但 1.18.2 中 ItemCastingRecipe 的 getFluids() 是 final 的，
     * 而 MaterialCastingRecipe 没有重写，但可以通过反射访问父类字段
     */
    private static void processMaterialCastingRecipe(MaterialCastingRecipe recipe, FluidStack targetFluid, List<CastingInfo> result) {
        try {
            FluidStack recipeFluid = getMaterialCastingFluid(recipe);
            if (recipeFluid == null || recipeFluid.isEmpty()) {
                return;
            }

            if (!matchesFluid(recipeFluid, targetFluid)) {
                return;
            }

            ItemStack output = getMaterialCastingOutput(recipe);
            if (output == null || output.isEmpty()) {
                return;
            }

            boolean requiresCast = getMaterialCastingHasCast(recipe);
            result.add(new CastingInfo(output.copy(), requiresCast));

        } catch (Exception e) {
            // 忽略单个配方的解析错误
        }
    }

    /**
     * 处理其他 IDisplayableCastingRecipe
     */
    private static void processDisplayableCastingRecipe(IDisplayableCastingRecipe recipe, FluidStack targetFluid, List<CastingInfo> result) {
        try {
            FluidStack recipeFluid = getDisplayableCastingFluid(recipe);
            if (recipeFluid == null || recipeFluid.isEmpty()) {
                return;
            }

            if (!matchesFluid(recipeFluid, targetFluid)) {
                return;
            }

            ItemStack output = getDisplayableCastingOutput(recipe);
            if (output == null || output.isEmpty()) {
                return;
            }

            boolean requiresCast = getDisplayableCastingHasCast(recipe);
            result.add(new CastingInfo(output.copy(), requiresCast));

        } catch (Exception e) {
            // 忽略
        }
    }

    // ==================== MaterialCastingRecipe 反射工具方法 ====================

    /**
     * 从 MaterialCastingRecipe 获取流体
     *
     * MaterialCastingRecipe 继承自 ItemCastingRecipe，
     * 有一个 protected 字段 fluid（FluidIngredient 类型）
     */
    private static FluidStack getMaterialCastingFluid(MaterialCastingRecipe recipe) {
        // ===== 方式1：访问父类 ItemCastingRecipe 的 fluid 字段 =====
        try {
            Field field = ItemCastingRecipe.class.getDeclaredField("fluid");
            field.setAccessible(true);
            Object fluidIngredient = field.get(recipe);

            if (fluidIngredient != null) {
                // 尝试调用 getFluids() 方法
                try {
                    Method getFluids = fluidIngredient.getClass().getMethod("getFluids");
                    @SuppressWarnings("unchecked")
                    List<FluidStack> fluids = (List<FluidStack>) getFluids.invoke(fluidIngredient);
                    if (fluids != null && !fluids.isEmpty()) {
                        return fluids.get(0);
                    }
                } catch (Exception ignored) {}

                // 尝试调用 getFluid() 方法
                try {
                    Method getFluid = fluidIngredient.getClass().getMethod("getFluid");
                    return (FluidStack) getFluid.invoke(fluidIngredient);
                } catch (Exception ignored) {}

                // 尝试访问 FluidIngredient 的 fluid 字段
                try {
                    Field fluidField = fluidIngredient.getClass().getDeclaredField("fluid");
                    fluidField.setAccessible(true);
                    return (FluidStack) fluidField.get(fluidIngredient);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // ===== 方式2：尝试 getFluidIngredient() 方法 =====
        try {
            // MaterialCastingRecipe 可能有 getFluidIngredient 方法
            Method getFluidIngredient = MaterialCastingRecipe.class.getMethod("getFluidIngredient");
            Object fluidIngredient = getFluidIngredient.invoke(recipe);
            if (fluidIngredient != null) {
                try {
                    Method getFluids = fluidIngredient.getClass().getMethod("getFluids");
                    @SuppressWarnings("unchecked")
                    List<FluidStack> fluids = (List<FluidStack>) getFluids.invoke(fluidIngredient);
                    if (fluids != null && !fluids.isEmpty()) {
                        return fluids.get(0);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // ===== 方式3：尝试 getFluid() 方法 =====
        try {
            Method getFluid = MaterialCastingRecipe.class.getMethod("getFluid");
            return (FluidStack) getFluid.invoke(recipe);
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * 从 MaterialCastingRecipe 获取输出
     *
     * MaterialCastingRecipe 有一个 protected 字段 output（ItemStack 类型）
     */
    private static ItemStack getMaterialCastingOutput(MaterialCastingRecipe recipe) {
        // ===== 方式1：访问 output 字段 =====
        try {
            // MaterialCastingRecipe 有 output 字段
            Field field = MaterialCastingRecipe.class.getDeclaredField("output");
            field.setAccessible(true);
            Object result = field.get(recipe);
            if (result instanceof ItemStack) {
                return (ItemStack) result;
            }
        } catch (Exception ignored) {}

        // ===== 方式2：尝试 getResult() 方法 =====
        try {
            Method method = MaterialCastingRecipe.class.getMethod("getResult");
            Object result = method.invoke(recipe);
            if (result instanceof ItemStack) {
                return (ItemStack) result;
            }
        } catch (Exception ignored) {}

        // ===== 方式3：尝试 getOutput() 方法 =====
        try {
            Method method = MaterialCastingRecipe.class.getMethod("getOutput");
            Object result = method.invoke(recipe);
            if (result instanceof ItemStack) {
                return (ItemStack) result;
            }
        } catch (Exception ignored) {}

        return ItemStack.EMPTY;
    }

    /**
     * 从 MaterialCastingRecipe 获取 hasCast
     *
     * MaterialCastingRecipe 有一个 protected 字段 cast（IIngredient 类型）
     */
    private static boolean getMaterialCastingHasCast(MaterialCastingRecipe recipe) {
        // ===== 方式1：访问 cast 字段 =====
        try {
            // ItemCastingRecipe 有 cast 字段
            Field field = ItemCastingRecipe.class.getDeclaredField("cast");
            field.setAccessible(true);
            Object cast = field.get(recipe);
            return cast != null;
        } catch (Exception ignored) {}

        // ===== 方式2：尝试 hasCast() 方法 =====
        try {
            Method method = MaterialCastingRecipe.class.getMethod("hasCast");
            return (boolean) method.invoke(recipe);
        } catch (Exception ignored) {}

        // ===== 方式3：尝试 getCast() 方法 =====
        try {
            Method method = MaterialCastingRecipe.class.getMethod("getCast");
            Object cast = method.invoke(recipe);
            return cast != null;
        } catch (Exception ignored) {}

        return false;
    }

    // ==================== IDisplayableCastingRecipe 反射工具方法 ====================

    /**
     * 从 IDisplayableCastingRecipe 获取流体
     */
    private static FluidStack getDisplayableCastingFluid(IDisplayableCastingRecipe recipe) {
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
                    if (fluids != null && !fluids.isEmpty()) {
                        return fluids.get(0);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * 从 IDisplayableCastingRecipe 获取输出
     */
    private static ItemStack getDisplayableCastingOutput(IDisplayableCastingRecipe recipe) {
        try {
            Method method = recipe.getClass().getMethod("getResult");
            Object result = method.invoke(recipe);
            if (result instanceof ItemStack) {
                return (ItemStack) result;
            }
        } catch (Exception ignored) {}

        try {
            Method method = recipe.getClass().getMethod("getOutput");
            Object result = method.invoke(recipe);
            if (result instanceof ItemStack) {
                return (ItemStack) result;
            }
        } catch (Exception ignored) {}

        return ItemStack.EMPTY;
    }

    /**
     * 从 IDisplayableCastingRecipe 获取 hasCast
     */
    private static boolean getDisplayableCastingHasCast(IDisplayableCastingRecipe recipe) {
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

    // ==================== 工具方法 ====================

    /**
     * 检查两个流体是否匹配
     */
    private static boolean matchesFluid(FluidStack a, FluidStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.getFluid().getRegistryName() == null || b.getFluid().getRegistryName() == null) {
            return false;
        }
        return a.getFluid().getRegistryName().equals(b.getFluid().getRegistryName());
    }

    /**
     * 检查流体是否有铸造配方
     */
    public static boolean hasCastingRecipes(FluidStack fluidStack) {
        return !getCastingRecipesForFluid(fluidStack).isEmpty();
    }
}