package top.leipishu.tinkerssearch.alloy;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe;
import slimeknights.mantle.recipe.ingredient.FluidIngredient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 合金配方读取器 - 支持多种配方来源
 *
 * 读取策略（按优先级）：
 * 1. Forge 官方 API - RecipeManager.getRecipes()
 * 2. RecipeType 过滤 - 通过反射调用 byType()
 * 3. 反射读取 - 兼容旧版本/被修改的环境
 * 4. KubeJS 集成 - 读取 KubeJS 动态添加的配方
 * 5. CraftTweaker 集成 - 读取 CraftTweaker 动态添加的配方
 */
public class TinkersAlloyReader {

    private static List<AlloyRecipeData> cachedAlloyRecipes = null;
    private static List<FluidStack> cachedAllMaterials = null;
    private static long cacheTime = 0;
    private static final long CACHE_DURATION = 5000;

    // ===== 缓存 RecipeType 引用 =====
    private static RecipeType<?> alloyRecipeType = null;

    // ===== KubeJS 相关 =====
    private static boolean kubeJSDetected = false;
    private static boolean kubeJSChecked = false;

    // ===== CraftTweaker 相关 =====
    private static boolean craftTweakerDetected = false;
    private static boolean craftTweakerChecked = false;

    // ===== 配方缓存（用于快速查找） =====
    private static final Map<ResourceLocation, AlloyRecipeData> recipeCache = new ConcurrentHashMap<>();

    // ===== 日志控制 =====
    private static boolean debugMode = false;

