package top.leipishu.tinkerssearch.data;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.IMaterialRegistry;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.Material;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.stats.IMaterialStats;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;
import slimeknights.tconstruct.tools.stats.LimbMaterialStats;

import top.leipishu.tinkerssearch.data.FluidPartData.MaterialEntry;
import top.leipishu.tinkerssearch.data.FluidPartData.ModifierInfo;
import top.leipishu.tinkerssearch.data.FluidPartData.PartInfo;
import top.leipishu.tinkerssearch.data.FluidPartData.PartProperties;
import top.leipishu.tinkerssearch.recipe.CastingRecipeHelper;
import top.leipishu.tinkerssearch.recipe.MaterialCompatibility;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FluidPartDataCache {

    private static final Map<ResourceLocation, FluidPartData> CACHE = new ConcurrentHashMap<>();
    private static volatile long buildGeneration = 0L;

    private static final String[] HARD_EXCLUDE = {
            "ingot", "nugget", "gem", "rod", "coin", "wire", "gear"
    };

    private static final Map<MaterialId, List<PartInfo>> PARTS_CACHE = new ConcurrentHashMap<>();

    public static void invalidate() {
        buildGeneration++;
        CACHE.clear();
        PARTS_CACHE.clear();
    }

    // ============================================================
    // ===== 入口 =================================================
    // ============================================================

    public static FluidPartData get(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.isEmpty()) {
            return new FluidPartData(null, new ArrayList<>(), buildGeneration);
        }
        ResourceLocation fluidId = fluidStack.getFluid().getRegistryName();
        if (fluidId == null) {
            return new FluidPartData(null, new ArrayList<>(), buildGeneration);
        }

        FluidPartData fresh = build(fluidStack.getFluid(), fluidId);

        FluidPartData cached = CACHE.get(fluidId);
        if (cached != null && cached.buildTime == buildGeneration) {
            boolean cachedHasBase = hasBaseEntry(cached);
            boolean freshHasBase = hasBaseEntry(fresh);

            if (cachedHasBase && !freshHasBase) {
                System.out.println("[Tinker's Search] " + fluidId
                        + ": keeping cached (has BASE entry, fresh lost it)");
                return cached;
            }

            int cachedTotal = countTotalParts(cached);
            int freshTotal = countTotalParts(fresh);

            if (cachedTotal > freshTotal) {
                System.out.println("[Tinker's Search] " + fluidId
                        + ": cached=" + cachedTotal + " > fresh=" + freshTotal
                        + ", keeping cached");
                return cached;
            }

            System.out.println("[Tinker's Search] " + fluidId
                    + ": cached=" + cachedTotal + " <= fresh=" + freshTotal
                    + ", updating cache");
        }

        CACHE.put(fluidId, fresh);
        return fresh;
    }

    private static int countTotalParts(FluidPartData data) {
        if (data == null || data.entries == null) return 0;
        int total = 0;
        for (MaterialEntry entry : data.entries) {
            if (entry != null && entry.parts != null) total += entry.parts.size();
        }
        return total;
    }

    private static boolean hasBaseEntry(FluidPartData data) {
        if (data == null || data.entries == null) return false;
        for (MaterialEntry entry : data.entries) {
            if (entry != null && entry.kind == MaterialEntry.SourceKind.BASE) return true;
        }
        return false;
    }

    // ============================================================
    // ===== 构建主流程 ===========================================
    // ============================================================

    private static FluidPartData build(Fluid fluid, ResourceLocation fluidId) {
        LinkedHashMap<MaterialId, MaterialEntry> entries = new LinkedHashMap<>();

        // ===== 阶段 1：本体材料 =====
        MaterialId baseMat = resolveBaseMaterial(fluid);
        FluidStack fluidStack = new FluidStack(fluid, 1000);

        // ★ 四路来源取并集，任意来源确认即收录。
        List<PartInfo> mainParts = new ArrayList<>();
        Set<ResourceLocation> seenPartIds = new HashSet<>();

        mergeParts(mainParts, seenPartIds, collectPartsFromCastingRecipes(fluidStack, baseMat));
        mergeParts(mainParts, seenPartIds, collectPartsFromAnyRecipe(fluidStack, baseMat));
        mergeParts(mainParts, seenPartIds, collectPartsFromDirectRecipe(fluidStack, baseMat));
        if (baseMat != null) {
            mergeParts(mainParts, seenPartIds, collectPartsFor(baseMat));
        }
        mainParts.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));

        // 写入页面
        if (!mainParts.isEmpty()) {
            MaterialId entryKey = baseMat != null
                    ? baseMat
                    : new MaterialId(fluidId.getNamespace(), stripPrefix(fluidId.getPath()));
            String title = baseMat != null
                    ? getMaterialDisplayName(baseMat)
                    : defaultDisplayName(entryKey.getPath());
            entries.put(entryKey, new MaterialEntry(entryKey, title,
                    MaterialEntry.SourceKind.BASE, null, null, mainParts));
        }

        // ===== 阶段 2：复合关系 =====
        List<CompositeRelation> relations = collectCompositeRelations(fluid);
        for (CompositeRelation rel : relations) {
            if (rel.result == null) continue;
            if (entries.containsKey(rel.result)) continue;

            List<PartInfo> parts = collectPartsFor(rel.result);
            if (parts.isEmpty()) continue;

            String resultName = getMaterialDisplayName(rel.result);
            String inputName = getMaterialDisplayName(rel.input);
            String title = resultName + " (" + inputName + ")";

            entries.put(rel.result, new MaterialEntry(rel.result, title,
                    MaterialEntry.SourceKind.COMPOSITE, rel.input, inputName, parts));
        }

        return new FluidPartData(fluidId, new ArrayList<>(entries.values()), buildGeneration);
    }

    /** 把 source 中未出现过的部件合并进 target。 */
    private static void mergeParts(List<PartInfo> target, Set<ResourceLocation> seen, List<PartInfo> source) {
        if (source == null || source.isEmpty()) return;
        for (PartInfo p : source) {
            if (p == null || p.itemId == null) continue;
            if (seen.add(p.itemId)) target.add(p);
        }
    }

    // ============================================================
    // ===== 本体材料解析 =========================================
    // ============================================================

    private static MaterialId resolveBaseMaterial(Fluid fluid) {
        if (fluid == null) return null;
        ResourceLocation fluidId = fluid.getRegistryName();
        if (fluidId == null) return null;

        try {
            ResourceLocation matId = CastingRecipeHelper.getMaterialIdForFluid(fluid);
            if (matId != null) return new MaterialId(matId);
        } catch (Exception ignored) {}

        String path = fluidId.getPath();
        for (String prefix : new String[]{"molten_", "liquid_", "fluid_"}) {
            if (path.startsWith(prefix)) {
                String stripped = path.substring(prefix.length());
                if (!stripped.isEmpty()) {
                    return new MaterialId(fluidId.getNamespace(), stripped);
                }
            }
        }

        return null;
    }

    // ============================================================
    // ===== 从浇筑配方反查部件 ===================================
    // ============================================================

    private static List<PartInfo> collectPartsFromCastingRecipes(FluidStack fluidStack, MaterialId materialId) {
        List<PartInfo> result = new ArrayList<>();
        if (fluidStack == null || fluidStack.isEmpty()) return result;

        List<CastingRecipeHelper.CastingInfo> castingInfos;
        try {
            castingInfos = CastingRecipeHelper.getCastingRecipesForFluid(fluidStack);
        } catch (Throwable t) {
            return result;
        }
        if (castingInfos == null || castingInfos.isEmpty()) return result;

        IMaterialRegistry registry = MaterialRegistry.getInstance();
        Set<ResourceLocation> seenIds = new HashSet<>();

        for (CastingRecipeHelper.CastingInfo info : castingInfos) {
            try {
                ItemStack output = info.outputItem;
                if (output == null || output.isEmpty()) continue;

                Item item = output.getItem();
                if (!(item instanceof IMaterialItem)) continue;

                ResourceLocation itemId = item.getRegistryName();
                if (itemId == null || !seenIds.add(itemId)) continue;
                if (!isToolPartPath(itemId.getPath().toLowerCase())) continue;

                String displayName = item.getDescription().getString();
                if (displayName.isEmpty()) continue;

                MaterialStatsId statType = inferStatType((IMaterialItem) item);
                PartProperties properties = buildProperties(registry, materialId, statType);

                int requiredAmount = info.requiredAmount;
                if (requiredAmount <= 0) requiredAmount = CastingRecipeHelper.getRequiredAmountForPart(itemId);
                if (requiredAmount <= 0) requiredAmount = 90;

                ItemStack displayStack = new ItemStack(item);
                if (materialId != null) {
                    displayStack.getOrCreateTag().putString("Material", materialId.toString());
                }

                result.add(new PartInfo(itemId, item, displayName, materialId, statType,
                        properties, requiredAmount, displayStack));
            } catch (Throwable ignored) {}
        }

        result.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
        return result;
    }

    private static List<PartInfo> collectPartsFromDirectRecipe(FluidStack fluidStack, MaterialId materialId) {
        List<PartInfo> result = new ArrayList<>();
        if (fluidStack == null || fluidStack.isEmpty()) return result;

        List<CastingRecipeHelper.CastingInfo> infos;
        try {
            infos = CastingRecipeHelper.getDirectPartCastingRecipes(fluidStack);
        } catch (Throwable t) {
            return result;
        }
        if (infos == null || infos.isEmpty()) return result;

        IMaterialRegistry registry = MaterialRegistry.getInstance();
        Set<ResourceLocation> seenIds = new HashSet<>();

        for (CastingRecipeHelper.CastingInfo info : infos) {
            try {
                ItemStack output = info.outputItem;
                if (output == null || output.isEmpty()) continue;

                Item item = output.getItem();
                if (!(item instanceof IMaterialItem)) continue;

                ResourceLocation itemId = item.getRegistryName();
                if (itemId == null || !seenIds.add(itemId)) continue;
                if (!isToolPartPath(itemId.getPath().toLowerCase())) continue;

                String displayName = item.getDescription().getString();
                if (displayName.isEmpty()) continue;

                MaterialStatsId statType = inferStatType((IMaterialItem) item);
                PartProperties properties = buildProperties(registry, materialId, statType);

                int requiredAmount = info.requiredAmount;
                if (requiredAmount <= 0) requiredAmount = CastingRecipeHelper.getRequiredAmountForPart(itemId);
                if (requiredAmount <= 0) requiredAmount = 90;

                ItemStack displayStack = new ItemStack(item);
                if (materialId != null) {
                    displayStack.getOrCreateTag().putString("Material", materialId.toString());
                }

                result.add(new PartInfo(itemId, item, displayName, materialId, statType,
                        properties, requiredAmount, displayStack));
            } catch (Throwable ignored) {}
        }

        result.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
        return result;
    }

    private static List<PartInfo> collectPartsFromAnyRecipe(FluidStack fluidStack, MaterialId materialId) {
        List<PartInfo> result = new ArrayList<>();
        if (fluidStack == null || fluidStack.isEmpty()) return result;

        List<CastingRecipeHelper.CastingInfo> infos;
        try {
            infos = CastingRecipeHelper.getAnyPartCastingRecipes(fluidStack);
        } catch (Throwable t) {
            return result;
        }
        if (infos == null || infos.isEmpty()) return result;

        IMaterialRegistry registry = MaterialRegistry.getInstance();
        Set<ResourceLocation> seenIds = new HashSet<>();

        for (CastingRecipeHelper.CastingInfo info : infos) {
            try {
                ItemStack output = info.outputItem;
                if (output == null || output.isEmpty()) continue;

                Item item = output.getItem();
                if (!(item instanceof IMaterialItem)) continue;

                ResourceLocation itemId = item.getRegistryName();
                if (itemId == null || !seenIds.add(itemId)) continue;
                if (!isToolPartPath(itemId.getPath().toLowerCase())) continue;

                String displayName = item.getDescription().getString();
                if (displayName.isEmpty()) continue;

                MaterialStatsId statType = inferStatType((IMaterialItem) item);
                PartProperties properties = buildProperties(registry, materialId, statType);

                int requiredAmount = info.requiredAmount;
                if (requiredAmount <= 0) requiredAmount = CastingRecipeHelper.getRequiredAmountForPart(itemId);
                if (requiredAmount <= 0) requiredAmount = 90;

                ItemStack displayStack = output.copy();
                if (materialId != null && displayStack.getTag() == null) {
                    displayStack.getOrCreateTag().putString("Material", materialId.toString());
                }

                result.add(new PartInfo(itemId, item, displayName, materialId, statType,
                        properties, requiredAmount, displayStack));
            } catch (Throwable ignored) {}
        }

        result.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
        return result;
    }

    // ============================================================
    // ===== 回退路径：遍历所有 IMaterialItem ====================
    // ============================================================

    private static List<PartInfo> collectPartsFor(MaterialId materialId) {
        if (materialId == null) return new ArrayList<>();
        List<PartInfo> cached = PARTS_CACHE.get(materialId);
        if (cached != null) return cached;

        List<PartInfo> result = new ArrayList<>();
        IMaterialRegistry registry = MaterialRegistry.getInstance();
        Set<ResourceLocation> seenIds = new HashSet<>();

        for (Item item : ForgeRegistries.ITEMS) {
            if (!(item instanceof IMaterialItem)) continue;
            IMaterialItem mi = (IMaterialItem) item;

            ResourceLocation id = item.getRegistryName();
            if (id == null || !seenIds.add(id)) continue;
            if (!isToolPartPath(id.getPath().toLowerCase())) continue;

            String displayName = item.getDescription().getString();
            if (displayName.isEmpty()) continue;

            if (!isPartUsable(registry, materialId, mi)) continue;

            try {
                MaterialStatsId statType = inferStatType(mi);
                PartProperties properties = buildProperties(registry, materialId, statType);
                int requiredAmount = CastingRecipeHelper.getRequiredAmountForPart(id);

                ItemStack displayStack = new ItemStack(item);
                displayStack.getOrCreateTag().putString("Material", materialId.toString());

                result.add(new PartInfo(id, item, displayName, materialId, statType,
                        properties, requiredAmount, displayStack));
            } catch (Throwable ignored) {}
        }

        result.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));

        if (!result.isEmpty()) PARTS_CACHE.put(materialId, result);
        return result;
    }

    private static boolean isPartUsable(IMaterialRegistry registry, MaterialId materialId, IMaterialItem mi) {
        return MaterialCompatibility.canUseMaterial(mi, materialId);
    }

    private static boolean isToolPartPath(String path) {
        if (path.endsWith("_cast") || path.startsWith("cast_") || path.contains("plate_cast")) return false;
        for (String kw : HARD_EXCLUDE) if (path.contains(kw)) return false;
        return true;
    }

    private static PartProperties buildProperties(IMaterialRegistry registry, MaterialId materialId, MaterialStatsId statType) {
        List<ModifierInfo> modifiers = getModifiers(materialId, statType);
        if (statType == null) return new PartProperties(new ArrayList<>(), modifiers);

        Optional<IMaterialStats> opt;
        try {
            opt = registry.getMaterialStats(materialId, statType);
        } catch (Throwable t) {
            return new PartProperties(new ArrayList<>(), modifiers);
        }
        if (!opt.isPresent()) return new PartProperties(new ArrayList<>(), modifiers);

        List<Component> statLines = new ArrayList<>();
        try {
            List<Component> loc = opt.get().getLocalizedInfo();
            if (loc != null) statLines.addAll(loc);
        } catch (Throwable ignored) {}
        return new PartProperties(statLines, modifiers);
    }

    // ============================================================
    // ===== 复合关系提取 =========================================
    // ============================================================

    private static class CompositeRelation {
        final MaterialId result;
        final MaterialId input;
        CompositeRelation(MaterialId r, MaterialId i) { result = r; input = i; }
    }

    private static List<CompositeRelation> collectCompositeRelations(Fluid fluid) {
        List<CompositeRelation> out = new ArrayList<>();
        if (fluid == null) return out;
        ResourceLocation fluidId = fluid.getRegistryName();
        if (fluidId == null) return out;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return out;

        Set<String> seen = new HashSet<>();

        for (var recipe : mc.getConnection().getRecipeManager().getRecipes()) {
            try {
                FluidStack rf = extractRecipeFluid(recipe);
                if (rf == null || rf.isEmpty()) continue;
                if (!fluidId.equals(rf.getFluid().getRegistryName())) continue;

                List<CompositeRelation> rels = extractCompositeRelations(recipe);
                for (CompositeRelation r : rels) {
                    String key = r.result + "|" + r.input;
                    if (seen.add(key)) out.add(r);
                }
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static List<CompositeRelation> extractCompositeRelations(Object recipe) {
        List<CompositeRelation> rels = extractRelationsFromMaterialFluid(recipe);
        if (!rels.isEmpty()) return rels;
        rels = extractRelationsFromSubRecipes(recipe);
        if (!rels.isEmpty()) return rels;
        return extractRelationsDirect(recipe);
    }

    private static List<CompositeRelation> extractRelationsFromMaterialFluid(Object recipe) {
        List<CompositeRelation> list = new ArrayList<>();
        Object mf = findFieldValue(recipe,
                "materialFluid", "materialFluidRecipe", "fluidRecipe", "materialRecipe");
        if (mf == null) return list;

        MaterialId output = extractMaterialIdByName(mf,
                new String[]{"getOutput", "getOutputMaterial", "getResult"},
                new String[]{"output", "outputMaterial", "result", "material"});
        if (output == null) return list;

        List<MaterialId> inputs = extractMaterialIdListByName(mf,
                new String[]{"getInputs", "getInput", "getMatchingMaterials", "getMaterials"},
                new String[]{"inputs", "input", "inputMaterials", "materials"});
        if (inputs.isEmpty()) return list;

        for (MaterialId input : inputs) {
            list.add(new CompositeRelation(output, input));
        }
        return list;
    }

    private static List<CompositeRelation> extractRelationsFromSubRecipes(Object recipe) {
        List<CompositeRelation> list = new ArrayList<>();
        List<Object> subs = extractRecipeDisplays(recipe);
        if (subs.isEmpty()) return list;

        Set<String> seen = new HashSet<>();
        for (Object sub : subs) {
            MaterialId output = extractOutputMaterialFrom(sub);
            if (output == null) continue;

            List<MaterialId> inputs = extractInputMaterialsFrom(sub);
            for (MaterialId in : inputs) {
                String key = output + "|" + in;
                if (seen.add(key)) list.add(new CompositeRelation(output, in));
            }
        }
        return list;
    }

    private static List<CompositeRelation> extractRelationsDirect(Object recipe) {
        List<CompositeRelation> list = new ArrayList<>();
        MaterialId output = extractOutputMaterialFrom(recipe);
        List<MaterialId> inputs = extractInputMaterialsFrom(recipe);
        if (output == null || inputs.isEmpty()) return list;

        for (MaterialId in : inputs) {
            list.add(new CompositeRelation(output, in));
        }
        return list;
    }

    // ============================================================
    // ===== 通用反射工具 =========================================
    // ============================================================

    private static Object findFieldValue(Object obj, String... names) {
        if (obj == null) return null;
        for (String name : names) {
            try {
                Class<?> c = obj.getClass();
                while (c != null && c != Object.class) {
                    try {
                        Field f = c.getDeclaredField(name);
                        f.setAccessible(true);
                        Object v = f.get(obj);
                        if (v != null) return v;
                    } catch (NoSuchFieldException ignored) {}
                    c = c.getSuperclass();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static MaterialId extractMaterialIdByName(Object obj, String[] methods, String[] fields) {
        if (obj == null) return null;
        for (String mn : methods) {
            try {
                Method m = obj.getClass().getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(obj);
                MaterialId id = toMaterialId(v);
                if (id != null) return id;
            } catch (Exception ignored) {}
        }
        for (String fn : fields) {
            Object v = findFieldValue(obj, fn);
            MaterialId id = toMaterialId(v);
            if (id != null) return id;
        }
        return null;
    }

    private static List<MaterialId> extractMaterialIdListByName(Object obj, String[] methods, String[] fields) {
        List<MaterialId> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (obj == null) return result;

        for (String mn : methods) {
            try {
                Method m = obj.getClass().getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(obj);
                collectMaterialIds(v, result, seen);
                if (!result.isEmpty()) return result;
            } catch (Exception ignored) {}
        }
        for (String fn : fields) {
            Object v = findFieldValue(obj, fn);
            collectMaterialIds(v, result, seen);
            if (!result.isEmpty()) return result;
        }
        return result;
    }

    private static void collectMaterialIds(Object v, List<MaterialId> out, Set<String> seen) {
        if (v == null) return;

        MaterialId id = toMaterialId(v);
        if (id != null) {
            if (seen.add(id.toString())) out.add(id);
            return;
        }

        if (v instanceof Iterable) {
            for (Object o : (Iterable<?>) v) {
                MaterialId m = toMaterialId(o);
                if (m != null && seen.add(m.toString())) out.add(m);
            }
            return;
        }
        if (v instanceof Object[]) {
            for (Object o : (Object[]) v) {
                MaterialId m = toMaterialId(o);
                if (m != null && seen.add(m.toString())) out.add(m);
            }
        }
    }

    private static List<MaterialId> extractInputMaterialsFrom(Object obj) {
        List<MaterialId> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (obj == null) return result;

        for (String mn : new String[]{"getInput", "getInputMaterial", "getMaterial",
                "getMatchingMaterials", "getMaterials", "getMaterialIds"}) {
            try {
                Method m = obj.getClass().getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(obj);
                collectMaterialIds(v, result, seen);
                if (!result.isEmpty()) return result;
            } catch (Exception ignored) {}
        }

        for (String fn : new String[]{"input", "inputMaterial", "inputId",
                "material", "materialId", "materialIngredient",
                "inputIngredient", "baseMaterial", "baseMaterialId"}) {
            Object v = findFieldValue(obj, fn);
            collectMaterialIds(v, result, seen);
            if (!result.isEmpty()) return result;
        }
        return result;
    }

    private static MaterialId extractOutputMaterialFrom(Object obj) {
        if (obj == null) return null;

        for (String mn : new String[]{"getResult", "getOutput", "getResultItem", "getOutputItem",
                "getItemStack", "getStack", "getResultStack", "getOutputStack",
                "getMaterial", "getOutputMaterial", "getResultMaterial"}) {
            try {
                Method m = obj.getClass().getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(obj);
                MaterialId id = toMaterialId(v);
                if (id != null) return id;
                if (v instanceof ItemStack) {
                    MaterialId s = extractMaterialFromStack((ItemStack) v);
                    if (s != null) return s;
                }
            } catch (Exception ignored) {}
        }

        for (String fn : new String[]{"result", "output", "resultItem", "outputItem",
                "stack", "material", "outputMaterial", "resultMaterial"}) {
            Object v = findFieldValue(obj, fn);
            MaterialId id = toMaterialId(v);
            if (id != null) return id;
            if (v instanceof ItemStack) {
                MaterialId s = extractMaterialFromStack((ItemStack) v);
                if (s != null) return s;
            }
        }
        return null;
    }

    private static List<Object> extractRecipeDisplays(Object recipe) {
        List<Object> out = new ArrayList<>();
        if (recipe == null) return out;

        for (String mn : new String[]{"getRecipes", "getSubRecipes", "getDisplayRecipes"}) {
            try {
                Method m = recipe.getClass().getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(recipe);
                if (v instanceof Iterable) {
                    for (Object o : (Iterable<?>) v) out.add(o);
                    return out;
                }
                if (v instanceof Object[]) {
                    for (Object o : (Object[]) v) out.add(o);
                    return out;
                }
            } catch (Exception ignored) {}
        }

        for (String fn : new String[]{"recipes", "subRecipes", "displayRecipes", "subrecipes"}) {
            Object v = findFieldValue(recipe, fn);
            if (v instanceof Iterable) {
                for (Object o : (Iterable<?>) v) out.add(o);
                return out;
            }
            if (v instanceof Object[]) {
                for (Object o : (Object[]) v) out.add(o);
                return out;
            }
        }
        return out;
    }

    // ============================================================
    // ===== StatType 推断 ========================================
    // ============================================================

    private static MaterialStatsId inferStatType(IMaterialItem item) {
        if (item == null) return null;

        for (String mn : new String[]{"getStatType", "getStatsType", "getStatTypeId"}) {
            try {
                Method m = item.getClass().getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(item);
                if (v instanceof MaterialStatsId) return (MaterialStatsId) v;
            } catch (Exception ignored) {}
        }

        for (String fn : new String[]{"statType", "statsType", "statTypeId", "materialStatId"}) {
            try {
                Class<?> c = item.getClass();
                while (c != null && c != Object.class) {
                    try {
                        Field f = c.getDeclaredField(fn);
                        f.setAccessible(true);
                        Object v = f.get(item);
                        if (v instanceof MaterialStatsId) return (MaterialStatsId) v;
                    } catch (NoSuchFieldException ignored) {}
                    c = c.getSuperclass();
                }
            } catch (Exception ignored) {}
        }

        try {
            String path = item.asItem().getRegistryName() != null
                    ? item.asItem().getRegistryName().getPath().toLowerCase() : "";
            if (path.contains("head") || path.contains("blade") || path.contains("axe")
                    || path.contains("pick") || path.contains("sword") || path.contains("dagger")
                    || path.contains("hammer")) return HeadMaterialStats.ID;
            if (path.contains("handle") || path.contains("binding") || path.contains("grip")) return HandleMaterialStats.ID;
            if (path.contains("limb") || path.contains("bow") || path.contains("arm")) return LimbMaterialStats.ID;
        } catch (Exception ignored) {}

        return null;
    }

    // ============================================================
    // ===== 词条读取 =============================================
    // ============================================================

    private static List<ModifierInfo> getModifiers(MaterialId materialId, MaterialStatsId statType) {
        List<ModifierInfo> result = new ArrayList<>();
        if (materialId == null) return result;

        try {
            Object tm = getTraitsManager();
            if (tm == null) return result;

            List<ModifierEntry> entries = null;

            if (statType != null) {
                try {
                    Method m = tm.getClass().getMethod("getTraits", MaterialId.class, MaterialStatsId.class);
                    m.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    List<ModifierEntry> traits = (List<ModifierEntry>) m.invoke(tm, materialId, statType);
                    entries = traits;
                } catch (Exception ignored) {}
            }

            if ((entries == null || entries.isEmpty()) && statType != null) {
                try {
                    Method m = tm.getClass().getMethod("getDefaultTraits", MaterialId.class);
                    m.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    List<ModifierEntry> traits = (List<ModifierEntry>) m.invoke(tm, materialId);
                    entries = traits;
                } catch (Exception ignored) {}
            }

            if (entries == null || entries.isEmpty()) return result;

            for (ModifierEntry e : entries) {
                ModifierInfo info = parseModifier(e);
                if (info != null) result.add(info);
            }
        } catch (Throwable ignored) {}
        return result;
    }

    private static ModifierInfo parseModifier(ModifierEntry entry) {
        if (entry == null) return null;
        try {
            Object modifier = entry.getModifier();
            if (modifier == null) return null;

            int level = entry.getLevel();
            String id = "";
            try {
                Method m = modifier.getClass().getMethod("getId");
                m.setAccessible(true);
                Object v = m.invoke(modifier);
                if (v != null) id = v.toString();
            } catch (Exception ignored) {}

            Component displayName = null;
            for (String mn : new String[]{"getDisplayName", "getColoredName", "getName"}) {
                try {
                    Method m = modifier.getClass().getMethod(mn);
                    m.setAccessible(true);
                    Object v = m.invoke(modifier);
                    if (v instanceof Component) { displayName = (Component) v; break; }
                } catch (Exception ignored) {}
            }
            if (displayName == null) {
                displayName = new TextComponent(id.isEmpty() ? modifier.getClass().getSimpleName() : id);
            }

            List<Component> desc = new ArrayList<>();
            for (String mn : new String[]{"getDescriptionList", "getDescription"}) {
                try {
                    Method m = modifier.getClass().getMethod(mn);
                    m.setAccessible(true);
                    Object v = m.invoke(modifier);
                    if (v instanceof List) {
                        for (Object line : (List<?>) v) if (line instanceof Component) desc.add((Component) line);
                        break;
                    } else if (v instanceof Component) {
                        desc.add((Component) v);
                        break;
                    }
                } catch (Exception ignored) {}
            }
            return new ModifierInfo(id, displayName, level, desc);
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object getTraitsManager() {
        try {
            IMaterialRegistry registry = MaterialRegistry.getInstance();
            if (registry == null) return null;
            Class<?> c = registry.getClass();
            while (c != null && c != Object.class) {
                for (String fn : new String[]{"materialTraitsManager", "traitsManager"}) {
                    try {
                        Field f = c.getDeclaredField(fn);
                        f.setAccessible(true);
                        Object v = f.get(registry);
                        if (v != null) return v;
                    } catch (NoSuchFieldException ignored) {}
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ============================================================
    // ===== 流体提取 =============================================
    // ============================================================

    private static FluidStack extractRecipeFluid(Object recipe) {
        if (recipe == null) return null;

        FluidStack direct = tryExtractFluidDirect(recipe);
        if (direct != null) return direct;

        Object mf = findFieldValue(recipe,
                "materialFluid", "materialFluidRecipe", "fluidRecipe", "materialRecipe");
        if (mf != null) {
            FluidStack viaMf = tryExtractFluidDirect(mf);
            if (viaMf != null) return viaMf;
            Object innerFluid = findFieldValue(mf, "fluid", "inputFluid");
            FluidStack f2 = fluidFromIngredient(innerFluid);
            if (f2 != null) return f2;
        }

        for (Object sub : extractRecipeDisplays(recipe)) {
            FluidStack subFluid = tryExtractFluidDirect(sub);
            if (subFluid != null) return subFluid;
        }
        return null;
    }

    private static FluidStack tryExtractFluidDirect(Object recipe) {
        if (recipe == null) return null;

        Object fluidField = findFieldValue(recipe, "fluid", "inputFluid", "fluidIngredient");
        FluidStack got = fluidFromIngredient(fluidField);
        if (got != null) return got;

        try {
            Method m = recipe.getClass().getMethod("getFluid");
            m.setAccessible(true);
            Object v = m.invoke(recipe);
            if (v instanceof FluidStack) return (FluidStack) v;
        } catch (Exception ignored) {}

        try {
            Method m = recipe.getClass().getMethod("getFluids");
            m.setAccessible(true);
            Object v = m.invoke(recipe);
            if (v instanceof List) {
                List<?> list = (List<?>) v;
                if (!list.isEmpty() && list.get(0) instanceof FluidStack) return (FluidStack) list.get(0);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static FluidStack fluidFromIngredient(Object fi) {
        if (fi == null) return null;
        if (fi instanceof FluidStack) return (FluidStack) fi;

        try {
            Method m = fi.getClass().getMethod("getFluids");
            m.setAccessible(true);
            Object fs = m.invoke(fi);
            if (fs instanceof List) {
                List<?> list = (List<?>) fs;
                if (!list.isEmpty() && list.get(0) instanceof FluidStack) return (FluidStack) list.get(0);
            }
        } catch (Exception ignored) {}

        try {
            Method m = fi.getClass().getMethod("getFluid");
            m.setAccessible(true);
            Object fs = m.invoke(fi);
            if (fs instanceof FluidStack) return (FluidStack) fs;
        } catch (Exception ignored) {}

        Object inner = findFieldValue(fi, "fluid", "fluidStack");
        if (inner != null && inner != fi) return fluidFromIngredient(inner);
        return null;
    }

    // ============================================================
    // ===== 通用工具 =============================================
    // ============================================================

    private static MaterialId toMaterialId(Object v) {
        if (v == null) return null;
        if (v instanceof MaterialId) return (MaterialId) v;
        if (v instanceof IMaterial) return ((IMaterial) v).getIdentifier();
        for (String mn : new String[]{"getIdentifier", "getLocation", "getId"}) {
            try {
                Method m = v.getClass().getMethod(mn);
                m.setAccessible(true);
                Object id = m.invoke(v);
                if (id instanceof MaterialId) return (MaterialId) id;
                if (id != null) {
                    try { return new MaterialId(new ResourceLocation(id.toString())); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static MaterialId extractMaterialFromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("Material")) return null;
        try { return new MaterialId(new ResourceLocation(tag.getString("Material"))); } catch (Exception e) { return null; }
    }

    private static String getMaterialDisplayName(MaterialId materialId) {
        if (materialId == null) return "";
        try {
            IMaterial m = MaterialRegistry.getInstance().getMaterial(materialId);
            if (m != null && m != IMaterial.UNKNOWN) return getMaterialDisplayName(m);
        } catch (Throwable ignored) {}
        return defaultDisplayName(materialId.getPath());
    }

    private static String getMaterialDisplayName(IMaterial material) {
        if (material == null) return "";
        try {
            Method m = material.getClass().getMethod("getDisplayName");
            m.setAccessible(true);
            Object v = m.invoke(material);
            if (v instanceof Component) {
                String s = ((Component) v).getString();
                if (s != null && !s.isEmpty()) {
                    return s;
                }
            }
        } catch (Throwable ignored) {}
        return defaultDisplayName(material.getIdentifier().getPath());
    }

    private static String defaultDisplayName(String path) {
        if (path == null || path.isEmpty()) return "";
        String[] words = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) sb.append(w.substring(1));
        }
        return sb.length() > 0 ? sb.toString() : path;
    }

    private static String stripPrefix(String path) {
        if (path == null) return "";
        for (String prefix : new String[]{"molten_", "liquid_", "fluid_"}) {
            if (path.startsWith(prefix)) return path.substring(prefix.length());
        }
        return path;
    }

    @SuppressWarnings("unused")
    private static FluidStack getFluidForMaterial(IMaterial material) {
        if (material == null) return null;
        if (material instanceof Material) {
            Material mat = (Material) material;
            for (String mn : new String[]{"getFluid", "getFluidStack"}) {
                try {
                    Method m = Material.class.getMethod(mn);
                    Object v = m.invoke(mat);
                    if (v instanceof FluidStack) return (FluidStack) v;
                } catch (Exception ignored) {}
            }
            for (String fn : new String[]{"fluid", "fluidStack"}) {
                try {
                    Field f = Material.class.getDeclaredField(fn);
                    f.setAccessible(true);
                    Object v = f.get(mat);
                    if (v instanceof FluidStack) return (FluidStack) v;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }
}