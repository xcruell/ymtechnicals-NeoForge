package com.yesmenn.technicals.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Soundeffectz3000SoundLibrary {
    private static volatile Index index;

    private Soundeffectz3000SoundLibrary() {
    }

    public static List<ResourceLocation> sounds() {
        return index().sounds();
    }

    public static List<String> namespaces() {
        return index().namespaces();
    }

    public static List<ResourceLocation> sounds(String namespace, Category category) {
        Map<Category, List<ResourceLocation>> categories = index().byNamespace().get(namespace);
        return categories == null ? List.of() : categories.getOrDefault(category, List.of());
    }

    public static int count(String namespace, Category category) {
        if ("*".equals(namespace)) {
            return index().byCategory().getOrDefault(category, List.of()).size();
        }
        return sounds(namespace, category).size();
    }

    public static List<ResourceLocation> sounds(Category category) {
        return index().byCategory().getOrDefault(category, List.of());
    }

    private static Index index() {
        Index current = index;
        if (current == null) {
            synchronized (Soundeffectz3000SoundLibrary.class) {
                current = index;
                if (current == null) {
                    current = buildIndex();
                    index = current;
                }
            }
        }
        return current;
    }

    private static Index buildIndex() {
        List<ResourceLocation> sounds = BuiltInRegistries.SOUND_EVENT.keySet().stream().sorted().toList();
        List<String> namespaces = sounds.stream()
                .map(ResourceLocation::getNamespace)
                .distinct()
                .sorted()
                .toList();
        Map<Category, List<ResourceLocation>> byCategory = new EnumMap<>(Category.class);
        Map<String, Map<Category, List<ResourceLocation>>> byNamespace = new LinkedHashMap<>();
        for (Category category : Category.values()) {
            byCategory.put(category, sounds.stream()
                    .filter(id -> category(id) == category)
                    .toList());
        }
        for (String namespace : namespaces) {
            Map<Category, List<ResourceLocation>> categories = new EnumMap<>(Category.class);
            for (Category category : Category.values()) {
                categories.put(category, byCategory.get(category).stream()
                        .filter(id -> id.getNamespace().equals(namespace))
                        .toList());
            }
            byNamespace.put(namespace, Map.copyOf(categories));
        }
        return new Index(sounds, namespaces, Map.copyOf(byCategory), Map.copyOf(byNamespace));
    }

    private static Category category(ResourceLocation id) {
        String path = id.getPath();
        String root = path.substring(0, path.indexOf('.') < 0 ? path.length() : path.indexOf('.'));
        return switch (root) {
            case "block" -> Category.BLOCKS;
            case "entity" -> Category.ENTITIES;
            case "music", "music_disc", "record" -> Category.MUSIC;
            case "ambient" -> Category.AMBIENT;
            case "item" -> Category.ITEMS;
            case "ui" -> Category.UI;
            case "weather" -> Category.WEATHER;
            default -> Category.MISC;
        };
    }

    public enum Category {
        BLOCKS("Blocks"),
        ENTITIES("Entities"),
        MUSIC("Music"),
        AMBIENT("Ambient"),
        ITEMS("Items"),
        UI("UI"),
        WEATHER("Weather"),
        MISC("Misc");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private record Index(
            List<ResourceLocation> sounds,
            List<String> namespaces,
            Map<Category, List<ResourceLocation>> byCategory,
            Map<String, Map<Category, List<ResourceLocation>>> byNamespace) {
    }
}
