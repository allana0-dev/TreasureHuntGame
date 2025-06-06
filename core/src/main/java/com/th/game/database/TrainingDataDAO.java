package com.th.game.database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import com.badlogic.gdx.math.Vector2;

/**
 * Data Access Object (DAO) for managing treasure collection training data.
 * Handles database initialization, insertion, and querying of collection events.
 */
public class TrainingDataDAO {
    /** database connection URL for the SQLite database file. */
    private static final String DB_URL = "jdbc:sqlite:treasure_hunt_game.db";

    /**
     * Ensures the database and the treasure_collections table exist.
     *
     * @throws SQLException if a database access error occurs
     */
    public static void initializeDatabase() throws SQLException {
        String sqlCreate = "CREATE TABLE IF NOT EXISTS treasure_collections (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "round_number INTEGER, " +
            "map_name TEXT, " +
            "treasure_x REAL, treasure_y REAL, " +
            "collector_x REAL, collector_y REAL, " +
            "collected_by_player BOOLEAN, " +
            "timestamp INTEGER" +
            ")";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sqlCreate)) {
            stmt.execute();
            System.out.println("Database initialized: treasure_collections table created (if not exists).");
        }
    }

    /**
     * Retrieves collection records for treasures located within a certain radius of a landmark.
     *
     * @param mapName    the map name to filter collections by
     * @param landmarkX  x-coordinate of the landmark
     * @param landmarkY  y-coordinate of the landmark
     * @param radius     search radius around the landmark
     * @return list of TreasureCollectionData within the specified radius
     * @throws SQLException if a database access error occurs
     */
    public static List<TreasureCollectionData> getCollectionsNearLandmark(
        String mapName, float landmarkX, float landmarkY, float radius) throws SQLException {

        float minX = landmarkX - radius;
        float maxX = landmarkX + radius;
        float minY = landmarkY - radius;
        float maxY = landmarkY + radius;

        String sqlSelect = "SELECT round_number, map_name, treasure_x, treasure_y, " +
            "collector_x, collector_y, collected_by_player, timestamp " +
            "FROM treasure_collections " +
            "WHERE map_name = ? " +
            "AND treasure_x BETWEEN ? AND ? " +
            "AND treasure_y BETWEEN ? AND ?";

        List<TreasureCollectionData> collections = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sqlSelect)) {

            stmt.setString(1, mapName);
            stmt.setFloat(2, minX);
            stmt.setFloat(3, maxX);
            stmt.setFloat(4, minY);
            stmt.setFloat(5, maxY);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int roundNumber = rs.getInt("round_number");
                    String map = rs.getString("map_name");
                    Vector2 treasurePos = new Vector2(rs.getFloat("treasure_x"), rs.getFloat("treasure_y"));
                    Vector2 collectorPos = new Vector2(rs.getFloat("collector_x"), rs.getFloat("collector_y"));
                    boolean byPlayer = rs.getBoolean("collected_by_player");

                    float distToLandmark = Vector2.dst(
                        treasurePos.x, treasurePos.y, landmarkX, landmarkY);

                    if (distToLandmark <= radius) {
                        TreasureCollectionData data = new TreasureCollectionData(
                            roundNumber, map, treasurePos, collectorPos, byPlayer);
                        collections.add(data);
                    }
                }
            }
        }

        return collections;
    }

    /**
     * Persists a new treasure collection event into the database.
     *
     * @param data the TreasureCollectionData object to save
     * @throws SQLException if a database access error occurs
     */
    public static void saveTreasureCollection(TreasureCollectionData data) throws SQLException {
        String sqlInsert = "INSERT INTO treasure_collections(" +
            "round_number, map_name, treasure_x, treasure_y, collector_x, collector_y, " +
            "collected_by_player, timestamp) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sqlInsert)) {
            stmt.setInt(1, data.getRoundNumber());
            stmt.setString(2, data.getMapName());
            stmt.setFloat(3, data.getTreasurePosition().x);
            stmt.setFloat(4, data.getTreasurePosition().y);
            stmt.setFloat(5, data.getCollectorPosition().x);
            stmt.setFloat(6, data.getCollectorPosition().y);
            stmt.setBoolean(7, data.isCollectedByPlayer());
            stmt.setLong(8, data.getTimeStamp());
            stmt.executeUpdate();
            System.out.println("Treasure collection data saved to database.");
        }
    }

    /**
     * Retrieves all treasure collection records from the database.
     *
     * @return list of all TreasureCollectionData entries
     * @throws SQLException if a database access error occurs
     */
//    public static List<TreasureCollectionData> getAllCollectionData() throws SQLException {
//        String sqlSelect = "SELECT round_number, map_name, treasure_x, treasure_y, " +
//            "collector_x, collector_y, collected_by_player, timestamp " +
//            "FROM treasure_collections";
//        List<TreasureCollectionData> collections = new ArrayList<>();
//        try (Connection conn = DriverManager.getConnection(DB_URL);
//             PreparedStatement stmt = conn.prepareStatement(sqlSelect);
//             ResultSet rs = stmt.executeQuery()) {
//            while (rs.next()) {
//                int roundNumber = rs.getInt("round_number");
//                String mapName = rs.getString("map_name");
//                Vector2 treasurePos = new Vector2(rs.getFloat("treasure_x"), rs.getFloat("treasure_y"));
//                Vector2 collectorPos = new Vector2(rs.getFloat("collector_x"), rs.getFloat("collector_y"));
//                boolean byPlayer = rs.getBoolean("collected_by_player");
//
//                TreasureCollectionData data = new TreasureCollectionData(
//                    roundNumber, mapName, treasurePos, collectorPos, byPlayer);
//                collections.add(data);
//            }
//        }
//        return collections;
//    }

    /**
     * Retrieves all treasure collection records for a given map.
     *
     * @param mapName the name of the map to filter by
     * @return list of TreasureCollectionData entries for that map
     * @throws SQLException if a database access error occurs
     */
    public static List<TreasureCollectionData> getCollectionDataByMap(String mapName) throws SQLException {
        String sqlSelect = "SELECT round_number, map_name, treasure_x, treasure_y, " +
            "collector_x, collector_y, collected_by_player, timestamp " +
            "FROM treasure_collections WHERE map_name = ?";
        List<TreasureCollectionData> collections = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sqlSelect)) {

            stmt.setString(1, mapName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int roundNumber = rs.getInt("round_number");
                    String map = rs.getString("map_name");
                    Vector2 treasurePos = new Vector2(rs.getFloat("treasure_x"), rs.getFloat("treasure_y"));
                    Vector2 collectorPos = new Vector2(rs.getFloat("collector_x"), rs.getFloat("collector_y"));
                    boolean byPlayer = rs.getBoolean("collected_by_player");

                    TreasureCollectionData data = new TreasureCollectionData(
                        roundNumber, map, treasurePos, collectorPos, byPlayer);
                    collections.add(data);
                }
            }
        }
        return collections;
    }
}
