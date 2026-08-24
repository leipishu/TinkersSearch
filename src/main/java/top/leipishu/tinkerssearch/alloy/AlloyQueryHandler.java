package top.leipishu.tinkerssearch.alloy;

import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class AlloyQueryHandler {

    private static AlloyQueryHandler instance;
    private List<FluidStack> allMaterials = new ArrayList<>();
    private List<FluidStack> filteredMaterials = new ArrayList<>();
    private FluidStack selectedMaterial = null;
    private List<AlloyResultCalculator.AlloyChainResult> currentResults = new ArrayList<>();
    private String currentSearchTerm = "";
    private boolean isQueryMode = false;

    public static AlloyQueryHandler getInstance() {
        if (instance == null) {
            instance = new AlloyQueryHandler();
        }
        return instance;
    }

    public void performQuery(String searchTerm, List<FluidStack> availableFluids, int currentTemperature) {
        this.currentSearchTerm = searchTerm;
        this.isQueryMode = true;
        this.selectedMaterial = null;
        this.currentResults.clear();

        allMaterials = TinkersAlloyReader.getAllSmelteryFluids();
        filteredMaterials = filterMaterials(allMaterials, searchTerm);
    }

    private List<FluidStack> filterMaterials(List<FluidStack> materials, String searchTerm) {
        if (searchTerm == null || searchTerm.isEmpty()) {
            return new ArrayList<>(materials);
        }

        List<FluidStack> result = new ArrayList<>();
        String lowerSearch = searchTerm.toLowerCase();

        for (FluidStack fs : materials) {
            String name = fs.getDisplayName().getString().toLowerCase()
                    .replace("molten ", "");
            if (name.contains(lowerSearch)) {
                result.add(fs);
            }
        }
        return result;
    }

    public void selectMaterial(FluidStack material, List<FluidStack> availableFluids, int currentTemperature) {
        this.selectedMaterial = material;
        this.currentResults.clear();

        // ===== 将搜索的材料视为足量（加入可用流体列表） =====
        List<FluidStack> simulatedFluids = new ArrayList<>(availableFluids);
        // 检查是否已存在，如果存在则更新为足量（10000mB），否则添加
        boolean found = false;
        for (FluidStack fs : simulatedFluids) {
            if (fs.getFluid().getRegistryName().equals(material.getFluid().getRegistryName())) {
                fs.setAmount(10000); // 设为足量
                found = true;
                break;
            }
        }
        if (!found) {
            FluidStack copy = material.copy();
            copy.setAmount(10000);
            simulatedFluids.add(copy);
        }

        List<AlloyRecipeData> allRecipes = TinkersAlloyReader.getAlloyRecipes();
        List<AlloyResultCalculator.AlloyChainResult> results =
                AlloyResultCalculator.calculateAlloyChain(material, simulatedFluids, currentTemperature, allRecipes);

        results.sort((a, b) -> {
            if (a.isFullyFeasible() && !b.isFullyFeasible()) return -1;
            if (!a.isFullyFeasible() && b.isFullyFeasible()) return 1;
            if (a.getFeasibility().isTemperatureOk() && !b.getFeasibility().isTemperatureOk()) return -1;
            if (!a.getFeasibility().isTemperatureOk() && b.getFeasibility().isTemperatureOk()) return 1;
            return 0;
        });

        this.currentResults = results;
    }

    public void backToMaterials() {
        this.selectedMaterial = null;
        this.currentResults.clear();
    }

    public void exitQueryMode() {
        this.isQueryMode = false;
        this.selectedMaterial = null;
        this.currentResults.clear();
        this.filteredMaterials.clear();
        this.allMaterials.clear();
        this.currentSearchTerm = "";
    }

    public boolean isQueryMode() { return isQueryMode; }
    public List<FluidStack> getFilteredMaterials() { return filteredMaterials; }
    public FluidStack getSelectedMaterial() { return selectedMaterial; }
    public List<AlloyResultCalculator.AlloyChainResult> getCurrentResults() { return currentResults; }
    public String getCurrentSearchTerm() { return currentSearchTerm; }
    public boolean hasResults() { return !currentResults.isEmpty(); }
    public boolean hasMaterials() { return !filteredMaterials.isEmpty(); }
}