package com.th.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import java.util.ArrayList;
import java.util.Random;

public class Maze {
    private int width, height;
    private ArrayList<Rectangle> walls;
    private Random random;

    public Maze(int width, int height) {
        this.width = width;
        this.height = height;
        walls = new ArrayList<>();
        random = new Random();
        generateMaze();
    }

    private void generateMaze() {
        // Create several random wall rectangles for demonstration.
        for (int i = 0; i < 10; i++) {
            int w = random.nextInt(100) + 50;
            int h = random.nextInt(20) + 20;
            int x = random.nextInt(width - w);
            int y = random.nextInt(height - h);
            walls.add(new Rectangle(x, y, w, h));
        }
        // Add border walls.
        walls.add(new Rectangle(0, 0, width, 10));           // bottom
        walls.add(new Rectangle(0, height - 10, width, 10));   // top
        walls.add(new Rectangle(0, 0, 10, height));            // left
        walls.add(new Rectangle(width - 10, 0, 10, height));   // right
    }

    public boolean isWalkable(Vector2 pos) {
        if (pos.x < 0 || pos.x > width || pos.y < 0 || pos.y > height) return false;
        for (Rectangle wall : walls) {
            if (wall.contains(pos)) return false;
        }
        return true;
    }

    public void render(ShapeRenderer shapeRenderer) {
        shapeRenderer.setColor(Color.LIME);
        for (Rectangle wall : walls) {
            shapeRenderer.rect(wall.x, wall.y, wall.width, wall.height);
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}

