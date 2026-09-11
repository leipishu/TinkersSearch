package top.leipishu.tinkerssearch.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
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
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;
import slimeknights.tconstruct.tools.stats.LimbMaterialStats;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 部件属性辅助类
 *
 * 关键修复：
 * - **每个部件只读取它自己 statType 对应的那一个统计类型**
 * - 不再混合 Head / Handle / Limb 的所有属性
 * - 数值型与倍率型属性分开存储
 */
public class PartPropertyHelper {

    private static final boolean DEBUG_PARTS = false;

    // ===== 材料反查缓存 =====
    private static final Map<ResourceLocation, IMaterial> materialLookupCache = new HashMap<>();
    private static final IMaterial NULL_SENTINEL = IMaterial.UNKNOWN;

    public static void clearMaterialCache() {
        materialLookupCache.clear();
    }

    // ==================== 数据类 ====================

    public static class PartInfo {
        public final String partName;
        public final PartProperties properties;
        public final ResourceLocation partId;
        public final int requiredAmount;
        public final ItemStack displayStack;

        public PartInfo(String name, PartProperties props, ResourceLocation id, int requiredAmount, ItemStack displayStack) {
            this.partName = name;
            this.properties = props;
            this.partId = id;
            this.requiredAmount = requiredAmount;
            this.displayStack = displayStack;
        }
    }

    public static class ModifierInfo {
        public final String name;
        public final int level;
        public final String description;

        public ModifierInfo(String name, int level, String description) {
            this.name = name;
            this.level = level;
            this.description = description;
        }
    }

    /**
     * 部件属性
     *
     * - numeric*：数值型（直接显示）
     * - multiplierStats：倍率型（显示为 ×N.NN）
     */
    public static class PartProperties {
        public final int durability;
        public final String harvestTierKey;
        public final float attackDamage;
        public final float miningSpeed;
        public final Map<String, Float> multiplierStats;  // 倍率型
        public final List<ModifierInfo> modifiers;
        public final String statsTypeId;  // 用于调试

        public PartProperties(int durability, String harvestTierKey, float attackDamage, float miningSpeed,
                              Map<String, Float> multiplierStats,
                              List<ModifierInfo> modifiers, String statsTypeId) {
            this.durability = durability;
            this.harvestTierKey = harvestTierKey;
            this.attackDamage = attackDamage;
            this.miningSpeed = miningSpeed;
            this.multiplierStats = multiplierStats != null ? multiplierStats : new LinkedHashMap<>();
            this.modifiers = modifiers != null ? modifiers : new ArrayList<>();
            this.statsTypeId = statsTypeId;
        }

        public boolean hasStats() {
            return durability > 0 || (harvestTierKey != null && !harvestTierKey.isEmpty())
                    || attackDamage > 0 || miningSpeed > 0
                    || !multiplierStats.isEmpty() || !modifiers.isEmpty();
        }

        public String formatNumericStats() {
            List<String> parts = new ArrayList<>();
            if (durability > 0) parts.add("§a耐久§f" + durability);
            if (attackDamage > 0) parts.add("§a伤害§f" + String.format("%.1f", attackDamage));
            if (miningSpeed > 0) parts.add("§a速度§f" + String.format("%.1f", miningSpeed));
            if (harvestTierKey != null && !harvestTierKey.isEmpty()) {
                parts.add("§a等级§f" + new TranslatableComponent(harvestTierKey).getString());
            }
            return String.join(" ", parts);
        }

        public String formatModifierStats() {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Float> entry : multiplierStats.entrySet()) {
                String fieldName = entry.getKey();
                float value = entry.getValue();
                if (Math.abs(value - 1.0f) < 0.001f) continue;
                String displayName = getShortModifierName(fieldName);
                parts.add("§a" + displayName + "§f×" + String.format("%.2f", value));
            }
            return String.join(" ", parts);
        }

        public String formatModifiers() {
            if (modifiers.isEmpty()) return "";
            List<String> parts = new ArrayList<>();
            for (ModifierInfo mod : modifiers) {
                parts.add("§b" + mod.name + (mod.level > 1 ? " " + mod.level : ""));
            }
            return String.join("§7, ", parts);
        }

