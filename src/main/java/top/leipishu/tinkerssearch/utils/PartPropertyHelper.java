package top.leipishu.tinkerssearch.utils;

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
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;
import slimeknights.tconstruct.tools.stats.LimbMaterialStats;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 部件属性辅助类
 */
public class PartPropertyHelper {

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

    public static class PartProperties {
        public final int durability;
        public final String harvestTierKey;
        public final float attackDamage;
        public final float miningSpeed;
        public final Map<String, Float> modifierStats;
        public final List<ModifierInfo> modifiers;
        private final String statsType;

        public PartProperties(int durability, String harvestTierKey, float attackDamage, float miningSpeed,
                              Map<String, Float> modifierStats,
                              List<ModifierInfo> modifiers, String statsType) {
            this.durability = durability;
            this.harvestTierKey = harvestTierKey;
            this.attackDamage = attackDamage;
            this.miningSpeed = miningSpeed;
            this.modifierStats = modifierStats != null ? modifierStats : new LinkedHashMap<>();
            this.modifiers = modifiers != null ? modifiers : new ArrayList<>();
            this.statsType = statsType;
        }

        public boolean hasStats() {
            return durability > 0
                    || (harvestTierKey != null && !harvestTierKey.isEmpty())
                    || attackDamage > 0 || miningSpeed > 0
                    || !modifierStats.isEmpty()
                    || !modifiers.isEmpty();
        }

        /**
         * 数值型属性（紧凑格式，减少宽度）
         */
        public String formatNumericStats() {
            List<String> parts = new ArrayList<>();
            if (durability > 0) {
                parts.add("§a耐久§f" + durability);
            }
            if (attackDamage > 0) {
                parts.add("§a伤害§f" + String.format("%.1f", attackDamage));
            }
            if (miningSpeed > 0) {
                parts.add("§a速度§f" + String.format("%.1f", miningSpeed));
            }
            if (harvestTierKey != null && !harvestTierKey.isEmpty()) {
                parts.add("§a等级§f" + new TranslatableComponent(harvestTierKey).getString());
            }
            return String.join(" ", parts);
        }

        /**
         * 倍率型属性（紧凑格式，减少宽度）
         */
        public String formatModifierStats() {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Float> entry : modifierStats.entrySet()) {
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

        /**
         * 短标签（压缩宽度）
         */
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

    // ==================== 关键词 ====================

    private static final String[] EXCLUDE_KEYWORDS = {
            "ingot", "nugget", "gem", "rod", "coin", "wire", "gear",
            "block", "ore", "raw", "dust", "plate_cast", "cast_"
    };

    // ==================== 核心方法 ====================

    public static List<PartInfo> getPartsForFluid(FluidStack fluidStack) {
        List<PartInfo> result = new ArrayList<>();

        if (fluidStack == null || fluidStack.isEmpty()) return result;

        IMaterial material = getMaterialForFluid(fluidStack.getFluid());
        if (material == null) return result;

        MaterialId materialId = material.getIdentifier();
        IMaterialRegistry registry = MaterialRegistry.getInstance();

        Set<ResourceLocation> seenIds = new HashSet<>();
        Set<String> seenNames = new HashSet<>();

        for (Item item : ForgeRegistries.ITEMS) {
            if (!(item instanceof IMaterialItem)) continue;
            IMaterialItem materialItem = (IMaterialItem) item;

            try {
                if (!materialItem.canUseMaterial(materialId)) continue;

                ResourceLocation id = item.getRegistryName();
                if (id == null) continue;
                if (!seenIds.add(id)) continue;

                String path = id.getPath().toLowerCase();

                if (!isToolPartPath(path)) continue;

                String displayName = item.getDescription().getString();
                if (displayName.isEmpty()) continue;
                if (!seenNames.add(displayName)) continue;

                PartProperties properties = getPropertiesForPart(registry, materialId, path);
                int requiredAmount = CastingRecipeHelper.getRequiredAmountForPart(id);

                // ===== 构造带材料染色的图标 =====
                ItemStack displayStack = new ItemStack(item);
                try {
                    displayStack.getOrCreateTag().putString("Material", materialId.toString());
                } catch (Exception ignored) {}

                result.add(new PartInfo(displayName, properties, id, requiredAmount, displayStack));
            } catch (Exception ignored) {}
        }

        result.sort((a, b) -> a.partName.compareToIgnoreCase(b.partName));
        return result;
    }

    private static boolean isToolPartPath(String path) {
        for (String kw : EXCLUDE_KEYWORDS) {
            if (path.contains(kw)) return false;
        }
        return true;
    }

    private static PartProperties getPropertiesForPart(IMaterialRegistry registry, MaterialId materialId, String path) {
        int durability = 0;
        String harvestTierKey = "";
        float attackDamage = 0;
        float miningSpeed = 0;
        Map<String, Float> modifierStats = new LinkedHashMap<>();
        List<ModifierInfo> modifiers = new ArrayList<>();
        String statsType = "unknown";

        try {
            boolean isHead = path.contains("head") || path.contains("blade") || path.contains("axe")
                    || path.contains("pick") || path.contains("shovel") || path.contains("saw")
                    || path.contains("sword") || path.contains("dagger") || path.contains("hammer")
                    || path.contains("adze") || path.contains("shield")
                    || path.contains("large") || path.contains("small") || path.contains("broad");

            boolean isHandle = path.contains("handle") || path.contains("binding") || path.contains("grip");

            boolean isLimb = path.contains("limb") || path.contains("bow") || path.contains("arm")
                    || path.contains("crossbow");

            if (isHead) {
                Optional<IMaterialStats> headOpt = registry.getMaterialStats(materialId, HeadMaterialStats.ID);
                if (headOpt.isPresent()) {
                    HeadMaterialStats headStats = (HeadMaterialStats) headOpt.get();
                    durability = headStats.getDurability();
                    miningSpeed = headStats.getMiningSpeed();
                    attackDamage = headStats.getAttack();
                    Tier tier = headStats.getTier();
                    if (tier != null) harvestTierKey = getTierTranslationKey(tier);
                    statsType = "head";
                }
            } else if (isHandle) {
                Optional<IMaterialStats> handleOpt = registry.getMaterialStats(materialId, HandleMaterialStats.ID);
                if (handleOpt.isPresent()) {
                    HandleMaterialStats handleStats = (HandleMaterialStats) handleOpt.get();
                    modifierStats = extractAllFloatFields(handleStats);
                    statsType = "handle";
                }
            } else if (isLimb) {
                Optional<IMaterialStats> limbOpt = registry.getMaterialStats(materialId, LimbMaterialStats.ID);
                if (limbOpt.isPresent()) {
                    LimbMaterialStats limbStats = (LimbMaterialStats) limbOpt.get();
                    durability = limbStats.getDurability();
                    modifierStats = extractAllFloatFields(limbStats);
                    statsType = "limb";
                }
            } else {
                Optional<IMaterialStats> headOpt = registry.getMaterialStats(materialId, HeadMaterialStats.ID);
                if (headOpt.isPresent()) {
                    HeadMaterialStats headStats = (HeadMaterialStats) headOpt.get();
                    durability = headStats.getDurability();
                    miningSpeed = headStats.getMiningSpeed();
                    attackDamage = headStats.getAttack();
                    Tier tier = headStats.getTier();
                    if (tier != null) harvestTierKey = getTierTranslationKey(tier);
                    statsType = "head(fallback)";
                }
            }

            modifiers = getModifiersForMaterial(materialId);
        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to get part properties: " + e.getMessage());
        }

        return new PartProperties(durability, harvestTierKey, attackDamage, miningSpeed,
                modifierStats, modifiers, statsType);
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
                    float value = field.getFloat(stats);
                    result.put(field.getName(), value);
                } catch (Exception ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
        return result;
    }

    // ==================== 辅助方法 ====================

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
            if (parts.length >= 2) {
                return "gui.tinkerssearch.tier." + parts[1].replaceAll("[^a-z0-9_]", "");
            }
        }
        return "gui.tinkerssearch.tier." + str.replaceAll("[^a-z0-9_]", "");
    }

