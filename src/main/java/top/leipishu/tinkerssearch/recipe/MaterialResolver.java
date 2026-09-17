package top.leipishu.tinkerssearch.recipe;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.definition.MaterialId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流体 → {@link MaterialId} 解析。
 *
 * <p>两条路径：
 * <ol>
 *   <li>{@code fluidToMaterial} 映射表（由 {@code MaterialFluidRecipe} 构建）</li>
 *   <li>前缀剥离：{@code molten_} / {@code liquid_} / {@code fluid_}</li>
 * </ol>
 *
 * <p>不依赖 {@code MaterialRegistry}，KubeJS 等外部改动导致材料注册残缺时
 * 仍能正确解析。
 */
public class MaterialResolver {

    private static Map<ResourceLocation, ResourceLocation> fluidToMaterial = null;

    /** 解析为 {@link MaterialId}；无映射时返回 null。 */
    public static MaterialId resolve(Fluid fluid) {
        ResourceLocation id = resolveAsResourceLocation(fluid);
        return id != null ? new MaterialId(id) : null;
    }

    /** 解析为 {@link ResourceLocation}；无映射时返回 null。 */
    public static ResourceLocation resolveAsResourceLocation(Fluid fluid) {
        if (fluid == null) return null;
        ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluid);
        if (fluidId == null) return null;

        // 1. 映射表
        ResourceLocation fromMap = getMap().get(fluidId);
        if (fromMap != null) return fromMap;

        // 2. 前缀剥离
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

    /** 清空缓存（配方重载时调用）。 */
    public static void clear() {
        fluidToMaterial = null;
    }

    /** 预热映射表。 */
    public static void prewarm() {
        getMap();
    }

    // ============================================================
    // ===== 内部：映射表构建 ======================================
    // ============================================================

    private static Map<ResourceLocation, ResourceLocation> getMap() {
        if (fluidToMaterial != null) return fluidToMaterial;

        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            buildMap();
        } else {
            if (fluidToMaterial == null) fluidToMaterial = new HashMap<>();
            mc.execute(MaterialResolver::buildMap);
        }
        return fluidToMaterial;
    }

    private static void buildMap() {
        Map<ResourceLocation, ResourceLocation> newMap = new HashMap<>();

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            fluidToMaterial = newMap;
            return;
        }

        RecipeManager recipeManager = mc.getConnection().getRecipeManager();
        int count = 0;

        try {
            for (Recipe<?> recipe : recipeManager.getRecipes()) {
                String className = recipe.getClass().getName().toLowerCase();
                if (!className.contains("materialfluid")) continue;

                try {
                    ResourceLocation materialId = RecipeReflection.extractMaterialId(recipe);
                    if (materialId == null) continue;

                    List<FluidStack> fluids = RecipeReflection.extractFluids(recipe);
                    if (fluids == null || fluids.isEmpty()) continue;

                    for (FluidStack fluid : fluids) {
                        if (fluid == null || fluid.isEmpty()) continue;
                        ResourceLocation fid = ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
                        if (fid == null) continue;
                        newMap.putIfAbsent(fid, materialId);
                        count++;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.println("Tinker's Search: Error building fluid→material map: " + e.getMessage());
        }

        fluidToMaterial = newMap;
        System.out.println("[Tinker's Search] Built fluid→material map: " + count + " entries");
    }
}
