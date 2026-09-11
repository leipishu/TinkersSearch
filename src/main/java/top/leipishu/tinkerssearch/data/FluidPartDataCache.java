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
import top.leipishu.tinkerssearch.utils.CastingRecipeHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
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

    private static final Map<String, Boolean> USABLE_CACHE = new HashMap<>();

    public static void invalidate() {
        buildGeneration++;
        CACHE.clear();
        USABLE_CACHE.clear();
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
        FluidPartData cached = CACHE.get(fluidId);
        if (cached != null && cached.buildTime == buildGeneration) return cached;

        FluidPartData data = build(fluidStack.getFluid(), fluidId);
        CACHE.put(fluidId, data);
        return data;
    }

    // ============================================================
    // ===== 构建主流程 ===========================================
    // ============================================================

    private static FluidPartData build(Fluid fluid, ResourceLocation fluidId) {
        LinkedHashMap<MaterialId, MaterialEntry> entries = new LinkedHashMap<>();

        // 1. 本体材料 → 一页
        MaterialId baseMat = resolveBaseMaterial(fluid);
        if (baseMat != null) {
            entries.put(baseMat, buildEntry(baseMat, MaterialEntry.SourceKind.BASE, null, null));
        }

        // 2. 复合关系 → N 页（键是 MaterialId，不是 title）
        List<CompositeRelation> relations = collectCompositeRelations(fluid);
        for (CompositeRelation rel : relations) {
            if (entries.containsKey(rel.result)) continue;
            entries.put(rel.result, buildEntry(rel.result, MaterialEntry.SourceKind.COMPOSITE,
                    rel.input, getMaterialDisplayName(rel.input)));
        }

        // 3. 过滤空页
        List<MaterialEntry> pages = new ArrayList<>();
        for (MaterialEntry e : entries.values()) {
            if (!e.parts.isEmpty()) pages.add(e);
        }
        return new FluidPartData(fluidId, pages, buildGeneration);
    }

    private static MaterialEntry buildEntry(MaterialId materialId, MaterialEntry.SourceKind kind,
                                            MaterialId compositeInput, String compositeInputName) {
        List<PartInfo> parts = collectPartsFor(materialId);
        String matName = getMaterialDisplayName(materialId);
        String title = kind == MaterialEntry.SourceKind.BASE
                ? matName
                : matName + " (" + (compositeInputName != null ? compositeInputName : "?") + ")";
        return new MaterialEntry(materialId, title, kind, compositeInput, compositeInputName, parts);
    }

    // ============================================================
    // ===== 1. 本体材料解析 ======================================
    // ============================================================

    private static MaterialId resolveBaseMaterial(Fluid fluid) {
        if (fluid == null) return null;
        ResourceLocation fluidId = fluid.getRegistryName();
        if (fluidId == null) return null;

        try {
            ResourceLocation matId = CastingRecipeHelper.getMaterialIdForFluid(fluid);
            if (matId != null) {
                MaterialId mid = new MaterialId(matId);
                if (hasUsablePart(mid)) return mid;
            }
        } catch (Exception ignored) {}

        String path = fluidId.getPath();
        String stripped = null;
        for (String p : new String[]{"molten_", "liquid_", "fluid_"}) {
            if (path.startsWith(p)) { stripped = path.substring(p.length()); break; }
        }
        if (stripped != null) {
            for (String ns : new String[]{fluidId.getNamespace(), "tconstruct", "kubejs", "crafttweaker", "minecraft"}) {
                try {
                    MaterialId mid = new MaterialId(ns, stripped);
                    if (MaterialRegistry.getInstance().getMaterial(mid) != null && hasUsablePart(mid)) {
                        return mid;
                    }
                } catch (Exception ignored) {}
            }
        }

        try {
            IMaterialRegistry registry = MaterialRegistry.getInstance();
            for (IMaterial m : registry.getAllMaterials()) {
                FluidStack mf = getFluidForMaterial(m);
                if (mf != null && !mf.isEmpty() && fluidId.equals(mf.getFluid().getRegistryName())) {
                    if (hasUsablePart(m.getIdentifier())) return m.getIdentifier();
                }
            }
        } catch (Exception ignored) {}

        try {
            IMaterialRegistry registry = MaterialRegistry.getInstance();
            for (IMaterial m : registry.getAllMaterials()) {
                String matPath = m.getIdentifier().getPath();
                if (matPath.length() >= 3 && path.endsWith(matPath) && hasUsablePart(m.getIdentifier())) {
                    return m.getIdentifier();
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    // ============================================================
    // ===== 2. 复合关系提取（核心） ==============================
    // ============================================================

    private static class CompositeRelation {
        final MaterialId result;
        final MaterialId input;
        CompositeRelation(MaterialId r, MaterialId i) { result = r; input = i; }
    }

    /**
     * 遍历所有配方，只要"流体匹配当前流体"，就尝试所有路径提取 (input, output) 对。
     * 不再依赖 instanceof CompositeCastingRecipe（避免整合包魔改类型漏判）。
     */
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

    /**
     * 三层兜底：
     *   A. materialFluid 字段（CompositeCastingRecipe 真正的数据所在）
     *   B. getRecipes() 子配方遍历
     *   C. 直接从 recipe 拿 input + output
     */
    private static List<CompositeRelation> extractCompositeRelations(Object recipe) {
        // 路径 A：materialFluid
        List<CompositeRelation> rels = extractRelationsFromMaterialFluid(recipe);
        if (!rels.isEmpty()) return rels;

        // 路径 B：子配方
        rels = extractRelationsFromSubRecipes(recipe);
        if (!rels.isEmpty()) return rels;

        // 路径 C：直接从 recipe 提取
        return extractRelationsDirect(recipe);
    }

    /**
     * 路径 A：从 recipe 的 materialFluid 字段读 inputs + output。
     *
     * CompositeCastingRecipe 里有个 MaterialFluidRecipe 对象，
     * 它保存了"流体 + 底材列表 + 输出材料"的完整信息。
     */
    private static List<CompositeRelation> extractRelationsFromMaterialFluid(Object recipe) {
        List<CompositeRelation> list = new ArrayList<>();
        Object mf = findFieldValue(recipe,
                "materialFluid", "materialFluidRecipe", "fluidRecipe", "materialRecipe");
        if (mf == null) return list;

        // 输出材料
        MaterialId output = extractMaterialIdByName(mf,
                new String[]{"getOutput", "getOutputMaterial", "getResult"},
                new String[]{"output", "outputMaterial", "result", "material"});
        if (output == null) return list;

        // 输入材料列表
        List<MaterialId> inputs = extractMaterialIdListByName(mf,
                new String[]{"getInputs", "getInput", "getMatchingMaterials", "getMaterials"},
                new String[]{"inputs", "input", "inputMaterials", "materials"});
        if (inputs.isEmpty()) return list;

        for (MaterialId input : inputs) {
            list.add(new CompositeRelation(output, input));
        }
        return list;
    }

    /**
     * 路径 B：遍历 getRecipes() 子配方，每个子配方提取 input + output。
     */
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

    /**
     * 路径 C：整个 recipe 层面拿 input + output 并交叉配对。
     */
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

    /** 在对象及其父类中按名字找字段值 */
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

    /** 按方法名/字段名提取 MaterialId */
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

    /** 按方法名/字段名提取 MaterialId 列表 */
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

    /** 从任意值收集 MaterialId（一次展开 Iterable / 数组 / MaterialIngredient） */
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
            return;
        }

        // MaterialIngredient：有 getMatchingMaterials
        for (String mn : new String[]{"getMatchingMaterials", "getMaterials", "getMaterialIds"}) {
            try {
                Method m = v.getClass().getMethod(mn);
                m.setAccessible(true);
                Object inner = m.invoke(v);
                if (inner != null && inner != v) {
                    collectMaterialIds(inner, out, seen);
                    if (!out.isEmpty()) return;
                }
            } catch (Exception ignored) {}
        }

        // getMaterial() 单值
        try {
            Method m = v.getClass().getMethod("getMaterial");
            m.setAccessible(true);
            Object inner = m.invoke(v);
            MaterialId mid = toMaterialId(inner);
            if (mid != null && seen.add(mid.toString())) out.add(mid);
        } catch (Exception ignored) {}

        // getMatchingStacks -> ItemStack[]
        try {
            Method m = v.getClass().getMethod("getMatchingStacks");
            m.setAccessible(true);
            Object inner = m.invoke(v);
            if (inner instanceof ItemStack[]) {
                for (ItemStack s : (ItemStack[]) inner) {
                    MaterialId mid = extractMaterialFromStack(s);
                    if (mid != null && seen.add(mid.toString())) out.add(mid);
                }
            }
        } catch (Exception ignored) {}
    }

    /** 从任意对象（recipe / 子配方）提取 input 材料列表 */
    private static List<MaterialId> extractInputMaterialsFrom(Object obj) {
        List<MaterialId> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (obj == null) return result;

        // 方法
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

        // 字段（含 materialId —— MaterialCastingRecipe 里底材常放在这个字段）
        for (String fn : new String[]{"input", "inputMaterial", "inputId",
                "material", "materialId", "materialIngredient",
                "inputIngredient", "baseMaterial", "baseMaterialId"}) {
            Object v = findFieldValue(obj, fn);
            collectMaterialIds(v, result, seen);
            if (!result.isEmpty()) return result;
        }
        return result;
    }

    /** 从单个对象提取输出材料 */
    private static MaterialId extractOutputMaterialFrom(Object obj) {
        if (obj == null) return null;

        // 方法
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

        // 字段
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

    /** 拿 getRecipes() 结果 */
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
    // ===== 3. 部件收集 ==========================================
    // ============================================================

    private static List<PartInfo> collectPartsFor(MaterialId materialId) {
        List<PartInfo> result = new ArrayList<>();
        if (materialId == null) return result;
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

            // ★ 多层判定替代单纯的 canUseMaterial
            if (!isPartUsable(registry, materialId, mi)) continue;

            try {
                MaterialStatsId statType = inferStatType(mi);
                PartProperties properties = buildProperties(registry, materialId, statType);
                int requiredAmount = CastingRecipeHelper.getRequiredAmountForPart(id);

                ItemStack displayStack = new ItemStack(item);
                displayStack.getOrCreateTag().putString("Material", materialId.toString());

                result.add(new PartInfo(id, item, displayName, materialId, statType,
                        properties, requiredAmount, displayStack));
            } catch (Exception ignored) {}
        }

        result.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
        return result;
    }

    /**
     * 判断某个部件能否用于某材料。三层判定，逐层放宽：
     *
     *   层 1：canUseMaterial        —— TConstruct 官方判定（首选）
     *   层 2：statType + getMaterialStats
     *                              —— 绕过 canUseMaterial 直查 registry
     *   层 3：推断不出 statType     —— 整合包可能加了非常规部件，宽松放行
     *
     * 层 2 存在但 registry 里没有该 stat 时返回 false，因为那时材料确实不支持这类部件。
     */
    private static boolean isPartUsable(IMaterialRegistry registry, MaterialId materialId, IMaterialItem mi) {
        // 层 1
        try {
            if (mi.canUseMaterial(materialId)) return true;
        } catch (Throwable ignored) {
            // canUseMaterial 在某些整合包实现里可能抛异常，忽略，继续下层
        }

        // 层 2
        MaterialStatsId statType = inferStatType(mi);
        if (statType != null) {
            try {
                if (registry.getMaterialStats(materialId, statType).isPresent()) return true;
            } catch (Throwable ignored) {}
            // statType 已经能推断，但 registry 里没有对应数据
            // → 材料确实不支持这类部件，不放行
            return false;
        }

        // 层 3：statType 完全推断不出（整合包自定义部件路径不匹配原版规则）
        // → 无法判断，宽松放行
        return true;
    }

    private static boolean isToolPartPath(String path) {
        if (path.endsWith("_cast") || path.startsWith("cast_") || path.contains("plate_cast")) return false;
        for (String kw : HARD_EXCLUDE) if (path.contains(kw)) return false;
        return true;
    }

    private static PartProperties buildProperties(IMaterialRegistry registry, MaterialId materialId, MaterialStatsId statType) {
        List<ModifierInfo> modifiers = getModifiers(materialId, statType);
        if (statType == null) return new PartProperties(new ArrayList<>(), modifiers);

        Optional<IMaterialStats> opt = registry.getMaterialStats(materialId, statType);
        if (!opt.isPresent()) return new PartProperties(new ArrayList<>(), modifiers);

        List<Component> statLines = new ArrayList<>();
        try {
            List<Component> loc = opt.get().getLocalizedInfo();
            if (loc != null) statLines.addAll(loc);
        } catch (Exception ignored) {}
        return new PartProperties(statLines, modifiers);
    }

    // ============================================================
    // ===== 4. StatType 推断 =====================================
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

        for (String fn : new String[]{"statType", "statsType", "statTypeId"}) {
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
    // ===== 5. 词条读取 ==========================================
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
        } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}
        return null;
    }

    // ============================================================
    // ===== 流体提取 =============================================
    // ============================================================

    private static FluidStack extractRecipeFluid(Object recipe) {
        if (recipe == null) return null;

        // 直接找
        FluidStack direct = tryExtractFluidDirect(recipe);
        if (direct != null) return direct;

        // 从 materialFluid 找
        Object mf = findFieldValue(recipe,
                "materialFluid", "materialFluidRecipe", "fluidRecipe", "materialRecipe");
        if (mf != null) {
            FluidStack viaMf = tryExtractFluidDirect(mf);
            if (viaMf != null) return viaMf;
            // materialFluid 可能又有一个 fluid 字段
            Object innerFluid = findFieldValue(mf, "fluid", "inputFluid");
            FluidStack f2 = fluidFromIngredient(innerFluid);
            if (f2 != null) return f2;
        }

        // 从子配方找
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

        // 递归到内部的 fluid 字段
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
            if (m != null) return getMaterialDisplayName(m);
        } catch (Exception ignored) {}
        String path = materialId.getPath();
        if (path.isEmpty()) return "";
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    private static String getMaterialDisplayName(IMaterial material) {
        if (material == null) return "";
        try {
            Method m = material.getClass().getMethod("getDisplayName");
            m.setAccessible(true);
            Object v = m.invoke(material);
            if (v instanceof Component) {
                String s = ((Component) v).getString();
                if (s != null && !s.isEmpty()) return s;
            }
        } catch (Exception ignored) {}
        String path = material.getIdentifier().getPath();
        if (path.isEmpty()) return "";
        return Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

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

    private static boolean hasUsablePart(MaterialId materialId) {
        if (materialId == null) return false;
        String key = materialId.toString();
        Boolean cached = USABLE_CACHE.get(key);
        if (cached != null) return cached;

        boolean found = false;
        for (Item item : ForgeRegistries.ITEMS) {
            if (!(item instanceof IMaterialItem)) continue;
            try {
                if (((IMaterialItem) item).canUseMaterial(materialId)) { found = true; break; }
            } catch (Exception ignored) {}
        }
        USABLE_CACHE.put(key, found);
        return found;
    }
}