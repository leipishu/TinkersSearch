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

    // ===== 温度缓存 =====
    private int lastKnownTemperature = 0;
    private long lastTemperatureUpdate = 0;
    private static final long TEMPERATURE_CACHE_DURATION = 200;

    public static AlloyQueryHandler getInstance() {
        if (instance == null) {
            instance = new AlloyQueryHandler();
        }
        return instance;
    }

    // ===== 温度刷新方法 =====
    public void refreshTemperature(int currentTemperature) {
        this.lastKnownTemperature = currentTemperature;
        this.lastTemperatureUpdate = System.currentTimeMillis();
    }

    public int getLastKnownTemperature() {
        return lastKnownTemperature;
    }

    public void performQuery(String searchTerm, List<FluidStack> availableFluids, int currentTemperature) {
        this.currentSearchTerm = searchTerm;
        this.isQueryMode = true;
        this.selectedMaterial = null;
        this.currentResults.clear();
        this.lastKnownTemperature = currentTemperature;

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

    // ===== 无参版本：使用缓存温度 =====
    public void selectMaterial(FluidStack material, List<FluidStack> availableFluids) {
        selectMaterial(material, availableFluids, lastKnownTemperature);
    }

    public void selectMaterial(FluidStack material, List<FluidStack> availableFluids, int currentTemperature) {
        this.selectedMaterial = material;
        this.currentResults.clear();
        this.lastKnownTemperature = currentTemperature;

        // ===== 将搜索的材料视为足量（加入可用流体列表） =====
        List<FluidStack> simulatedFluids = new ArrayList<>(availableFluids);
        boolean found = false;
        for (FluidStack fs : simulatedFluids) {
            if (fs.getFluid().getRegistryName().equals(material.getFluid().getRegistryName())) {
                fs.setAmount(10000);
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
        this.lastKnownTemperature = 0;
    }

    public boolean isQueryMode() { return isQueryMode; }
    public List<FluidStack> getFilteredMaterials() { return filteredMaterials; }
    public FluidStack getSelectedMaterial() { return selectedMaterial; }
    public List<AlloyResultCalculator.AlloyChainResult> getCurrentResults() { return currentResults; }
    public String getCurrentSearchTerm() { return currentSearchTerm; }
    public boolean hasResults() { return !currentResults.isEmpty(); }
    public boolean hasMaterials() { return !filteredMaterials.isEmpty(); }
}