        private static String getShortModifierName(String fieldName) {
            switch (fieldName) {
                case "durability": return "耐";
                case "miningSpeed": return "挖";
                case "attackSpeed": return "攻速";
                case "attackDamage": return "攻伤";
                case "drawSpeed": return "拉弓";
                case "velocity": return "弹速";
                case "accuracy": return "精度";
                default: return fieldName;
            }
        }
    }

    private static final String[] HARD_EXCLUDE_KEYWORDS = {
            "ingot", "nugget", "gem", "rod", "coin", "wire", "gear"
    };

    // ==================== 核心方法 ====================

    public static List<PartInfo> getPartsForFluid(FluidStack fluidStack) {
        List<PartInfo> result = new ArrayList<>();
        if (fluidStack == null || fluidStack.isEmpty()) return result;

        IMaterial material = getMaterialForFluid(fluidStack.getFluid());
        if (material == null || material == NULL_SENTINEL) return result;

        MaterialId materialId = material.getIdentifier();
        IMaterialRegistry registry = MaterialRegistry.getInstance();

        Set<ResourceLocation> seenIds = new HashSet<>();
        Set<String> seenNames = new HashSet<>();

        for (Item item : ForgeRegistries.ITEMS) {
            if (!(item instanceof IMaterialItem)) continue;
            IMaterialItem materialItem = (IMaterialItem) item;

            try {
                ResourceLocation id = item.getRegistryName();
                if (id == null || !seenIds.add(id)) continue;

                String path = id.getPath().toLowerCase();
                if (!isToolPartPath(path)) continue;

                String displayName = item.getDescription().getString();
                if (displayName.isEmpty() || !seenNames.add(displayName)) continue;

                boolean canUse = false;
                try {
                    canUse = materialItem.canUseMaterial(materialId);
                } catch (Exception ignored) {}

                if (!canUse) continue;

                // ===== 关键：传入 materialItem，只读它自己的 statType =====
                PartProperties properties = getPropertiesForPart(registry, materialId, materialItem);

                int requiredAmount = CastingRecipeHelper.getRequiredAmountForPart(id);

                ItemStack displayStack = new ItemStack(item);
                try {
                    displayStack.getOrCreateTag().putString("Material", materialId.toString());
                } catch (Exception ignored) {}

                result.add(new PartInfo(displayName, properties, id, requiredAmount, displayStack));
            } catch (Exception ignored) {}
        }

        result.sort((a, b) -> a.partName.compareToIgnoreCase(b.partName));

        if (DEBUG_PARTS) {
            System.out.println("[Tinker's Search] Found " + result.size() + " parts");
        }
        return result;
    }

    private static boolean isToolPartPath(String path) {
        if (path.endsWith("_cast") || path.startsWith("cast_") || path.contains("plate_cast")) return false;
        for (String kw : HARD_EXCLUDE_KEYWORDS) {
            if (path.contains(kw)) return false;
        }
        return true;
    }

    // ============================================================
    // ===== 关键修复：根据部件的 statType 只读一种统计 ============
    // ============================================================

    /**
     * 获取部件属性
     *
     * 核心：通过反射获取部件自己的 statType（如 "tconstruct:head"、"tconstruct:handle"），
     * 只读那一种统计类型，不混合。
     */
    private static PartProperties getPropertiesForPart(IMaterialRegistry registry, MaterialId materialId, IMaterialItem item) {
        // ===== 1. 获取部件的 statType =====
        MaterialStatsId statType = getPartStatType(item);

        if (statType == null) {
            if (DEBUG_PARTS) {
                System.out.println("[Tinker's Search] No statType for: " + item);
            }
            return new PartProperties(0, "", 0, 0, new LinkedHashMap<>(), new ArrayList<>(), "unknown");
        }

        // ===== 2. 只读对应类型的统计 =====
        Optional<IMaterialStats> opt = registry.getMaterialStats(materialId, statType);
        if (!opt.isPresent()) {
            return new PartProperties(0, "", 0, 0, new LinkedHashMap<>(), new ArrayList<>(), statType.toString());
        }

        IMaterialStats stats = opt.get();

        // ===== 3. 根据具体类型解析属性 =====
        int durability = 0;
        float attackDamage = 0;
        float miningSpeed = 0;
        String harvestTierKey = "";
        Map<String, Float> multiplierStats = new LinkedHashMap<>();

        if (stats instanceof HeadMaterialStats) {
            // 头部：全部数值型
            HeadMaterialStats hs = (HeadMaterialStats) stats;
            durability = hs.getDurability();
            miningSpeed = hs.getMiningSpeed();
            attackDamage = hs.getAttack();
            Tier tier = hs.getTier();
            if (tier != null) harvestTierKey = getTierTranslationKey(tier);
        } else if (stats instanceof HandleMaterialStats) {
            // 手柄：全部倍率型
            multiplierStats = extractAllFloatFields(stats);
        } else if (stats instanceof LimbMaterialStats) {
            // 肢体：durability 数值型，其他倍率型
            LimbMaterialStats ls = (LimbMaterialStats) stats;
            durability = ls.getDurability();
            Map<String, Float> all = extractAllFloatFields(stats);
            all.remove("durability");  // 已作为数值型
            multiplierStats = all;
        } else {
            // 通用：反射所有字段，按字段名/值判断数值型 vs 倍率型
            Map<String, Float> all = extractAllFloatFields(stats);
            for (Map.Entry<String, Float> e : all.entrySet()) {
                String k = e.getKey();
                float v = e.getValue();

                if (k.equals("durability") || k.equals("durabilityModifier")) {
                    // 值 > 5 视为数值型（如 210），否则视为倍率型（如 1.10）
                    if (v > 5) durability = (int) v;
                    else multiplierStats.put(k, v);
                } else if (k.equals("attack") || k.equals("attackDamage") || k.equals("damage")) {
                    if (v > 3) attackDamage = v;
                    else multiplierStats.put(k, v);
                } else if (k.equals("miningSpeed") || k.equals("speed")) {
                    if (v > 3) miningSpeed = v;
                    else multiplierStats.put(k, v);
                } else if (k.equals("harvestTier")) {
                    harvestTierKey = getTierKeyFromInt((int) v);
                } else if (k.equals("tier")) {
                    // 某些统计类型的字段名是 tier
                    if (v < 10) harvestTierKey = getTierKeyFromInt((int) v);
                } else {
                    // 其他字段一律按倍率型处理
                    multiplierStats.put(k, v);
                }
            }
        }

        List<ModifierInfo> modifiers = getModifiersForMaterial(materialId);

        return new PartProperties(durability, harvestTierKey, attackDamage, miningSpeed,
                multiplierStats, modifiers, statType.toString());
    }

    /**
     * 反射获取部件的 statType
     *
     * 优先级：getStatType() 方法 > statsType/statType 字段
     */
    private static MaterialStatsId getPartStatType(IMaterialItem item) {
        if (item == null) return null;
        Class<?> clazz = item.getClass();

        // ===== 1. 尝试方法 =====
        for (String mn : new String[]{"getStatType", "getStatsType", "getStatTypeId"}) {
            try {
                Method m = clazz.getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(item);
                if (v instanceof MaterialStatsId) return (MaterialStatsId) v;
            } catch (Exception ignored) {}
        }

        // ===== 2. 尝试字段（含父类）=====
        for (String fn : new String[]{"statType", "statsType", "statTypeId"}) {
            try {
                Class<?> c = clazz;
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

        // ===== 3. 兜底：根据类名/路径猜测 =====
        // （一般不会走到这里，因为 ToolPartItem 都有 statType）
        try {
            String className = clazz.getName().toLowerCase();
            String itemPath = item.asItem().getRegistryName() != null
                    ? item.asItem().getRegistryName().getPath().toLowerCase()
                    : "";

            String pathToCheck = itemPath.isEmpty() ? className : itemPath;

            if (pathToCheck.contains("head") || pathToCheck.contains("blade")
                    || pathToCheck.contains("axe") || pathToCheck.contains("pick")
                    || pathToCheck.contains("sword") || pathToCheck.contains("dagger")
                    || pathToCheck.contains("hammer")) {
                return HeadMaterialStats.ID;
            }
            if (pathToCheck.contains("handle") || pathToCheck.contains("binding")
                    || pathToCheck.contains("grip")) {
                return HandleMaterialStats.ID;
            }
            if (pathToCheck.contains("limb") || pathToCheck.contains("bow")
                    || pathToCheck.contains("arm")) {
                return LimbMaterialStats.ID;
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static Map<String, Float> extractAllFloatFields(Object stats) {
        Map<String, Float> result = new LinkedHashMap<>();
        if (stats == null) return result;
        Class<?> clazz = stats.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (field.getType() != float.class) continue;
                field.setAccessible(true);
                try {
                    result.put(field.getName(), field.getFloat(stats));
                } catch (Exception ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
        return result;
    }

    private static String getTierKeyFromInt(int tier) {
        switch (tier) {
            case 0: return "gui.tinkerssearch.tier.wood";
            case 1: return "gui.tinkerssearch.tier.stone";
            case 2: return "gui.tinkerssearch.tier.iron";
            case 3: return "gui.tinkerssearch.tier.diamond";
            case 4: return "gui.tinkerssearch.tier.netherite";
            default: return "gui.tinkerssearch.tier.iron";
        }
    }

    private static String getTierTranslationKey(Tier tier) {
        if (tier == null) return "";
        if (tier == Tiers.WOOD) return "gui.tinkerssearch.tier.wood";
        if (tier == Tiers.STONE) return "gui.tinkerssearch.tier.stone";
        if (tier == Tiers.IRON) return "gui.tinkerssearch.tier.iron";
        if (tier == Tiers.DIAMOND) return "gui.tinkerssearch.tier.diamond";
        if (tier == Tiers.NETHERITE) return "gui.tinkerssearch.tier.netherite";
        if (tier == Tiers.GOLD) return "gui.tinkerssearch.tier.gold";
        String str = tier.toString().toLowerCase();
        if (str.contains(":")) {
            String[] parts = str.split(":");
            if (parts.length >= 2) return "gui.tinkerssearch.tier." + parts[1].replaceAll("[^a-z0-9_]", "");
        }
        return "gui.tinkerssearch.tier." + str.replaceAll("[^a-z0-9_]", "");
    }

    // ============================================================
    // ===== 材料反查（带缓存） ===================================
    // ============================================================

    private static IMaterial getMaterialForFluid(Fluid fluid) {
        if (fluid == null) return null;
        ResourceLocation fluidId = fluid.getRegistryName();
        if (fluidId == null) return null;

        if (materialLookupCache.containsKey(fluidId)) {
            IMaterial cached = materialLookupCache.get(fluidId);
            return cached == NULL_SENTINEL ? null : cached;
        }

        IMaterial result = resolveMaterialForFluid(fluid, fluidId);
        materialLookupCache.put(fluidId, result != null ? result : NULL_SENTINEL);
        return result;
    }

    private static IMaterial resolveMaterialForFluid(Fluid fluid, ResourceLocation fluidId) {
        // 方案1：CastingRecipeHelper 映射
        try {
            ResourceLocation matId = CastingRecipeHelper.getMaterialIdForFluid(fluid);
            if (matId != null) {
                IMaterial material = MaterialRegistry.getInstance().getMaterial(new MaterialId(matId));
                if (material != null && material != IMaterial.UNKNOWN) return material;
            }
        } catch (Exception ignored) {}

        // 方案2：molten_xxx 前缀反推
        String fluidPath = fluidId.getPath();
        String[] prefixes = {"molten_", "liquid_", "fluid_"};
        String materialName = null;
        for (String prefix : prefixes) {
            if (fluidPath.startsWith(prefix)) {
                materialName = fluidPath.substring(prefix.length());
                break;
            }
        }
        if (materialName == null) materialName = fluidPath;

        String[] namespaces = {fluidId.getNamespace(), "tconstruct", "kubejs", "crafttweaker", "minecraft"};
        for (String ns : namespaces) {
            try {
                MaterialId guessedId = new MaterialId(ns, materialName);
                IMaterial guessed = MaterialRegistry.getInstance().getMaterial(guessedId);
                if (guessed != null && guessed != IMaterial.UNKNOWN) return guessed;
            } catch (Exception ignored) {}
        }

        // 方案3：遍历材料反射 fluid 字段
        try {
            IMaterialRegistry registry = MaterialRegistry.getInstance();
            for (IMaterial material : registry.getAllMaterials()) {
                FluidStack mf = getFluidForMaterial(material);
                if (mf != null && !mf.isEmpty()) {
                    ResourceLocation rid = mf.getFluid().getRegistryName();
                    if (fluidId.equals(rid)) return material;
                }
            }
        } catch (Exception ignored) {}

        // 方案4：模糊匹配
        try {
            IMaterialRegistry registry = MaterialRegistry.getInstance();
            String lowerFluidPath = fluidPath.toLowerCase();
            for (IMaterial material : registry.getAllMaterials()) {
                String matPath = material.getIdentifier().getPath().toLowerCase();
                if (matPath.isEmpty()) continue;
                if (lowerFluidPath.contains(matPath) || matPath.contains(lowerFluidPath)) return material;
            }
        } catch (Exception ignored) {}

        return null;
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

    private static List<ModifierInfo> getModifiersForMaterial(MaterialId materialId) {
        List<ModifierInfo> result = new ArrayList<>();
        try {
            Class<?> traitsManagerClass = Class.forName("slimeknights.tconstruct.library.materials.traits.MaterialTraitsManager");
            Method getInstance = traitsManagerClass.getMethod("getInstance");
            Object traitsManager = getInstance.invoke(null);
            if (traitsManager != null) {
                Method getTraits = traitsManager.getClass().getMethod("getTraits", MaterialId.class);
                Object traitsObj = getTraits.invoke(traitsManager, materialId);
                if (traitsObj instanceof List) {
                    for (Object trait : (List<?>) traitsObj) {
                        try {
                            Method getName = trait.getClass().getMethod("getName");
                            Method getLevel = trait.getClass().getMethod("getLevel");
                            String name = (String) getName.invoke(trait);
                            int level = (int) getLevel.invoke(trait);
                            result.add(new ModifierInfo(name, level, ""));
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    public static boolean hasParts(FluidStack fluidStack) {
        return !getPartsForFluid(fluidStack).isEmpty();
    }
}