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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PartRequirementsCache {

    private static volatile Map<ResourceLocation, Integer> cache = null;
    private static final Map<ResourceLocation, Integer> EMPTY_MAP = Collections.emptyMap();

    public static void clear() {
        cache = null;
    }

    public static void prewarm() {
        getMap();
    }

    public static int get(ResourceLocation partId) {
        if (partId == null) return -1;
        Integer v = getMap().get(partId);
        return v != null ? v : -1;
    }

    private static Map<ResourceLocation, Integer> getMap() {
        Map<ResourceLocation, Integer> local = cache;
        if (local != null) return local;

        synchronized (PartRequirementsCache.class) {
            if (cache != null) return cache;
            try {
                build();
            } catch (Throwable t) {
                System.err.println("Tinker's Search: PartRequirementsCache build failed: " + t);
                t.printStackTrace();
            }
        }
        return cache != null ? cache : EMPTY_MAP;
    }

    private static void build() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        Map<ResourceLocation, Integer> newMap = new HashMap<>();
        RecipeManager recipeManager = mc.getConnection().getRecipeManager();
        int builderCount = 0;
        int materialCastingCount = 0;
        int materialCastingSkipped = 0;
        int idFallbackCount = 0;

        try {
            // 阶段1：part_builder
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                Integer cost = RecipeReflection.tryGetPartBuilderCost(recipe);
                if (cost == null || cost <= 0) continue;

                ItemStack result = RecipeReflection.tryGetPartBuilderResult(recipe);
                if (result.isEmpty()) continue;

                Item item = result.getItem();
                if (!(item instanceof IMaterialItem)) continue;

                ResourceLocation partId = ForgeRegistries.ITEMS.getKey(item);
                if (partId == null) continue;

                int amount = cost * MaterialCastingCost.MB_PER_COST;
                if (newMap.putIfAbsent(partId, amount) == null) builderCount++;
            }

            // 阶段2：MaterialCastingRecipe
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                if (!(recipe instanceof MaterialCastingRecipe)) continue;

                ItemStack output = RecipeReflection.tryGetOutput(recipe);
                if (output == null || output.isEmpty()) continue;

                ResourceLocation partId = ForgeRegistries.ITEMS.getKey(output.getItem());
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

            // 阶段3：ItemCastingRecipe（_cast 后缀反推）
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                if (recipe instanceof MaterialCastingRecipe) continue;
                if (!(recipe instanceof ItemCastingRecipe)) continue;

                ItemStack output = RecipeReflection.tryGetOutput(recipe);
                if (output == null || output.isEmpty()) continue;

                ResourceLocation outputId = ForgeRegistries.ITEMS.getKey(output.getItem());
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

            // 阶段4：配方 ID 反推兜底
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
            return;
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
        if (path.contains("parts/building/")) return last;
        return null;
    }
}