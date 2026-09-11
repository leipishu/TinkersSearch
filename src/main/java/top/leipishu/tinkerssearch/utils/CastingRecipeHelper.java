package top.leipishu.tinkerssearch.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.IMaterialRegistry;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.recipe.casting.IDisplayableCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingRecipe;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 铸造配方辅助类
 *
 * 关键特性：
 * - MaterialCastingRecipe 的 fluid 是空占位，真正成本在 itemCost 字段
 * - 成本 = itemCost * 90 mB
 * - 增强版 getMaterialCastingItemCost：兼容附属模组的各种字段命名
 */
public class CastingRecipeHelper {

    private static final int MB_PER_COST = 90;
    private static final boolean DEBUG_COST = false;

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
    // ===== 铸造配方获取 =========================================
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
            int amount = recipeFluid.getAmount();
            if (amount <= 0) amount = MB_PER_COST;
            result.add(new CastingInfo(output.copy(), requiresCast, amount));
        } catch (Exception ignored) {}
    }

    private static void processMaterialCastingRecipe(MaterialCastingRecipe recipe, FluidStack targetFluid,
                                                     List<CastingInfo> result, Set<ResourceLocation> seenOutputIds) {
        try {
            // ===== MaterialCastingRecipe 的 fluid 是空占位，优先用 itemCost 计算 =====
            int itemCost = getMaterialCastingItemCost(recipe);

            // ===== 判断该配方是否使用当前流体 =====
            boolean usesThisFluid = false;
            FluidStack recipeFluid = getCastingFluid(recipe);
            if (recipeFluid != null && !recipeFluid.isEmpty() && matchesFluid(recipeFluid, targetFluid)) {
                usesThisFluid = true;
            } else {
                // 走"材质匹配"逻辑
                try {
                    IMaterial recipeMaterial = getMaterialFromCastingRecipe(recipe);
                    if (recipeMaterial != null) {
                        IMaterial targetMaterial = getMaterialForFluid(targetFluid.getFluid());
                        if (targetMaterial != null && recipeMaterial.getIdentifier().equals(targetMaterial.getIdentifier())) {
                            usesThisFluid = true;
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (!usesThisFluid) return;

            ItemStack output = tryGetOutput(recipe);
            if (output == null || output.isEmpty()) return;

            ResourceLocation outputId = output.getItem().getRegistryName();
            if (outputId == null) return;
            if (!seenOutputIds.add(outputId)) return;

            boolean requiresCast = true;

            // ===== 计算真正的流体量 =====
            int amount;
            if (itemCost > 0) {
                amount = itemCost * MB_PER_COST;
            } else if (recipeFluid != null && !recipeFluid.isEmpty() && recipeFluid.getAmount() > 0) {
                amount = recipeFluid.getAmount();
            } else {
                Integer cached = getPartRequirementsCache().get(outputId);
                amount = (cached != null) ? cached : MB_PER_COST;
            }

            if (DEBUG_COST) {
                System.out.println("[Tinker's Search] MaterialCasting card: " + outputId
                        + " itemCost=" + itemCost + " amount=" + amount + "mB");
            }

            result.add(new CastingInfo(output.copy(), requiresCast, amount));
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
            int amount = recipeFluid.getAmount();
            if (amount <= 0) amount = MB_PER_COST;
            result.add(new CastingInfo(output.copy(), requiresCast, amount));
        } catch (Exception ignored) {}
    }

    // ============================================================
    // ===== MaterialCastingRecipe 关键字段读取（增强版）==========
    // ============================================================

    /**
     * 读取 MaterialCastingRecipe 的 itemCost（增强版，兼容附属模组）
     *
     * 策略：
     * 1. 优先尝试 getItemCost() 方法
     * 2. 遍历所有字段，寻找名为 itemCost / item_cost / cost 的 int 字段
     * 3. 如果找到的是 LoadableField 包装，尝试解包
     * 4. 兜底：尝试从序列化器读取
     */
    private static int getMaterialCastingItemCost(Recipe<?> recipe) {
        if (recipe == null) return 0;

        // ===== 1. 尝试方法 getItemCost() / getCost() / getRequiredCost() =====
        for (String mn : new String[]{"getItemCost", "getCost", "getRequiredCost"}) {
            try {
                Method m = recipe.getClass().getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(recipe);
                if (v instanceof Integer && (Integer) v > 0) return (Integer) v;
            } catch (Exception ignored) {}
        }

        // ===== 2. 遍历所有字段（含父类），匹配任何含 "cost" 的 int 字段 =====
        Class<?> clazz = recipe.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                String name = f.getName().toLowerCase();

                // 2a. int 类型
                if (name.contains("cost") && f.getType() == int.class) {
                    f.setAccessible(true);
                    try {
                        int v = f.getInt(recipe);
                        if (v > 0) return v;
                    } catch (Exception ignored) {}
                }

                // 2b. 包装类型（如 LoadableField、Supplier）
                if (name.contains("cost") && f.getType() != int.class) {
                    f.setAccessible(true);
                    try {
                        Object v = f.get(recipe);
                        if (v instanceof Integer && (Integer) v > 0) return (Integer) v;

                        // 尝试调用 get() 解包
                        if (v != null) {
                            try {
                                Method get = v.getClass().getMethod("get");
                                get.setAccessible(true);
                                Object inner = get.invoke(v);
                                if (inner instanceof Integer && (Integer) inner > 0) return (Integer) inner;
                            } catch (Exception ignored) {}

                            // 尝试调用 getAsInt() 解包
                            try {
                                Method getAsInt = v.getClass().getMethod("getAsInt");
                                getAsInt.setAccessible(true);
                                Object inner = getAsInt.invoke(v);
                                if (inner instanceof Integer && (Integer) inner > 0) return (Integer) inner;
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception ignored) {}
                }
            }
            clazz = clazz.getSuperclass();
        }

        // ===== 3. 兜底：从序列化器读取 =====
        try {
            Method getSerializer = recipe.getClass().getMethod("getSerializer");
            getSerializer.setAccessible(true);
            Object serializer = getSerializer.invoke(recipe);
            if (serializer != null) {
                for (String mn : new String[]{"getItemCost", "getCost"}) {
                    try {
                        Method m = serializer.getClass().getMethod(mn);
                        m.setAccessible(true);
                        Object v = m.invoke(serializer);
                        if (v instanceof Integer && (Integer) v > 0) return (Integer) v;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

        return 0;
    }

    /**
     * 从 MaterialCastingRecipe 中读取它关联的 Material
     */
    private static IMaterial getMaterialFromCastingRecipe(MaterialCastingRecipe recipe) {
        if (recipe == null) return null;

        // ===== 方法1：调 getMaterial() =====
        try {
            Method m = recipe.getClass().getMethod("getMaterial");
            m.setAccessible(true);
            Object v = m.invoke(recipe);
            if (v instanceof IMaterial) return (IMaterial) v;
        } catch (Exception ignored) {}

        // ===== 方法2：反射字段 =====
        for (String fn : new String[]{"material", "materialFluidRecipe", "fluidRecipe"}) {
            try {
                Class<?> clazz = recipe.getClass();
                while (clazz != null && clazz != Object.class) {
                    try {
                        Field f = clazz.getDeclaredField(fn);
                        f.setAccessible(true);
                        Object v = f.get(recipe);
                        if (v instanceof IMaterial) return (IMaterial) v;

                        // MaterialFluidRecipe -> getMaterial()
                        if (v != null && v.getClass().getName().toLowerCase().contains("materialfluid")) {
                            try {
                                Method getMat = v.getClass().getMethod("getMaterial");
                                getMat.setAccessible(true);
                                Object mat = getMat.invoke(v);
                                if (mat instanceof IMaterial) return (IMaterial) mat;
                            } catch (Exception ignored) {}
                        }
                    } catch (NoSuchFieldException ignored) {}
                    clazz = clazz.getSuperclass();
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    /**
     * 通过流体反查材料
     */
    private static IMaterial getMaterialForFluid(Fluid fluid) {
        if (fluid == null) return null;
        ResourceLocation fluidId = fluid.getRegistryName();
        if (fluidId == null) return null;

        // 优先从映射表查
        ResourceLocation matId = getMaterialIdForFluid(fluid);
        if (matId != null) {
            try {
                IMaterial mat = MaterialRegistry.getInstance().getMaterial(new MaterialId(matId));
                if (mat != null && mat != IMaterial.UNKNOWN) return mat;
            } catch (Exception ignored) {}
        }

        // 兜底：molten_xxx 前缀反推
        String fluidPath = fluidId.getPath();
        String matName = fluidPath.startsWith("molten_") ? fluidPath.substring(7) : fluidPath;
        String[] namespaces = {fluidId.getNamespace(), "tconstruct", "kubejs", "crafttweaker", "minecraft"};
        for (String ns : namespaces) {
            try {
                MaterialId mid = new MaterialId(ns, matName);
                IMaterial mat = MaterialRegistry.getInstance().getMaterial(mid);
                if (mat != null && mat != IMaterial.UNKNOWN) return mat;
            } catch (Exception ignored) {}
        }

        return null;
    }

    // ============================================================
    // ===== 流体 → 材料 映射 =====================================
    // ============================================================

    private static Map<ResourceLocation, ResourceLocation> fluidToMaterial = null;

    public static ResourceLocation getMaterialIdForFluid(Fluid fluid) {
        if (fluid == null) return null;
        ResourceLocation fluidId = fluid.getRegistryName();
        if (fluidId == null) return null;
        return getFluidToMaterialMap().get(fluidId);
    }

    private static Map<ResourceLocation, ResourceLocation> getFluidToMaterialMap() {
        if (fluidToMaterial != null) return fluidToMaterial;

        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            buildFluidToMaterialMap();
        } else {
            if (fluidToMaterial == null) fluidToMaterial = new HashMap<>();
            mc.execute(CastingRecipeHelper::buildFluidToMaterialMap);
        }
        return fluidToMaterial;
    }

    private static void buildFluidToMaterialMap() {
        Map<ResourceLocation, ResourceLocation> newMap = new HashMap<>();

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            fluidToMaterial = newMap;
            return;
        }

        RecipeManager recipeManager = mc.getConnection().getRecipeManager();
        int count = 0;

        try {
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                String className = recipe.getClass().getName().toLowerCase();
                if (!className.contains("materialfluid")) continue;

                try {
                    ResourceLocation materialId = extractMaterialId(recipe);
                    if (materialId == null) continue;

                    List<FluidStack> fluids = extractFluids(recipe);
                    if (fluids == null || fluids.isEmpty()) continue;

                    for (FluidStack fluid : fluids) {
                        if (fluid == null || fluid.isEmpty()) continue;
                        ResourceLocation fid = fluid.getFluid().getRegistryName();
                        if (fid == null) continue;
                        newMap.putIfAbsent(fid, materialId);  // ← 改为 putIfAbsent
                        count++;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.println("Tinker's Search: Error building fluid→material map: " + e.getMessage());
        }

        fluidToMaterial = newMap;
        System.out.println("[Tinker's Search] Built fluid→material map: " + count + " entries");
    }

    private static ResourceLocation extractMaterialId(Recipe<?> recipe) {
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

    private static ResourceLocation toResourceLocation(Object v) {
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

    @SuppressWarnings("unchecked")
    private static List<FluidStack> extractFluids(Recipe<?> recipe) {
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

    @SuppressWarnings("unchecked")
    private static List<FluidStack> toFluidList(Object v) {
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
    // ===== 部件需求量缓存 =======================================
    // ============================================================

    private static Map<ResourceLocation, Integer> partRequirements = null;

    public static void invalidateCache() {
        partRequirements = null;
        fluidToMaterial = null;
        PartPropertyHelper.clearMaterialCache();
        System.out.println("[Tinker's Search] All caches invalidated");
    }

    public static void prewarmPartRequirements() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            buildFluidToMaterialMap();
            buildPartRequirementsCache();
        } else {
            mc.execute(CastingRecipeHelper::buildFluidToMaterialMap);
            mc.execute(CastingRecipeHelper::buildPartRequirementsCache);
        }
    }

    public static int getRequiredAmountForPart(ResourceLocation partId) {
        if (partId == null) return -1;
        Integer v = getPartRequirementsCache().get(partId);
        return v != null ? v : -1;
    }

    private static Map<ResourceLocation, Integer> getPartRequirementsCache() {
        if (partRequirements != null) return partRequirements;

        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            buildPartRequirementsCache();
        } else {
            if (partRequirements == null) partRequirements = new HashMap<>();
            mc.execute(CastingRecipeHelper::buildPartRequirementsCache);
        }
        return partRequirements;
    }

    private static void buildPartRequirementsCache() {
        Map<ResourceLocation, Integer> newMap = new HashMap<>();

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            partRequirements = newMap;
            return;
        }

        RecipeManager recipeManager = mc.getConnection().getRecipeManager();
        int builderCount = 0;
        int materialCastingCount = 0;
        int idFallbackCount = 0;

        try {
            // ===== 阶段1：part_builder =====
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                Integer cost = tryGetPartBuilderCost(recipe);
                if (cost == null || cost <= 0) continue;

                ItemStack result = tryGetPartBuilderResult(recipe);
                if (result.isEmpty()) continue;

                Item item = result.getItem();
                if (!(item instanceof IMaterialItem)) continue;

                ResourceLocation partId = item.getRegistryName();
                if (partId == null) continue;

                int amount = cost * MB_PER_COST;
                if (newMap.putIfAbsent(partId, amount) == null) {
                    builderCount++;
                }
            }

            // ===== 阶段2：MaterialCastingRecipe（用增强版 itemCost）=====
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                if (!(recipe instanceof MaterialCastingRecipe)) continue;

                ItemStack output = tryGetOutput(recipe);
                if (output == null || output.isEmpty()) continue;

                ResourceLocation partId = output.getItem().getRegistryName();
                if (partId == null) continue;
                if (newMap.containsKey(partId)) continue;

                int itemCost = getMaterialCastingItemCost(recipe);
                int amount;
                if (itemCost > 0) {
                    amount = itemCost * MB_PER_COST;
                } else {
                    FluidStack fluid = getCastingFluid(recipe);
                    if (fluid != null && !fluid.isEmpty() && fluid.getAmount() > 0) {
                        amount = fluid.getAmount();
                    } else {
                        amount = MB_PER_COST;
                    }
                }

                newMap.put(partId, amount);
                materialCastingCount++;
            }

            // ===== 阶段3：普通 ItemCastingRecipe（_cast 后缀反推）=====
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                if (recipe instanceof MaterialCastingRecipe) continue;
                if (!(recipe instanceof ItemCastingRecipe)) continue;

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
            }

            // ===== 阶段4：配方 ID 反推兜底 =====
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                ResourceLocation recipeId = recipe.getId();
                if (recipeId == null) continue;

                String path = recipeId.getPath();
                if (!path.contains("parts/")) continue;

                String partName = extractPartNameFromRecipePath(path);
                if (partName == null) continue;

                ResourceLocation partId = new ResourceLocation(recipeId.getNamespace(), partName);
                if (newMap.containsKey(partId)) continue;

                Item item = ForgeRegistries.ITEMS.getValue(partId);
                if (!(item instanceof IMaterialItem)) continue;

                newMap.put(partId, MB_PER_COST);
                idFallbackCount++;
            }
        } catch (Exception e) {
            System.err.println("Tinker's Search: Error building part req cache: " + e.getMessage());
        }

        partRequirements = newMap;
        System.out.println("[Tinker's Search] Built part req cache: " + partRequirements.size()
                + " entries (builder=" + builderCount + ", materialCasting=" + materialCastingCount
                + ", idFallback=" + idFallbackCount + ")");
    }

    private static String extractPartNameFromRecipePath(String path) {
        String[] segments = path.split("/");
        if (segments.length == 0) return null;
        String last = segments[segments.length - 1];

        String[] suffixes = {"_red_sand_cast", "_sand_cast", "_gold_cast", "_cast"};
        for (String suffix : suffixes) {
            if (last.endsWith(suffix)) {
                return last.substring(0, last.length() - suffix.length());
            }
        }

        if (path.contains("parts/building/")) {
            return last;
        }
        return null;
    }

    // ============================================================
    // ===== 结构匹配工具 =========================================
    // ============================================================

    private static Integer tryGetPartBuilderCost(Recipe<?> recipe) {
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

    private static ItemStack tryGetPartBuilderResult(Recipe<?> recipe) {
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

    private static ItemStack tryGetOutput(Recipe<?> recipe) {
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