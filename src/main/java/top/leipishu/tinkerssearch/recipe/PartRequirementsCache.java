package top.leipishu.tinkerssearch.recipe;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.recipe.casting.ItemCastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.MaterialCastingRecipe;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

import java.util.HashMap;
import java.util.Map;

/**
 * 部件 → 需求量（mB）缓存。
 *
 * <p>从多个来源推断每个部件浇筑一次需要多少流体：
 * <ol>
 *   <li>{@code part_builder} 配方的 cost 字段</li>
 *   <li>{@code MaterialCastingRecipe} 的 itemCost</li>
 *   <li>普通 {@code ItemCastingRecipe}，从 {@code _cast} 后缀反推</li>
 *   <li>配方 ID 反推兜底</li>
 * </ol>
 *
 * <p>结果只在内存中缓存，配方重载或重启游戏时清空。
 */
public class PartRequirementsCache {

    private static Map<ResourceLocation, Integer> cache = null;

    /** 清空缓存。 */
    public static void clear() {
        cache = null;
    }

    /** 预热缓存。 */
    public static void prewarm() {
        getMap();
    }

    /** 返回部件需求量（mB），无记录时返回 -1。 */
    public static int get(ResourceLocation partId) {
        if (partId == null) return -1;
        Integer v = getMap().get(partId);
        return v != null ? v : -1;
    }

    // ============================================================
    // ===== 内部实现 =============================================
    // ============================================================

    private static Map<ResourceLocation, Integer> getMap() {
        if (cache != null) return cache;

        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            build();
        } else {
            if (cache == null) cache = new HashMap<>();
            mc.execute(PartRequirementsCache::build);
        }
        return cache;
    }

    private static void build() {
        Map<ResourceLocation, Integer> newMap = new HashMap<>();

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            cache = newMap;
            return;
        }

        RecipeManager recipeManager = mc.getConnection().getRecipeManager();
        int builderCount = 0;
        int materialCastingCount = 0;
        int materialCastingSkipped = 0;
        int idFallbackCount = 0;

        try {
            // ===== 阶段1：part_builder =====
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                Integer cost = RecipeReflection.tryGetPartBuilderCost(recipe);
                if (cost == null || cost <= 0) continue;

                ItemStack result = RecipeReflection.tryGetPartBuilderResult(recipe);
                if (result.isEmpty()) continue;

                Item item = result.getItem();
                if (!(item instanceof IMaterialItem)) continue;

                ResourceLocation partId = item.getRegistryName();
                if (partId == null) continue;

                int amount = cost * MaterialCastingCost.MB_PER_COST;
                if (newMap.putIfAbsent(partId, amount) == null) {
                    builderCount++;
                }
            }

            // ===== 阶段2：MaterialCastingRecipe =====
            int materialCastingTotal = 0;
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                if (!(recipe instanceof MaterialCastingRecipe)) continue;
                materialCastingTotal++;

                ItemStack output = RecipeReflection.tryGetOutput(recipe);
                if (output == null || output.isEmpty()) continue;

                ResourceLocation partId = output.getItem().getRegistryName();
                if (partId == null) continue;

                if (newMap.containsKey(partId)) {
                    materialCastingSkipped++;
                    continue;
                }

                int itemCost = MaterialCastingCost.getItemCost(recipe);
                int amount;
                if (itemCost > 0) {
                    amount = itemCost * MaterialCastingCost.MB_PER_COST;
                } else {
                    FluidStack fluid = RecipeReflection.getCastingFluid(recipe);
                    if (fluid != null && !fluid.isEmpty() && fluid.getAmount() > 0) {
                        amount = fluid.getAmount();
                    } else {
                        amount = MaterialCastingCost.MB_PER_COST;
                    }
                }

                newMap.put(partId, amount);
                materialCastingCount++;
            }

            // ===== 阶段3：普通 ItemCastingRecipe（_cast 后缀反推）=====
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                if (recipe instanceof MaterialCastingRecipe) continue;
                if (!(recipe instanceof ItemCastingRecipe)) continue;

                ItemStack output = RecipeReflection.tryGetOutput(recipe);
                if (output == null || output.isEmpty()) continue;

                ResourceLocation outputId = output.getItem().getRegistryName();
                if (outputId == null) continue;

                String path = outputId.getPath();
                if (!path.endsWith("_cast")) continue;

                String partPath = path.substring(0, path.length() - "_cast".length());
                ResourceLocation partId = new ResourceLocation(outputId.getNamespace(), partPath);

                if (newMap.containsKey(partId)) continue;

                FluidStack fluid = RecipeReflection.getCastingFluid(recipe);
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

                newMap.put(partId, MaterialCastingCost.MB_PER_COST);
                idFallbackCount++;
            }
        } catch (Exception e) {
            System.err.println("Tinker's Search: Error building part req cache: " + e.getMessage());
        }

        cache = newMap;
        System.out.println("[Tinker's Search] Built part req cache: " + cache.size()
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
}