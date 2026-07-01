package dev.neonjava.neonvoter.listener;

import dev.neonjava.neonvoter.NeonVoter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Delivers queued votes when a player joins the server.
 */
public class PlayerJoinListener implements Listener {

    private final NeonVoter plugin;

    public PlayerJoinListener(NeonVoter plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getRewardManager().deliverQueuedVotes(event.getPlayer());
    }
}
