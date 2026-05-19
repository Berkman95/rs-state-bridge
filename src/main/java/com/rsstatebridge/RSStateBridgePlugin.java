package com.rsstatebridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GroundObject;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
        name = "RS State Bridge"
)
public class RSStateBridgePlugin extends Plugin
{
    private static final int MAX_OBJECT_DISTANCE = 15;
    private static final int MAX_OBJECTS = 100;

    @Inject
    private Client client;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final File outputFile = new File(
            System.getProperty("user.home")
                    + File.separator
                    + ".rs-state-bridge"
                    + File.separator
                    + "latest_state.json"
    );

    @Override
    protected void startUp()
    {
        log.info("RS State Bridge started");

        File parent = outputFile.getParentFile();

        if (!parent.exists())
        {
            parent.mkdirs();
        }
    }

    @Override
    protected void shutDown()
    {
        log.info("RS State Bridge stopped");
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        Player player = client.getLocalPlayer();

        if (player == null)
        {
            return;
        }

        WorldPoint playerWp = player.getWorldLocation();

        Map<String, Object> state = new HashMap<>();

        state.put("game_state", client.getGameState().name());
        state.put("tick_count", client.getTickCount());
        state.put("plane", client.getPlane());

        if (client.getMapRegions() != null && client.getMapRegions().length > 0)
        {
            state.put("region_id", client.getMapRegions()[0]);
        }

        Map<String, Object> playerData = new HashMap<>();
        playerData.put("x", playerWp.getX());
        playerData.put("y", playerWp.getY());
        playerData.put("plane", playerWp.getPlane());
        state.put("player", playerData);

        if (client.getLocalDestinationLocation() != null)
        {
            WorldPoint destination = WorldPoint.fromLocal(
                    client,
                    client.getLocalDestinationLocation()
            );

            Map<String, Object> destinationData = new HashMap<>();
            destinationData.put("x", destination.getX());
            destinationData.put("y", destination.getY());
            destinationData.put("plane", destination.getPlane());

            state.put("destination", destinationData);
        }

        Map<String, Object> camera = new HashMap<>();
        camera.put("yaw", client.getCameraYaw());
        camera.put("pitch", client.getCameraPitch());
        state.put("camera", camera);

        state.put("animation_id", player.getAnimation());
        state.put("pose_animation", player.getPoseAnimation());
        state.put("idle_pose_animation", player.getIdlePoseAnimation());
        state.put("is_moving", player.getPoseAnimation() != player.getIdlePoseAnimation());

        state.put("inventory", getInventory());
        state.put("nearby_objects", getNearbyObjects(playerWp));

        writeState(state);
    }

    private List<Map<String, Object>> getInventory()
    {
        List<Map<String, Object>> inventoryData = new ArrayList<>();

        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);

        if (inventory == null)
        {
            return inventoryData;
        }

        Item[] items = inventory.getItems();

        for (int slot = 0; slot < items.length; slot++)
        {
            Item item = items[slot];

            if (item == null)
            {
                continue;
            }

            int id = item.getId();

            if (id <= 0)
            {
                continue;
            }

            int quantity = item.getQuantity();
            String name = "unknown";

            try
            {
                ItemComposition composition = client.getItemDefinition(id);

                if (composition != null && composition.getName() != null)
                {
                    name = composition.getName();
                }
            }
            catch (Exception ignored)
            {
                // Keep inventory export resilient.
            }

            Map<String, Object> itemData = new HashMap<>();
            itemData.put("slot", slot + 1);
            itemData.put("id", id);
            itemData.put("name", name);
            itemData.put("quantity", quantity);

            inventoryData.add(itemData);
        }

        return inventoryData;
    }

    private List<Map<String, Object>> getNearbyObjects(WorldPoint playerWp)
    {
        List<Map<String, Object>> objects = new ArrayList<>();

        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();
        int plane = client.getPlane();

        if (plane < 0 || plane >= tiles.length)
        {
            return objects;
        }

        Tile[][] planeTiles = tiles[plane];

        for (int x = 0; x < planeTiles.length; x++)
        {
            for (int y = 0; y < planeTiles[x].length; y++)
            {
                Tile tile = planeTiles[x][y];

                if (tile == null)
                {
                    continue;
                }

                WallObject wallObject = tile.getWallObject();
                if (wallObject != null)
                {
                    addObject(objects, wallObject, "wall", playerWp);
                }

                DecorativeObject decorativeObject = tile.getDecorativeObject();
                if (decorativeObject != null)
                {
                    addObject(objects, decorativeObject, "decorative", playerWp);
                }

                GroundObject groundObject = tile.getGroundObject();
                if (groundObject != null)
                {
                    addObject(objects, groundObject, "ground", playerWp);
                }

                GameObject[] gameObjects = tile.getGameObjects();
                if (gameObjects != null)
                {
                    for (GameObject gameObject : gameObjects)
                    {
                        if (gameObject != null)
                        {
                            addObject(objects, gameObject, "game", playerWp);
                        }
                    }
                }

                if (objects.size() >= MAX_OBJECTS)
                {
                    return objects;
                }
            }
        }

        return objects;
    }

    private void addObject(List<Map<String, Object>> objects, TileObject object, String type, WorldPoint playerWp)
    {
        if (objects.size() >= MAX_OBJECTS)
        {
            return;
        }

        WorldPoint objectWp = object.getWorldLocation();

        if (objectWp == null)
        {
            return;
        }

        int distance = playerWp.distanceTo(objectWp);

        if (distance > MAX_OBJECT_DISTANCE)
        {
            return;
        }

        int id = object.getId();
        String name = "unknown";
        String[] actions = new String[0];

        try
        {
            ObjectComposition composition = client.getObjectDefinition(id);

            if (composition != null)
            {
                if (composition.getName() != null)
                {
                    name = composition.getName();
                }

                if (composition.getActions() != null)
                {
                    actions = composition.getActions();
                }
            }
        }
        catch (Exception ignored)
        {
            // Keep object export resilient.
        }

        if (name.equals("null") || name.equalsIgnoreCase("unknown"))
        {
            return;
        }

        Map<String, Object> objectData = new HashMap<>();
        objectData.put("id", id);
        objectData.put("name", name);
        objectData.put("type", type);
        objectData.put("distance", distance);
        objectData.put("x", objectWp.getX());
        objectData.put("y", objectWp.getY());
        objectData.put("plane", objectWp.getPlane());
        objectData.put("actions", actions);

        objects.add(objectData);
    }

    private void writeState(Map<String, Object> state)
    {
        try (FileWriter writer = new FileWriter(outputFile))
        {
            gson.toJson(state, writer);
        }
        catch (IOException e)
        {
            log.error("Failed writing state file", e);
        }
    }
}