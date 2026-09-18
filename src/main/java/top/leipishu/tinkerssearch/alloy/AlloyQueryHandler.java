package top.leipishu.tinkerssearch.alloy;

import net.minecraftforge.fluids.FluidStack;
import top.leipishu.tinkerssearch.utils.SearchHelper;   // ★ 新增

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