package com.th.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MapManager {

    public static class MapInfo {
        private String name;
        private String path;

        public MapInfo(String name, String path) {
            this.name = name;
            this.path = path;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }
    }

    private static List<MapInfo> maps = new ArrayList<>();

    // Static block to initialize available maps.
    static {
        // For now, we only have one map. Add additional maps as needed.
        maps.add(new MapInfo("Map 1", "maps/map1.tmx"));
        // Example for future maps:
        // maps.add(new MapInfo("Map 2", "maps/map2.tmx"));
        // maps.add(new MapInfo("Map 3", "maps/map3.tmx"));
    }

    // Returns a random map from the list.
    public static MapInfo getRandomMap() {
        Random random = new Random();
        int index = random.nextInt(maps.size());
        return maps.get(index);
    }

    // Retrieves a map by its name. Returns null if not found.
    public static MapInfo getMapByName(String name) {
        for (MapInfo map : maps) {
            if (map.getName().equalsIgnoreCase(name)) {
                return map;
            }
        }
        return null;  // Alternatively, you can return a default map.
    }

    // Returns a list of the names of all stored maps.
    public static List<String> getMapNames() {
        List<String> names = new ArrayList<>();
        for (MapInfo map : maps) {
            names.add(map.getName());
        }
        return names;
    }
}

