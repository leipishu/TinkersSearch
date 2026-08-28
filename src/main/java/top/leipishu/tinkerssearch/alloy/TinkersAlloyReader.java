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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 合金配方读取器 - 完全基于反射，不依赖匠魂具体类
 * 兼容 1.19.2 的匠魂 3.9.x
 */
public class TinkersAlloyReader {

    private static List<AlloyRecipeData> cachedAlloyRecipes = null;
    private static List<FluidStack> cachedAllMaterials = null;
    private static long cacheTime = 0;
    private static final long CACHE_DURATION = 5000;

    private static RecipeType<?> alloyRecipeType = null;
    private static final Map<ResourceLocation, AlloyRecipeData> recipeCache = new ConcurrentHashMap<>();
    private static boolean debugMode = false;

    // KubeJS
    private static boolean kubeJSDetected = false;
    private static boolean kubeJSChecked = false;
    private static boolean craftTweakerDetected = false;
    private static boolean craftTweakerChecked = false;

    public static void setDebugMode(boolean enabled) {
        debugMode = enabled;
    }

    public static List<AlloyRecipeData> getAlloyRecipes() {
        long now = System.currentTimeMillis();
        if (cachedAlloyRecipes != null && (now - cacheTime) < CACHE_DURATION) {
            return cachedAlloyRecipes;
        }

        log("=== TINKER'S SEARCH: Loading Alloy Recipes ===");

        List<AlloyRecipeData> recipes = new ArrayList<>();

        recipes.addAll(loadFromForgeAPI());

        if (recipes.isEmpty()) {
            log("Strategy 1 empty, trying RecipeType lookup...");
            recipes.addAll(loadFromRecipeType());
        }

        if (recipes.isEmpty() || recipes.size() < 10) {
            log("Trying KubeJS integration...");
            recipes.addAll(loadFromKubeJS());
        }

        if (recipes.isEmpty()) {
            log("Trying reflection...");
            recipes.addAll(loadFromReflection());
        }

        Map<ResourceLocation, AlloyRecipeData> uniqueMap = new LinkedHashMap<>();
        for (AlloyRecipeData recipe : recipes) {
            if (!uniqueMap.containsKey(recipe.getId())) {
                uniqueMap.put(recipe.getId(), recipe);
            }
        }
        recipes = new ArrayList<>(uniqueMap.values());

        log("Total alloy recipes loaded: " + recipes.size());

        if (recipes.isEmpty()) {
            log("WARNING: No alloy recipes found! Will use fluid registry as fallback.");
        }

        cachedAlloyRecipes = recipes;
        cacheTime = System.currentTimeMillis();
        cachedAllMaterials = null;

        return cachedAlloyRecipes;
    }

    // ============================================================
    // ===== 策略1：Forge 官方 API（反射解析） =====================
    // ============================================================

    private static List<AlloyRecipeData> loadFromForgeAPI() {
        List<AlloyRecipeData> result = new ArrayList<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) {
                log("No connection, cannot load recipes");
                return result;
            }

            RecipeManager recipeManager = mc.getConnection().getRecipeManager();
            Collection<Recipe<?>> allRecipes = recipeManager.getRecipes();

            log("Forge API: Total recipes = " + allRecipes.size());

