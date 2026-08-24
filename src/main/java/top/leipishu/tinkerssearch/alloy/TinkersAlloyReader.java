package top.leipishu.tinkerssearch.alloy;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;

import java.lang.reflect.Field;
import java.util.*;

public class TinkersAlloyReader {

    private static List<AlloyRecipeData> cachedAlloyRecipes = null;
    private static List<FluidStack> cachedAllMaterials = null;
    private static long cacheTime = 0;
    private static final long CACHE_DURATION = 5000;

    public static List<AlloyRecipeData> getAlloyRecipes() {
        long now = System.currentTimeMillis();
        if (cachedAlloyRecipes != null && (now - cacheTime) < CACHE_DURATION) {
            return cachedAlloyRecipes;
        }

        cachedAlloyRecipes = new ArrayList<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) {
                System.out.println("Tinker's Search: No connection, cannot read alloy recipes");
                return cachedAlloyRecipes;
            }

            RecipeManager recipeManager = mc.getConnection().getRecipeManager();

            Field recipesField = RecipeManager.class.getDeclaredField("recipes");
            recipesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipesMap =
                    (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) recipesField.get(recipeManager);

            int totalAlloyRecipes = 0;
            for (Map.Entry<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> typeEntry : recipesMap.entrySet()) {
                Map<ResourceLocation, Recipe<?>> recipes = typeEntry.getValue();
                for (Map.Entry<ResourceLocation, Recipe<?>> entry : recipes.entrySet()) {
                    Recipe<?> recipe = entry.getValue();
                    if (recipe instanceof AlloyRecipe) {
                        AlloyRecipeData parsed = parseAlloyRecipe((AlloyRecipe) recipe, entry.getKey());
                        if (parsed != null) {
                            cachedAlloyRecipes.add(parsed);
                            totalAlloyRecipes++;
                        }
                    }
                }
            }

            System.out.println("Tinker's Search: Loaded " + totalAlloyRecipes + " alloy recipes");

        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to read alloy recipes: " + e.getMessage());
            e.printStackTrace();
            cachedAlloyRecipes = new ArrayList<>();
        }

        cacheTime = System.currentTimeMillis();
        return cachedAlloyRecipes;
    }

    @SuppressWarnings("unchecked")
    private static AlloyRecipeData parseAlloyRecipe(AlloyRecipe recipe, ResourceLocation id) {
        try {
            List<FluidStack> inputFluids = new ArrayList<>();
            List<Integer> inputAmounts = new ArrayList<>();

            Field inputField = AlloyRecipe.class.getDeclaredField("inputs");
            inputField.setAccessible(true);
            List<FluidIngredient> inputList = (List<FluidIngredient>) inputField.get(recipe);

            for (FluidIngredient ingredient : inputList) {
                // ===== getFluids() 返回 FluidStack[]（数组），不是 List =====
                FluidStack[] fluidArray = ingredient.getFluids().toArray(new FluidStack[0]);
                if (fluidArray != null && fluidArray.length > 0) {
                    FluidStack firstFluid = fluidArray[0];
                    int amount = ingredient.getAmount(firstFluid.getFluid());
                    FluidStack copy = firstFluid.copy();
                    copy.setAmount(amount);
                    inputFluids.add(copy);
                    inputAmounts.add(amount);
                }
            }

            if (inputFluids.isEmpty()) {
                System.err.println("Tinker's Search: Recipe " + id + " has no parseable inputs");
                return null;
            }

            FluidStack output = recipe.getOutput();
            if (output == null || output.isEmpty()) {
                System.err.println("Tinker's Search: Recipe " + id + " has null or empty output");
                return null;
            }

            int temperature = recipe.getTemperature();

            List<AlloyRecipeData.FluidIngredientData> inputData = new ArrayList<>();
            for (int i = 0; i < inputFluids.size(); i++) {
                inputData.add(new AlloyRecipeData.FluidIngredientData(
                        inputFluids.get(i),
                        inputAmounts.get(i)
                ));
            }

            return new AlloyRecipeData(id, inputData, output, temperature);

        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to parse recipe " + id + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static List<FluidStack> getAllSmelteryFluids() {
        long now = System.currentTimeMillis();
        if (cachedAllMaterials != null && (now - cacheTime) < CACHE_DURATION) {
            return cachedAllMaterials;
        }

        Set<ResourceLocation> fluidSet = new HashSet<>();
        List<AlloyRecipeData> recipes = getAlloyRecipes();

        for (AlloyRecipeData recipe : recipes) {
            for (AlloyRecipeData.FluidIngredientData input : recipe.getInputs()) {
                FluidStack fs = input.getFluid();
                if (fs != null && !fs.isEmpty()) {
                    ResourceLocation rl = fs.getFluid().getRegistryName();
                    if (rl != null) {
                        fluidSet.add(rl);
                    }
                }
            }
            FluidStack result = recipe.getResult();
            if (result != null && !result.isEmpty()) {
                ResourceLocation rl = result.getFluid().getRegistryName();
                if (rl != null) {
                    fluidSet.add(rl);
                }
            }
        }

        if (fluidSet.isEmpty()) {
            System.out.println("Tinker's Search: No alloy recipes found, scanning fluids");
            for (Fluid fluid : ForgeRegistries.FLUIDS) {
                ResourceLocation rl = fluid.getRegistryName();
                if (rl != null) {
                    String path = rl.getPath();
                    if (rl.getNamespace().equals("tconstruct") ||
                            path.contains("molten") ||
                            path.contains("liquid")) {
                        fluidSet.add(rl);
                    }
                }
            }
        }

        cachedAllMaterials = new ArrayList<>();
        for (ResourceLocation rl : fluidSet) {
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(rl);
            if (fluid != null) {
                cachedAllMaterials.add(new FluidStack(fluid, 1000));
            }
        }

        System.out.println("Tinker's Search: Found " + cachedAllMaterials.size() + " alloy-relevant fluids");
        return cachedAllMaterials;
    }

    public static void clearCache() {
        cachedAlloyRecipes = null;
        cachedAllMaterials = null;
        cacheTime = 0;
    }
}