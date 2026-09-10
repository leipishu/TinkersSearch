package top.leipishu.tinkerssearch.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class CastingRecipeHelper {

    private static final int MB_PER_COST = 90;

    public static class CastingInfo {
        public final ItemStack outputItem;
        public final boolean requiresCast;
        public final int requiredAmount;

        public CastingInfo(ItemStack output, boolean requiresCast, int requiredAmount) {
            this.outputItem = output;
            this.requiresCast = requiresCast;
            this.requiredAmount = requiredAmount;
        }
    }

    // ============================================================
    // ===== 铸造配方获取（用于卡片显示） ==========================
    // ============================================================

    public static List<CastingInfo> getCastingRecipesForFluid(FluidStack fluidStack) {
        List<CastingInfo> result = new ArrayList<>();
        if (fluidStack == null || fluidStack.isEmpty()) return result;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return result;

        RecipeManager recipeManager = mc.getConnection().getRecipeManager();
        Set<ResourceLocation> seenOutputIds = new HashSet<>();

        try {
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                if (recipe instanceof MaterialCastingRecipe) {
                    processMaterialCastingRecipe((MaterialCastingRecipe) recipe, fluidStack, result, seenOutputIds);
                } else if (recipe instanceof ItemCastingRecipe) {
                    processItemCastingRecipe((ItemCastingRecipe) recipe, fluidStack, result, seenOutputIds);
                } else if (recipe instanceof IDisplayableCastingRecipe) {
                    processDisplayableCastingRecipe((IDisplayableCastingRecipe) recipe, fluidStack, result, seenOutputIds);
                }
            }
        } catch (Exception e) {
            System.err.println("Tinker's Search: Error loading casting recipes: " + e.getMessage());
        }

        return result;
    }

    private static void processItemCastingRecipe(ItemCastingRecipe recipe, FluidStack targetFluid,
                                                 List<CastingInfo> result, Set<ResourceLocation> seenOutputIds) {
        try {
            List<FluidStack> recipeFluids = recipe.getFluids();
            if (recipeFluids == null || recipeFluids.isEmpty()) return;

            FluidStack recipeFluid = recipeFluids.get(0);
            if (!matchesFluid(recipeFluid, targetFluid)) return;

            ItemStack output = recipe.getOutput();
            if (output == null || output.isEmpty()) return;

            ResourceLocation outputId = output.getItem().getRegistryName();
            if (outputId == null) return;
            if (!seenOutputIds.add(outputId)) return;

            boolean requiresCast = recipe.hasCast();
            int requiredAmount = recipeFluid.getAmount();
            result.add(new CastingInfo(output.copy(), requiresCast, requiredAmount));
        } catch (Exception ignored) {}
    }

    private static void processMaterialCastingRecipe(MaterialCastingRecipe recipe, FluidStack targetFluid,
                                                     List<CastingInfo> result, Set<ResourceLocation> seenOutputIds) {
        try {
            FluidStack recipeFluid = getCastingFluid(recipe);
            if (recipeFluid == null || recipeFluid.isEmpty()) return;
            if (!matchesFluid(recipeFluid, targetFluid)) return;

            ItemStack output = tryGetOutput(recipe);
            if (output == null || output.isEmpty()) return;

            ResourceLocation outputId = output.getItem().getRegistryName();
            if (outputId == null) return;
            if (!seenOutputIds.add(outputId)) return;

            boolean requiresCast = getMaterialCastingHasCast(recipe);
            int requiredAmount = recipeFluid.getAmount();
            result.add(new CastingInfo(output.copy(), requiresCast, requiredAmount));
        } catch (Exception ignored) {}
    }

    private static void processDisplayableCastingRecipe(IDisplayableCastingRecipe recipe, FluidStack targetFluid,
                                                        List<CastingInfo> result, Set<ResourceLocation> seenOutputIds) {
        try {
            FluidStack recipeFluid = getDisplayableCastingFluid(recipe);
            if (recipeFluid == null || recipeFluid.isEmpty()) return;
            if (!matchesFluid(recipeFluid, targetFluid)) return;

            ItemStack output = getDisplayableCastingOutput(recipe);
            if (output == null || output.isEmpty()) return;

            ResourceLocation outputId = output.getItem().getRegistryName();
            if (outputId == null) return;
            if (!seenOutputIds.add(outputId)) return;

            boolean requiresCast = getDisplayableCastingHasCast(recipe);
            int requiredAmount = recipeFluid.getAmount();
            result.add(new CastingInfo(output.copy(), requiresCast, requiredAmount));
        } catch (Exception ignored) {}
    }

    // ============================================================
    // ===== 部件需求量缓存 =======================================
    // ============================================================

    private static Map<ResourceLocation, Integer> partRequirements = null;
    private static long partReqCacheTime = 0;
    private static final long PART_REQ_CACHE_DURATION = 3000;

    public static void prewarmPartRequirements() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            buildPartRequirementsCache();
        } else {
            mc.execute(CastingRecipeHelper::buildPartRequirementsCache);
        }
    }

    public static int getRequiredAmountForPart(ResourceLocation partId) {
        if (partId == null) return -1;
        Map<ResourceLocation, Integer> map = getPartRequirementsCache();
        Integer v = map.get(partId);
        return v != null ? v : -1;
    }

    private static Map<ResourceLocation, Integer> getPartRequirementsCache() {
        long now = System.currentTimeMillis();
        if (partRequirements != null && (now - partReqCacheTime) < PART_REQ_CACHE_DURATION) {
            return partRequirements;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            buildPartRequirementsCache();
        } else {
            if (partRequirements == null) partRequirements = new HashMap<>();
            mc.execute(CastingRecipeHelper::buildPartRequirementsCache);
        }
        return partRequirements;
    }

    /**
     * 构建缓存
     *
     * 阶段1：读 part_builder 配方的 cost → mB = cost * 180
     * 阶段2：读 casting 配方作为回退（补齐 part_builder 没有的）
     */
    private static void buildPartRequirementsCache() {
        Map<ResourceLocation, Integer> newMap = new HashMap<>();

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            partRequirements = newMap;
            partReqCacheTime = System.currentTimeMillis();
            return;
        }

        RecipeManager recipeManager = mc.getConnection().getRecipeManager();
        int builderCount = 0;
        int castingCount = 0;

        try {
            // ===== 阶段1：part_builder 配方（优先） =====
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                Integer cost = tryGetPartBuilderCost(recipe);
                if (cost == null || cost <= 0) continue;

                ItemStack result = tryGetPartBuilderResult(recipe);
                if (result.isEmpty()) continue;

                ResourceLocation partId = result.getItem().getRegistryName();
                if (partId == null) continue;

                int amount = cost * MB_PER_COST;
                newMap.putIfAbsent(partId, amount);
                builderCount++;
            }

            // ===== 阶段2：casting 配方（回退补齐） =====
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                ItemStack output = tryGetOutput(recipe);
                if (output == null || output.isEmpty()) continue;

                ResourceLocation outputId = output.getItem().getRegistryName();
                if (outputId == null) continue;

                String path = outputId.getPath();
                if (!path.endsWith("_cast")) continue;

                String partPath = path.substring(0, path.length() - "_cast".length());
                ResourceLocation partId = new ResourceLocation(outputId.getNamespace(), partPath);

                if (newMap.containsKey(partId)) continue;

                FluidStack fluid = getCastingFluid(recipe);
                if (fluid == null || fluid.isEmpty()) continue;

                newMap.put(partId, fluid.getAmount());
                castingCount++;
            }
        } catch (Exception e) {
            System.err.println("Tinker's Search: Error building part requirements cache: " + e.getMessage());
        }

        partRequirements = newMap;
        partReqCacheTime = System.currentTimeMillis();
        System.out.println("[Tinker's Search] Built part req cache: " + partRequirements.size()
                + " entries (builder=" + builderCount + ", casting=" + castingCount + ")");

        int dump = 0;
        for (Map.Entry<ResourceLocation, Integer> e : partRequirements.entrySet()) {
            if (dump++ >= 20) break;
            System.out.println("  KEY: " + e.getKey() + " -> " + e.getValue() + " mB");
        }
    }

    /**
     * 尝试从 part_builder 配方获取 cost
     * 判断方式：类名包含 "PartBuilder"
     */
    private static Integer tryGetPartBuilderCost(Recipe<?> recipe) {
        String className = recipe.getClass().getName().toLowerCase();
        if (!className.contains("partbuilder")) return null;

        // 尝试 getCost() 方法
        try {
            Method m = recipe.getClass().getMethod("getCost");
            Object v = m.invoke(recipe);
            if (v instanceof Integer) return (Integer) v;
        } catch (Exception ignored) {}

        // 尝试反射 cost 字段
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

    /**
     * 尝试从 part_builder 配方获取输出物品
     */
    private static ItemStack tryGetPartBuilderResult(Recipe<?> recipe) {
        String className = recipe.getClass().getName().toLowerCase();
        if (!className.contains("partbuilder")) return ItemStack.EMPTY;

        // 尝试常见方法名
        String[] methodNames = {"getResultItem", "getResult", "getOutput", "getRecipeOutput"};
        for (String name : methodNames) {
            try {
                Method m = recipe.getClass().getMethod(name);
                Object v = m.invoke(recipe);
                if (v instanceof ItemStack && !((ItemStack) v).isEmpty()) return (ItemStack) v;
            } catch (Exception ignored) {}
        }

        // 反射字段
        String[] fieldNames = {"result", "output", "recipeOutput"};
        for (String name : fieldNames) {
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

    /**
     * 尝试从 recipe 拿 output（不依赖具体类）
     */
    private static ItemStack tryGetOutput(Recipe<?> recipe) {
        String[] methodNames = {"getOutput", "getResult", "getResultItem"};
        for (String name : methodNames) {
            try {
                Method m = recipe.getClass().getMethod(name);
                m.setAccessible(true);
                Object v = m.invoke(recipe);
                if (v instanceof ItemStack && !((ItemStack) v).isEmpty()) return (ItemStack) v;
            } catch (Exception ignored) {}
        }

        String[] fieldNames = {"output", "result"};
        for (String name : fieldNames) {
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

    /**
     * 从任意铸造配方提取流体
     */
    private static FluidStack getCastingFluid(Recipe<?> recipe) {
        try {
            Class<?> clazz = recipe.getClass();
            while (clazz != null && clazz != Object.class) {
                try {
                    Field field = clazz.getDeclaredField("fluid");
                    field.setAccessible(true);
                    Object fluidIngredient = field.get(recipe);
                    if (fluidIngredient != null) {
                        try {
                            Method getFluids = fluidIngredient.getClass().getMethod("getFluids");
                            @SuppressWarnings("unchecked")
                            List<FluidStack> fluids = (List<FluidStack>) getFluids.invoke(fluidIngredient);
                            if (fluids != null && !fluids.isEmpty()) return fluids.get(0);
                        } catch (Exception ignored) {}
                        try {
                            Method getFluid = fluidIngredient.getClass().getMethod("getFluid");
                            Object v = getFluid.invoke(fluidIngredient);
                            if (v instanceof FluidStack) return (FluidStack) v;
                        } catch (Exception ignored) {}
                    }
                } catch (NoSuchFieldException ignored) {}
                clazz = clazz.getSuperclass();
            }
        } catch (Exception ignored) {}

        try {
            Method m = recipe.getClass().getMethod("getFluid");
            Object v = m.invoke(recipe);
            if (v instanceof FluidStack) return (FluidStack) v;
        } catch (Exception ignored) {}

        return null;
    }

    // ============================================================
    // ===== 其他反射工具 =========================================
    // ============================================================

    private static boolean getMaterialCastingHasCast(MaterialCastingRecipe recipe) {
        try {
            Field field = ItemCastingRecipe.class.getDeclaredField("cast");
            field.setAccessible(true);
            Object cast = field.get(recipe);
            return cast != null;
        } catch (Exception ignored) {}
        return false;
    }

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
                    if (fluids != null && !fluids.isEmpty()) return fluids.get(0);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static ItemStack getDisplayableCastingOutput(IDisplayableCastingRecipe recipe) {
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

    private static boolean matchesFluid(FluidStack a, FluidStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        if (a.getFluid().getRegistryName() == null || b.getFluid().getRegistryName() == null) return false;
        return a.getFluid().getRegistryName().equals(b.getFluid().getRegistryName());
    }

    public static boolean hasCastingRecipes(FluidStack fluidStack) {
        return !getCastingRecipesForFluid(fluidStack).isEmpty();
    }
}