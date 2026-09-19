package top.leipishu.tinkerssearch.recipe;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialId;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流体 → {@link MaterialId} 解析。
 *
 * <p>1.20.1 移植要点：
 * <ul>
 *   <li>保留"无条件同步构建"语义——{@code FluidDetailScreen} 在后台线程调用本类，
 *       使用 {@code mc.execute(...)} 会拿到空 map。</li>
 *   <li>失败时返回 {@link #EMPTY_MAP} 而非 null，避免调用方 NPE。</li>
 * </ul>
 */
public class MaterialResolver {

    private static volatile Map<ResourceLocation, ResourceLocation> fluidToMaterial = null;
    private static final Map<ResourceLocation, ResourceLocation> EMPTY_MAP = Collections.emptyMap();

    public static MaterialId resolve(Fluid fluid) {
        ResourceLocation id = resolveAsResourceLocation(fluid);
        return id != null ? new MaterialId(id) : null;
    }

    public static ResourceLocation resolveAsResourceLocation(Fluid fluid) {
        if (fluid == null) return null;
        ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluid);
        if (fluidId == null) return null;

        ResourceLocation fromMap = getMap().get(fluidId);
        if (fromMap != null) return fromMap;

        String path = fluidId.getPath();
        for (String prefix : new String[]{"molten_", "liquid_", "fluid_"}) {
            if (path.startsWith(prefix)) {
                String stripped = path.substring(prefix.length());
                if (!stripped.isEmpty()) {
                    return new ResourceLocation(fluidId.getNamespace(), stripped);
                }
            }
        }
        return null;
    }

    public static void clear() {
        fluidToMaterial = null;
    }

    public static void prewarm() {
        getMap();
    }

    // ============================================================
    // ===== 内部：映射表构建 ======================================
    // ============================================================

    private static Map<ResourceLocation, ResourceLocation> getMap() {
        Map<ResourceLocation, ResourceLocation> local = fluidToMaterial;
        if (local != null) return local;

        synchronized (MaterialResolver.class) {
            if (fluidToMaterial != null) return fluidToMaterial;
            try {
                buildMap();
            } catch (Throwable t) {
                System.err.println("Tinker's Search: MaterialResolver buildMap failed: " + t);
                t.printStackTrace();
            }
        }
        return fluidToMaterial != null ? fluidToMaterial : EMPTY_MAP;
    }

    private static void buildMap() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            // 主菜单 / 初始化阶段 → 不缓存空结果，下次重试
            return;
        }

        Map<ResourceLocation, ResourceLocation> newMap = new HashMap<>();
        RecipeManager recipeManager = mc.getConnection().getRecipeManager();
        int count = 0;
        int recipeScanned = 0;

        try {
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                recipeScanned++;
                String className = recipe.getClass().getName().toLowerCase();
                if (!className.contains("materialfluid")
                        && !className.contains("material_fluid")
                        && !className.contains("fluidmaterial")) continue;

                try {
                    ResourceLocation materialId = RecipeReflection.extractMaterialId(recipe);
                    if (materialId == null) continue;

                    List<FluidStack> fluids = RecipeReflection.extractFluids(recipe);
                    if (fluids == null || fluids.isEmpty()) continue;

                    for (FluidStack fluid : fluids) {
                        if (fluid == null || fluid.isEmpty()) continue;
                        ResourceLocation fid = ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
                        if (fid == null) continue;
                        if (newMap.putIfAbsent(fid, materialId) == null) {
                            count++;
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.println("Tinker's Search: Error building fluid→material map: " + e.getMessage());
            return;
        }

        fluidToMaterial = newMap;
        System.out.println("[Tinker's Search] Built fluid→material map: " + count
                + " entries (scanned " + recipeScanned + " recipes)");
    }
}