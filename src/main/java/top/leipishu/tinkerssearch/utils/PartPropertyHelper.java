package top.leipishu.tinkerssearch.utils;

import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 部件属性辅助类
 * 用于获取流体对应的部件及属性
 * 兼容 1.18.2 匠魂 API
 */
public class PartPropertyHelper {

    // ==================== 数据类 ====================

    public static class PartInfo {
        public final String partName;
        public final PartProperties properties;
        public final ResourceLocation partId;

        public PartInfo(String name, PartProperties props, ResourceLocation id) {
            this.partName = name;
            this.properties = props;
            this.partId = id;
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
        // 数值型属性（直接显示数字）
        public final int durability;
        public final String harvestTier;
        public final float attackDamage;
        public final float miningSpeed;
        // 倍率型属性（显示为 x1.1 格式）
        public final float handleModifier;
        public final float drawSpeed;
        public final float launchSpeed;
        public final float arrowSpeed;
        public final float arrowAccuracy;
        public final List<ModifierInfo> modifiers;
        // 标记这个部件是什么类型的统计
        private final String statsType;

        public PartProperties(int durability, String harvestTier, float attackDamage,
                              float miningSpeed, float handleModifier,
                              float drawSpeed, float launchSpeed, float arrowSpeed, float arrowAccuracy,
                              List<ModifierInfo> modifiers, String statsType) {
            this.durability = durability;
            this.harvestTier = harvestTier;
            this.attackDamage = attackDamage;
            this.miningSpeed = miningSpeed;
            this.handleModifier = handleModifier;
            this.drawSpeed = drawSpeed;
            this.launchSpeed = launchSpeed;
            this.arrowSpeed = arrowSpeed;
            this.arrowAccuracy = arrowAccuracy;
            this.modifiers = modifiers != null ? modifiers : new ArrayList<>();
            this.statsType = statsType;
        }

        public boolean hasStats() {
            return durability > 0 || (harvestTier != null && !harvestTier.isEmpty()) ||
                    attackDamage > 0 || miningSpeed > 0 || handleModifier > 0 ||
                    drawSpeed > 0 || launchSpeed > 0 || arrowSpeed > 0 || arrowAccuracy > 0 ||
                    !modifiers.isEmpty();
        }

        /**
         * 格式化属性显示
         * 数值型：直接显示数字
         * 倍率型：显示为 x1.1 格式
         */
        public String formatStats() {
            List<String> parts = new ArrayList<>();

            // 耐久：数值型
            if (durability > 0) {
                parts.add("§7" + new TranslatableComponent("gui.tinkerssearch.detail.durability").getString() + ":" + durability);
            }

            // 等级：字符串
            if (harvestTier != null && !harvestTier.isEmpty()) {
                parts.add("§7" + new TranslatableComponent("gui.tinkerssearch.detail.harvest_tier").getString() + ":" + harvestTier);
            }

            // 伤害：数值型
            if (attackDamage > 0) {
                parts.add("§7" + new TranslatableComponent("gui.tinkerssearch.detail.attack_damage").getString() + ":" + String.format("%.1f", attackDamage));
            }

            // 挖掘速度：数值型
            if (miningSpeed > 0) {
                parts.add("§7" + new TranslatableComponent("gui.tinkerssearch.detail.mining_speed").getString() + ":" + String.format("%.1f", miningSpeed));
            }

            // 手柄系数：倍率型（显示为 x1.1）
            if (handleModifier > 0) {
                parts.add("§7" + new TranslatableComponent("gui.tinkerssearch.detail.handle_modifier").getString() + ":§ex" + String.format("%.2f", handleModifier));
            }

            // 拉弓速度：倍率型
            if (drawSpeed > 0) {
                parts.add("§7" + new TranslatableComponent("gui.tinkerssearch.detail.draw_speed").getString() + ":§ex" + String.format("%.2f", drawSpeed));
            }

            // 弹射速度：倍率型
            if (launchSpeed > 0) {
                parts.add("§7" + new TranslatableComponent("gui.tinkerssearch.detail.launch_speed").getString() + ":§ex" + String.format("%.2f", launchSpeed));
            }

            // 箭速：倍率型
            if (arrowSpeed > 0) {
                parts.add("§7" + new TranslatableComponent("gui.tinkerssearch.detail.arrow_speed").getString() + ":§ex" + String.format("%.2f", arrowSpeed));
            }

            // 精度：倍率型
            if (arrowAccuracy > 0) {
                parts.add("§7" + new TranslatableComponent("gui.tinkerssearch.detail.arrow_accuracy").getString() + ":§ex" + String.format("%.2f", arrowAccuracy));
            }

            return String.join(" ", parts);
        }

        public String formatModifiers() {
            if (modifiers.isEmpty()) return "";
            List<String> parts = new ArrayList<>();
            for (ModifierInfo mod : modifiers) {
                parts.add(mod.name + (mod.level > 1 ? " " + mod.level : ""));
            }
            return String.join(", ", parts);
        }
    }

