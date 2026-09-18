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
 * <p><b>1.19.2 修复</b>：
 * <ul>
 *   <li>{@link #processItemCastingRecipe} 检查配方的所有流体，而不是只看第一个</li>
 *   <li>对外暴露的三条路径（主/direct/any）都保持可用</li>
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

        System.out.println("[Tinker's Search] getCastingRecipesForFluid(" + ForgeRegistries.FLUIDS.getKey(fluidStack.getFluid()) + "):"
                + " MaterialCasting=" + materialCastingMatched + "/" + materialCastingTotal
                + ", ItemCasting=" + itemCastingMatched + "/" + itemCastingTotal
                + ", Displayable=" + displayableMatched + "/" + displayableTotal
                + " → total " + result.size() + " outputs");

        return result;
    }

    public static boolean hasCastingRecipes(FluidStack fluidStack) {
        return !getCastingRecipesForFluid(fluidStack).isEmpty();
    }

    public static ResourceLocation getMaterialIdForFluid(Fluid fluid) {
        return MaterialResolver.resolveAsResourceLocation(fluid);
    }

    public static void invalidateCache() {
        PartRequirementsCache.clear();
        MaterialResolver.clear();
        PartPropertyHelper.clearMaterialCache();
        System.out.println("[Tinker's Search] All caches invalidated");
    }

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

    /**
     * <p><b>1.19.2 修复</b>：{@code ItemCastingRecipe.getFluids()} 返回配方可接受的
     * <b>所有</b>流体，不再假设目标流体一定在第一位。
     */
    private static void processItemCastingRecipe(ItemCastingRecipe recipe, FluidStack targetFluid,
                                                 List<CastingInfo> result, Set<ResourceLocation> seenOutputIds) {
        try {
            List<FluidStack> recipeFluids = recipe.getFluids();
            if (recipeFluids == null || recipeFluids.isEmpty()) return;

            // 遍历所有流体，找到匹配的那个
            int matchedAmount = -1;
            for (FluidStack recipeFluid : recipeFluids) {
                if (RecipeReflection.matchesFluid(recipeFluid, targetFluid)) {
                    matchedAmount = recipeFluid.getAmount();
                    break;
                }
            }
            if (matchedAmount < 0) return;

            ItemStack output = recipe.getOutput();
            if (output == null || output.isEmpty()) return;

            ResourceLocation outputId = ForgeRegistries.ITEMS.getKey(output.getItem());
            if (outputId == null) return;
            if (!seenOutputIds.add(outputId)) return;

            boolean requiresCast = recipe.hasCast();
            int amount = matchedAmount;
            if (amount <= 0) amount = MaterialCastingCost.MB_PER_COST;
            result.add(new CastingInfo(output.copy(), requiresCast, amount));
        } catch (Exception ignored) {}
    }

    public static List<CastingInfo> getDirectPartCastingRecipes(FluidStack fluidStack) {
        List<CastingInfo> result = new ArrayList<>();
        if (fluidStack == null || fluidStack.isEmpty()) return result;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return result;

        RecipeManager recipeManager = mc.getConnection().getRecipeManager();
        Set<ResourceLocation> seenOutputIds = new HashSet<>();

        try {
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                if (!(recipe instanceof MaterialCastingRecipe)) continue;
                processDirectPartCasting((MaterialCastingRecipe) recipe, fluidStack, result, seenOutputIds);
            }
        } catch (Exception e) {
            System.err.println("Tinker's Search: Error loading direct part recipes: " + e.getMessage());
        }

        return result;
    }

    private static void processDirectPartCasting(MaterialCastingRecipe recipe, FluidStack targetFluid,
                                                 List<CastingInfo> result, Set<ResourceLocation> seenOutputIds) {
        try {
            ItemStack output = RecipeReflection.tryGetOutput(recipe);
            if (output == null || output.isEmpty()) return;
            if (!(output.getItem() instanceof IMaterialItem)) return;

            ResourceLocation outputId = ForgeRegistries.ITEMS.getKey(output.getItem());
            if (outputId == null) return;

            Boolean accepted = MaterialCompatibility.recipeAcceptsFluid(recipe, targetFluid);
            if (accepted == null || !accepted) return;

            int amount = MaterialCastingCost.getAmount(recipe);
            if (!seenOutputIds.add(outputId)) return;

            result.add(new CastingInfo(output.copy(), true, amount));
        } catch (Exception ignored) {}
    }

    public static List<CastingInfo> getAnyPartCastingRecipes(FluidStack fluidStack) {
        List<CastingInfo> result = new ArrayList<>();
        if (fluidStack == null || fluidStack.isEmpty()) return result;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return result;

        RecipeManager recipeManager = mc.getConnection().getRecipeManager();
        Set<ResourceLocation> seenOutputIds = new HashSet<>();

        try {
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                try {
                    ItemStack output = RecipeReflection.tryGetOutput(recipe);
                    if (output == null || output.isEmpty()) continue;
                    if (!(output.getItem() instanceof IMaterialItem)) continue;

                    ResourceLocation outputId = ForgeRegistries.ITEMS.getKey(output.getItem());
                    if (outputId == null) continue;

                    List<FluidStack> recipeFluids = RecipeReflection.extractFluids(recipe);
                    if (recipeFluids == null || recipeFluids.isEmpty()) continue;

                    boolean fluidMatched = false;
                    for (FluidStack f : recipeFluids) {
                        if (RecipeReflection.matchesFluid(f, fluidStack)) {
                            fluidMatched = true;
                            break;
                        }
                    }
                    if (!fluidMatched) continue;

                    if (!seenOutputIds.add(outputId)) continue;

                    int amount = MaterialCastingCost.MB_PER_COST;
                    for (FluidStack f : recipeFluids) {
                        if (RecipeReflection.matchesFluid(f, fluidStack) && f.getAmount() > 0) {
                            amount = f.getAmount();
                            break;
                        }
                    }

                    result.add(new CastingInfo(output.copy(), false, amount));
                } catch (Throwable ignored) {}
            }
        } catch (Exception e) {
            System.err.println("Tinker's Search: Error loading any part recipes: " + e.getMessage());
        }

        return result;
    }

    private static void processMaterialCastingRecipe(MaterialCastingRecipe recipe, FluidStack targetFluid,
                                                     List<CastingInfo> result, Set<ResourceLocation> seenOutputIds) {
        try {
            ItemStack output = RecipeReflection.tryGetOutput(recipe);
            if (output == null || output.isEmpty()) return;

            Item item = output.getItem();
            if (!(item instanceof IMaterialItem)) return;

            ResourceLocation outputId = ForgeRegistries.ITEMS.getKey(item);
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

            ResourceLocation outputId = ForgeRegistries.ITEMS.getKey(output.getItem());
            if (outputId == null) return;
            if (!seenOutputIds.add(outputId)) return;

            boolean requiresCast = RecipeReflection.getDisplayableCastingHasCast(recipe);
            int amount = recipeFluid.getAmount();
            if (amount <= 0) amount = MaterialCastingCost.MB_PER_COST;
            result.add(new CastingInfo(output.copy(), requiresCast, amount));
        } catch (Exception ignored) {}
    }
}