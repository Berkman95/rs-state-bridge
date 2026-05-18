package com.rsstatebridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
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

        WorldPoint wp = player.getWorldLocation();

        Map<String, Object> state = new HashMap<>();

        if (client.getMapRegions() != null && client.getMapRegions().length > 0)
        {
            state.put("region_id", client.getMapRegions()[0]);
        }

        Map<String, Object> playerData = new HashMap<>();
        playerData.put("x", wp.getX());
        playerData.put("y", wp.getY());
        playerData.put("plane", wp.getPlane());

        state.put("player", playerData);

        Map<String, Object> camera = new HashMap<>();
        camera.put("yaw", client.getCameraYaw());
        camera.put("pitch", client.getCameraPitch());

        state.put("camera", camera);

        state.put("animation_id", player.getAnimation());
        state.put("is_moving", player.getPoseAnimation() != player.getIdlePoseAnimation());

        writeState(state);
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