    private static IMaterial getMaterialForFluid(Fluid fluid) {
        if (fluid == null) return null;
        ResourceLocation fluidId = fluid.getRegistryName();
        if (fluidId == null) return null;

        try {
            IMaterialRegistry registry = MaterialRegistry.getInstance();
            for (IMaterial material : registry.getAllMaterials()) {
                FluidStack materialFluid = getFluidForMaterial(material);
                if (materialFluid != null && !materialFluid.isEmpty()) {
                    ResourceLocation materialFluidId = materialFluid.getFluid().getRegistryName();
                    if (fluidId.equals(materialFluidId)) return material;
                }
            }
        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to get material for fluid: " + e.getMessage());
        }

        String fluidPath = fluidId.getPath();
        if (fluidPath.startsWith("molten_")) {
            String materialName = fluidPath.substring(7);
            try {
                MaterialId guessedId = new MaterialId(fluidId.getNamespace(), materialName);
                IMaterial guessedMaterial = MaterialRegistry.getInstance().getMaterial(guessedId);
                if (guessedMaterial != null && guessedMaterial != IMaterial.UNKNOWN) return guessedMaterial;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static FluidStack getFluidForMaterial(IMaterial material) {
        if (material == null) return null;

        if (material instanceof Material) {
            Material mat = (Material) material;
            try {
                Method method = Material.class.getMethod("getFluid");
                Object result = method.invoke(mat);
                if (result instanceof FluidStack) return (FluidStack) result;
            } catch (Exception ignored) {}
            try {
                Method method = Material.class.getMethod("getFluidStack");
                Object result = method.invoke(mat);
                if (result instanceof FluidStack) return (FluidStack) result;
            } catch (Exception ignored) {}
            try {
                Field field = Material.class.getDeclaredField("fluid");
                field.setAccessible(true);
                Object result = field.get(mat);
                if (result instanceof FluidStack) return (FluidStack) result;
            } catch (Exception ignored) {}
        }

        String[] methodNames = {"getFluid", "getFluidStack"};
        for (String name : methodNames) {
            try {
                Method method = material.getClass().getMethod(name);
                Object result = method.invoke(material);
                if (result instanceof FluidStack) return (FluidStack) result;
            } catch (Exception ignored) {}
        }
        String[] fieldNames = {"fluid", "fluidStack"};
        for (String fname : fieldNames) {
            try {
                Field field = material.getClass().getDeclaredField(fname);
                field.setAccessible(true);
                Object result = field.get(material);
                if (result instanceof FluidStack) return (FluidStack) result;
            } catch (Exception ignored) {}
        }

        try {
            MaterialId id = material.getIdentifier();
            String fluidName = "molten_" + id.getPath();
            ResourceLocation fluidRl = new ResourceLocation(id.getNamespace(), fluidName);
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(fluidRl);
            if (fluid != null) return new FluidStack(fluid, 1000);
        } catch (Exception ignored) {}

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