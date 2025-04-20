package com.th.game.extenders.gamescreen;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.th.game.util.map.MapManager;

/**
 * Encapsulates loading and rendering of a tiled map,
 * and exposes map properties for other systems.
 */
public class MapLoader {
    private final TiledMap map;
    private final OrthogonalTiledMapRenderer renderer;
    private final OrthographicCamera camera;
    private final int tileWidth;
    private final int tileHeight;
    private final int mapPixelWidth;
    private final int mapPixelHeight;

    /**
     * Loads the map from the given MapInfo and sets up camera & renderer.
     */
    public MapLoader(MapManager.MapInfo mapInfo) {
        this.map = new TmxMapLoader().load(mapInfo.getPath());

        // map dimensions in tiles
        int mapTilesWide = map.getProperties().get("width", Integer.class);
        int mapTilesHigh = map.getProperties().get("height", Integer.class);
        // individual tile dimensions in pixels
        this.tileWidth  = map.getProperties().get("tilewidth", Integer.class);
        this.tileHeight = map.getProperties().get("tileheight", Integer.class);

        // full map size in pixels
        this.mapPixelWidth  = mapTilesWide  * tileWidth;
        this.mapPixelHeight = mapTilesHigh * tileHeight;

        // Create and position camera centered on full map
        this.camera = new OrthographicCamera(mapPixelWidth, mapPixelHeight);
        this.camera.position.set(mapPixelWidth / 2f, mapPixelHeight / 2f, 0);
        this.camera.update();

        // Create renderer
        this.renderer = new OrthogonalTiledMapRenderer(map);
    }

    /**
     * Renders the map using its internal camera.
     */
    public void render() {
        camera.update();
        renderer.setView(camera);
        renderer.render();
    }

    /**
     * Must be called on resize to adjust the camera viewport.
     */
    public void resize(int width, int height) {
        camera.viewportWidth  = width;
        camera.viewportHeight = height;
        camera.update();
    }

    /**
     * Exposes the loaded TiledMap for gameplay logic (e.g. collision checks).
     */
    public TiledMap getMap() {
        return map;
    }

    /**
     * Exposes the camera so GameScreen or renderers can use its combined matrix.
     */
    public OrthographicCamera getCamera() {
        return camera;
    }

    /**
     * Width of one tile in pixels.
     */
    public int getTileWidth() {
        return tileWidth;
    }

    /**
     * Height of one tile in pixels.
     */
    public int getTileHeight() {
        return tileHeight;
    }

    /**
     * Full map width in pixels.
     */
    public int getMapPixelWidth() {
        return mapPixelWidth;
    }

    /**
     * Full map height in pixels.
     */
    public int getMapPixelHeight() {
        return mapPixelHeight;
    }

    /**
     * Clean up resources.
     */
    public void dispose() {
        renderer.dispose();
        map.dispose();
    }
}