    // ==================== 核心方法 ====================

    public static List<PartInfo> getPartsForFluid(FluidStack fluidStack) {
        List<PartInfo> result = new ArrayList<>();

        if (fluidStack == null || fluidStack.isEmpty()) {
            return result;
        }

        IMaterial material = getMaterialForFluid(fluidStack.getFluid());
        if (material == null) {
            return result;
        }

        MaterialId materialId = material.getIdentifier();
        IMaterialRegistry registry = MaterialRegistry.getInstance();

        for (Item item : ForgeRegistries.ITEMS) {
            if (item instanceof IMaterialItem) {
                IMaterialItem materialItem = (IMaterialItem) item;
                try {
                    if (!materialItem.canUseMaterial(materialId)) {
                        continue;
                    }
                    String partName = item.getDescription().getString();
                    // ===== 关键修正：根据部件类型获取对应的统计 =====
                    PartProperties properties = getPropertiesForPart(registry, materialId, item);

                    // ===== 修正：从 Item 获取注册名，不是从 IMaterialItem =====
                    ResourceLocation id = item.getRegistryName();
                    result.add(new PartInfo(
                            partName,
                            properties,
                            id != null ? id : new ResourceLocation("unknown")
                    ));
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        result.sort((a, b) -> a.partName.compareToIgnoreCase(b.partName));
        return result;
    }

    /**
     * 根据部件类型获取对应的属性统计
     * 关键：不同部件使用不同的统计类型，不能混用
     *
     * @param registry 材料注册表
     * @param materialId 材料ID
     * @param item 部件物品（用于判断部件类型）
     */
    private static PartProperties getPropertiesForPart(IMaterialRegistry registry, MaterialId materialId, Item item) {
        int durability = 0;
        String harvestTier = "";
        float attackDamage = 0;
        float miningSpeed = 0;
        float handleModifier = 0;
        float drawSpeed = 0;
        float launchSpeed = 0;
        float arrowSpeed = 0;
        float arrowAccuracy = 0;
        List<ModifierInfo> modifiers = new ArrayList<>();
        String statsType = "unknown";

        try {
            // ===== 通过注册名判断部件类型 =====
            ResourceLocation partId = item.getRegistryName();
            String path = partId != null ? partId.getPath().toLowerCase() : "";

            // ===== 判断部件类型 =====
            boolean isHeadPart = path.contains("head") || path.contains("blade") ||
                    path.contains("axe") || path.contains("pick") ||
                    path.contains("shovel") || path.contains("saw") ||
                    path.contains("sword") || path.contains("dagger") ||
                    path.contains("hammer") || path.contains("plate") ||
                    path.contains("gear") || path.contains("coin") ||
                    path.contains("wire") || path.contains("ingot") ||
                    path.contains("nugget") || path.contains("gem") ||
                    path.contains("rod") || path.contains("repair_kit");

            boolean isHandlePart = path.contains("handle") || path.contains("tool_handle") ||
                    path.contains("tough_handle") || path.contains("binding") ||
                    path.contains("tool_binding") || path.contains("grip");

            boolean isLimbPart = path.contains("limb") || path.contains("bow") ||
                    path.contains("arm") || path.contains("crossbow");

            // ===== 根据部件类型获取对应的统计 =====
            if (isHeadPart) {
                // 头部部件：使用 HeadMaterialStats
                Optional<IMaterialStats> headOpt = registry.getMaterialStats(materialId, HeadMaterialStats.ID);
                if (headOpt.isPresent()) {
                    HeadMaterialStats headStats = (HeadMaterialStats) headOpt.get();
                    durability = headStats.getDurability();
                    miningSpeed = headStats.getMiningSpeed();
                    attackDamage = headStats.getAttack();
                    Tier tier = headStats.getTier();
                    if (tier != null) {
                        harvestTier = getTierName(tier);
                    }
                    statsType = "head";
                }
            } else if (isHandlePart) {
                // 手柄部件：使用 HandleMaterialStats
                Optional<IMaterialStats> handleOpt = registry.getMaterialStats(materialId, HandleMaterialStats.ID);
                if (handleOpt.isPresent()) {
                    HandleMaterialStats handleStats = (HandleMaterialStats) handleOpt.get();
                    // HandleMaterialStats 的 durability 实际是倍率系数
                    handleModifier = handleStats.getDurability();
                    statsType = "handle";
                }
            } else if (isLimbPart) {
                // 肢体/弓臂部件：使用 LimbMaterialStats
                Optional<IMaterialStats> limbOpt = registry.getMaterialStats(materialId, LimbMaterialStats.ID);
                if (limbOpt.isPresent()) {
                    LimbMaterialStats limbStats = (LimbMaterialStats) limbOpt.get();
                    durability = limbStats.getDurability();
                    drawSpeed = limbStats.getDrawSpeed();
                    launchSpeed = limbStats.getVelocity();
                    arrowAccuracy = limbStats.getAccuracy();
                    arrowSpeed = launchSpeed;
                    statsType = "limb";
                }
            } else {
                // ===== 未知类型：尝试获取所有统计，但只取第一个非空的 =====
                Optional<IMaterialStats> headOpt = registry.getMaterialStats(materialId, HeadMaterialStats.ID);
                if (headOpt.isPresent()) {
                    HeadMaterialStats headStats = (HeadMaterialStats) headOpt.get();
                    durability = headStats.getDurability();
                    miningSpeed = headStats.getMiningSpeed();
                    attackDamage = headStats.getAttack();
                    Tier tier = headStats.getTier();
                    if (tier != null) {
                        harvestTier = getTierName(tier);
                    }
                    statsType = "head(fallback)";
                } else {
                    Optional<IMaterialStats> handleOpt = registry.getMaterialStats(materialId, HandleMaterialStats.ID);
                    if (handleOpt.isPresent()) {
                        HandleMaterialStats handleStats = (HandleMaterialStats) handleOpt.get();
                        handleModifier = handleStats.getDurability();
                        statsType = "handle(fallback)";
                    } else {
                        Optional<IMaterialStats> limbOpt = registry.getMaterialStats(materialId, LimbMaterialStats.ID);
                        if (limbOpt.isPresent()) {
                            LimbMaterialStats limbStats = (LimbMaterialStats) limbOpt.get();
                            durability = limbStats.getDurability();
                            drawSpeed = limbStats.getDrawSpeed();
                            launchSpeed = limbStats.getVelocity();
                            arrowAccuracy = limbStats.getAccuracy();
                            arrowSpeed = launchSpeed;
                            statsType = "limb(fallback)";
                        }
                    }
                }
            }

            // 获取特性（简化实现）
            modifiers = getModifiersForMaterial(materialId);

            // 调试日志
            System.out.println("[Tinker's Search] Part: " + item.getDescription().getString() +
                    " | Type: " + statsType +
                    " | Durability: " + durability +
                    " | Handle: " + handleModifier +
                    " | Draw: " + drawSpeed);

        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to get part properties: " + e.getMessage());
            e.printStackTrace();
        }

        return new PartProperties(
                durability, harvestTier, attackDamage, miningSpeed, handleModifier,
                drawSpeed, launchSpeed, arrowSpeed, arrowAccuracy,
                modifiers, statsType
        );
    }

    // ==================== 辅助方法 ====================

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
                    if (fluidId.equals(materialFluidId)) {
                        return material;
                    }
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
                if (guessedMaterial != null && guessedMaterial != IMaterial.UNKNOWN) {
                    return guessedMaterial;
                }
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
            if (fluid != null) {
                return new FluidStack(fluid, 1000);
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static String getTierName(Tier tier) {
        if (tier == null) return "";
        if (tier == Tiers.WOOD) return "wood";
        if (tier == Tiers.STONE) return "stone";
        if (tier == Tiers.IRON) return "iron";
        if (tier == Tiers.DIAMOND) return "diamond";
        if (tier == Tiers.NETHERITE) return "netherite";
        if (tier == Tiers.GOLD) return "gold";
        return tier.toString();
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
                    List<?> traits = (List<?>) traitsObj;
                    for (Object trait : traits) {
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
        } catch (Exception e) {
            // 如果找不到 MaterialTraitsManager，则忽略
        }
        return result;
    }

    public static boolean hasParts(FluidStack fluidStack) {
        return !getPartsForFluid(fluidStack).isEmpty();
    }
}