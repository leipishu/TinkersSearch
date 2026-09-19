package top.leipishu.tinkerssearch.data;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.IMaterialRegistry;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.stats.IMaterialStats;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

import top.leipishu.tinkerssearch.data.FluidPartData.MaterialEntry;
import top.leipishu.tinkerssearch.data.FluidPartData.ModifierInfo;
import top.leipishu.tinkerssearch.data.FluidPartData.PartInfo;
import top.leipishu.tinkerssearch.data.FluidPartData.PartProperties;
import top.leipishu.tinkerssearch.recipe.CastingRecipeHelper;
import top.leipishu.tinkerssearch.recipe.CastingRecipeHelper.PartTypeRef;
import top.leipishu.tinkerssearch.recipe.MaterialCompatibility;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FluidPartDataCache {

    private static final Map<ResourceLocation, FluidPartData> CACHE = new ConcurrentHashMap<>();
    private static volatile long buildGeneration = 0L;
    private static final Map<MaterialId, List<PartInfo>> PARTS_CACHE = new ConcurrentHashMap<>();
    private static volatile GlobalIndex INDEX = null;
    private static final Object INDEX_LOCK = new Object();

    /**
     * 非部件物品（模具、图案）——这些不应作为部件展示。
     * 修补件已从此列表中移除：它是合法部件，即使一页里只有它也应该显示。
     */
    private static final String[] NON_PART_KEYWORDS = {
            "pattern", "sand_cast", "red_sand_cast", "gold_cast", "plate_cast"
    };

    private static final String[] FLUID_PREFIXES = {"molten_", "liquid_", "fluid_"};
    private static final String[] ALT_NAMESPACES = {"tconstruct", "forge"};

    /** 缓存 {@code IMaterialItem} 类的 {@code withMaterial} 方法 */
    private static final Map<Class<?>, Method> WITH_MATERIAL_CACHE = new ConcurrentHashMap<>();

    public static void invalidate() {
        buildGeneration++;
        CACHE.clear();
        PARTS_CACHE.clear();
        INDEX = null;
        WITH_MATERIAL_CACHE.clear();
    }

    // ============================================================
    // ===== 全局索引 =============================================
    // ============================================================

    private static final class GlobalIndex {
        final Map<Fluid, MaterialId> meltMap = new HashMap<>();
        final Map<Fluid, List<CompositeEntry>> compositeMap = new HashMap<>();
        final List<PartTypeRef> castableParts = new ArrayList<>();
    }

    private static final class CompositeEntry {
        final MaterialId output;
        final MaterialId input;
        CompositeEntry(MaterialId o, MaterialId i) { output = o; input = i; }
    }

    private static GlobalIndex getIndex() {
        GlobalIndex cached = INDEX;
        if (cached != null) return cached;
        synchronized (INDEX_LOCK) {
            if (INDEX != null) return INDEX;
            INDEX = buildIndex();
            return INDEX;
        }
    }

    private static GlobalIndex buildIndex() {
        GlobalIndex idx = new GlobalIndex();
        long t0 = System.currentTimeMillis();

        // ===== 1. 部件类型（注册表）=====
        int partCount = 0;
        for (Item item : ForgeRegistries.ITEMS) {
            if (!(item instanceof IMaterialItem)) continue;
            ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item);
            if (rl == null || isNonPartItem(rl.getPath())) continue;
            idx.castableParts.add(new PartTypeRef(rl, (IMaterialItem) item, 90));
            partCount++;
        }

        // ===== 2. MFR 扫描 =====
        int mfrCount = 0, meltCount = 0, compCount = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            for (Recipe<?> recipe : mc.getConnection().getRecipeManager().getRecipes()) {
                try {
                    String cn = recipe.getClass().getName().toLowerCase();
                    if (!cn.contains("materialfluid")) continue;
                    mfrCount++;

                    FluidStack fluid = extractFluid(recipe);
                    if (fluid == null || fluid.isEmpty()) continue;

                    MaterialId out = extractMaterialId(recipe);
                    if (out == null) continue;

                    List<MaterialId> ins = extractMaterialList(recipe);

                    if (ins.isEmpty()) {
                        MaterialId existing = idx.meltMap.get(fluid.getFluid());
                        if (existing == null || isBetterMeltCandidate(out, fluid, existing)) {
                            idx.meltMap.put(fluid.getFluid(), out);
                        }
                        meltCount++;
                    } else {
                        List<CompositeEntry> list = idx.compositeMap.computeIfAbsent(
                                fluid.getFluid(), k -> new ArrayList<>());
                        for (MaterialId in : ins) {
                            if (in == null || in.equals(out)) continue;
                            list.add(new CompositeEntry(out, in));
                        }
                        compCount++;
                    }
                } catch (Throwable ignored) {}
            }
        }

        System.out.println("[Tinker's Search] Global index: " + partCount + " partTypes, "
                + mfrCount + " MFRs (melt=" + meltCount + ", composite=" + compCount + ")"
                + " in " + (System.currentTimeMillis() - t0) + "ms");

        return idx;
    }

    private static boolean isBetterMeltCandidate(MaterialId candidate, FluidStack fluid,
                                                 MaterialId existing) {
        ResourceLocation fid = ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
        if (fid == null) return false;
        String path = fid.getPath();
        for (String prefix : FLUID_PREFIXES) {
            if (path.startsWith(prefix)) {
                String stripped = path.substring(prefix.length());
                return candidate.getPath().equals(stripped)
                        && !existing.getPath().equals(stripped);
            }
        }
        return false;
    }

    // ============================================================
    // ===== 提取 ==================================================
    // ============================================================

    private static FluidStack extractFluid(Object obj) {
        Object v = invokeMethod(obj, "getFluid");
        FluidStack fs = toFluidStack(v);
        if (fs != null && !fs.isEmpty()) return fs;

        for (String fn : new String[]{"fluid", "inputFluid", "fluidStack", "fluidIngredient"}) {
            Object f = findFieldValue(obj, fn);
            fs = toFluidStack(f);
            if (fs != null && !fs.isEmpty()) return fs;
        }
        return null;
    }

    private static FluidStack toFluidStack(Object v) {
        if (v == null) return null;
        if (v instanceof FluidStack) {
            FluidStack fs = (FluidStack) v;
            return fs.isEmpty() ? null : fs;
        }
        if (v instanceof Fluid) return new FluidStack((Fluid) v, 1000);

        for (String mn : new String[]{"getFluids", "getFluidStacks", "getMatchingFluids"}) {
            Object fluids = invokeMethod(v, mn);
            if (fluids instanceof Iterable) {
                for (Object o : (Iterable<?>) fluids) {
                    FluidStack fs = toFluidStack(o);
                    if (fs != null) return fs;
                }
            }
            if (fluids instanceof FluidStack[]) {
                for (FluidStack fs : (FluidStack[]) fluids) {
                    if (fs != null && !fs.isEmpty()) return fs;
                }
            }
            if (fluids instanceof Fluid) return new FluidStack((Fluid) fluids, 1000);
        }
        Object f = invokeMethod(v, "getFluid");
        if (f != null && f != v) return toFluidStack(f);
        return null;
    }

    private static MaterialId extractMaterialId(Object obj) {
        for (String mn : new String[]{"getOutput", "getOutputMaterial", "getResultMaterial"}) {
            MaterialId id = toMaterialIdStrict(invokeMethod(obj, mn));
            if (id != null) return id;
        }
        for (String fn : new String[]{"output", "outputMaterial", "resultMaterial"}) {
            MaterialId id = toMaterialIdStrict(findFieldValue(obj, fn));
            if (id != null) return id;
        }
        return null;
    }

    private static List<MaterialId> extractMaterialList(Object obj) {
        Object inputs = invokeMethod(obj, "getInputs");
        if (inputs == null) inputs = findFieldValue(obj, "inputs", "input", "inputMaterials");
        if (inputs == null) return Collections.emptyList();

        List<MaterialId> result = new ArrayList<>();
        collectMaterials(inputs, result);
        return result;
    }

    private static void collectMaterials(Object v, List<MaterialId> out) {
        if (v == null) return;
        MaterialId id = toMaterialIdStrict(v);
        if (id != null) { out.add(id); return; }
        if (v instanceof Iterable) {
            for (Object o : (Iterable<?>) v) collectMaterials(o, out);
            return;
        }
        for (String mn : new String[]{"getMatchingMaterials", "getMaterials",
                "getMatchingMaterialIds", "getMaterialIds"}) {
            Object inner = invokeMethod(v, mn);
            if (inner != null && inner != v) {
                collectMaterials(inner, out);
                if (!out.isEmpty()) return;
            }
        }
    }

    private static MaterialId toMaterialIdStrict(Object v) {
        if (v == null) return null;
        if (v instanceof MaterialId) return (MaterialId) v;
        if (v instanceof IMaterial) return ((IMaterial) v).getIdentifier();
        for (String mn : new String[]{"getIdentifier", "getId", "getMaterialId"}) {
            Object id = invokeMethod(v, mn);
            if (id instanceof MaterialId) return (MaterialId) id;
            if (id != null) {
                try { return new MaterialId(new ResourceLocation(id.toString())); }
                catch (Exception ignored) {}
            }
        }
        return null;
    }

    // ============================================================
    // ===== 对外入口 =============================================
    // ============================================================

    public static FluidPartData get(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.isEmpty()) {
            return new FluidPartData(null, new ArrayList<>(), buildGeneration);
        }
        ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluidStack.getFluid());
        if (fluidId == null) {
            return new FluidPartData(null, new ArrayList<>(), buildGeneration);
        }

        FluidPartData fresh = build(fluidStack.getFluid(), fluidId);

        FluidPartData cached = CACHE.get(fluidId);
        if (cached != null && cached.buildTime == buildGeneration) {
            boolean cachedHasBase = hasBaseEntry(cached);
            boolean freshHasBase = hasBaseEntry(fresh);
            if (cachedHasBase && !freshHasBase) return cached;
            if (countTotalParts(cached) > countTotalParts(fresh)) return cached;
        }

        CACHE.put(fluidId, fresh);
        return fresh;
    }

    private static int countTotalParts(FluidPartData data) {
        if (data == null || data.entries == null) return 0;
        int t = 0;
        for (MaterialEntry e : data.entries) if (e != null && e.parts != null) t += e.parts.size();
        return t;
    }

    private static boolean hasBaseEntry(FluidPartData data) {
        if (data == null || data.entries == null) return false;
        for (MaterialEntry e : data.entries) {
            if (e != null && e.kind == MaterialEntry.SourceKind.BASE) return true;
        }
        return false;
    }

    // ============================================================
    // ===== 主构建 ===============================================
    // ============================================================

    private static FluidPartData build(Fluid fluid, ResourceLocation fluidId) {
        GlobalIndex idx = getIndex();
        List<MaterialEntry> entries = new ArrayList<>();
        Set<String> pageKeys = new HashSet<>();

        MaterialId meltMat = idx.meltMap.get(fluid);
        if (meltMat == null) meltMat = inferMaterialFromFluid(fluid);

        System.out.println("[Tinker's Search] build(" + fluidId + "): meltMat=" + meltMat);

        if (meltMat != null) {
            List<PartInfo> parts = filterPartsForMaterial(idx, meltMat);
            if (isPageMeaningful(parts)) {
                String key = "BASE:" + meltMat;
                if (pageKeys.add(key)) {
                    entries.add(new MaterialEntry(meltMat,
                            getMaterialDisplayName(meltMat),
                            MaterialEntry.SourceKind.BASE, null, null, parts));
                    System.out.println("[Tinker's Search]   [BASE] "
                            + getMaterialDisplayName(meltMat) + " → " + parts.size() + " 部件");
                }
            } else {
                System.out.println("[Tinker's Search]   [SKIP BASE] 无有效部件");
            }
        }

        List<CompositeEntry> composites = idx.compositeMap.getOrDefault(fluid,
                Collections.emptyList());
        System.out.println("[Tinker's Search]   composites=" + composites.size());

        for (CompositeEntry ce : composites) {
            if (ce.output == null || ce.input == null) continue;
            if (ce.output.equals(ce.input)) continue;

            String key = "COMP:" + ce.output + "|" + ce.input;
            if (!pageKeys.add(key)) continue;

            List<PartInfo> parts = filterPartsForMaterial(idx, ce.output);
            if (!isPageMeaningful(parts)) continue;

            String title = getMaterialDisplayName(ce.output)
                    + " (" + getMaterialDisplayName(ce.input) + ")";

            entries.add(new MaterialEntry(ce.output, title,
                    MaterialEntry.SourceKind.COMPOSITE, ce.input,
                    getMaterialDisplayName(ce.input), parts));

            System.out.println("[Tinker's Search]   [COMPOSITE] " + title
                    + " → " + parts.size() + " 部件");
        }

        System.out.println("[Tinker's Search]   → total pages: " + entries.size());
        return new FluidPartData(fluidId, entries, buildGeneration);
    }

    // ============================================================
    // ===== 材料 → 部件 ==========================================
    // ============================================================

    private static List<PartInfo> filterPartsForMaterial(GlobalIndex idx, MaterialId mat) {
        if (mat == null) return new ArrayList<>();
        List<PartInfo> cached = PARTS_CACHE.get(mat);
        if (cached != null) return cached;

        List<PartInfo> result = new ArrayList<>();
        IMaterialRegistry registry = MaterialRegistry.getInstance();
        for (PartTypeRef ref : idx.castableParts) {
            if (!canMaterialProduce(registry, mat, ref.item)) continue;
            PartInfo info = buildPartInfo(ref, mat, registry);
            if (info != null) result.add(info);
        }
        result.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
        PARTS_CACHE.put(mat, result);
        return result;
    }

    private static boolean canMaterialProduce(IMaterialRegistry registry,
                                              MaterialId mat, IMaterialItem item) {
        if (mat == null || item == null) return false;
        try { if (item.canUseMaterial(mat)) return true; } catch (Throwable ignored) {}
        MaterialStatsId statType = MaterialCompatibility.inferStatType(item);
        if (statType != null) {
            try {
                if (registry.getMaterialStats(mat, statType).isPresent()) return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static PartInfo buildPartInfo(PartTypeRef ref, MaterialId mat,
                                          IMaterialRegistry registry) {
        try {
            Item item = ref.item.asItem();
            String displayName = getItemDisplayName(item);
            if (displayName.isEmpty()) return null;

            MaterialStatsId statType = MaterialCompatibility.inferStatType(ref.item);
            PartProperties props = buildProperties(mat, statType, registry);
            int amount = ref.amount > 0 ? ref.amount : 90;
            ItemStack displayStack = tryWithMaterial(ref.item, mat);
            if (displayStack.isEmpty()) return null;

            return new PartInfo(ref.itemId, item, displayName, mat, statType,
                    props, amount, displayStack);
        } catch (Throwable t) {
            return null;
        }
    }

    // ============================================================
    // ===== withMaterial（缓存 Method）===========================
    // ============================================================

    private static ItemStack tryWithMaterial(IMaterialItem mi, MaterialId mat) {
        if (mi == null || mat == null) return ItemStack.EMPTY;

        Method m = getWithMaterialMethod(mi);
        if (m != null) {
            try {
                Class<?>[] p = m.getParameterTypes();
                Object[] args = new Object[p.length];
                for (int i = 0; i < p.length; i++) {
                    if (p[i] == MaterialId.class) args[i] = mat;
                    else if (p[i] == boolean.class) args[i] = false;
                    else if (p[i] == ItemStack.class) args[i] = new ItemStack(mi.asItem());
                    else args[i] = null;
                }
                m.setAccessible(true);
                Object r = m.invoke(mi, args);
                if (r instanceof ItemStack && !((ItemStack) r).isEmpty()) return (ItemStack) r;
            } catch (Throwable ignored) {}
        }

        ItemStack s = new ItemStack(mi.asItem());
        try {
            CompoundTag tag = s.getOrCreateTag();
            tag.putString("Material", mat.toString());
        } catch (Throwable ignored) {}
        return s;
    }

    private static Method getWithMaterialMethod(IMaterialItem mi) {
        if (mi == null) return null;
        Class<?> c = mi.getClass();
        return WITH_MATERIAL_CACHE.computeIfAbsent(c, cls -> {
            for (String mn : new String[]{"withMaterialForDisplay", "withMaterial"}) {
                for (Method m : cls.getMethods()) {
                    if (!m.getName().equals(mn)) continue;
                    Class<?>[] p = m.getParameterTypes();
                    boolean hasMat = false;
                    for (Class<?> pc : p) if (pc == MaterialId.class) { hasMat = true; break; }
                    if (hasMat) return m;
                }
            }
            return null;
        });
    }

    // ============================================================
    // ===== 兜底：从流体名推断 ====================================
    // ============================================================

    private static MaterialId inferMaterialFromFluid(Fluid fluid) {
        if (fluid == null) return null;
        ResourceLocation fid = ForgeRegistries.FLUIDS.getKey(fluid);
        if (fid == null) return null;

        String ns = fid.getNamespace();
        String path = fid.getPath();

        String stripped = path;
        for (String prefix : FLUID_PREFIXES) {
            if (path.startsWith(prefix)) {
                String s = path.substring(prefix.length());
                if (!s.isEmpty()) { stripped = s; break; }
            }
        }

        MaterialId m = new MaterialId(ns, stripped);
        if (materialExists(m)) return m;

        for (String alt : ALT_NAMESPACES) {
            if (alt.equals(ns)) continue;
            m = new MaterialId(alt, stripped);
            if (materialExists(m)) return m;
        }

        if (!stripped.equals(path)) {
            m = new MaterialId(ns, path);
            if (materialExists(m)) return m;
        }
        return null;
    }

    private static boolean materialExists(MaterialId mat) {
        if (mat == null) return false;
        try {
            IMaterial m = MaterialRegistry.getInstance().getMaterial(mat);
            return m != null && m != IMaterial.UNKNOWN;
        } catch (Throwable t) { return false; }
    }

    // ============================================================
    // ===== 判定 =================================================
    // ============================================================

    /** ★ 只过滤真正的"非部件"（模具、图案）；修补件不再过滤。 */
    private static boolean isNonPartItem(String path) {
        if (path == null) return true;
        String p = path.toLowerCase();
        for (String kw : NON_PART_KEYWORDS) if (p.contains(kw)) return true;
        if (p.endsWith("_cast") || p.startsWith("cast_")) return true;
        return false;
    }

    /**
     * ★ 只要部件列表非空就认为是有意义的页面。
     *
     * <p>之前会剔除"只有修补件"的页面，导致玻璃等只能做修补件的材料
     * 在整个详情界面里什么都不显示。现在修补件是合法部件，正常显示。
     */
    private static boolean isPageMeaningful(List<PartInfo> parts) {
        return parts != null && !parts.isEmpty();
    }

    // ============================================================
    // ===== 属性 / 词条 ==========================================
    // ============================================================

    private static PartProperties buildProperties(MaterialId mat, MaterialStatsId statType,
                                                  IMaterialRegistry registry) {
        List<ModifierInfo> modifiers = getModifiers(mat, statType);
        if (statType == null) return new PartProperties(new ArrayList<>(), modifiers);
        try {
            Optional<IMaterialStats> opt = registry.getMaterialStats(mat, statType);
            if (!opt.isPresent()) return new PartProperties(new ArrayList<>(), modifiers);
            List<Component> stats = opt.get().getLocalizedInfo();
            return new PartProperties(stats != null ? stats : new ArrayList<>(), modifiers);
        } catch (Throwable t) {
            return new PartProperties(new ArrayList<>(), modifiers);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ModifierInfo> getModifiers(MaterialId mat, MaterialStatsId statType) {
        List<ModifierInfo> result = new ArrayList<>();
        if (mat == null) return result;
        try {
            Object tm = getTraitsManager();
            if (tm == null) return result;
            List<ModifierEntry> entries = null;
            if (statType != null) {
                try {
                    Method m = tm.getClass().getMethod("getTraits",
                            MaterialId.class, MaterialStatsId.class);
                    m.setAccessible(true);
                    entries = (List<ModifierEntry>) m.invoke(tm, mat, statType);
                } catch (Exception ignored) {}
            }
            if ((entries == null || entries.isEmpty()) && statType != null) {
                try {
                    Method m = tm.getClass().getMethod("getDefaultTraits", MaterialId.class);
                    m.setAccessible(true);
                    entries = (List<ModifierEntry>) m.invoke(tm, mat);
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
                // ★ 1.18.2：使用 new TextComponent(...)
                displayName = new TextComponent(id.isEmpty()
                        ? modifier.getClass().getSimpleName() : id);
            }
            List<Component> desc = new ArrayList<>();
            for (String mn : new String[]{"getDescriptionList", "getDescription"}) {
                try {
                    Method m = modifier.getClass().getMethod(mn);
                    m.setAccessible(true);
                    Object v = m.invoke(modifier);
                    if (v instanceof List) {
                        for (Object line : (List<?>) v)
                            if (line instanceof Component) desc.add((Component) line);
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
    // ===== 反射 =================================================
    // ============================================================

    private static Object invokeMethod(Object obj, String name) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod(name);
            if (m.getParameterCount() != 0) return null;
            m.setAccessible(true);
            return m.invoke(obj);
        } catch (Throwable ignored) {}
        return null;
    }

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

    // ============================================================
    // ===== 名称 =================================================
    // ============================================================

    private static String getMaterialDisplayName(MaterialId mat) {
        if (mat == null) return "";
        try {
            IMaterial m = MaterialRegistry.getInstance().getMaterial(mat);
            if (m != null && m != IMaterial.UNKNOWN) {
                Method dm = m.getClass().getMethod("getDisplayName");
                dm.setAccessible(true);
                Object v = dm.invoke(m);
                if (v instanceof Component) {
                    String s = ((Component) v).getString();
                    if (!s.isEmpty()) return s;
                }
            }
        } catch (Throwable ignored) {}
        return defaultDisplayName(mat.getPath());
    }

    private static String getItemDisplayName(Item item) {
        if (item == null) return "";
        try {
            String s = item.getDescription().getString();
            if (!s.isEmpty()) return s;
        } catch (Throwable ignored) {}
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(item);
        return rl != null ? defaultDisplayName(rl.getPath()) : "";
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
}