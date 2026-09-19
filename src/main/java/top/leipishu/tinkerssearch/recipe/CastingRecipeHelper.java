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
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.recipe.casting.IDisplayableCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingRecipe;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import top.leipishu.tinkerssearch.utils.PartPropertyHelper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 浇筑配方读取器 —— 材料中心设计。
 *
 * <p>核心概念：TC3 的 {@link MaterialCastingRecipe} 是"通用配方"，
 * 它接受任何"有对应熔融流体的材料"，输出通过 {@code IMaterialItem.withMaterial(mat)}
 * 动态生成。因此判断"某材料能否浇筑出某部件"的正确判据是：
 * <pre>
 *     MaterialRegistry.getInstance()
 *         .getMaterialStats(materialId, statTypeOf(partItem))
 *         .isPresent()
 * </pre>
 */
public class CastingRecipeHelper {

    public static class CastingInfo {
        public final ItemStack outputItem;
        public final boolean requiresCast;
        public final int requiredAmount;
        public final boolean isPart;

        public CastingInfo(ItemStack output, boolean requiresCast, int requiredAmount) {
            this.outputItem = output;
            this.requiresCast = requiresCast;
            this.requiredAmount = requiredAmount;
            this.isPart = output != null && !output.isEmpty()
                    && output.getItem() instanceof IMaterialItem;
        }
    }

    // ============================================================
    // ===== 全局缓存的部件类型清单 ===============================
    // ============================================================

    private static Set<ResourceLocation> CACHED_CASTABLE_PART_TYPES = null;
    private static final Map<ResourceLocation, Integer> CACHED_PART_AMOUNTS = new ConcurrentHashMap<>();

    public static Set<ResourceLocation> getAllCastablePartTypes() {
        if (CACHED_CASTABLE_PART_TYPES != null) return CACHED_CASTABLE_PART_TYPES;

        Set<ResourceLocation> types = new HashSet<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return types;

        RecipeManager rm = mc.getConnection().getRecipeManager();
        int total = 0;

        for (Recipe<?> recipe : rm.getRecipes()) {
            try {
                boolean isCasting = recipe instanceof ItemCastingRecipe
                        || recipe instanceof MaterialCastingRecipe
                        || recipe instanceof IDisplayableCastingRecipe;
                if (!isCasting) continue;
                total++;

                ItemStack output = tryGetOutputThorough(recipe);
                if (output.isEmpty()) continue;

                Item item = output.getItem();
                if (!(item instanceof IMaterialItem)) continue;

                ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                if (id == null) continue;

                String path = id.getPath().toLowerCase();
                if (path.endsWith("_cast") || path.startsWith("cast_")
                        || path.contains("plate_cast") || path.contains("sand_cast")
                        || path.contains("red_sand_cast") || path.contains("gold_cast")) continue;

                types.add(id);

                int amount = extractAmount(recipe);
                if (amount > 0) CACHED_PART_AMOUNTS.putIfAbsent(id, amount);
            } catch (Throwable ignored) {}
        }

        CACHED_CASTABLE_PART_TYPES = types;
        System.out.println("[Tinker's Search] Castable part types: " + types.size()
                + " (scanned " + total + " casting recipes)");
        return types;
    }

    public static List<PartTypeRef> getPartTypesForMaterial(MaterialId mat) {
        List<PartTypeRef> result = new ArrayList<>();
        if (mat == null) return result;

        for (ResourceLocation typeId : getAllCastablePartTypes()) {
            Item item = ForgeRegistries.ITEMS.getValue(typeId);
            if (!(item instanceof IMaterialItem)) continue;
            IMaterialItem mi = (IMaterialItem) item;

            if (materialCanProduce(mi, mat)) {
                int amount = CACHED_PART_AMOUNTS.getOrDefault(typeId, 90);
                result.add(new PartTypeRef(typeId, mi, amount));
            }
        }
        return result;
    }

    public static class PartTypeRef {
        public final ResourceLocation itemId;
        public final IMaterialItem item;
        public final int amount;
        public PartTypeRef(ResourceLocation id, IMaterialItem i, int a) {
            this.itemId = id; this.item = i; this.amount = a;
        }
    }

    public static boolean materialCanProduce(IMaterialItem item, MaterialId mat) {
        if (item == null || mat == null) return false;

        MaterialStatsId statType = inferStatType(item);

        if (statType != null) {
            try {
                if (MaterialRegistry.getInstance()
                        .getMaterialStats(mat, statType).isPresent()) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        try {
            if (item.canUseMaterial(mat)) return true;
        } catch (Throwable ignored) {}

        return statType == null;
    }

    public static ResourceLocation getMaterialIdForFluid(Fluid fluid) {
        return MaterialResolver.resolveAsResourceLocation(fluid);
    }

    // ============================================================
    // ===== 兼容旧 API：扫描具体流体 ==============================
    // ============================================================

    private static final Map<ResourceLocation, List<CastingInfo>> FLUID_CACHE = new ConcurrentHashMap<>();

    public static List<CastingInfo> getAllCastingOutputs(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.isEmpty()) return new ArrayList<>();
        ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluidStack.getFluid());
        if (fluidId == null) return new ArrayList<>();

        List<CastingInfo> cached = FLUID_CACHE.get(fluidId);
        if (cached != null) return cached;

        List<CastingInfo> result = scanFluidCastingOutputs(fluidStack);
        FLUID_CACHE.put(fluidId, result);
        return result;
    }

