package top.leipishu.tinkerssearch.alloy;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

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

    private int lastKnownTemperature = 0;
    private long lastTemperatureUpdate = 0;
    private static final long TEMPERATURE_CACHE_DURATION = 200;

    public static AlloyQueryHandler getInstance() {
        if (instance == null) {
            instance = new AlloyQueryHandler();
        }
        return instance;
    }

    public void refreshTemperature(int currentTemperature) {
        this.lastKnownTemperature = currentTemperature;
        this.lastTemperatureUpdate = System.currentTimeMillis();
        System.out.println("[Tinker's Search] Temperature updated: " + currentTemperature + "°C");
    }

    /**
     * 强制刷新温度（打开面板时使用）
     */
    public void forceRefreshTemperature(int currentTemperature) {
        this.lastKnownTemperature = currentTemperature;
        this.lastTemperatureUpdate = System.currentTimeMillis();
        System.out.println("[Tinker's Search] Temperature FORCE updated: " + currentTemperature + "°C");
    }

    /**
     * 清除温度缓存（更换燃料或刷新时调用）
     */
    public void invalidateTemperatureCache() {
        this.lastKnownTemperature = 0;
        this.lastTemperatureUpdate = 0;
        System.out.println("[Tinker's Search] Temperature cache invalidated");
    }

    public int getLastKnownTemperature() {
        if (lastKnownTemperature <= 0) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lastTemperatureUpdate;
        if (elapsed > TEMPERATURE_CACHE_DURATION) {
            System.out.println("[Tinker's Search] Temperature cache expired (" + elapsed + "ms)");
            return 0;
        }
        return lastKnownTemperature;
    }

    public boolean hasValidTemperature() {
        long elapsed = System.currentTimeMillis() - lastTemperatureUpdate;
        return elapsed <= TEMPERATURE_CACHE_DURATION && lastKnownTemperature > 0;
    }

    public void performQuery(String searchTerm, List<FluidStack> availableFluids, int currentTemperature) {
        this.currentSearchTerm = searchTerm;
        this.isQueryMode = true;
        this.selectedMaterial = null;
        this.currentResults.clear();
        this.lastKnownTemperature = currentTemperature;

        List<FluidStack> source = TinkersAlloyReader.getAllSmelteryFluids();
        if (source == null || source.isEmpty()) {
            // 兜底：强制重载一次
            TinkersAlloyReader.forceReload();
            source = TinkersAlloyReader.getAllSmelteryFluids();
        }
        this.allMaterials = (source != null) ? source : new ArrayList<>();
        this.filteredMaterials = filterMaterials(this.allMaterials, searchTerm);
    }

    private List<FluidStack> filterMaterials(List<FluidStack> materials, String searchTerm) {
        if (searchTerm == null || searchTerm.isEmpty()) {
            return new ArrayList<>(materials);
        }

        List<FluidStack> result = new ArrayList<>();
        String lowerSearch = searchTerm.toLowerCase();

        for (FluidStack fs : materials) {
            String displayName = fs.getDisplayName().getString()
                    .toLowerCase()
                    .replace("molten ", "")
                    .replace("熔融", "");

            // ✅ 1.20.1：通过 ForgeRegistries 获取注册名
            ResourceLocation rl = ForgeRegistries.FLUIDS.getKey(fs.getFluid());
            String registryName = rl != null ? rl.getPath().toLowerCase() : "";

            if (displayName.contains(lowerSearch) || registryName.contains(lowerSearch)) {
                result.add(fs);
            }
        }
        return result;
    }

    public void selectMaterial(FluidStack material, List<FluidStack> availableFluids) {
        selectMaterial(material, availableFluids, lastKnownTemperature);
    }

    public void selectMaterial(FluidStack material, List<FluidStack> availableFluids, int currentTemperature) {
        this.selectedMaterial = material;
        this.currentResults.clear();
        this.lastKnownTemperature = currentTemperature;

        List<FluidStack> simulatedFluids = new ArrayList<>();
        for (FluidStack fs : availableFluids) {
            FluidStack copy = fs.copy();
            simulatedFluids.add(copy);
        }

        boolean found = false;
        // ✅ 1.20.1：通过 ForgeRegistries 获取注册名
        ResourceLocation targetRl = ForgeRegistries.FLUIDS.getKey(material.getFluid());
        for (FluidStack fs : simulatedFluids) {
            ResourceLocation fsRl = ForgeRegistries.FLUIDS.getKey(fs.getFluid());
            if (fsRl != null && fsRl.equals(targetRl)) {
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
        this.lastTemperatureUpdate = 0;
    }

    public boolean isQueryMode() { return isQueryMode; }
    public List<FluidStack> getFilteredMaterials() { return filteredMaterials; }
    public FluidStack getSelectedMaterial() { return selectedMaterial; }
    public List<AlloyResultCalculator.AlloyChainResult> getCurrentResults() { return currentResults; }
    public String getCurrentSearchTerm() { return currentSearchTerm; }
    public boolean hasResults() { return !currentResults.isEmpty(); }
    public boolean hasMaterials() { return !filteredMaterials.isEmpty(); }
}
