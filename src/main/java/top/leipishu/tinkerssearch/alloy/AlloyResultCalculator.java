package top.leipishu.tinkerssearch.alloy;

import net.minecraftforge.fluids.FluidStack;

import java.util.*;

public class AlloyResultCalculator {

    private static final int MAX_CHAIN_DEPTH = 3;

    public static List<AlloyChainResult> calculateAlloyChain(
            FluidStack selectedFluid,
            List<FluidStack> availableFluids,
            int currentTemperature,
            List<AlloyRecipeData> allRecipes) {

        List<AlloyChainResult> results = new ArrayList<>();

        for (AlloyRecipeData recipe : allRecipes) {
            for (AlloyRecipeData.FluidIngredientData input : recipe.getInputs()) {
                FluidStack inputFluid = input.getFluid();
                if (inputFluid.getFluid().getRegistryName().equals(selectedFluid.getFluid().getRegistryName())) {
                    AlloyChainResult chain = buildChain(recipe, availableFluids, currentTemperature, allRecipes, 0, selectedFluid);
                    if (chain != null) {
                        results.add(chain);
                    }
                    break;
                }
            }
        }

        return results;
    }

    private static AlloyChainResult buildChain(
            AlloyRecipeData currentRecipe,
            List<FluidStack> availableFluids,
            int currentTemperature,
            List<AlloyRecipeData> allRecipes,
            int depth,
            FluidStack selectedFluid) {

        if (depth > MAX_CHAIN_DEPTH) {
            return null;
        }

        AlloyRecipeData.AlloyFeasibility feasibility =
                currentRecipe.checkFeasibility(availableFluids, currentTemperature, selectedFluid);

        if (!feasibility.isFeasible()) {
            return new AlloyChainResult(currentRecipe, feasibility, null);
        }

        FluidStack result = currentRecipe.getResult();
        List<AlloyRecipeData> childRecipes = findChildRecipes(result, allRecipes);

        AlloyChainResult childResult = null;
        if (!childRecipes.isEmpty()) {
            for (AlloyRecipeData child : childRecipes) {
                AlloyChainResult childChain = buildChain(child, availableFluids, currentTemperature, allRecipes, depth + 1, selectedFluid);
                if (childChain != null) {
                    childResult = childChain;
                    break;
                }
            }
        }

        return new AlloyChainResult(currentRecipe, feasibility, childResult);
    }

    private static List<AlloyRecipeData> findChildRecipes(FluidStack product, List<AlloyRecipeData> allRecipes) {
        List<AlloyRecipeData> children = new ArrayList<>();
        for (AlloyRecipeData recipe : allRecipes) {
            for (AlloyRecipeData.FluidIngredientData input : recipe.getInputs()) {
                FluidStack inputFluid = input.getFluid();
                if (inputFluid.getFluid().getRegistryName().equals(product.getFluid().getRegistryName())) {
                    children.add(recipe);
                    break;
                }
            }
        }
        return children;
    }

    public static class AlloyChainResult {
        private final AlloyRecipeData recipe;
        private final AlloyRecipeData.AlloyFeasibility feasibility;
        private final AlloyChainResult next;

        public AlloyChainResult(AlloyRecipeData recipe,
                                AlloyRecipeData.AlloyFeasibility feasibility,
                                AlloyChainResult next) {
            this.recipe = recipe;
            this.feasibility = feasibility;
            this.next = next;
        }

        public AlloyRecipeData getRecipe() { return recipe; }
        public AlloyRecipeData.AlloyFeasibility getFeasibility() { return feasibility; }
        public AlloyChainResult getNext() { return next; }

        public String formatChain() {
            StringBuilder sb = new StringBuilder();
            sb.append(formatSingle(recipe));
            if (next != null) {
                sb.append(" → ");
                sb.append(next.formatChain());
            }
            return sb.toString();
        }

        private String formatSingle(AlloyRecipeData recipe) {
            StringBuilder sb = new StringBuilder();
            List<AlloyRecipeData.FluidIngredientData> inputs = recipe.getInputs();
            for (int i = 0; i < inputs.size(); i++) {
                if (i > 0) sb.append(" + ");
                FluidStack fs = inputs.get(i).getFluid();
                // ===== 统一移除 "Molten " 和 "熔融" 前缀 =====
                String name = fs.getDisplayName().getString()
                        .replace("Molten ", "")
                        .replace("熔融", "");
                sb.append(name);
            }
            sb.append(" = ");
            String resultName = recipe.getResult().getDisplayName().getString()
                    .replace("Molten ", "")
                    .replace("熔融", "");
            sb.append(resultName);
            return sb.toString();
        }

        public boolean isFullyFeasible() {
            if (!feasibility.isFeasible()) return false;
            if (next != null) {
                return next.isFullyFeasible();
            }
            return true;
        }
    }
}