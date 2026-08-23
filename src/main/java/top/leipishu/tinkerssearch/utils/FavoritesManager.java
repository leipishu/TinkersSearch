package top.leipishu.tinkerssearch.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class FavoritesManager {
    private static final String CONFIG_FILE = "tinkerssearch_favorites.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Set<ResourceLocation> favorites = new HashSet<>();
    private static Path configPath;

    static {
        configPath = FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE);
        load();
    }

    public static boolean isFavorite(ResourceLocation fluidId) {
        return favorites.contains(fluidId);
    }

    public static void toggleFavorite(ResourceLocation fluidId) {
        if (favorites.contains(fluidId)) {
            favorites.remove(fluidId);
        } else {
            favorites.add(fluidId);
        }
        save();
    }

    public static Set<ResourceLocation> getFavorites() {
        return new HashSet<>(favorites);
    }

    public static void load() {
        if (Files.exists(configPath)) {
            try (Reader reader = new InputStreamReader(Files.newInputStream(configPath), StandardCharsets.UTF_8)) {
                TypeToken<Set<ResourceLocation>> typeToken = new TypeToken<Set<ResourceLocation>>() {};
                favorites = GSON.fromJson(reader, typeToken.getType());
                if (favorites == null) favorites = new HashSet<>();
            } catch (IOException e) {
                System.err.println("Failed to load favorites: " + e.getMessage());
            }
        } else {
            favorites = new HashSet<>();
        }
    }

    public static void save() {
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(configPath), StandardCharsets.UTF_8)) {
            GSON.toJson(favorites, writer);
        } catch (IOException e) {
            System.err.println("Failed to save favorites: " + e.getMessage());
        }
    }
}