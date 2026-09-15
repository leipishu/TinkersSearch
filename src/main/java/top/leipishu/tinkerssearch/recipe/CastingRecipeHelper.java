package top.leipishu.tinkerssearch.recipe;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.recipe.casting.IDisplayableCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingRecipe;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import top.leipishu.tinkerssearch.utils.PartPropertyHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 浇筑配方读取的对外入口。
 *
 * <p>职责：
 * <ul>
 *   <li>遍历 {@code RecipeManager}，按配方类型分发到三个处理方法</li>
 *   <li>对外暴露 {@link #getCastingRecipesForFluid}、{@link #hasCastingRecipes} 等 API</li>
 *   <li>聚合并转发缓存清空/预热请求给 {@link MaterialResolver}
 *       与 {@link PartRequirementsCache}</li>
 * </ul>
 *
 * <p>具体职责已拆到同包：
 * <ul>
 *   <li>{@link MaterialResolver} — 流体→材料 ID</li>
 *   <li>{@link MaterialCompatibility} — 部件与材料兼容性</li>
 *   <li>{@link MaterialCastingCost} — itemCost 与 mB 换算</li>
 *   <li>{@link PartRequirementsCache} — 部件需求量缓存</li>
 *   <li>{@link RecipeReflection} — 通用反射工具</li>
 * </ul>
 */
public class CastingRecipeHelper {

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
    // ===== 对外 API ============================================
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

    public static boolean hasCastingRecipes(FluidStack fluidStack) {
        return !getCastingRecipesForFluid(fluidStack).isEmpty();
    }

    /** 转发给 {@link MaterialResolver}，保留旧签名。 */
    public static ResourceLocation getMaterialIdForFluid(Fluid fluid) {
        return MaterialResolver.resolveAsResourceLocation(fluid);
    }

    /** 清空所有相关缓存。 */
    public static void invalidateCache() {
        PartRequirementsCache.clear();
        MaterialResolver.clear();
        PartPropertyHelper.clearMaterialCache();
        System.out.println("[Tinker's Search] All caches invalidated");
    }

    /** 预热部件需求量缓存。 */
    public static void prewarmPartRequirements() {
        MaterialResolver.prewarm();
        PartRequirementsCache.prewarm();
    }

    public static int getRequiredAmountForPart(ResourceLocation partId) {
        return PartRequirementsCache.get(partId);
    }

    // ============================================================
    // ===== 三种配方处理 =========================================
    // ============================================================

    private static void processItemCastingRecipe(ItemCastingRecipe recipe, FluidStack targetFluid,
                                                 List<CastingInfo> result, Set<ResourceLocation> seenOutputIds) {
        try {
            List<FluidStack> recipeFluids = recipe.getFluids();
            if (recipeFluids == null || recipeFluids.isEmpty()) return;

            FluidStack recipeFluid = recipeFluids.get(0);
            if (!RecipeReflection.matchesFluid(recipeFluid, targetFluid)) return;

            ItemStack output = recipe.getOutput();
            if (output == null || output.isEmpty()) return;

            ResourceLocation outputId = output.getItem().getRegistryName();
            if (outputId == null) return;
            if (!seenOutputIds.add(outputId)) return;

            boolean requiresCast = recipe.hasCast();
            int amount = recipeFluid.getAmount();
            if (amount <= 0) amount = MaterialCastingCost.MB_PER_COST;
            result.add(new CastingInfo(output.copy(), requiresCast, amount));
        } catch (Exception ignored) {}
    }

    /**
     * {@code MaterialCastingRecipe} 是"任意材料 → 该材料做的部件"的通用配方，
     * 匹配逻辑：
     *   1. 从 result 字段拿输出部件（{@link IMaterialItem}）
     *   2. 用 {@link MaterialCompatibility#canUseMaterial} 判断该部件能否用目标材料
     */
    private static void processMaterialCastingRecipe(MaterialCastingRecipe recipe, FluidStack targetFluid,
                                                     List<CastingInfo> result, Set<ResourceLocation> seenOutputIds) {
        try {
            ItemStack output = RecipeReflection.tryGetOutput(recipe);
            if (output == null || output.isEmpty()) return;

            Item item = output.getItem();
            if (!(item instanceof IMaterialItem)) return;

            ResourceLocation outputId = item.getRegistryName();
            if (outputId == null) return;

            MaterialId targetMat = MaterialResolver.resolve(targetFluid.getFluid());
            if (targetMat == null) return;

            if (!MaterialCompatibility.canUseMaterial((IMaterialItem) item, targetMat)) return;

            int amount = MaterialCastingCost.getAmount(recipe);

            if (!seenOutputIds.add(outputId)) return;
            result.add(new CastingInfo(output.copy(), true, amount));
        } catch (Exception ignored) {}
    }

    private static void processDisplayableCastingRecipe(IDisplayableCastingRecipe recipe, FluidStack targetFluid,
                                                        List<CastingInfo> result, Set<ResourceLocation> seenOutputIds) {
        try {
            FluidStack recipeFluid = RecipeReflection.getDisplayableCastingFluid(recipe);
            if (recipeFluid == null || recipeFluid.isEmpty()) return;
            if (!RecipeReflection.matchesFluid(recipeFluid, targetFluid)) return;

            ItemStack output = RecipeReflection.getDisplayableCastingOutput(recipe);
            if (output == null || output.isEmpty()) return;

            ResourceLocation outputId = output.getItem().getRegistryName();
            if (outputId == null) return;
            if (!seenOutputIds.add(outputId)) return;

            boolean requiresCast = RecipeReflection.getDisplayableCastingHasCast(recipe);
            int amount = recipeFluid.getAmount();
            if (amount <= 0) amount = MaterialCastingCost.MB_PER_COST;
            result.add(new CastingInfo(output.copy(), requiresCast, amount));
        } catch (Exception ignored) {}
    }
}