    /**
     * 启用调试模式（在 TinkersSearch 主类中可调用）
     */
    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
    }

    /**
     * 获取所有合金配方（主入口）
     */
    public static List<AlloyRecipeData> getAlloyRecipes() {
        long now = System.currentTimeMillis();
        if (cachedAlloyRecipes != null && (now - cacheTime) < CACHE_DURATION) {
            return cachedAlloyRecipes;
        }

        log("=== TINKER'S SEARCH: Loading Alloy Recipes ===");

        List<AlloyRecipeData> recipes = new ArrayList<>();

        // ===== 策略1：Forge 官方 API =====
        recipes.addAll(loadFromForgeAPI());

        // ===== 策略2：RecipeType 过滤 =====
        if (recipes.isEmpty()) {
            log("Strategy 1 (Forge API) returned empty, trying RecipeType filter...");
            recipes.addAll(loadFromRecipeType());
        }

        // ===== 策略3：反射读取 =====
        if (recipes.isEmpty()) {
            log("Strategy 2 (RecipeType) returned empty, trying reflection...");
            recipes.addAll(loadFromReflection());
        }

        // ===== 策略4：KubeJS 集成 =====
        if (recipes.isEmpty() || shouldForceKubeJS()) {
            log("Trying KubeJS integration...");
            recipes.addAll(loadFromKubeJS());
        }

        // ===== 策略5：CraftTweaker 集成 =====
        if (recipes.isEmpty() || shouldForceCraftTweaker()) {
            log("Trying CraftTweaker integration...");
            recipes.addAll(loadFromCraftTweaker());
        }

        // ===== 去重（按 Recipe ID） =====
        Map<ResourceLocation, AlloyRecipeData> uniqueMap = new LinkedHashMap<>();
        for (AlloyRecipeData recipe : recipes) {
            if (!uniqueMap.containsKey(recipe.getId())) {
                uniqueMap.put(recipe.getId(), recipe);
            }
        }
        recipes = new ArrayList<>(uniqueMap.values());

        log("Total alloy recipes loaded: " + recipes.size());

        // ===== 如果没有找到任何配方，尝试从注册表生成材料列表（降级方案） =====
        if (recipes.isEmpty()) {
            log("WARNING: No alloy recipes found! Will use fluid registry as fallback.");
        }

        cachedAlloyRecipes = recipes;
        cacheTime = System.currentTimeMillis();
        // 清除旧的材料缓存
        cachedAllMaterials = null;

        return cachedAlloyRecipes;
    }

    // ============================================================
    // ===== 策略1：Forge 官方 API ================================
    // ============================================================

    private static List<AlloyRecipeData> loadFromForgeAPI() {
        List<AlloyRecipeData> result = new ArrayList<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) {
                log("No connection, cannot load recipes via Forge API");
                return result;
            }

            RecipeManager recipeManager = mc.getConnection().getRecipeManager();

            // ===== 1.18.2 使用 getRecipes() =====
            Collection<Recipe<?>> allRecipes = recipeManager.getRecipes();

            log("Forge API: Total recipes = " + allRecipes.size());

            for (Recipe<?> recipe : allRecipes) {
                if (recipe instanceof AlloyRecipe) {
                    ResourceLocation id = recipe.getId();
                    AlloyRecipeData parsed = parseAlloyRecipe((AlloyRecipe) recipe, id);
                    if (parsed != null) {
                        result.add(parsed);
                        recipeCache.put(id, parsed);
                    }
                }
            }

            log("Forge API: Found " + result.size() + " alloy recipes");
        } catch (Exception e) {
            logError("Forge API failed: " + e.getMessage());
            if (debugMode) e.printStackTrace();
        }
        return result;
    }

    // ============================================================
    // ===== 策略2：RecipeType 过滤 ===============================
    // ============================================================

    private static List<AlloyRecipeData> loadFromRecipeType() {
        List<AlloyRecipeData> result = new ArrayList<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) return result;

            RecipeManager recipeManager = mc.getConnection().getRecipeManager();
            Level level = mc.level;

            // 获取合金 RecipeType
            RecipeType<?> type = getAlloyRecipeType();
            if (type == null) {
                log("Alloy RecipeType not found");
                return result;
            }

            // ===== 方法1：使用 getRecipesFor() 需要 Level 参数 =====
            // 注意：在 1.18.2 中，getRecipesFor 需要 RecipeType 和 Level
            try {
                // 尝试调用 getRecipesFor(RecipeType, Level)
                Method getRecipesForMethod = RecipeManager.class.getMethod(
                        "getRecipesFor",
                        RecipeType.class,
                        Level.class
                );

                @SuppressWarnings("unchecked")
                List<Recipe<?>> typedRecipes = (List<Recipe<?>>) getRecipesForMethod.invoke(
                        recipeManager, type, level
                );

                if (typedRecipes != null) {
                    for (Recipe<?> recipe : typedRecipes) {
                        if (recipe instanceof AlloyRecipe) {
                            ResourceLocation id = recipe.getId();
                            AlloyRecipeData parsed = parseAlloyRecipe((AlloyRecipe) recipe, id);
                            if (parsed != null) {
                                result.add(parsed);
                                recipeCache.put(id, parsed);
                            }
                        }
                    }
                    log("RecipeType: Found " + result.size() + " alloy recipes via getRecipesFor()");
                }
            } catch (NoSuchMethodException e) {
                log("getRecipesFor(RecipeType, Level) not available");
            } catch (Exception e) {
                logError("getRecipesFor() failed: " + e.getMessage());
            }

            // ===== 方法2：使用 byType() =====
            if (result.isEmpty()) {
                try {
                    Method byTypeMethod = RecipeManager.class.getMethod("byType", RecipeType.class);
                    @SuppressWarnings("unchecked")
                    Map<ResourceLocation, Recipe<?>> typedRecipes =
                            (Map<ResourceLocation, Recipe<?>>) byTypeMethod.invoke(recipeManager, type);

                    if (typedRecipes != null) {
                        for (Map.Entry<ResourceLocation, Recipe<?>> entry : typedRecipes.entrySet()) {
                            Recipe<?> recipe = entry.getValue();
                            if (recipe instanceof AlloyRecipe) {
                                AlloyRecipeData parsed = parseAlloyRecipe((AlloyRecipe) recipe, entry.getKey());
                                if (parsed != null) {
                                    result.add(parsed);
                                    recipeCache.put(entry.getKey(), parsed);
                                }
                            }
                        }
                        log("RecipeType: Found " + result.size() + " alloy recipes via byType()");
                    }
                } catch (NoSuchMethodException e) {
                    log("byType() not available");
                } catch (Exception e) {
                    logError("byType() failed: " + e.getMessage());
                }
            }

            // ===== 方法3：遍历所有 RecipeType =====
            if (result.isEmpty()) {
                log("Trying to find alloy recipes by scanning all RecipeTypes...");
                for (RecipeType<?> rt : Registry.RECIPE_TYPE) {
                    try {
                        // 尝试通过 getRecipesFor 获取
                        Method getRecipesForMethod = RecipeManager.class.getMethod(
                                "getRecipesFor",
                                RecipeType.class,
                                Level.class
                        );
                        @SuppressWarnings("unchecked")
                        List<Recipe<?>> typedRecipes = (List<Recipe<?>>) getRecipesForMethod.invoke(
                                recipeManager, rt, level
                        );

                        if (typedRecipes != null) {
                            for (Recipe<?> recipe : typedRecipes) {
                                if (recipe instanceof AlloyRecipe) {
                                    ResourceLocation id = recipe.getId();
                                    AlloyRecipeData parsed = parseAlloyRecipe((AlloyRecipe) recipe, id);
                                    if (parsed != null) {
                                        result.add(parsed);
                                        recipeCache.put(id, parsed);
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}

                    if (!result.isEmpty()) {
                        log("RecipeType: Found " + result.size() + " alloy recipes by scanning");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logError("RecipeType strategy failed: " + e.getMessage());
            if (debugMode) e.printStackTrace();
        }
        return result;
    }

    // ============================================================
    // ===== 策略3：反射读取 ======================================
    // ============================================================

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<AlloyRecipeData> loadFromReflection() {
        List<AlloyRecipeData> result = new ArrayList<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) return result;

            RecipeManager recipeManager = mc.getConnection().getRecipeManager();

            // 尝试多个可能的字段名
            String[] fieldNames = {"recipes", "recipesMap", "byName", "recipeMap"};

            for (String fieldName : fieldNames) {
                try {
                    Field field = RecipeManager.class.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object recipesObj = field.get(recipeManager);

                    if (recipesObj instanceof Map) {
                        Map<?, ?> recipesMap = (Map<?, ?>) recipesObj;

                        // 结构1: Map<RecipeType, Map<ResourceLocation, Recipe>>
                        for (Map.Entry<?, ?> entry : recipesMap.entrySet()) {
                            Object value = entry.getValue();
                            if (value instanceof Map) {
                                Map<?, ?> innerMap = (Map<?, ?>) value;
                                for (Map.Entry<?, ?> innerEntry : innerMap.entrySet()) {
                                    Object recipeObj = innerEntry.getValue();
                                    if (recipeObj instanceof AlloyRecipe) {
                                        ResourceLocation id = extractId(innerEntry.getKey());
                                        if (id != null) {
                                            AlloyRecipeData parsed = parseAlloyRecipe((AlloyRecipe) recipeObj, id);
                                            if (parsed != null) {
                                                result.add(parsed);
                                                recipeCache.put(id, parsed);
                                            }
                                        }
                                    }
                                }
                            }
                            // 结构2: Map<ResourceLocation, Recipe> (直接存储)
                            else if (value instanceof AlloyRecipe) {
                                ResourceLocation id = extractId(entry.getKey());
                                if (id != null) {
                                    AlloyRecipeData parsed = parseAlloyRecipe((AlloyRecipe) value, id);
                                    if (parsed != null) {
                                        result.add(parsed);
                                        recipeCache.put(id, parsed);
                                    }
                                }
                            }
                        }

                        if (!result.isEmpty()) {
                            log("Reflection: Found " + result.size() + " alloy recipes via field '" + fieldName + "'");
                            break;
                        }
                    }
                } catch (NoSuchFieldException e) {
                    // 字段不存在，继续尝试下一个
                } catch (Exception e) {
                    logError("Reflection via '" + fieldName + "' failed: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logError("Reflection strategy failed: " + e.getMessage());
            if (debugMode) e.printStackTrace();
        }
        return result;
    }

    // ============================================================
    // ===== 策略4：KubeJS 集成 ===================================
    // ============================================================

    private static List<AlloyRecipeData> loadFromKubeJS() {
        List<AlloyRecipeData> result = new ArrayList<>();

        if (!kubeJSChecked) {
            try {
                Class.forName("dev.latvian.mods.kubejs.KubeJS");
                kubeJSDetected = true;
                log("KubeJS detected!");
            } catch (ClassNotFoundException e) {
                kubeJSDetected = false;
            }
            kubeJSChecked = true;
        }

        if (!kubeJSDetected) {
            return result;
        }

        try {
            // 尝试通过 KubeJS 的 RecipeManager 获取配方
            Class<?> kubeJSRecipeManagerClass = Class.forName("dev.latvian.mods.kubejs.recipe.RecipeManagerJS");

            // 获取 KubeJS 的配方管理器实例
            Method getInstance = kubeJSRecipeManagerClass.getMethod("getInstance");
            Object kubeJSManager = getInstance.invoke(null);

            if (kubeJSManager != null) {
                // 尝试获取所有配方
                try {
                    Method getRecipes = kubeJSManager.getClass().getMethod("getRecipes");
                    Object recipesObj = getRecipes.invoke(kubeJSManager);

                    if (recipesObj instanceof Map) {
                        Map<?, ?> recipesMap = (Map<?, ?>) recipesObj;
                        for (Map.Entry<?, ?> entry : recipesMap.entrySet()) {
                            Object recipeObj = entry.getValue();
                            if (recipeObj != null && isKubeJSAlloyRecipe(recipeObj)) {
                                AlloyRecipeData parsed = parseKubeJSRecipe(recipeObj, entry.getKey());
                                if (parsed != null) {
                                    result.add(parsed);
                                }
                            }
                        }
                    }
                } catch (NoSuchMethodException e) {
                    // 尝试其他方法名
                    try {
                        Method getAllRecipes = kubeJSManager.getClass().getMethod("getAllRecipes");
                        Object recipesObj = getAllRecipes.invoke(kubeJSManager);
                        if (recipesObj instanceof Map) {
                            // 类似处理
                        }
                    } catch (Exception ignored) {}
                }
            }

            log("KubeJS: Found " + result.size() + " alloy recipes");
        } catch (Exception e) {
            logError("KubeJS integration failed: " + e.getMessage());
            if (debugMode) e.printStackTrace();
        }

        return result;
    }

    /**
     * 检查 KubeJS 的配方对象是否为合金配方
     */
    private static boolean isKubeJSAlloyRecipe(Object recipeObj) {
        try {
            Class<?> clazz = recipeObj.getClass();

            // 检查类名
            String className = clazz.getName().toLowerCase();
            if (className.contains("alloy") || className.contains("alloyrecipe")) {
                return true;
            }

            // 检查是否有 alloy 相关的类型标识
            try {
                Method getType = clazz.getMethod("getType");
                Object type = getType.invoke(recipeObj);
                if (type != null && type.toString().toLowerCase().contains("alloy")) {
                    return true;
                }
            } catch (Exception ignored) {}

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析 KubeJS 的配方对象
     */
    private static AlloyRecipeData parseKubeJSRecipe(Object recipeObj, Object idObj) {
        try {
            ResourceLocation id = extractId(idObj);
            if (id == null) {
                id = new ResourceLocation("kubejs", "alloy_" + System.currentTimeMillis());
            }

            Class<?> clazz = recipeObj.getClass();

            // 尝试获取输出
            FluidStack output = null;
            try {
                Method getOutput = clazz.getMethod("getOutput");
                Object outputObj = getOutput.invoke(recipeObj);
                if (outputObj instanceof FluidStack) {
                    output = (FluidStack) outputObj;
                }
            } catch (Exception ignored) {}

            if (output == null) {
                // 尝试获取 result
                try {
                    Field resultField = clazz.getDeclaredField("result");
                    resultField.setAccessible(true);
                    Object resultObj = resultField.get(recipeObj);
                    if (resultObj instanceof FluidStack) {
                        output = (FluidStack) resultObj;
                    }
                } catch (Exception ignored) {}
            }

            if (output == null) return null;

            // 尝试获取输入
            List<AlloyRecipeData.FluidIngredientData> inputs = new ArrayList<>();
            try {
                Method getIngredients = clazz.getMethod("getIngredients");
                Object ingredientsObj = getIngredients.invoke(recipeObj);
                if (ingredientsObj instanceof List) {
                    List<?> ingredientList = (List<?>) ingredientsObj;
                    for (Object ing : ingredientList) {
                        if (ing instanceof FluidIngredient) {
                            FluidIngredient fi = (FluidIngredient) ing;
                            FluidStack[] fluids = fi.getFluids().toArray(new FluidStack[0]);
                            if (fluids.length > 0) {
                                int amount = fi.getAmount(fluids[0].getFluid());
                                FluidStack copy = fluids[0].copy();
                                copy.setAmount(amount);
                                inputs.add(new AlloyRecipeData.FluidIngredientData(copy, amount));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            if (inputs.isEmpty()) return null;

            int temperature = 0;
            try {
                Method getTemperature = clazz.getMethod("getTemperature");
                Object tempObj = getTemperature.invoke(recipeObj);
                if (tempObj instanceof Integer) {
                    temperature = (Integer) tempObj;
                }
            } catch (Exception ignored) {}

            return new AlloyRecipeData(id, inputs, output, temperature);

        } catch (Exception e) {
            logError("Failed to parse KubeJS recipe: " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // ===== 策略5：CraftTweaker 集成 =============================
    // ============================================================

    private static List<AlloyRecipeData> loadFromCraftTweaker() {
        List<AlloyRecipeData> result = new ArrayList<>();

        if (!craftTweakerChecked) {
            try {
                Class.forName("com.blamejared.crafttweaker.api.CraftTweakerAPI");
                craftTweakerDetected = true;
                log("CraftTweaker detected!");
            } catch (ClassNotFoundException e) {
                craftTweakerDetected = false;
            }
            craftTweakerChecked = true;
        }

        if (!craftTweakerDetected) {
            return result;
        }

        try {
            // CraftTweaker 的配方管理
            Class<?> craftTweakerAPIClass = Class.forName("com.blamejared.crafttweaker.api.CraftTweakerAPI");

            // 尝试获取配方管理器
            try {
                Method getRecipeManager = craftTweakerAPIClass.getMethod("getRecipeManager");
                Object recipeManager = getRecipeManager.invoke(null);

                if (recipeManager != null) {
                    // 获取所有配方
                    try {
                        Method getAllRecipes = recipeManager.getClass().getMethod("getAllRecipes");
                        Object recipesObj = getAllRecipes.invoke(recipeManager);

                        if (recipesObj instanceof List) {
                            List<?> recipeList = (List<?>) recipesObj;
                            for (Object recipe : recipeList) {
                                if (recipe != null && isCraftTweakerAlloyRecipe(recipe)) {
                                    AlloyRecipeData parsed = parseCraftTweakerRecipe(recipe);
                                    if (parsed != null) {
                                        result.add(parsed);
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (NoSuchMethodException e) {
                // CraftTweaker 版本不同，尝试其他方式
                try {
                    // 一些版本的 CraftTweaker 使用静态方法
                    Method getRecipes = craftTweakerAPIClass.getMethod("getRecipes");
                    Object recipesObj = getRecipes.invoke(null);
                    if (recipesObj instanceof List) {
                        // 类似处理
                    }
                } catch (Exception ignored) {}
            }

            log("CraftTweaker: Found " + result.size() + " alloy recipes");
        } catch (Exception e) {
            logError("CraftTweaker integration failed: " + e.getMessage());
            if (debugMode) e.printStackTrace();
        }

        return result;
    }

    /**
     * 检查 CraftTweaker 的配方对象是否为合金配方
     */
    private static boolean isCraftTweakerAlloyRecipe(Object recipeObj) {
        try {
            String className = recipeObj.getClass().getName().toLowerCase();
            return className.contains("alloy") || className.contains("alloyrecipe");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析 CraftTweaker 的配方对象
     */
    private static AlloyRecipeData parseCraftTweakerRecipe(Object recipeObj) {
        try {
            Class<?> clazz = recipeObj.getClass();
            ResourceLocation id = new ResourceLocation("crafttweaker", "alloy_" + System.currentTimeMillis());

            FluidStack output = null;
            try {
                Method getOutput = clazz.getMethod("getOutput");
                Object outputObj = getOutput.invoke(recipeObj);
                if (outputObj instanceof FluidStack) {
                    output = (FluidStack) outputObj;
                }
            } catch (Exception ignored) {}

            if (output == null) return null;

            List<AlloyRecipeData.FluidIngredientData> inputs = new ArrayList<>();
            try {
                Method getIngredients = clazz.getMethod("getIngredients");
                Object ingredientsObj = getIngredients.invoke(recipeObj);
                if (ingredientsObj instanceof List) {
                    List<?> ingredientList = (List<?>) ingredientsObj;
                    for (Object ing : ingredientList) {
                        if (ing instanceof FluidIngredient) {
                            FluidIngredient fi = (FluidIngredient) ing;
                            FluidStack[] fluids = fi.getFluids().toArray(new FluidStack[0]);
                            if (fluids.length > 0) {
                                int amount = fi.getAmount(fluids[0].getFluid());
                                FluidStack copy = fluids[0].copy();
                                copy.setAmount(amount);
                                inputs.add(new AlloyRecipeData.FluidIngredientData(copy, amount));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            if (inputs.isEmpty()) return null;

            int temperature = 0;
            try {
                Method getTemperature = clazz.getMethod("getTemperature");
                Object tempObj = getTemperature.invoke(recipeObj);
                if (tempObj instanceof Integer) {
                    temperature = (Integer) tempObj;
                }
            } catch (Exception ignored) {}

            return new AlloyRecipeData(id, inputs, output, temperature);

        } catch (Exception e) {
            logError("Failed to parse CraftTweaker recipe: " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // ===== 工具方法 =============================================
    // ============================================================

    /**
     * 获取合金 RecipeType
     */
    private static RecipeType<?> getAlloyRecipeType() {
        if (alloyRecipeType != null) {
            return alloyRecipeType;
        }

        // 遍历注册表查找
        for (RecipeType<?> type : Registry.RECIPE_TYPE) {
            String name = type.toString();
            if (name.contains("alloy") || name.contains("Alloy")) {
                alloyRecipeType = type;
                log("Found alloy RecipeType: " + type);
                return type;
            }
        }

        // 尝试通过类名查找
        try {
            Class<?> alloyRecipeClass = Class.forName("slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe");
            for (RecipeType<?> type : Registry.RECIPE_TYPE) {
                // 检查这个 RecipeType 是否与 AlloyRecipe 关联
                try {
                    Method getRecipeClass = type.getClass().getMethod("getRecipeClass");
                    Object recipeClass = getRecipeClass.invoke(type);
                    if (recipeClass != null && recipeClass.equals(alloyRecipeClass)) {
                        alloyRecipeType = type;
                        log("Found alloy RecipeType via class: " + type);
                        return type;
                    }
                } catch (Exception ignored) {}
            }
        } catch (ClassNotFoundException ignored) {}

        log("Alloy RecipeType not found");
        return null;
    }

    /**
     * 提取 ResourceLocation ID
     */
    private static ResourceLocation extractId(Object obj) {
        if (obj == null) return null;
        if (obj instanceof ResourceLocation) {
            return (ResourceLocation) obj;
        }
        try {
            String str = obj.toString();
            if (str.contains(":")) {
                String[] parts = str.split(":");
                if (parts.length == 2) {
                    return new ResourceLocation(parts[0], parts[1]);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 解析匠魂合金配方
     */
    @SuppressWarnings("unchecked")
    private static AlloyRecipeData parseAlloyRecipe(AlloyRecipe recipe, ResourceLocation id) {
        try {
            List<AlloyRecipeData.FluidIngredientData> inputData = new ArrayList<>();

            // 尝试多种方式获取输入
            List<FluidIngredient> inputs = null;

            // 方式1：通过 inputs 字段
            try {
                Field inputField = AlloyRecipe.class.getDeclaredField("inputs");
                inputField.setAccessible(true);
                inputs = (List<FluidIngredient>) inputField.get(recipe);
            } catch (Exception ignored) {}

            // 方式2：通过 getInputs 方法
            if (inputs == null) {
                try {
                    Method getInputs = AlloyRecipe.class.getMethod("getInputs");
                    Object result = getInputs.invoke(recipe);
                    if (result instanceof List) {
                        inputs = (List<FluidIngredient>) result;
                    }
                } catch (Exception ignored) {}
            }

            // 方式3：通过 getIngredients
            if (inputs == null) {
                try {
                    Method getIngredients = AlloyRecipe.class.getMethod("getIngredients");
                    Object result = getIngredients.invoke(recipe);
                    if (result instanceof List) {
                        inputs = (List<FluidIngredient>) result;
                    }
                } catch (Exception ignored) {}
            }

            if (inputs == null || inputs.isEmpty()) {
                log("Recipe " + id + " has no parseable inputs");
                return null;
            }

            for (FluidIngredient ingredient : inputs) {
                if (ingredient == null) continue;
                FluidStack[] fluidArray = ingredient.getFluids().toArray(new FluidStack[0]);
                if (fluidArray != null && fluidArray.length > 0) {
                    FluidStack firstFluid = fluidArray[0];
                    int amount = ingredient.getAmount(firstFluid.getFluid());
                    FluidStack copy = firstFluid.copy();
                    copy.setAmount(amount);
                    inputData.add(new AlloyRecipeData.FluidIngredientData(copy, amount));
                }
            }

            if (inputData.isEmpty()) {
                log("Recipe " + id + " has no parseable fluid inputs");
                return null;
            }

            FluidStack output = recipe.getOutput();
            if (output == null || output.isEmpty()) {
                log("Recipe " + id + " has null or empty output");
                return null;
            }

            int temperature = recipe.getTemperature();

            return new AlloyRecipeData(id, inputData, output, temperature);

        } catch (Exception e) {
            logError("Failed to parse recipe " + id + ": " + e.getMessage());
            if (debugMode) e.printStackTrace();
            return null;
        }
    }

    // ============================================================
    // ===== 材料列表获取 =========================================
    // ============================================================

    /**
     * 获取所有可冶炼流体。
     *
     * <p>这两个 Tab（合金模式、全部材料）展示的是"游戏里能进冶炼炉的流体"，
     * 与合金配方无关——一个流体完全可以只参与浇筑、不参与任何合金。
     * 因此数据来源只能是流体注册表本身。
     */
    public static List<FluidStack> getAllSmelteryFluids() {
        long now = System.currentTimeMillis();
        if (cachedAllMaterials != null && !cachedAllMaterials.isEmpty()
                && (now - cacheTime) < CACHE_DURATION) {
            return cachedAllMaterials;
        }

        // ★ 直接扫描注册表，不再从合金配方里提取
        Set<ResourceLocation> fluidSet = new HashSet<>();
        scanFluidRegistry(fluidSet);

        List<FluidStack> built = new ArrayList<>();
        for (ResourceLocation rl : fluidSet) {
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(rl);
            if (fluid != null) {
                built.add(new FluidStack(fluid, 1000));
            }
        }

        built.sort((a, b) -> {
            String nameA = a.getDisplayName().getString().replace("Molten ", "").replace("熔融", "");
            String nameB = b.getDisplayName().getString().replace("Molten ", "").replace("熔融", "");
            return nameA.compareToIgnoreCase(nameB);
        });

        if (!built.isEmpty()) {
            cachedAllMaterials = built;
            log("Total smeltery fluids: " + built.size());
        } else {
            cachedAllMaterials = null;
            log("WARN: getAllSmelteryFluids returned empty; will retry on next call");
        }

        return built;
    }

    /**
     * 添加流体到集合（过滤流动流体）
     */
    private static void addFluidToSet(FluidStack fs, Set<ResourceLocation> set) {
        if (fs == null || fs.isEmpty()) return;
        ResourceLocation rl = fs.getFluid().getRegistryName();
        if (rl == null) return;

        String path = rl.getPath();
        // 跳过流动流体
        if (path.contains("flowing") || path.contains("flow")) {
            return;
        }
        set.add(rl);
    }

    private static void scanFluidRegistry(Set<ResourceLocation> set) {
        int totalScanned = 0;
        int added = 0;

        for (Fluid fluid : ForgeRegistries.FLUIDS) {
            ResourceLocation rl = fluid.getRegistryName();
            if (rl == null) continue;
            totalScanned++;

            String path = rl.getPath();

            // 跳过流动形态（部分模组会注册 flowing_xxx 条目）
            if (path.startsWith("flowing_") || path.equals("flowing")) continue;

            // 匠魂命名空间全收：这是冶炼炉流体的主要来源
            if (rl.getNamespace().equals("tconstruct")) {
                set.add(rl);
                added++;
                continue;
            }

            // 其他模组：只收熔融/液态类关键字命名的流体
            if (path.contains("molten")
                    || path.contains("liquid")
                    || path.contains("metal")
                    || path.contains("alloy")) {
                set.add(rl);
                added++;
            }
        }

        log("Scanned " + totalScanned + " fluids, added " + added + " to set");
    }

    // ============================================================
    // ===== 缓存管理 =============================================
    // ============================================================

    public static void clearCache() {
        cachedAlloyRecipes = null;
        cachedAllMaterials = null;
        recipeCache.clear();
        cacheTime = 0;
        log("Cache cleared");
    }

    public static void forceReload() {
        clearCache();
        getAlloyRecipes();
        log("Forced reload completed");
    }

    // ============================================================
    // ===== 日志工具 =============================================
    // ============================================================

    private static void log(String message) {
        System.out.println("[Tinker's Search] " + message);
    }

    private static void logError(String message) {
        System.err.println("[Tinker's Search] ERROR: " + message);
    }

    // ============================================================
    // ===== 配置选项 =============================================
    // ============================================================

    private static boolean shouldForceKubeJS() {
        // 可以通过配置文件控制
        return false;
    }

    private static boolean shouldForceCraftTweaker() {
        return false;
    }
}