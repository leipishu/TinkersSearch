package top.leipishu.tinkerssearch.recipe;

import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.tools.stats.HandleMaterialStats;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;
import slimeknights.tconstruct.tools.stats.LimbMaterialStats;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 部件与材料的兼容性判断。
 *
 * <p>三层判定，容忍 KubeJS 等外部改动导致材料注册残缺：
 * <ol>
 *   <li>官方 {@link IMaterialItem#canUseMaterial}</li>
 *   <li>检查 {@code MaterialRegistry} 中该材料是否有对应 stats</li>
 *   <li>材料完全未注册时宽松通过</li>
 * </ol>
 */
public class MaterialCompatibility {

    /**
     * 判断部件能否使用指定材料。
     */
    public static boolean canUseMaterial(IMaterialItem mi, MaterialId targetMat) {
        // 1. 官方判断
        try {
            if (mi.canUseMaterial(targetMat)) return true;
        } catch (Throwable ignored) {}

        // 2. 检查 stats
        MaterialStatsId statType = inferStatType(mi);
        if (statType != null) {
            try {
                if (MaterialRegistry.getInstance().getMaterialStats(targetMat, statType).isPresent()) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        // 3. 材料未注册时宽松通过（KubeJS 场景兜底）
        try {
            IMaterial mat = MaterialRegistry.getInstance().getMaterial(targetMat);
            if (mat == null || mat == IMaterial.UNKNOWN) {
                return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }

    /**
     * 推断部件的 statType。
     *
     * <p>优先读 {@code getStatType()} / {@code statType} 字段，
     * 失败时按注册名关键字猜测。
     */
    public static MaterialStatsId inferStatType(IMaterialItem item) {
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
}