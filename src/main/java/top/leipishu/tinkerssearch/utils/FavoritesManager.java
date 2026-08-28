package top.leipishu.tinkerssearch.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FavoritesManager {
    private static final String CONFIG_FILE = "tinkerssearch_favorites.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static List<FluidStack> favorites = new ArrayList<>();
    private static Path configPath;

    static {
        configPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
        load();
    }

    public static boolean isFavorite(FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) return false;
        ResourceLocation rl = ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
        if (rl == null) return false;

        for (FluidStack fs : favorites) {
            ResourceLocation fsRl = ForgeRegistries.FLUIDS.getKey(fs.getFluid());
            if (fsRl != null && fsRl.equals(rl)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isFavorite(ResourceLocation fluidId) {
        if (fluidId == null) return false;
        for (FluidStack fs : favorites) {
            ResourceLocation fsRl = ForgeRegistries.FLUIDS.getKey(fs.getFluid());
            if (fsRl != null && fsRl.equals(fluidId)) {
                return true;
            }
        }
        return false;
    }

    public static void toggleFavorite(FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) return;
        ResourceLocation rl = ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
        if (rl == null) return;

        boolean found = false;
        Iterator<FluidStack> iter = favorites.iterator();
        while (iter.hasNext()) {
            FluidStack fs = iter.next();
            ResourceLocation fsRl = ForgeRegistries.FLUIDS.getKey(fs.getFluid());
            if (fsRl != null && fsRl.equals(rl)) {
                iter.remove();
                found = true;
                break;
            }
        }

        if (!found) {
            FluidStack toSave = fluid.copy();
            toSave.setAmount(1);
            favorites.add(toSave);
        }
        save();
    }

    public static List<FluidStack> getFavorites() {
        return new ArrayList<>(favorites);
    }

    @SuppressWarnings("unchecked")
    public static void load() {
        favorites.clear();
        if (!Files.exists(configPath)) {
            save();
            return;
        }

        try (Reader reader = new InputStreamReader(Files.newInputStream(configPath), StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            if (json.isJsonArray()) {
                TypeToken<List<FluidStackData>> typeToken = new TypeToken<List<FluidStackData>>() {};
                List<FluidStackData> dataList = GSON.fromJson(json, typeToken.getType());

                for (FluidStackData data : dataList) {
                    Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(data.fluidId));
                    if (fluid != null) {
                        FluidStack fs = new FluidStack(fluid, 1);
                        favorites.add(fs);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Tinker's Search: Failed to load favorites: " + e.getMessage());
            favorites.clear();
        }
    }

    public static void save() {
        List<FluidStackData> dataList = new ArrayList<>();
        for (FluidStack fs : favorites) {
            ResourceLocation rl = ForgeRegistries.FLUIDS.getKey(fs.getFluid());
            if (rl != null) {
                dataList.add(new FluidStackData(rl.toString(), 1));
            }
        }

        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(configPath), StandardCharsets.UTF_8)) {
            GSON.toJson(dataList, writer);
        } catch (IOException e) {
            System.err.println("Tinker's Search: Failed to save favorites: " + e.getMessage());
        }
    }

    private static class FluidStackData {
        String fluidId;
        int amount;

        FluidStackData(String fluidId, int amount) {
            this.fluidId = fluidId;
            this.amount = amount;
        }
    }
}