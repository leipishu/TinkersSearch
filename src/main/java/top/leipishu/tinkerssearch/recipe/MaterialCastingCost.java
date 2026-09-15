package top.leipishu.tinkerssearch.recipe;

import net.minecraft.world.item.crafting.Recipe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * {@code MaterialCastingRecipe} 的浇筑成本读取。
 *
 * <p>成本以"itemCost 单位"计，1 单位 = {@link #MB_PER_COST} mB。
 * 通过反射兼容附属模组对字段/方法命名的各种差异。
 */
public class MaterialCastingCost {

    /** 1 个 itemCost 单位对应多少 mB。 */
    public static final int MB_PER_COST = 90;

    /**
     * 返回 recipe 的原始 itemCost；找不到时返回 0。
     *
     * <p>查找顺序：
     * <ol>
     *   <li>常见方法名 {@code getItemCost} / {@code getCost} / {@code getRequiredCost}</li>
     *   <li>字段名包含 "cost" 的 int / Integer 字段</li>
     *   <li>上面的字段若被包装（如 Supplier / LoadableField），尝试 {@code get() / getAsInt()}</li>
     *   <li>序列化器上的同名方法</li>
     * </ol>
     */
    public static int getItemCost(Recipe<?> recipe) {
        if (recipe == null) return 0;

        // 1. 常见方法
        for (String mn : new String[]{"getItemCost", "getCost", "getRequiredCost"}) {
            try {
                Method m = recipe.getClass().getMethod(mn);
                m.setAccessible(true);
                Object v = m.invoke(recipe);
                if (v instanceof Integer && (Integer) v > 0) return (Integer) v;
            } catch (Exception ignored) {}
        }

        // 2. 字段扫描
        Class<?> clazz = recipe.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                String name = f.getName().toLowerCase();

                if (name.contains("cost") && f.getType() == int.class) {
                    f.setAccessible(true);
                    try {
                        int v = f.getInt(recipe);
                        if (v > 0) return v;
                    } catch (Exception ignored) {}
                }

                if (name.contains("cost") && f.getType() != int.class) {
                    f.setAccessible(true);
                    try {
                        Object v = f.get(recipe);
                        if (v instanceof Integer && (Integer) v > 0) return (Integer) v;

                        if (v != null) {
                            try {
                                Method get = v.getClass().getMethod("get");
                                get.setAccessible(true);
                                Object inner = get.invoke(v);
                                if (inner instanceof Integer && (Integer) inner > 0) return (Integer) inner;
                            } catch (Exception ignored) {}

                            try {
                                Method getAsInt = v.getClass().getMethod("getAsInt");
                                getAsInt.setAccessible(true);
                                Object inner = getAsInt.invoke(v);
                                if (inner instanceof Integer && (Integer) inner > 0) return (Integer) inner;
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception ignored) {}
                }
            }
            clazz = clazz.getSuperclass();
        }

        // 3. 序列化器兜底
        try {
            Method getSerializer = recipe.getClass().getMethod("getSerializer");
            getSerializer.setAccessible(true);
            Object serializer = getSerializer.invoke(recipe);
            if (serializer != null) {
                for (String mn : new String[]{"getItemCost", "getCost"}) {
                    try {
                        Method m = serializer.getClass().getMethod(mn);
                        m.setAccessible(true);
                        Object v = m.invoke(serializer);
                        if (v instanceof Integer && (Integer) v > 0) return (Integer) v;
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

        return 0;
    }

    /**
     * 返回该 recipe 应该消耗的 mB 量。
     * <ul>
     *   <li>itemCost &gt; 0 → {@code itemCost * MB_PER_COST}</li>
     *   <li>否则 → {@code MB_PER_COST}</li>
     * </ul>
     */
    public static int getAmount(Recipe<?> recipe) {
        int cost = getItemCost(recipe);
        return cost > 0 ? cost * MB_PER_COST : MB_PER_COST;
    }
}