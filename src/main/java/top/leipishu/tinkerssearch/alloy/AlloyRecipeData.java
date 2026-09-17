package top.leipishu.tinkerssearch.alloy;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class AlloyRecipeData {
    private final ResourceLocation id;
    private final List<FluidIngredientData> inputs;
    private final FluidStack result;
    private final int requiredTemperature;

    public AlloyRecipeData(ResourceLocation id,
                           List<FluidIngredientData> inputs,
                           FluidStack result,
                           int requiredTemperature) {
        this.id = id;
        this.inputs = inputs != null ? inputs : new ArrayList<>();
        this.result = result;
        this.requiredTemperature = requiredTemperature;
    }

    public ResourceLocation getId() { return id; }
    public List<FluidIngredientData> getInputs() { return inputs; }
    public FluidStack getResult() { return result; }
    public int getRequiredTemperature() { return requiredTemperature; }

    public boolean isTemperatureSatisfied(int currentTemperature) {
        return currentTemperature >= requiredTemperature;
    }

    public AlloyFeasibility checkFeasibility(List<FluidStack> availableFluids, int currentTemperature, FluidStack selectedMaterial) {
        List<AlloyFeasibility.MissingFluid> missing = new ArrayList<>();
        List<AlloyFeasibility.SufficientFluid> sufficient = new ArrayList<>();

        // ===== 计算最大可执行次数 =====
        int maxTimes = Integer.MAX_VALUE;

        for (FluidIngredientData input : inputs) {
            FluidStack inputFluid = input.getFluid();
            int needed = input.getAmount();
            int available = 0;
            FluidStack matched = null;

            // ✅ 1.20.1：通过 ForgeRegistries 获取注册名
            ResourceLocation inputRl = ForgeRegistries.FLUIDS.getKey(inputFluid.getFluid());

            for (FluidStack fs : availableFluids) {
                ResourceLocation fsRl = ForgeRegistries.FLUIDS.getKey(fs.getFluid());
                if (fsRl != null && fsRl.equals(inputRl)) {
                    available = fs.getAmount();
                    matched = fs;
                    break;
                }
            }

            // 如果这个流体是搜索的材料，视为足量（无限大）
            boolean isSelected = false;
            if (selectedMaterial != null) {
                ResourceLocation selectedRl = ForgeRegistries.FLUIDS.getKey(selectedMaterial.getFluid());
                if (selectedRl != null && selectedRl.equals(inputRl)) {
                    available = Math.max(available, 10000);
                    isSelected = true;
                }
            }

            if (available >= needed) {
                sufficient.add(new AlloyFeasibility.SufficientFluid(
                        inputFluid, needed, available, matched
                ));

                // 非查询物才参与次数计算
                if (!isSelected) {
                    int times = available / needed;
                    if (times < maxTimes) {
                        maxTimes = times;
                    }
                }
            } else {
                missing.add(new AlloyFeasibility.MissingFluid(
                        inputFluid, needed, available
                ));
                maxTimes = 0;
            }
        }

        boolean temperatureOk = isTemperatureSatisfied(currentTemperature);
        boolean feasible = missing.isEmpty() && temperatureOk;

        return new AlloyFeasibility(
                this,
                feasible,
                missing,
                sufficient,
                temperatureOk,
                requiredTemperature,
                currentTemperature,
                maxTimes == Integer.MAX_VALUE ? 9999 : maxTimes
        );
    }

    /**
     * 流体成分数据 - 不依赖匠魂的 FluidIngredient
     */
    public static class FluidIngredientData {
        private final FluidStack fluid;
        private final int amount;

        public FluidIngredientData(FluidStack fluid, int amount) {
            this.fluid = fluid;
            this.amount = amount;
        }

        public FluidStack getFluid() { return fluid; }
        public int getAmount() { return amount; }
    }

    public static class AlloyFeasibility {
        private final AlloyRecipeData recipe;
        private final boolean feasible;
        private final List<MissingFluid> missingFluids;
        private final List<SufficientFluid> sufficientFluids;
        private final boolean temperatureOk;
        private final int requiredTemp;
        private final int currentTemp;

        // ===== 新增：最大可执行次数 =====
        private final int maxTimes;

        public AlloyFeasibility(AlloyRecipeData recipe, boolean feasible,
                                List<MissingFluid> missing, List<SufficientFluid> sufficient,
                                boolean temperatureOk, int requiredTemp, int currentTemp,
                                int maxTimes) {
            this.recipe = recipe;
            this.feasible = feasible;
            this.missingFluids = missing;
            this.sufficientFluids = sufficient;
            this.temperatureOk = temperatureOk;
            this.requiredTemp = requiredTemp;
            this.currentTemp = currentTemp;
            this.maxTimes = maxTimes;
        }

        public boolean isFeasible() { return feasible; }
        public List<MissingFluid> getMissingFluids() { return missingFluids; }
        public List<SufficientFluid> getSufficientFluids() { return sufficientFluids; }
        public boolean isTemperatureOk() { return temperatureOk; }
        public int getRequiredTemp() { return requiredTemp; }
        public int getCurrentTemp() { return currentTemp; }
        public AlloyRecipeData getRecipe() { return recipe; }

        // ===== 新增：获取最大可执行次数 =====
        public int getMaxTimes() { return maxTimes; }

        public static class MissingFluid {
            public final FluidStack fluid;
            public final int needed;
            public final int available;
            public MissingFluid(FluidStack fluid, int needed, int available) {
                this.fluid = fluid;
                this.needed = needed;
                this.available = available;
            }
        }

        public static class SufficientFluid {
            public final FluidStack fluid;
            public final int needed;
            public final int available;
            public final FluidStack actualFluid;
            public SufficientFluid(FluidStack fluid, int needed, int available, FluidStack actual) {
                this.fluid = fluid;
                this.needed = needed;
                this.available = available;
                this.actualFluid = actual;
            }
        }
    }
}
