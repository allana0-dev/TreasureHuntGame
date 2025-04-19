package com.th.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Manages the collection of available game maps.
 * Provides methods to retrieve maps by name or at random.
 */
public class MapManager {

    /**
     * Encapsulates metadata for a single map, including its display name and file path.
     */
    public static class MapInfo {
        private final String name;
        private final String path;

        /**
         * Constructs a MapInfo with the given name and TMX file path.
         *
         * @param name the display name of the map
         * @param path the internal file path to the .tmx map file
         */
        public MapInfo(String name, String path) {
            this.name = name;
            this.path = path;
        }

        /**
         * @return the display name of the map
         */
        public String getName() {
            return name;
        }

        /**
         * @return the file path to the .tmx map resource
         */
        public String getPath() {
            return path;
        }
    }

    /**
     * Internal list of available MapInfo entries.
     */
    private static final List<MapInfo> maps = new ArrayList<>();

    static {
        // Initialize the list of built-in maps
        maps.add(new MapInfo("Map 1", "maps/map1.tmx"));
        // To add more maps, register here:
        // maps.add(new MapInfo("Map 2", "maps/map2.tmx"));
    }

    /**
     * Returns a random map from the available list.
     *
     * @return a randomly chosen MapInfo
     */
    public static MapInfo getRandomMap() {
        Random random = new Random();
        int index = random.nextInt(maps.size());
        return maps.get(index);
    }

    /**
     * Finds a map by its name (case-insensitive).
     *
     * @param name the name to search for
     * @return the matching MapInfo, or null if none found
     */
    public static MapInfo getMapByName(String name) {
        for (MapInfo map : maps) {
            if (map.getName().equalsIgnoreCase(name)) {
                return map;
            }
        }
        return null;
    }

    /**
     * @return a list of all available map names
     */
    public static List<String> getMapNames() {
        List<String> names = new ArrayList<>();
        for (MapInfo map : maps) {
            names.add(map.getName());
        }
        return names;
    }
}