            for (Recipe<?> recipe : allRecipes) {
                String className = recipe.getClass().getName();
                if (className.contains("AlloyRecipe") || className.contains("alloy")) {
                    log("Found alloy recipe: " + recipe.getId() + " -> " + className);
                    AlloyRecipeData parsed = parseAlloyRecipeReflection(recipe, recipe.getId());
                    if (parsed != null) {
                        result.add(parsed);
                        recipeCache.put(recipe.getId(), parsed);
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
    // ===== 策略2：RecipeType ====================================
    // ============================================================

    private static List<AlloyRecipeData> loadFromRecipeType() {
        List<AlloyRecipeData> result = new ArrayList<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) return result;

            RecipeManager recipeManager = mc.getConnection().getRecipeManager();
            Level level = mc.level;

            RecipeType<?> type = getAlloyRecipeType();
            if (type == null) {
                log("Alloy RecipeType not found");
                return result;
            }

            try {
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
                        AlloyRecipeData parsed = parseAlloyRecipeReflection(recipe, recipe.getId());
                        if (parsed != null) {
                            result.add(parsed);
                            recipeCache.put(recipe.getId(), parsed);
                        }
                    }
                    log("RecipeType: Found " + result.size() + " alloy recipes");
                }
            } catch (Exception e) {
                log("getRecipesFor failed: " + e.getMessage());
            }

            if (result.isEmpty()) {
                try {
                    Method byTypeMethod = RecipeManager.class.getMethod("byType", RecipeType.class);
                    @SuppressWarnings("unchecked")
                    Map<ResourceLocation, Recipe<?>> typedRecipes =
                            (Map<ResourceLocation, Recipe<?>>) byTypeMethod.invoke(recipeManager, type);

                    if (typedRecipes != null) {
                        for (Map.Entry<ResourceLocation, Recipe<?>> entry : typedRecipes.entrySet()) {
                            AlloyRecipeData parsed = parseAlloyRecipeReflection(entry.getValue(), entry.getKey());
                            if (parsed != null) {
                                result.add(parsed);
                                recipeCache.put(entry.getKey(), parsed);
                            }
                        }
                        log("RecipeType byType: Found " + result.size() + " alloy recipes");
                    }
                } catch (Exception e) {
                    log("byType failed: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logError("RecipeType strategy failed: " + e.getMessage());
            if (debugMode) e.printStackTrace();
        }
        return result;
    }

    // ============================================================
    // ===== 策略3：KubeJS ========================================
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
            Class<?> kubeJSRecipeManagerClass = Class.forName("dev.latvian.mods.kubejs.recipe.RecipeManagerJS");
            Method getInstance = kubeJSRecipeManagerClass.getMethod("getInstance");
            Object kubeJSManager = getInstance.invoke(null);

            if (kubeJSManager == null) {
                log("KubeJS manager is null");
                return result;
            }

            Map<?, ?> recipesMap = null;

            try {
                Method getRecipes = kubeJSManager.getClass().getMethod("getRecipes");
                Object obj = getRecipes.invoke(kubeJSManager);
                if (obj instanceof Map) {
                    recipesMap = (Map<?, ?>) obj;
                }
            } catch (NoSuchMethodException e) {
                try {
                    Method getAllRecipes = kubeJSManager.getClass().getMethod("getAllRecipes");
                    Object obj = getAllRecipes.invoke(kubeJSManager);
                    if (obj instanceof Map) {
                        recipesMap = (Map<?, ?>) obj;
                    }
                } catch (Exception ignored) {}
            }

            if (recipesMap == null) {
                try {
                    Field recipesField = kubeJSManager.getClass().getDeclaredField("recipes");
                    recipesField.setAccessible(true);
                    Object obj = recipesField.get(kubeJSManager);
                    if (obj instanceof Map) {
                        recipesMap = (Map<?, ?>) obj;
                    }
                } catch (Exception ignored) {}
            }

            if (recipesMap == null) {
                log("KubeJS: Could not access recipes map");
                return result;
            }

            for (Map.Entry<?, ?> entry : recipesMap.entrySet()) {
                Object recipeObj = entry.getValue();
                if (recipeObj == null) continue;

                if (isKubeJSAlloyRecipe(recipeObj)) {
                    AlloyRecipeData parsed = parseKubeJSRecipe(recipeObj);
                    if (parsed != null) {
                        result.add(parsed);
                    }
                }
            }

            log("KubeJS: Found " + result.size() + " alloy recipes");

        } catch (Exception e) {
            logError("KubeJS integration failed: " + e.getMessage());
            if (debugMode) e.printStackTrace();
        }

        return result;
    }

