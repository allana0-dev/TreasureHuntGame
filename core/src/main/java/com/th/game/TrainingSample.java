package com.th.game;

public class TrainingSample {
    // 8 input features: player_x, player_y, ai_x, ai_y, treasure_x, treasure_y, dx, dy
    private double[] features;
    // 4 output labels (one-hot): move_up, move_down, move_left, move_right
    private double[] labels;

    public TrainingSample(double[] features, double[] labels) {
        if (features.length != 8 || labels.length != 4) {
            throw new IllegalArgumentException("Expected 8 features and 4 label values.");
        }
        this.features = features;
        this.labels = labels;
    }

    public double[] getFeatures() {
        return features;
    }

    public double[] getLabels() {
        return labels;
    }
}
