package top.leipishu.tinkerssearch.utils;

import net.minecraftforge.fluids.FluidStack;
import top.leipishu.tinkerssearch.data.FluidPartDataCache;

/**
 * 保留的入口类，用于向后兼容。
 *
 * 真正的数据逻辑已迁移到 {@link FluidPartDataCache}。
 * 外部只需调用 clearMaterialCache() 和 hasParts()。
 */
public class PartPropertyHelper {

    /** 清空缓存（由 CastingRecipeHelper.invalidateCache 调用） */
    public static void clearMaterialCache() {
        FluidPartDataCache.invalidate();
    }

    /** 该流体是否有关联的部件页 */
    public static boolean hasParts(FluidStack fluidStack) {
        return !FluidPartDataCache.get(fluidStack).entries.isEmpty();
    }
}
