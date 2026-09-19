package top.leipishu.tinkerssearch.alloy;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import top.leipishu.tinkerssearch.utils.SearchHelper;

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

    public void forceRefreshTemperature(int currentTemperature) {
        this.lastKnownTemperature = currentTemperature;
        this.lastTemperatureUpdate = System.currentTimeMillis();
        System.out.println("[Tinker's Search] Temperature FORCE updated: " + currentTemperature + "°C");
    }

    public void invalidateTemperatureCache() {
        this.lastKnownTemperature = 0;
        this.lastTemperatureUpdate = 0;
        System.out.println("[Tinker's Search] Temperature cache invalidated");
    }

    public int getLastKnownTemperature() {
        if (lastKnownTemperature <= 0) return 0;
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
            TinkersAlloyReader.forceReload();
            source = TinkersAlloyReader.getAllSmelteryFluids();
        }
        this.allMaterials = (source != null) ? source : new ArrayList<>();
        this.filteredMaterials = filterMaterials(this.allMaterials, searchTerm);
    }

    /**
     * 过滤材料列表。
     *
     * <p>直接复用 {@link SearchHelper#filterFluids}，它已实现：
     * <ul>
     *   <li>显示名匹配（自动剥离 "Molten " / "熔融" 前缀）</li>
     *   <li>注册名 path 匹配</li>
     *   <li>拼音全拼匹配（pinyin4j）</li>
     *   <li>拼音首字母匹配（pinyin4j）</li>
     *   <li>关键词为 null / 空白时返回全集</li>
     * </ul>
     */
    private List<FluidStack> filterMaterials(List<FluidStack> materials, String searchTerm) {
        return SearchHelper.filterFluids(materials, searchTerm);
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

        // ★ 1.19.2：通过 ForgeRegistries 获取注册名
        ResourceLocation targetRl = ForgeRegistries.FLUIDS.getKey(material.getFluid());
        boolean found = false;
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