    private static List<CastingInfo> scanFluidCastingOutputs(FluidStack fluidStack) {
        List<CastingInfo> result = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return result;

        Set<ResourceLocation> seen = new HashSet<>();

        for (Recipe<?> recipe : mc.getConnection().getRecipeManager().getRecipes()) {
            try {
                boolean isCasting = recipe instanceof ItemCastingRecipe
                        || recipe instanceof MaterialCastingRecipe
                        || recipe instanceof IDisplayableCastingRecipe;
                if (!isCasting) continue;

                List<FluidStack> recipeFluids = RecipeReflection.extractFluids(recipe);
                if (recipeFluids.isEmpty()) continue;

                FluidStack matched = null;
                for (FluidStack fs : recipeFluids) {
                    if (RecipeReflection.matchesFluid(fs, fluidStack)) { matched = fs; break; }
                }
                if (matched == null) continue;

                ItemStack output = tryGetOutputThorough(recipe);
                if (output.isEmpty()) continue;

                ResourceLocation outId = ForgeRegistries.ITEMS.getKey(output.getItem());
                if (outId == null || !seen.add(outId)) continue;

                int amount = matched.getAmount();
                if (amount <= 0) amount = MaterialCastingCost.getAmount(recipe);
                if (amount <= 0) amount = MaterialCastingCost.MB_PER_COST;

                result.add(new CastingInfo(output.copy(), determineRequiresCast(recipe), amount));
            } catch (Throwable ignored) {}
        }
        return result;
    }

    // 兼容包装
    public static List<CastingInfo> getCastingRecipesForFluid(FluidStack fs) { return getAllCastingOutputs(fs); }
    public static boolean hasCastingRecipes(FluidStack fs) { return !getAllCastingOutputs(fs).isEmpty(); }
    public static List<CastingInfo> getAnyPartCastingRecipes(FluidStack fs) {
        List<CastingInfo> r = new ArrayList<>();
        for (CastingInfo c : getAllCastingOutputs(fs)) if (c.isPart) r.add(c);
        return r;
    }
    public static List<CastingInfo> getDirectPartCastingRecipes(FluidStack fs) { return getAnyPartCastingRecipes(fs); }
    public static List<CastingInfo> getAttachedPartCastingRecipes(FluidStack fs) { return new ArrayList<>(); }

    // ============================================================
    // ===== 工具 ==================================================
    // ============================================================

    static ItemStack tryGetOutputThorough(Object recipe) {
        try {
            ItemStack s = RecipeReflection.tryGetOutput((Recipe<?>) recipe);
            if (s != null && !s.isEmpty()) return s;
        } catch (Throwable ignored) {}

        for (String mn : new String[]{"getResult", "getOutput", "getResultItem", "getRecipeOutput"}) {
            try {
                Method m = recipe.getClass().getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(recipe);
                if (v instanceof ItemStack && !((ItemStack) v).isEmpty()) return (ItemStack) v;
                if (v instanceof Item) return new ItemStack((Item) v);
            } catch (Exception ignored) {}
        }

        for (String fn : new String[]{"result", "output", "castOutput", "item"}) {
            Object v = RecipeReflection.findFieldValue(recipe, fn);
            if (v instanceof ItemStack && !((ItemStack) v).isEmpty()) return (ItemStack) v;
            if (v instanceof Item) return new ItemStack((Item) v);
            if (v instanceof IMaterialItem) return new ItemStack(((IMaterialItem) v).asItem());
        }
        return ItemStack.EMPTY;
    }

    private static int extractAmount(Recipe<?> recipe) {
        try {
            List<FluidStack> fluids = RecipeReflection.extractFluids(recipe);
            if (!fluids.isEmpty() && fluids.get(0).getAmount() > 0) {
                return fluids.get(0).getAmount();
            }
        } catch (Throwable ignored) {}
        int c = MaterialCastingCost.getItemCost(recipe);
        return c > 0 ? c * MaterialCastingCost.MB_PER_COST : 90;
    }

    private static boolean determineRequiresCast(Object recipe) {
        for (String mn : new String[]{"hasCast", "requiresCast", "getHasCast"}) {
            try {
                Method m = recipe.getClass().getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(recipe);
                if (v instanceof Boolean) return (Boolean) v;
            } catch (Exception ignored) {}
        }
        return true;
    }

    static MaterialStatsId inferStatType(IMaterialItem item) {
        return MaterialCompatibility.inferStatType(item);
    }

    public static void invalidateCache() {
        FLUID_CACHE.clear();
        CACHED_CASTABLE_PART_TYPES = null;
        CACHED_PART_AMOUNTS.clear();
        PartRequirementsCache.clear();
        MaterialResolver.clear();
        PartPropertyHelper.clearMaterialCache();
        System.out.println("[Tinker's Search] CastingRecipeHelper caches invalidated");
    }

    public static void prewarmPartRequirements() {
        MaterialResolver.prewarm();
        PartRequirementsCache.prewarm();
        getAllCastablePartTypes();
    }

    public static int getRequiredAmountForPart(ResourceLocation partId) {
        Integer cached = CACHED_PART_AMOUNTS.get(partId);
        if (cached != null) return cached;
        int v = PartRequirementsCache.get(partId);
        return v > 0 ? v : 90;
    }
}