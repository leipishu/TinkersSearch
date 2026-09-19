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

import top.leipishu.tinkerssearch.recipe.RecipeReflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TinkersAlloyReader {

    private static List<AlloyRecipeData> cachedAlloyRecipes = null;
    private static List<FluidStack> cachedAllMaterials = null;
    private static Set<ResourceLocation> cachedFluidsWithRecipes = null;
    private static long cacheTime = 0;
    private static final long CACHE_DURATION = 5000;

    private static RecipeType<?> alloyRecipeType = null;

    private static boolean kubeJSDetected = false;
    private static boolean kubeJSChecked = false;

    private static boolean craftTweakerDetected = false;
    private static boolean craftTweakerChecked = false;

    private static final Map<ResourceLocation, AlloyRecipeData> recipeCache = new ConcurrentHashMap<>();

    private static boolean debugMode = false;

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
            log("Strategy 1 (Forge API) returned empty, trying RecipeType filter...");
            recipes.addAll(loadFromRecipeType());
        }

        if (recipes.isEmpty()) {
            log("Strategy 2 (RecipeType) returned empty, trying reflection...");
            recipes.addAll(loadFromReflection());
        }

        if (recipes.isEmpty() || shouldForceKubeJS()) {
            log("Trying KubeJS integration...");
            recipes.addAll(loadFromKubeJS());
        }

        if (recipes.isEmpty() || shouldForceCraftTweaker()) {
            log("Trying CraftTweaker integration...");
            recipes.addAll(loadFromCraftTweaker());
        }

        Map<ResourceLocation, AlloyRecipeData> uniqueMap = new LinkedHashMap<>();
        for (AlloyRecipeData recipe : recipes) {
            if (!uniqueMap.containsKey(recipe.getId())) {
                uniqueMap.put(recipe.getId(), recipe);
            }
        }
        recipes = new ArrayList<>(uniqueMap.values());

        log("Total alloy recipes loaded: " + recipes.size());

        cachedAlloyRecipes = recipes;
        cacheTime = System.currentTimeMillis();
        cachedAllMaterials = null;
        cachedFluidsWithRecipes = null;

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
            Collection<Recipe<?>> allRecipes = recipeManager.getRecipes();

            log("Forge API: Total recipes = " + allRecipes.size());

            for (Recipe<?> recipe : allRecipes) {
                if (isAlloyRecipeObject(recipe)) {
                    ResourceLocation id = recipe.getId();
                    AlloyRecipeData parsed = parseAlloyRecipe(recipe, id);
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

            RecipeType<?> type = getAlloyRecipeType();
            if (type == null) {
                log("Alloy RecipeType not found");
                return result;
            }

            try {
                Method getRecipesForMethod = RecipeManager.class.getMethod(
                        "getRecipesFor", RecipeType.class, Level.class);
                @SuppressWarnings("unchecked")
                List<Recipe<?>> typedRecipes = (List<Recipe<?>>) getRecipesForMethod.invoke(
                        recipeManager, type, level);
                if (typedRecipes != null) {
                    for (Recipe<?> recipe : typedRecipes) {
                        if (isAlloyRecipeObject(recipe)) {
                            ResourceLocation id = recipe.getId();
                            AlloyRecipeData parsed = parseAlloyRecipe(recipe, id);
                            if (parsed != null) {
                                result.add(parsed);
                                recipeCache.put(id, parsed);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}

            if (result.isEmpty()) {
                try {
                    Method byTypeMethod = RecipeManager.class.getMethod("byType", RecipeType.class);
                    @SuppressWarnings("unchecked")
                    Map<ResourceLocation, Recipe<?>> typedRecipes =
                            (Map<ResourceLocation, Recipe<?>>) byTypeMethod.invoke(recipeManager, type);
                    if (typedRecipes != null) {
                        for (Map.Entry<ResourceLocation, Recipe<?>> entry : typedRecipes.entrySet()) {
                            Recipe<?> recipe = entry.getValue();
                            if (isAlloyRecipeObject(recipe)) {
                                AlloyRecipeData parsed = parseAlloyRecipe(recipe, entry.getKey());
                                if (parsed != null) {
                                    result.add(parsed);
                                    recipeCache.put(entry.getKey(), parsed);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (result.isEmpty()) {
                for (RecipeType<?> rt : Registry.RECIPE_TYPE) {
                    try {
                        Method getRecipesForMethod = RecipeManager.class.getMethod(
                                "getRecipesFor", RecipeType.class, Level.class);
                        @SuppressWarnings("unchecked")
                        List<Recipe<?>> typedRecipes = (List<Recipe<?>>) getRecipesForMethod.invoke(
                                recipeManager, rt, level);
                        if (typedRecipes != null) {
                            for (Recipe<?> recipe : typedRecipes) {
                                if (isAlloyRecipeObject(recipe)) {
                                    ResourceLocation id = recipe.getId();
                                    AlloyRecipeData parsed = parseAlloyRecipe(recipe, id);
                                    if (parsed != null) {
                                        result.add(parsed);
                                        recipeCache.put(id, parsed);
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    if (!result.isEmpty()) break;
                }
            }
        } catch (Exception e) {
            logError("RecipeType strategy failed: " + e.getMessage());
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
                                    if (isAlloyRecipeObject(recipeObj)) {
                                        ResourceLocation id = extractId(innerEntry.getKey());
                                        if (id != null) {
                                            AlloyRecipeData parsed = parseAlloyRecipe(recipeObj, id);
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
                                    AlloyRecipeData parsed = parseAlloyRecipe(value, id);
                                    if (parsed != null) {
                                        result.add(parsed);
                                        recipeCache.put(id, parsed);
                                    }
                                }
                            }
                        }
                        if (!result.isEmpty()) break;
                    }
                } catch (NoSuchFieldException ignored) {
                } catch (Exception e) {
                    logError("Reflection via '" + fieldName + "' failed: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logError("Reflection strategy failed: " + e.getMessage());
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
            } catch (ClassNotFoundException e) {
                kubeJSDetected = false;
            }
            kubeJSChecked = true;
        }
        if (!kubeJSDetected) return result;

        try {
            Class<?> kubeJSRecipeManagerClass = Class.forName("dev.latvian.mods.kubejs.recipe.RecipeManagerJS");
            Method getInstance = kubeJSRecipeManagerClass.getMethod("getInstance");
            Object kubeJSManager = getInstance.invoke(null);
            if (kubeJSManager != null) {
                try {
                    Method getRecipes = kubeJSManager.getClass().getMethod("getRecipes");
                    Object recipesObj = getRecipes.invoke(kubeJSManager);
                    if (recipesObj instanceof Map) {
                        Map<?, ?> recipesMap = (Map<?, ?>) recipesObj;
                        for (Map.Entry<?, ?> entry : recipesMap.entrySet()) {
                            Object recipeObj = entry.getValue();
                            if (recipeObj != null && isKubeJSAlloyRecipe(recipeObj)) {
                                AlloyRecipeData parsed = parseKubeJSRecipe(recipeObj, entry.getKey());
                                if (parsed != null) result.add(parsed);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            logError("KubeJS integration failed: " + e.getMessage());
        }
        return result;
    }

    private static boolean isKubeJSAlloyRecipe(Object recipeObj) {
        try {
            String className = recipeObj.getClass().getName().toLowerCase();
            if (className.contains("alloy") || className.contains("alloyrecipe")) return true;
            try {
                Method getType = recipeObj.getClass().getMethod("getType");
                Object type = getType.invoke(recipeObj);
                if (type != null && type.toString().toLowerCase().contains("alloy")) return true;
            } catch (Exception ignored) {}
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static AlloyRecipeData parseKubeJSRecipe(Object recipeObj, Object idObj) {
        try {
            ResourceLocation id = extractId(idObj);
            if (id == null) id = new ResourceLocation("kubejs", "alloy_" + System.currentTimeMillis());

            Class<?> clazz = recipeObj.getClass();
            FluidStack output = null;
            try {
                Method getOutput = clazz.getMethod("getOutput");
                Object outputObj = getOutput.invoke(recipeObj);
                if (outputObj instanceof FluidStack) output = (FluidStack) outputObj;
            } catch (Exception ignored) {}

            if (output == null) {
                try {
                    Field resultField = clazz.getDeclaredField("result");
                    resultField.setAccessible(true);
                    Object resultObj = resultField.get(recipeObj);
                    if (resultObj instanceof FluidStack) output = (FluidStack) resultObj;
                } catch (Exception ignored) {}
            }
            if (output == null) return null;

            List<AlloyRecipeData.FluidIngredientData> inputs = new ArrayList<>();
            try {
                Method getIngredients = clazz.getMethod("getIngredients");
                Object ingredientsObj = getIngredients.invoke(recipeObj);
                if (ingredientsObj instanceof List) {
                    for (Object ing : (List<?>) ingredientsObj) {
                        AlloyRecipeData.FluidIngredientData data = extractFluidIngredient(ing);
                        if (data != null) inputs.add(data);
                    }
                }
            } catch (Exception ignored) {}
            if (inputs.isEmpty()) return null;

            int temperature = 0;
            try {
                Method getTemperature = clazz.getMethod("getTemperature");
                Object tempObj = getTemperature.invoke(recipeObj);
                if (tempObj instanceof Integer) temperature = (Integer) tempObj;
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
            } catch (ClassNotFoundException e) {
                craftTweakerDetected = false;
            }
            craftTweakerChecked = true;
        }
        if (!craftTweakerDetected) return result;

        try {
            Class<?> craftTweakerAPIClass = Class.forName("com.blamejared.crafttweaker.api.CraftTweakerAPI");
            try {
                Method getRecipeManager = craftTweakerAPIClass.getMethod("getRecipeManager");
                Object recipeManager = getRecipeManager.invoke(null);
                if (recipeManager != null) {
                    try {
                        Method getAllRecipes = recipeManager.getClass().getMethod("getAllRecipes");
                        Object recipesObj = getAllRecipes.invoke(recipeManager);
                        if (recipesObj instanceof List) {
                            for (Object recipe : (List<?>) recipesObj) {
                                if (recipe != null && isCraftTweakerAlloyRecipe(recipe)) {
                                    AlloyRecipeData parsed = parseCraftTweakerRecipe(recipe);
                                    if (parsed != null) result.add(parsed);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } catch (NoSuchMethodException ignored) {}
        } catch (Exception e) {
            logError("CraftTweaker integration failed: " + e.getMessage());
        }
        return result;
    }

    private static boolean isCraftTweakerAlloyRecipe(Object recipeObj) {
        try {
            String className = recipeObj.getClass().getName().toLowerCase();
            return className.contains("alloy") || className.contains("alloyrecipe");
        } catch (Exception e) {
            return false;
        }
    }

    private static AlloyRecipeData parseCraftTweakerRecipe(Object recipeObj) {
        try {
            Class<?> clazz = recipeObj.getClass();
            ResourceLocation id = new ResourceLocation("crafttweaker", "alloy_" + System.currentTimeMillis());

            FluidStack output = null;
            try {
                Method getOutput = clazz.getMethod("getOutput");
                Object outputObj = getOutput.invoke(recipeObj);
                if (outputObj instanceof FluidStack) output = (FluidStack) outputObj;
            } catch (Exception ignored) {}
            if (output == null) return null;

            List<AlloyRecipeData.FluidIngredientData> inputs = new ArrayList<>();
            try {
                Method getIngredients = clazz.getMethod("getIngredients");
                Object ingredientsObj = getIngredients.invoke(recipeObj);
                if (ingredientsObj instanceof List) {
                    for (Object ing : (List<?>) ingredientsObj) {
                        AlloyRecipeData.FluidIngredientData data = extractFluidIngredient(ing);
                        if (data != null) inputs.add(data);
                    }
                }
            } catch (Exception ignored) {}
            if (inputs.isEmpty()) return null;

            int temperature = 0;
            try {
                Method getTemperature = clazz.getMethod("getTemperature");
                Object tempObj = getTemperature.invoke(recipeObj);
                if (tempObj instanceof Integer) temperature = (Integer) tempObj;
            } catch (Exception ignored) {}

            return new AlloyRecipeData(id, inputs, output, temperature);
        } catch (Exception e) {
            logError("Failed to parse CraftTweaker recipe: " + e.getMessage());
            return null;
        }
    }

    // ============================================================
    // ===== 核心：反射解析 AlloyRecipe ===========================
    // ============================================================

    private static boolean isAlloyRecipeObject(Object obj) {
        if (obj == null) return false;
        String className = obj.getClass().getName().toLowerCase();
        return className.contains("alloyrecipe") || className.contains("alloy");
    }

    @SuppressWarnings("unchecked")
    private static AlloyRecipeData parseAlloyRecipe(Object recipeObj, ResourceLocation id) {
        try {
            Class<?> clazz = recipeObj.getClass();
            List<AlloyRecipeData.FluidIngredientData> inputData = new ArrayList<>();
            List<?> inputs = null;

            try {
                Field inputField = clazz.getDeclaredField("inputs");
                inputField.setAccessible(true);
                Object obj = inputField.get(recipeObj);
                if (obj instanceof List) inputs = (List<?>) obj;
            } catch (Exception ignored) {}

            if (inputs == null) {
                try {
                    Method getInputs = clazz.getMethod("getInputs");
                    Object result = getInputs.invoke(recipeObj);
                    if (result instanceof List) inputs = (List<?>) result;
                } catch (Exception ignored) {}
            }

            if (inputs == null) {
                try {
                    Method getIngredients = clazz.getMethod("getIngredients");
                    Object result = getIngredients.invoke(recipeObj);
                    if (result instanceof List) inputs = (List<?>) result;
                } catch (Exception ignored) {}
            }

            if (inputs == null || inputs.isEmpty()) return null;

            for (Object ingredient : inputs) {
                if (ingredient == null) continue;
                AlloyRecipeData.FluidIngredientData data = extractFluidIngredient(ingredient);
                if (data != null) inputData.add(data);
            }
            if (inputData.isEmpty()) return null;

            FluidStack output = null;
            try {
                Field outputField = clazz.getDeclaredField("output");
                outputField.setAccessible(true);
                Object obj = outputField.get(recipeObj);
                if (obj instanceof FluidStack) output = (FluidStack) obj;
            } catch (Exception ignored) {}

            if (output == null) {
                try {
                    Field resultField = clazz.getDeclaredField("result");
                    resultField.setAccessible(true);
                    Object obj = resultField.get(recipeObj);
                    if (obj instanceof FluidStack) output = (FluidStack) obj;
                } catch (Exception ignored) {}
            }

            if (output == null) {
                try {
                    Method getOutput = clazz.getMethod("getOutput");
                    Object obj = getOutput.invoke(recipeObj);
                    if (obj instanceof FluidStack) output = (FluidStack) obj;
                } catch (Exception ignored) {}
            }

            if (output == null || output.isEmpty()) return null;

            int temperature = 0;
            try {
                Method getTemperature = clazz.getMethod("getTemperature");
                Object obj = getTemperature.invoke(recipeObj);
                if (obj instanceof Integer) temperature = (Integer) obj;
            } catch (Exception ignored) {}

            if (temperature == 0) {
                try {
                    Field tempField = clazz.getDeclaredField("temperature");
                    tempField.setAccessible(true);
                    Object obj = tempField.get(recipeObj);
                    if (obj instanceof Integer) temperature = (Integer) obj;
                } catch (Exception ignored) {}
            }

            return new AlloyRecipeData(id, inputData, output, temperature);
        } catch (Exception e) {
            logError("Failed to parse recipe " + id + ": " + e.getMessage());
            return null;
        }
    }

    private static AlloyRecipeData.FluidIngredientData extractFluidIngredient(Object obj) {
        try {
            if (obj == null) return null;
            if (obj instanceof FluidStack) {
                FluidStack fs = (FluidStack) obj;
                return new AlloyRecipeData.FluidIngredientData(fs, fs.getAmount());
            }

            Class<?> clazz = obj.getClass();
            String className = clazz.getName();

            if (className.contains("FluidIngredient")) {
                List<?> fluids = null;
                try {
                    Method getFluids = clazz.getMethod("getFluids");
                    Object fluidsObj = getFluids.invoke(obj);
                    if (fluidsObj instanceof List) fluids = (List<?>) fluidsObj;
                } catch (Exception ignored) {}

                if (fluids != null && !fluids.isEmpty()) {
                    Object first = fluids.get(0);
                    if (first instanceof FluidStack) {
                        FluidStack fs = (FluidStack) first;
                        int amount = fs.getAmount();
                        try {
                            Method getAmount = clazz.getMethod("getAmount", Fluid.class);
                            Object amtObj = getAmount.invoke(obj, fs.getFluid());
                            if (amtObj instanceof Integer) amount = (Integer) amtObj;
                        } catch (Exception ignored) {}
                        FluidStack copy = fs.copy();
                        copy.setAmount(amount);
                        return new AlloyRecipeData.FluidIngredientData(copy, amount);
                    }
                }
            }

            if (className.contains("AlloyIngredient")) {
                Object fluidIngredient = null;
                int amount = 0;
                try {
                    Method getFluid = clazz.getMethod("getFluid");
                    fluidIngredient = getFluid.invoke(obj);
                } catch (Exception ignored) {}
                try {
                    Method getAmount = clazz.getMethod("getAmount");
                    Object amtObj = getAmount.invoke(obj);
                    if (amtObj instanceof Integer) amount = (Integer) amtObj;
                } catch (Exception ignored) {}

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
            return null;
        }
    }

    // ============================================================
    // ===== 工具方法 =============================================
    // ============================================================

    private static RecipeType<?> getAlloyRecipeType() {
        if (alloyRecipeType != null) return alloyRecipeType;

        for (RecipeType<?> type : Registry.RECIPE_TYPE) {
            String name = type.toString();
            if (name.contains("alloy") || name.contains("Alloy")) {
                alloyRecipeType = type;
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
                                return type;
                            }
                        } catch (Exception ignored) {}
                    }
                } catch (ClassNotFoundException ignored) {}
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static ResourceLocation extractId(Object obj) {
        if (obj == null) return null;
        if (obj instanceof ResourceLocation) return (ResourceLocation) obj;
        try {
            String str = obj.toString();
            if (str.contains(":")) {
                String[] parts = str.split(":");
                if (parts.length == 2) return new ResourceLocation(parts[0], parts[1]);
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ============================================================
    // ===== 材料列表获取 =========================================
    // ============================================================

    /**
     * 获取所有"有配方"的可冶炼流体。
     */
    public static List<FluidStack> getAllSmelteryFluids() {
        long now = System.currentTimeMillis();
        if (cachedAllMaterials != null && !cachedAllMaterials.isEmpty()
                && (now - cacheTime) < CACHE_DURATION) {
            return cachedAllMaterials;
        }

        Set<ResourceLocation> fluidSet = new HashSet<>();
        scanFluidRegistry(fluidSet);

        Set<ResourceLocation> fluidsWithRecipes = getFluidsWithRecipes();

        List<FluidStack> built = new ArrayList<>();
        for (ResourceLocation rl : fluidSet) {
            if (!fluidsWithRecipes.contains(rl)) continue;
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
            log("Total alloy-relevant fluids: " + built.size());
        } else {
            cachedAllMaterials = null;
        }

        return built;
    }

    /**
     * 收集所有"出现在某个配方中"的流体 ID。
     */
    public static Set<ResourceLocation> getFluidsWithRecipes() {
        long now = System.currentTimeMillis();
        if (cachedFluidsWithRecipes != null && (now - cacheTime) < CACHE_DURATION) {
            return cachedFluidsWithRecipes;
        }

        Set<ResourceLocation> set = new HashSet<>();

        // 1. 合金配方（输入 + 输出）
        try {
            for (AlloyRecipeData recipe : getAlloyRecipes()) {
                for (AlloyRecipeData.FluidIngredientData input : recipe.getInputs()) {
                    FluidStack fs = input.getFluid();
                    if (fs == null || fs.isEmpty()) continue;
                    ResourceLocation rl = ForgeRegistries.FLUIDS.getKey(fs.getFluid());
                    if (rl != null) set.add(rl);
                }
                FluidStack result = recipe.getResult();
                if (result != null && !result.isEmpty()) {
                    ResourceLocation rl = ForgeRegistries.FLUIDS.getKey(result.getFluid());
                    if (rl != null) set.add(rl);
                }
            }
        } catch (Throwable t) {
            logError("getFluidsWithRecipes: alloy scan failed: " + t.getMessage());
        }

        // 2. 所有其它配方
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                for (Recipe<?> recipe : mc.getConnection().getRecipeManager().getRecipes()) {
                    try {
                        List<FluidStack> fluids = RecipeReflection.extractFluids(recipe);
                        if (fluids == null || fluids.isEmpty()) continue;
                        for (FluidStack fs : fluids) {
                            if (fs == null || fs.isEmpty()) continue;
                            ResourceLocation rl = ForgeRegistries.FLUIDS.getKey(fs.getFluid());
                            if (rl != null) set.add(rl);
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            logError("getFluidsWithRecipes: recipe scan failed: " + t.getMessage());
        }

        cachedFluidsWithRecipes = set;
        log("Found " + set.size() + " fluids that participate in some recipe");
        return set;
    }

    private static void addFluidToSet(FluidStack fs, Set<ResourceLocation> set) {
        if (fs == null || fs.isEmpty()) return;
        ResourceLocation rl = ForgeRegistries.FLUIDS.getKey(fs.getFluid());
        if (rl == null) return;
        String path = rl.getPath();
        if (path.contains("flowing") || path.contains("flow")) return;
        set.add(rl);
    }

    private static void scanFluidRegistry(Set<ResourceLocation> set) {
        int totalScanned = 0;
        int added = 0;
        for (Fluid fluid : ForgeRegistries.FLUIDS) {
            ResourceLocation rl = ForgeRegistries.FLUIDS.getKey(fluid);
            if (rl == null) continue;
            totalScanned++;
            String path = rl.getPath();
            if (path.contains("flowing") || path.contains("flow")) continue;
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
        cachedFluidsWithRecipes = null;
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

    private static boolean shouldForceKubeJS() { return false; }
    private static boolean shouldForceCraftTweaker() { return false; }
}