package com.th.game.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowListener;
import com.th.game.Main;

/**
 * Launches the desktop (LWJGL3) application in a fullscreen-sized window
 * with decorations enabled (allowing to minimize/maximize) and clean exit support.
 */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // macOS & Windows support
        createApplication();
    }

    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(new Main(), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("TreasureHuntGame");

        // Vsync + FPS cap
        config.useVsync(true);
        config.setForegroundFPS(
            Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1
        );

        // --- FULLSCREEN-SIZED WINDOW WITH DECORATIONS ---
        DisplayMode dm = Lwjgl3ApplicationConfiguration.getDisplayMode();
        // Window size matches full screen resolution
        config.setWindowedMode(dm.width, dm.height);
        // Enable window decorations so minimize/maximize buttons appear
        config.setDecorated(true);
        // Allow resizing to support maximize
        config.setResizable(true);

        config.setWindowListener(new Lwjgl3WindowListener() {
            @Override
            public boolean closeRequested() {
                Gdx.app.exit();
                return false;
            }

            @Override public void created(com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window window) {}
            @Override public void iconified(boolean isIconified) {}
            @Override public void maximized(boolean isMaximized) {}
            @Override public void focusLost() {}
            @Override public void focusGained() {}
            @Override public void filesDropped(String[] files) {}
            @Override public void refreshRequested() {}
        });

        // Icons (optional)
        config.setWindowIcon(
            "libgdx128.png",
            "libgdx64.png",
            "libgdx32.png",
            "libgdx16.png"
        );

        return config;
    }
}
