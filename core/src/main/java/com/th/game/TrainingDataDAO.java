package com.th.game;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TrainingDataDAO {
    private static final String DB_URL = "jdbc:sqlite:ai_training.db";

    public static void initializeDatabase() throws SQLException {
        String sqlCreate = "CREATE TABLE IF NOT EXISTS training_data (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "player_x REAL, player_y REAL, " +
            "ai_x REAL, ai_y REAL, " +
            "treasure_x REAL, treasure_y REAL, " +
            "dx REAL, dy REAL, " +
            "move_up REAL, move_down REAL, move_left REAL, move_right REAL" +
            ")";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sqlCreate)) {
            stmt.execute();
            System.out.println("Database initialized: training_data table created (if not exists).");
        }
    }

    public static void saveTrainingSample(TrainingSample sample) throws SQLException {
        String sqlInsert = "INSERT INTO training_data(" +
            "player_x, player_y, ai_x, ai_y, treasure_x, treasure_y, dx, dy, " +
            "move_up, move_down, move_left, move_right) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sqlInsert)) {
            double[] features = sample.getFeatures();
            double[] labels = sample.getLabels();
            for (int i = 0; i < 8; i++) {
                stmt.setDouble(i + 1, features[i]);
            }
            for (int i = 0; i < 4; i++) {
                stmt.setDouble(i + 9, labels[i]);
            }
            stmt.executeUpdate();
            System.out.println("Training sample saved to database.");
        }
    }

    public static List<TrainingSample> getAllTrainingSamples() throws SQLException {
        String sqlSelect = "SELECT player_x, player_y, ai_x, ai_y, treasure_x, treasure_y, dx, dy, " +
            "move_up, move_down, move_left, move_right FROM training_data";
        List<TrainingSample> samples = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sqlSelect);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                double[] features = new double[8];
                double[] labels = new double[4];
                features[0] = rs.getDouble("player_x");
                features[1] = rs.getDouble("player_y");
                features[2] = rs.getDouble("ai_x");
                features[3] = rs.getDouble("ai_y");
                features[4] = rs.getDouble("treasure_x");
                features[5] = rs.getDouble("treasure_y");
                features[6] = rs.getDouble("dx");
                features[7] = rs.getDouble("dy");
                labels[0] = rs.getDouble("move_up");
                labels[1] = rs.getDouble("move_down");
                labels[2] = rs.getDouble("move_left");
                labels[3] = rs.getDouble("move_right");
                samples.add(new TrainingSample(features, labels));
            }
        }
        return samples;
    }
}