    private static boolean isKubeJSAlloyRecipe(Object recipeObj) {
        try {
            Class<?> clazz = recipeObj.getClass();
            String className = clazz.getName().toLowerCase();

            if (className.contains("alloy") || className.contains("alloyrecipe")) {
                return true;
            }

            try {
                Field typeField = clazz.getDeclaredField("type");
                typeField.setAccessible(true);
                Object type = typeField.get(recipeObj);
                if (type != null && type.toString().toLowerCase().contains("alloy")) {
                    return true;
                }
            } catch (Exception ignored) {}

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

    private static AlloyRecipeData parseKubeJSRecipe(Object recipeObj) {
        try {
            Class<?> clazz = recipeObj.getClass();
            ResourceLocation id = null;

            try {
                Field idField = clazz.getDeclaredField("id");
                idField.setAccessible(true);
                Object idObj = idField.get(recipeObj);
                if (idObj instanceof ResourceLocation) {
                    id = (ResourceLocation) idObj;
                }
            } catch (Exception ignored) {}

            if (id == null) {
                try {
                    Method getId = clazz.getMethod("getId");
                    Object idObj = getId.invoke(recipeObj);
                    if (idObj instanceof ResourceLocation) {
                        id = (ResourceLocation) idObj;
                    }
                } catch (Exception ignored) {}
            }

            if (id == null) {
                id = new ResourceLocation("kubejs", "alloy_" + System.currentTimeMillis());
            }

            FluidStack output = null;
            try {
                Method getOutput = clazz.getMethod("getOutput");
                Object outputObj = getOutput.invoke(recipeObj);
                if (outputObj instanceof FluidStack) {
                    output = (FluidStack) outputObj;
                }
            } catch (Exception ignored) {}

            if (output == null) {
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

            List<AlloyRecipeData.FluidIngredientData> inputs = new ArrayList<>();
            try {
                Method getIngredients = clazz.getMethod("getIngredients");
                Object ingredientsObj = getIngredients.invoke(recipeObj);
                if (ingredientsObj instanceof List) {
                    List<?> ingredientList = (List<?>) ingredientsObj;
                    for (Object ing : ingredientList) {
                        AlloyRecipeData.FluidIngredientData data = extractFluidIngredient(ing);
                        if (data != null) {
                            inputs.add(data);
                        }
                    }
                }
            } catch (Exception ignored) {}

            if (inputs.isEmpty()) {
                try {
                    Field inputsField = clazz.getDeclaredField("inputs");
                    inputsField.setAccessible(true);
                    Object inputsObj = inputsField.get(recipeObj);
                    if (inputsObj instanceof List) {
                        List<?> ingredientList = (List<?>) inputsObj;
                        for (Object ing : ingredientList) {
                            AlloyRecipeData.FluidIngredientData data = extractFluidIngredient(ing);
                            if (data != null) {
                                inputs.add(data);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (inputs.isEmpty()) return null;

            int temperature = 0;
            try {
                Method getTemperature = clazz.getMethod("getTemperature");
                Object tempObj = getTemperature.invoke(recipeObj);
                if (tempObj instanceof Integer) {
                    temperature = (Integer) tempObj;
                }
            } catch (Exception ignored) {}

            if (temperature == 0) {
                try {
                    Field tempField = clazz.getDeclaredField("temperature");
                    tempField.setAccessible(true);
                    Object tempObj = tempField.get(recipeObj);
                    if (tempObj instanceof Integer) {
                        temperature = (Integer) tempObj;
                    }
                } catch (Exception ignored) {}
            }

            return new AlloyRecipeData(id, inputs, output, temperature);

        } catch (Exception e) {
            logError("Failed to parse KubeJS recipe: " + e.getMessage());
            if (debugMode) e.printStackTrace();
            return null;
        }
    }

    // ============================================================
    // ===== 策略4：反射 ==========================================
    // ============================================================

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<AlloyRecipeData> loadFromReflection() {
        List<AlloyRecipeData> result = new ArrayList<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) return result;

            RecipeManager recipeManager = mc.getConnection().getRecipeManager();

            String[] fieldNames = {"recipes", "recipesMap", "byName", "recipeMap"};

            for (String fieldName : fieldNames) {
                try {
                    Field field = RecipeManager.class.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object recipesObj = field.get(recipeManager);

                    if (recipesObj instanceof Map) {
                        Map<?, ?> recipesMap = (Map<?, ?>) recipesObj;

                        for (Map.Entry<?, ?> entry : recipesMap.entrySet()) {
                            Object value = entry.getValue();
                            if (value instanceof Map) {
                                Map<?, ?> innerMap = (Map<?, ?>) value;
                                for (Map.Entry<?, ?> innerEntry : innerMap.entrySet()) {
                                    Object recipeObj = innerEntry.getValue();
                                    if (recipeObj != null && isAlloyRecipeObject(recipeObj)) {
                                        ResourceLocation id = extractId(innerEntry.getKey());
                                        if (id != null) {
                                            AlloyRecipeData parsed = parseAlloyRecipeReflection(recipeObj, id);
                                            if (parsed != null) {
                                                result.add(parsed);
                                                recipeCache.put(id, parsed);
                                            }
                                        }
                                    }
                                }
                            } else if (isAlloyRecipeObject(value)) {
                                ResourceLocation id = extractId(entry.getKey());
                                if (id != null) {
                                    AlloyRecipeData parsed = parseAlloyRecipeReflection(value, id);
                                    if (parsed != null) {
                                        result.add(parsed);
                                        recipeCache.put(id, parsed);
                                    }
                                }
                            }
                        }

                        if (!result.isEmpty()) {
                            log("Reflection: Found " + result.size() + " alloy recipes via '" + fieldName + "'");
                            break;
                        }
                    }
                } catch (NoSuchFieldException e) {
                    // 继续
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
    // ===== 核心：反射解析 AlloyRecipe ===========================
    // ============================================================

    private static boolean isAlloyRecipeObject(Object obj) {
        if (obj == null) return false;
        String className = obj.getClass().getName().toLowerCase();
        return className.contains("alloyrecipe") || className.contains("alloy");
    }

    /**
     * 完全通过反射解析合金配方，不依赖任何匠魂具体类
     */
    private static AlloyRecipeData parseAlloyRecipeReflection(Object recipeObj, ResourceLocation id) {
        try {
            Class<?> clazz = recipeObj.getClass();
            List<AlloyRecipeData.FluidIngredientData> inputData = new ArrayList<>();

            // ===== 获取 inputs 字段 =====
            List<?> inputs = null;

            // 尝试字段名 "inputs"
            try {
                Field field = clazz.getDeclaredField("inputs");
                field.setAccessible(true);
                Object obj = field.get(recipeObj);
                if (obj instanceof List) {
                    inputs = (List<?>) obj;
                }
            } catch (NoSuchFieldException e) {
                // 尝试 "ingredients"
                try {
                    Field field = clazz.getDeclaredField("ingredients");
                    field.setAccessible(true);
                    Object obj = field.get(recipeObj);
                    if (obj instanceof List) {
                        inputs = (List<?>) obj;
                    }
                } catch (Exception ignored) {}
            }

            // 尝试方法
            if (inputs == null) {
                try {
                    Method method = clazz.getMethod("getInputs");
                    Object obj = method.invoke(recipeObj);
                    if (obj instanceof List) {
                        inputs = (List<?>) obj;
                    }
                } catch (Exception ignored) {}
            }

            if (inputs == null || inputs.isEmpty()) {
                log("Recipe " + id + " has no parseable inputs");
                return null;
            }

            // ===== 遍历 inputs，提取流体信息 =====
            for (Object ing : inputs) {
                AlloyRecipeData.FluidIngredientData data = extractFluidIngredient(ing);
                if (data != null) {
                    inputData.add(data);
                }
            }

            if (inputData.isEmpty()) {
                log("Recipe " + id + " has no parseable fluid inputs");
                return null;
            }

            // ===== 获取输出 =====
            FluidStack output = null;

            // 尝试字段 "output" 或 "result"
            try {
                Field field = clazz.getDeclaredField("output");
                field.setAccessible(true);
                Object obj = field.get(recipeObj);
                if (obj instanceof FluidStack) {
                    output = (FluidStack) obj;
                }
            } catch (Exception ignored) {}

            if (output == null) {
                try {
                    Field field = clazz.getDeclaredField("result");
                    field.setAccessible(true);
                    Object obj = field.get(recipeObj);
                    if (obj instanceof FluidStack) {
                        output = (FluidStack) obj;
                    }
                } catch (Exception ignored) {}
            }

            if (output == null) {
                try {
                    Method method = clazz.getMethod("getOutput");
                    Object obj = method.invoke(recipeObj);
                    if (obj instanceof FluidStack) {
                        output = (FluidStack) obj;
                    }
                } catch (Exception ignored) {}
            }

            if (output == null) {
                log("Recipe " + id + " has null output");
                return null;
            }

            // ===== 获取温度 =====
            int temperature = 0;
            try {
                Field field = clazz.getDeclaredField("temperature");
                field.setAccessible(true);
                Object obj = field.get(recipeObj);
                if (obj instanceof Integer) {
                    temperature = (Integer) obj;
                }
            } catch (Exception ignored) {}

            if (temperature == 0) {
                try {
                    Method method = clazz.getMethod("getTemperature");
                    Object obj = method.invoke(recipeObj);
                    if (obj instanceof Integer) {
                        temperature = (Integer) obj;
                    }
                } catch (Exception ignored) {}
            }

            return new AlloyRecipeData(id, inputData, output, temperature);

        } catch (Exception e) {
            logError("Failed to parse recipe " + id + ": " + e.getMessage());
            if (debugMode) e.printStackTrace();
            return null;
        }
    }

    /**
     * 从任意对象中提取流体成分（反射方式）
     */
    private static AlloyRecipeData.FluidIngredientData extractFluidIngredient(Object obj) {
        try {
            if (obj == null) return null;

            // 情况1：直接是 FluidStack
            if (obj instanceof FluidStack) {
                FluidStack fs = (FluidStack) obj;
                return new AlloyRecipeData.FluidIngredientData(fs, fs.getAmount());
            }

            Class<?> clazz = obj.getClass();
            String className = clazz.getName();

            // 情况2：FluidIngredient（mantle 的）
            if (className.contains("FluidIngredient")) {
                // 尝试获取 fluids
                List<?> fluids = null;
                try {
                    Method getFluids = clazz.getMethod("getFluids");
                    Object fluidsObj = getFluids.invoke(obj);
                    if (fluidsObj instanceof List) {
                        fluids = (List<?>) fluidsObj;
                    }
                } catch (Exception ignored) {}

                if (fluids != null && !fluids.isEmpty()) {
                    Object first = fluids.get(0);
                    if (first instanceof FluidStack) {
                        FluidStack fs = (FluidStack) first;
                        // 尝试获取 amount
                        int amount = fs.getAmount();
                        try {
                            Method getAmount = clazz.getMethod("getAmount", Fluid.class);
                            Object amtObj = getAmount.invoke(obj, fs.getFluid());
                            if (amtObj instanceof Integer) {
                                amount = (Integer) amtObj;
                            }
                        } catch (Exception ignored) {}
                        FluidStack copy = fs.copy();
                        copy.setAmount(amount);
                        return new AlloyRecipeData.FluidIngredientData(copy, amount);
                    }
                }

                // 备用：尝试从字段读取
                try {
                    Field amountField = clazz.getDeclaredField("amount");
                    amountField.setAccessible(true);
                    int amount = (int) amountField.get(obj);
                    Field fluidField = clazz.getDeclaredField("fluid");
                    fluidField.setAccessible(true);
                    Object fluidObj = fluidField.get(obj);
                    if (fluidObj instanceof FluidStack) {
                        FluidStack fs = (FluidStack) fluidObj;
                        FluidStack copy = fs.copy();
                        copy.setAmount(amount);
                        return new AlloyRecipeData.FluidIngredientData(copy, amount);
                    }
                } catch (Exception ignored) {}
            }

            // 情况3：AlloyIngredient
            if (className.contains("AlloyIngredient")) {
                // 尝试获取 fluid 或 tag
                Object fluidIngredient = null;
                int amount = 0;

                // 尝试 getFluid() 方法
                try {
                    Method getFluid = clazz.getMethod("getFluid");
                    fluidIngredient = getFluid.invoke(obj);
                } catch (Exception ignored) {}

                // 尝试 getAmount() 方法
                try {
                    Method getAmount = clazz.getMethod("getAmount");
                    Object amtObj = getAmount.invoke(obj);
                    if (amtObj instanceof Integer) {
                        amount = (Integer) amtObj;
                    }
                } catch (Exception ignored) {}

                // 如果通过方法没找到，尝试字段
                if (fluidIngredient == null) {
                    try {
                        Field fluidField = clazz.getDeclaredField("fluid");
                        fluidField.setAccessible(true);
                        fluidIngredient = fluidField.get(obj);
                    } catch (Exception ignored) {}
                }

                if (amount == 0) {
                    try {
                        Field amountField = clazz.getDeclaredField("amount");
                        amountField.setAccessible(true);
                        amount = (int) amountField.get(obj);
                    } catch (Exception ignored) {}
                }

                if (fluidIngredient != null && amount > 0) {
                    // 递归提取 FluidIngredient
                    AlloyRecipeData.FluidIngredientData data = extractFluidIngredient(fluidIngredient);
                    if (data != null) {
                        FluidStack copy = data.getFluid().copy();
                        copy.setAmount(amount);
                        return new AlloyRecipeData.FluidIngredientData(copy, amount);
                    }
                }
            }

            return null;
        } catch (Exception e) {
            if (debugMode) {
                logError("extractFluidIngredient failed: " + e.getMessage());
            }
            return null;
        }
    }

    // ============================================================
    // ===== 工具方法 =============================================
    // ============================================================

    private static RecipeType<?> getAlloyRecipeType() {
        if (alloyRecipeType != null) {
            return alloyRecipeType;
        }

        for (RecipeType<?> type : Registry.RECIPE_TYPE) {
            String name = type.toString();
            if (name.contains("alloy") || name.contains("Alloy")) {
                alloyRecipeType = type;
                log("Found alloy RecipeType: " + type);
                return type;
            }
        }

        try {
            String[] classNames = {
                    "slimeknights.tconstruct.smeltery.recipe.AlloyRecipe",
                    "slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe"
            };
            for (String className : classNames) {
                try {
                    Class<?> alloyRecipeClass = Class.forName(className);
                    for (RecipeType<?> type : Registry.RECIPE_TYPE) {
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
            }
        } catch (Exception ignored) {}

        log("Alloy RecipeType not found");
        return null;
    }

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

    // ============================================================
    // ===== 材料列表 =============================================
    // ============================================================

    public static List<FluidStack> getAllSmelteryFluids() {
        long now = System.currentTimeMillis();
        if (cachedAllMaterials != null && (now - cacheTime) < CACHE_DURATION) {
            return cachedAllMaterials;
        }

        Set<ResourceLocation> fluidSet = new HashSet<>();
        List<AlloyRecipeData> recipes = getAlloyRecipes();

        for (AlloyRecipeData recipe : recipes) {
            for (AlloyRecipeData.FluidIngredientData input : recipe.getInputs()) {
                FluidStack fs = input.getFluid();
                if (fs != null && !fs.isEmpty()) {
                    addFluidToSet(fs, fluidSet);
                }
            }
            FluidStack result = recipe.getResult();
            if (result != null && !result.isEmpty()) {
                addFluidToSet(result, fluidSet);
            }
        }

        if (fluidSet.isEmpty()) {
            log("No fluids found from recipes, scanning fluid registry...");
            scanFluidRegistry(fluidSet);
        }

        cachedAllMaterials = new ArrayList<>();
        for (ResourceLocation rl : fluidSet) {
            Fluid fluid = ForgeRegistries.FLUIDS.getValue(rl);
            if (fluid != null) {
                cachedAllMaterials.add(new FluidStack(fluid, 1000));
            }
        }

        cachedAllMaterials.sort((a, b) -> {
            String nameA = a.getDisplayName().getString().replace("Molten ", "").replace("熔融", "");
            String nameB = b.getDisplayName().getString().replace("Molten ", "").replace("熔融", "");
            return nameA.compareToIgnoreCase(nameB);
        });

        log("Total alloy-relevant fluids: " + cachedAllMaterials.size());
        return cachedAllMaterials;
    }

    private static void addFluidToSet(FluidStack fs, Set<ResourceLocation> set) {
        if (fs == null || fs.isEmpty()) return;
        // ✅ 1.19.2：使用 ForgeRegistries 获取注册名
        ResourceLocation rl = ForgeRegistries.FLUIDS.getKey(fs.getFluid());
        if (rl == null) return;

        String path = rl.getPath();
        if (path.contains("flowing") || path.contains("flow")) {
            return;
        }
        set.add(rl);
    }

    private static void scanFluidRegistry(Set<ResourceLocation> set) {
        int totalScanned = 0;
        int added = 0;

        for (Fluid fluid : ForgeRegistries.FLUIDS) {
            // ✅ 1.19.2：使用 ForgeRegistries 获取注册名
            ResourceLocation rl = ForgeRegistries.FLUIDS.getKey(fluid);
            if (rl == null) continue;
            totalScanned++;

            String path = rl.getPath();
            if (path.contains("flowing") || path.contains("flow")) {
                continue;
            }

            if (rl.getNamespace().equals("tconstruct") ||
                    path.contains("molten") ||
                    path.contains("liquid") ||
                    path.contains("metal")) {
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
    // ===== 日志 =================================================
    // ============================================================

    private static void log(String message) {
        System.out.println("[Tinker's Search] " + message);
    }

    private static void logError(String message) {
        System.err.println("[Tinker's Search] ERROR: " + message);
    }
}