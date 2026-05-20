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
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GroundObject;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
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

    private static final int MAX_NPC_DISTANCE = 15;
    private static final int MAX_NPCS = 100;

    private static final int MAX_GROUND_ITEM_DISTANCE = 15;
    private static final int MAX_GROUND_ITEMS = 100;

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

        Map<String, Object> status = new HashMap<>();

        status.put("hp", client.getBoostedSkillLevel(Skill.HITPOINTS));
        status.put("hp_real", client.getRealSkillLevel(Skill.HITPOINTS));
        status.put("prayer", client.getBoostedSkillLevel(Skill.PRAYER));
        status.put("prayer_real", client.getRealSkillLevel(Skill.PRAYER));
        status.put("animation_id", player.getAnimation());
        status.put("pose_animation", player.getPoseAnimation());
        status.put("idle_pose_animation", player.getIdlePoseAnimation());
        status.put("is_moving", player.getPoseAnimation() != player.getIdlePoseAnimation());
        status.put("run_energy", client.getEnergy());
        status.put("in_combat", player.getInteracting() != null);

        state.put("status", status);

        Map<String, Object> combat = new HashMap<>();
        Actor interacting = player.getInteracting();

        if (interacting != null)
        {
            Map<String, Object> interactingData = new HashMap<>();

            interactingData.put("name", interacting.getName());
            interactingData.put("animation", interacting.getAnimation());

            WorldPoint wp = interacting.getWorldLocation();

            if (wp != null)
            {
                interactingData.put("x", wp.getX());
                interactingData.put("y", wp.getY());
                interactingData.put("plane", wp.getPlane());
            }

            if (interacting instanceof NPC)
            {
                NPC npc = (NPC) interacting;
                interactingData.put("type", "npc");
                interactingData.put("id", npc.getId());
            }
            else if (interacting instanceof Player)
            {
                interactingData.put("type", "player");
            }
            else
            {
                interactingData.put("type", "actor");
            }

            combat.put("interacting", interactingData);
        }

        state.put("combat", combat);

        state.put("animation_id", player.getAnimation());
        state.put("pose_animation", player.getPoseAnimation());
        state.put("idle_pose_animation", player.getIdlePoseAnimation());
        state.put("is_moving", player.getPoseAnimation() != player.getIdlePoseAnimation());

        state.put("widgets", getWidgetStates());
        state.put("skills", getSkills());

        state.put("inventory", getItemContainer(InventoryID.INVENTORY, true));
        state.put("equipment", getItemContainer(InventoryID.EQUIPMENT, true));
        state.put("bank", getItemContainer(InventoryID.BANK, false));

        state.put("nearby_objects", getNearbyObjects(playerWp));
        state.put("nearby_npcs", getNearbyNpcs(playerWp));
        state.put("ground_items", getGroundItems(playerWp));

        writeState(state);
    }

    private Map<String, Object> getWidgetStates()
    {
        Map<String, Object> widgets = new HashMap<>();

        widgets.put("bank_open", isWidgetVisible(WidgetInfo.BANK_CONTAINER));
        widgets.put("inventory_open", isWidgetVisible(WidgetInfo.INVENTORY));
        widgets.put("level_up_open", isWidgetVisible(WidgetInfo.LEVEL_UP_LEVEL));

        boolean npcDialogueOpen =
                isWidgetVisible(WidgetInfo.DIALOG_NPC_TEXT);

        boolean playerDialogueOpen =
                isWidgetVisible(WidgetInfo.DIALOG_PLAYER_TEXT);

        widgets.put(
                "dialogue_open",
                npcDialogueOpen || playerDialogueOpen
        );

        widgets.put("npc_dialogue_open", npcDialogueOpen);
        widgets.put("player_dialogue_open", playerDialogueOpen);

        // RuneLite versions differ on deposit box widget names.
        // Safe fallback for now.
        widgets.put("deposit_box_open", false);

        return widgets;
    }

    private boolean isWidgetVisible(WidgetInfo widgetInfo)
    {
        try
        {
            Widget widget = client.getWidget(widgetInfo);
            return widget != null && !widget.isHidden();
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    private Map<String, Object> getSkills()
    {
        Map<String, Object> skills = new HashMap<>();

        for (Skill skill : Skill.values())
        {
            Map<String, Object> skillData = new HashMap<>();

            try
            {
                skillData.put("level", client.getRealSkillLevel(skill));
                skillData.put("boosted_level", client.getBoostedSkillLevel(skill));
                skillData.put("xp", client.getSkillExperience(skill));
            }
            catch (Exception ignored)
            {
                continue;
            }

            skills.put(skill.getName().toLowerCase().replace(" ", "_"), skillData);
        }

        return skills;
    }

    private List<Map<String, Object>> getItemContainer(InventoryID inventoryID, boolean includeSlot)
    {
        List<Map<String, Object>> itemDataList = new ArrayList<>();

        ItemContainer container = client.getItemContainer(inventoryID);

        if (container == null)
        {
            return itemDataList;
        }

        Item[] items = container.getItems();

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
            String name = getItemName(id);

            Map<String, Object> itemData = new HashMap<>();

            if (includeSlot)
            {
                itemData.put("slot", slot + 1);
            }
            else
            {
                itemData.put("index", slot);
            }

            itemData.put("id", id);
            itemData.put("name", name);
            itemData.put("quantity", quantity);

            itemDataList.add(itemData);
        }

        return itemDataList;
    }

    private String getItemName(int id)
    {
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
            // Keep item export resilient.
        }

        return name;
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

    private List<Map<String, Object>> getNearbyNpcs(WorldPoint playerWp)
    {
        List<Map<String, Object>> npcs = new ArrayList<>();

        try
        {
            for (NPC npc : client.getNpcs())
            {
                if (npc == null || npc.getWorldLocation() == null)
                {
                    continue;
                }

                WorldPoint npcWp = npc.getWorldLocation();
                int distance = playerWp.distanceTo(npcWp);

                if (distance > MAX_NPC_DISTANCE)
                {
                    continue;
                }

                NPCComposition composition = npc.getTransformedComposition();

                if (composition == null || composition.getName() == null)
                {
                    continue;
                }

                Map<String, Object> npcData = new HashMap<>();
                npcData.put("id", npc.getId());
                npcData.put("name", composition.getName());
                npcData.put("distance", distance);
                npcData.put("x", npcWp.getX());
                npcData.put("y", npcWp.getY());
                npcData.put("plane", npcWp.getPlane());
                npcData.put("animation", npc.getAnimation());
                npcData.put("actions", composition.getActions());

                npcs.add(npcData);

                if (npcs.size() >= MAX_NPCS)
                {
                    return npcs;
                }
            }
        }
        catch (Exception ignored)
        {
            // Keep NPC export resilient.
        }

        return npcs;
    }

    private List<Map<String, Object>> getGroundItems(WorldPoint playerWp)
    {
        List<Map<String, Object>> groundItems = new ArrayList<>();

        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();
        int plane = client.getPlane();

        if (plane < 0 || plane >= tiles.length)
        {
            return groundItems;
        }

        Tile[][] planeTiles = tiles[plane];

        for (int x = 0; x < planeTiles.length; x++)
        {
            for (int y = 0; y < planeTiles[x].length; y++)
            {
                Tile tile = planeTiles[x][y];

                if (tile == null || tile.getGroundItems() == null)
                {
                    continue;
                }

                WorldPoint tileWp = tile.getWorldLocation();

                if (tileWp == null)
                {
                    continue;
                }

                int distance = playerWp.distanceTo(tileWp);

                if (distance > MAX_GROUND_ITEM_DISTANCE)
                {
                    continue;
                }

                for (TileItem tileItem : tile.getGroundItems())
                {
                    if (tileItem == null)
                    {
                        continue;
                    }

                    int id = tileItem.getId();

                    if (id <= 0)
                    {
                        continue;
                    }

                    Map<String, Object> groundItemData = new HashMap<>();
                    groundItemData.put("id", id);
                    groundItemData.put("name", getItemName(id));
                    groundItemData.put("quantity", tileItem.getQuantity());
                    groundItemData.put("distance", distance);
                    groundItemData.put("x", tileWp.getX());
                    groundItemData.put("y", tileWp.getY());
                    groundItemData.put("plane", tileWp.getPlane());

                    groundItems.add(groundItemData);

                    if (groundItems.size() >= MAX_GROUND_ITEMS)
                    {
                        return groundItems;
                    }
                }
            }
        }

        return groundItems;
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