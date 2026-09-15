package top.leipishu.tinkerssearch.recipe;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;
import slimeknights.tconstruct.tools.stats.LimbMaterialStats;
import slimeknights.tconstruct.library.recipe.casting.IDisplayableCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingRecipe;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import top.leipishu.tinkerssearch.utils.PartPropertyHelper;

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
 * 关键特性：
 * - MaterialCastingRecipe 的流体匹配改为"MaterialId 直接对比"，不依赖 MaterialRegistry 注册状态
 *   → KubeJS 出错导致材料未注册时仍可正确匹配
 * - 成本 = itemCost * 90 mB
 * - 增强版 getMaterialCastingItemCost：兼容附属模组的各种字段命名
 * - material 反查（依赖 MaterialRegistry）仅作为最后兜底
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

        int materialCastingTotal = 0;
        int materialCastingMatched = 0;
        int itemCastingTotal = 0;
        int itemCastingMatched = 0;
        int displayableTotal = 0;
        int displayableMatched = 0;

        try {
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                if (recipe instanceof MaterialCastingRecipe) {
                    materialCastingTotal++;
                    int before = result.size();
                    processMaterialCastingRecipe((MaterialCastingRecipe) recipe, fluidStack, result, seenOutputIds);
                    if (result.size() > before) materialCastingMatched++;
                } else if (recipe instanceof ItemCastingRecipe) {
                    itemCastingTotal++;
                    int before = result.size();
                    processItemCastingRecipe((ItemCastingRecipe) recipe, fluidStack, result, seenOutputIds);
                    if (result.size() > before) itemCastingMatched++;
                } else if (recipe instanceof IDisplayableCastingRecipe) {
                    displayableTotal++;
                    int before = result.size();
                    processDisplayableCastingRecipe((IDisplayableCastingRecipe) recipe, fluidStack, result, seenOutputIds);
                    if (result.size() > before) displayableMatched++;
                }
            }
        } catch (Exception e) {
            System.err.println("Tinker's Search: Error loading casting recipes: " + e.getMessage());
        }

        System.out.println("[Tinker's Search] getCastingRecipesForFluid(" + fluidStack.getFluid().getRegistryName() + "):"
                + " MaterialCasting=" + materialCastingMatched + "/" + materialCastingTotal
                + ", ItemCasting=" + itemCastingMatched + "/" + itemCastingTotal
                + ", Displayable=" + displayableMatched + "/" + displayableTotal
                + " → total " + result.size() + " outputs");

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

    /**
     * 处理 MaterialCastingRecipe。
     *
     * MaterialCastingRecipe 是"任意材料 → 该材料做的部件"的通用配方，
     * 因此匹配逻辑不是比对 material ID，而是：
     *   1. 从 result 字段拿输出部件（IMaterialItem）
     *   2. 判断该部件能否用目标材料（canUseMaterial）
     *
     * 这样即使 KubeJS 出错导致材料未注册，只要 canUseMaterial 能通过
     * （或走"材料未注册 → 宽松通过"兜底），部件就能正确显示。
     */
    private static void processMaterialCastingRecipe(MaterialCastingRecipe recipe, FluidStack targetFluid,
                                                     List<CastingInfo> result, Set<ResourceLocation> seenOutputIds) {
        try {
            // ===== 1. 拿输出部件 =====
            // MaterialCastingRecipe 的 result 字段就是部件（IMaterialItem / ToolPartItem）
            ItemStack output = tryGetOutput(recipe);
            if (output == null || output.isEmpty()) return;

            Item item = output.getItem();
            if (!(item instanceof IMaterialItem)) return;

            ResourceLocation outputId = item.getRegistryName();
            if (outputId == null) return;

            // ===== 2. 拿目标材料 =====
            MaterialId targetMat = resolveMaterialIdForFluid(targetFluid.getFluid());
            if (targetMat == null) return;

            // ===== 3. 判断该部件能否用该材料 =====
            if (!canMaterialCastingRecipeUseMaterial((IMaterialItem) item, targetMat)) {
                return;
            }

            // ===== 4. 计算需求量 =====
            int itemCost = getMaterialCastingItemCost(recipe);
            int amount = itemCost > 0 ? itemCost * MB_PER_COST : MB_PER_COST;

            if (!seenOutputIds.add(outputId)) return;

            result.add(new CastingInfo(output.copy(), true, amount));
        } catch (Exception ignored) {}
    }

    /**
     * 判断 MaterialCastingRecipe 是否能配合目标材料，浇筑出对应部件。
     *
     * 三层判定：
     *   1. 官方 canUseMaterial
     *   2. 检查材料 stats 是否存在
     *   3. 材料在 registry 里根本没注册（KubeJS 出错场景）→ 宽松通过
     */
    private static boolean canMaterialCastingRecipeUseMaterial(IMaterialItem mi, MaterialId targetMat) {
        // 1. 官方判断
        try {
            if (mi.canUseMaterial(targetMat)) return true;
        } catch (Throwable ignored) {}

        // 2. 检查 stats
        MaterialStatsId statType = inferStatType(mi);
        if (statType != null) {
            try {
                if (MaterialRegistry.getInstance().getMaterialStats(targetMat, statType).isPresent()) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        // 3. 材料未注册时宽松通过（KubeJS 场景兜底）
        try {
            IMaterial mat = MaterialRegistry.getInstance().getMaterial(targetMat);
            if (mat == null || mat == IMaterial.UNKNOWN) {
                return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }

    /**
     * 推断部件的 statType。
     * 与 FluidPartDataCache.inferStatType 逻辑一致。
     */
    private static MaterialStatsId inferStatType(IMaterialItem item) {
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
    // ===== MaterialId 提取与对比（核心修复）====================
    // ============================================================

    /**
     * 从 MaterialCastingRecipe 中提取它关联的 MaterialId。
     * 完全不查 MaterialRegistry，只做反射。
     */
    private static MaterialId getMaterialIdFromRecipe(Recipe<?> recipe) {
        if (recipe == null) return null;

        // ===== 方式1：常见方法 =====
        for (String mn : new String[]{"getMaterial", "getOutputMaterial", "getMaterialId", "getOutput"}) {
            try {
                Method m = recipe.getClass().getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(recipe);
                MaterialId id = toMaterialId(v);
                if (id != null) return id;
            } catch (Exception ignored) {}
        }

        // ===== 方式2：常见字段 =====
        for (String fn : new String[]{"material", "materialId", "outputMaterial", "output"}) {
            Object v = findFieldValue(recipe, fn);
            MaterialId id = toMaterialId(v);
            if (id != null) return id;
        }

        // ===== 方式3：fluidRecipe 里挖 =====
        Object mf = findFieldValue(recipe,
                "materialFluid", "materialFluidRecipe", "fluidRecipe", "materialRecipe");
        if (mf != null) {
            // 方法
            for (String mn : new String[]{"getOutput", "getOutputMaterial", "getResult", "getMaterial"}) {
                try {
                    Method m = mf.getClass().getMethod(mn);
                    m.setAccessible(true);
                    Object v = m.invoke(mf);
                    MaterialId id = toMaterialId(v);
                    if (id != null) return id;
                } catch (Exception ignored) {}
            }
            // 字段
            for (String fn : new String[]{"output", "outputMaterial", "result", "material"}) {
                Object v = findFieldValue(mf, fn);
                MaterialId id = toMaterialId(v);
                if (id != null) return id;
            }
        }

        // ===== 方式4：递归挖一层深度 =====
        // 如果 fluidRecipe 是个 wrapper，它内部可能还有一层
        if (mf != null) {
            MaterialId deeper = digDeeper(mf);
            if (deeper != null) return deeper;
        }

        // ===== 诊断：每个类首次遇到时打印一次字段结构 =====
        diagnoseIfNeeded(recipe);

        return null;
    }

    /**
     * 对 fluidRecipe 再深入一层：遍历它所有对象类型字段，尝试拿 MaterialId。
     * 处理 fluidRecipe 是 wrapper 的情况（例如内部还有 materialFluidRecipe）。
     */
    private static MaterialId digDeeper(Object obj) {
        if (obj == null) return null;

        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v == null) continue;

                    // 尝试直接转 MaterialId
                    MaterialId id = toMaterialId(v);
                    if (id != null) return id;

                    // 对象字段：尝试常见的 getter
                    if (!(v instanceof Number) && !(v instanceof String)
                            && !(v instanceof Boolean) && !(v instanceof Iterable)
                            && !(v instanceof Object[])) {
                        for (String mn : new String[]{"getOutput", "getOutputMaterial",
                                "getResult", "getMaterial"}) {
                            try {
                                Method m = v.getClass().getMethod(mn);
                                m.setAccessible(true);
                                Object inner = m.invoke(v);
                                MaterialId innerId = toMaterialId(inner);
                                if (innerId != null) return innerId;
                            } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static final Set<String> DIAGNOSED_RECIPE_CLASSES = new HashSet<>();
    private static final Set<String> DIAGNOSED_FLUID_CLASSES = new HashSet<>();

    private static void diagnoseIfNeeded(Recipe<?> recipe) {
        String className = recipe.getClass().getName();
        if (!DIAGNOSED_RECIPE_CLASSES.add(className)) return;

        System.out.println("[Tinker's Search] === DIAG RECIPE " + className + " ===");

        Class<?> c = recipe.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(recipe);
                    String vType = v == null ? "null" : v.getClass().getSimpleName();
                    System.out.println("[Tinker's Search]   field " + f.getName()
                            + " : " + f.getType().getSimpleName() + " = " + vType);

                    // 对象类型的字段进一步诊断（排除基础类型和集合）
                    if (v != null
                            && !(v instanceof Number)
                            && !(v instanceof String)
                            && !(v instanceof Boolean)
                            && !(v instanceof Iterable)
                            && !(v instanceof Object[])) {
                        diagnoseFluidObject(v);
                    }
                } catch (Exception ignored) {}
            }
            c = c.getSuperclass();
        }

        System.out.println("[Tinker's Search] === DIAG RECIPE END ===");
    }

    private static void diagnoseFluidObject(Object mf) {
        String className = mf.getClass().getName();
        if (!DIAGNOSED_FLUID_CLASSES.add(className)) return;

        System.out.println("[Tinker's Search]   === DIAG INNER " + className + " ===");

        // ===== getters =====
        System.out.println("[Tinker's Search]     getters:");
        for (Method m : mf.getClass().getMethods()) {
            if (m.getParameterCount() != 0) continue;
            String n = m.getName();
            if ("getClass".equals(n) || "hashCode".equals(n) || "toString".equals(n)) continue;
            if (!n.startsWith("get") && !n.startsWith("is")) continue;
            try {
                Object v = m.invoke(mf);
                String vType = v == null ? "null" : v.getClass().getSimpleName();
                System.out.println("[Tinker's Search]       " + n + "() : " + vType);
            } catch (Exception ignored) {}
        }

        // ===== fields =====
        System.out.println("[Tinker's Search]     fields:");
        Class<?> c = mf.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object v = f.get(mf);
                    String vType = v == null ? "null" : v.getClass().getSimpleName();
                    System.out.println("[Tinker's Search]       " + f.getName()
                            + " : " + f.getType().getSimpleName() + " = " + vType);
                } catch (Exception ignored) {}
            }
            c = c.getSuperclass();
        }

        System.out.println("[Tinker's Search]   === DIAG INNER END ===");
    }

    /**
     * 从流体推断 MaterialId（不查 MaterialRegistry）。
     *   1. 先查 fluidToMaterial 映射表
     *   2. 再前缀剥离 molten_ / liquid_ / fluid_
     */
    private static MaterialId resolveMaterialIdForFluid(Fluid fluid) {
        if (fluid == null) return null;
        ResourceLocation fluidId = fluid.getRegistryName();
        if (fluidId == null) return null;

        // 1. 映射表
        try {
            ResourceLocation matId = getMaterialIdForFluid(fluid);
            if (matId != null) return new MaterialId(matId);
        } catch (Exception ignored) {}

        // 2. 前缀剥离
        String path = fluidId.getPath();
        for (String prefix : new String[]{"molten_", "liquid_", "fluid_"}) {
            if (path.startsWith(prefix)) {
                String stripped = path.substring(prefix.length());
                if (!stripped.isEmpty()) {
                    return new MaterialId(fluidId.getNamespace(), stripped);
                }
            }
        }

        return null;
    }

    private static MaterialId toMaterialId(Object v) {
        if (v == null) return null;
        if (v instanceof MaterialId) return (MaterialId) v;
        if (v instanceof IMaterial) return ((IMaterial) v).getIdentifier();
        for (String mn : new String[]{"getIdentifier", "getLocation", "getId"}) {
            try {
                Method m = v.getClass().getMethod(mn);
                m.setAccessible(true);
                Object id = m.invoke(v);
                if (id instanceof MaterialId) return (MaterialId) id;
                if (id != null) {
                    try { return new MaterialId(new ResourceLocation(id.toString())); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ============================================================
    // ===== MaterialCastingRecipe 关键字段读取（增强版）==========
    // ============================================================

    private static int getMaterialCastingItemCost(Recipe<?> recipe) {
        if (recipe == null) return 0;

        for (String mn : new String[]{"getItemCost", "getCost", "getRequiredCost"}) {
            try {
                Method m = recipe.getClass().getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(recipe);
                if (v instanceof Integer && (Integer) v > 0) return (Integer) v;
            } catch (Exception ignored) {}
        }

        Class<?> clazz = recipe.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                String name = f.getName().toLowerCase();

                if (name.contains("cost") && f.getType() == int.class) {
                    f.setAccessible(true);
                    try {
                        int v = f.getInt(recipe);
                        if (v > 0) return v;
                    } catch (Exception ignored) {}
                }

                if (name.contains("cost") && f.getType() != int.class) {
                    f.setAccessible(true);
                    try {
                        Object v = f.get(recipe);
                        if (v instanceof Integer && (Integer) v > 0) return (Integer) v;

                        if (v != null) {
                            try {
                                Method get = v.getClass().getMethod("get");
                                get.setAccessible(true);
                                Object inner = get.invoke(v);
                                if (inner instanceof Integer && (Integer) inner > 0) return (Integer) inner;
                            } catch (Exception ignored) {}

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

    private static IMaterial getMaterialFromCastingRecipe(MaterialCastingRecipe recipe) {
        if (recipe == null) return null;

        try {
            Method m = recipe.getClass().getMethod("getMaterial");
            m.setAccessible(true);
            Object v = m.invoke(recipe);
            if (v instanceof IMaterial) return (IMaterial) v;
        } catch (Exception ignored) {}

        for (String fn : new String[]{"material", "materialFluidRecipe", "fluidRecipe"}) {
            try {
                Class<?> clazz = recipe.getClass();
                while (clazz != null && clazz != Object.class) {
                    try {
                        Field f = clazz.getDeclaredField(fn);
                        f.setAccessible(true);
                        Object v = f.get(recipe);
                        if (v instanceof IMaterial) return (IMaterial) v;

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

    private static IMaterial getMaterialForFluid(Fluid fluid) {
        if (fluid == null) return null;
        ResourceLocation fluidId = fluid.getRegistryName();
        if (fluidId == null) return null;

        ResourceLocation matId = getMaterialIdForFluid(fluid);
        if (matId != null) {
            try {
                IMaterial mat = MaterialRegistry.getInstance().getMaterial(new MaterialId(matId));
                if (mat != null && mat != IMaterial.UNKNOWN) return mat;
            } catch (Exception ignored) {}
        }

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
                        newMap.putIfAbsent(fid, materialId);
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
        int materialCastingSkipped = 0;
        int materialCastingNoMatch = 0;
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
            System.out.println("[Tinker's Search] PartReqCache stage1 (builder): " + builderCount + " entries");

            // ===== 阶段2：MaterialCastingRecipe =====
            int materialCastingTotal = 0;
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                if (!(recipe instanceof MaterialCastingRecipe)) continue;
                materialCastingTotal++;

                ItemStack output = tryGetOutput(recipe);
                if (output == null || output.isEmpty()) continue;

                ResourceLocation partId = output.getItem().getRegistryName();
                if (partId == null) continue;

                if (newMap.containsKey(partId)) {
                    materialCastingSkipped++;
                    continue;
                }

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
            System.out.println("[Tinker's Search] PartReqCache stage2 (materialCasting): "
                    + "total=" + materialCastingTotal
                    + ", added=" + materialCastingCount
                    + ", skippedByBuilder=" + materialCastingSkipped);

            // ===== 阶段3：普通 ItemCastingRecipe（_cast 后缀反推）=====
            int itemCastingCount = 0;
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
                itemCastingCount++;
            }
            System.out.println("[Tinker's Search] PartReqCache stage3 (itemCasting): " + itemCastingCount + " entries");

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
                + " entries (builder=" + builderCount
                + ", materialCasting=" + materialCastingCount
                + ", materialCastingSkipped=" + materialCastingSkipped
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

    /**
     * 读取浇筑配方的输入流体（纯反射，不再引用 ICastingRecipe.getFluidIngredient）。
     */
    private static FluidStack getCastingFluid(Recipe<?> recipe) {
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

    private static FluidStack fluidFromIngredient(Object fi) {
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

    private static Object findFieldValue(Object obj, String... names) {
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