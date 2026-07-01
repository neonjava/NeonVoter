package dev.neonjava.neonvoter.reward;

import dev.neonjava.neonvoter.NeonVoter;
import dev.neonjava.neonvoter.network.Vote;
import dev.neonjava.neonvoter.storage.VoteStorage;
import dev.neonjava.neonvoter.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Central reward manager.
 *
 * On receiving a vote:
 *  1. Persists the vote in storage (total count, streak).
 *  2. Checks if the player is online.
 *     - If online  → executes all matching rewards immediately.
 *     - If offline → queues the vote (up to config max) for delivery on next login.
 *  3. Broadcasts the vote message.
 *
 * Uses Folia-safe scheduling via {@link FoliaScheduler}.
 */
public class RewardManager {

    private final NeonVoter plugin;
    private final VoteStorage storage;
    private final Logger logger;
    private final List<VoteReward> rewards = new ArrayList<>();

    // Config cache
    private String broadcastMsg;
    private String playerMsg;

    public RewardManager(NeonVoter plugin, VoteStorage storage) {
        this.plugin  = plugin;
        this.storage = storage;
        this.logger  = plugin.getLogger();
        loadRewards();
    }

    /** Load/reload all reward blocks from config.yml. */
    public void loadRewards() {
        rewards.clear();
        broadcastMsg = color(plugin.getConfig().getString("vote-message.broadcast",
                "&a{player} voted on {service}!"));
        playerMsg    = color(plugin.getConfig().getString("vote-message.player-message", ""));

        List<?> rewardList = plugin.getConfig().getList("rewards");
        if (rewardList == null) return;

        for (Object obj : rewardList) {
            if (!(obj instanceof java.util.Map)) continue;
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> map = (java.util.Map<String, Object>) obj;

            String name  = String.valueOf(map.getOrDefault("name", "Unnamed"));
            List<String> services   = castList(map.get("services"));
            List<String> conditions = castList(map.get("conditions"));
            List<String> actions    = castList(map.get("actions"));
            rewards.add(new VoteReward(name, services, conditions, actions));
        }
        logger.info("[NeonVoter] Loaded " + rewards.size() + " reward(s) from config.");
    }

    /**
     * Process an incoming vote.
     * Called from a Netty thread — all Bukkit interactions are dispatched via FoliaScheduler.
     */
    public void handleVote(Vote vote) {
        final String playerName = vote.getUsername();
        final String service    = vote.getServiceName();

        // Schedule reward delivery on a global async/main-safe context
        FoliaScheduler.runAsync(plugin, () -> {
            long totalVotes = storage.incrementVotes(playerName);
            long streak     = storage.getStreak(playerName);

            Player online = Bukkit.getPlayerExact(playerName);

            // Broadcast
            if (!broadcastMsg.isEmpty()) {
                final String bcast = broadcastMsg
                        .replace("{player}", playerName)
                        .replace("{service}", service);
                FoliaScheduler.runGlobal(plugin, () -> Bukkit.broadcast(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(bcast)));
            }

            if (online != null) {
                // Player is online — deliver rewards immediately on their region thread
                final long tv = totalVotes;
                final long st = streak;
                FoliaScheduler.runForPlayer(plugin, online, () ->
                        deliverRewards(online, service, tv, st));
            } else {
                // Player offline — queue if enabled
                boolean queueEnabled = plugin.getConfig().getBoolean("offline-votes.enabled", true);
                int maxQ = plugin.getConfig().getInt("offline-votes.max-queued", 3);
                if (queueEnabled) {
                    storage.queueVote(playerName, vote);
                    logger.info("[NeonVoter] Queued vote for offline player " + playerName);
                }
            }
        });
    }

    /** Deliver queued votes when a player logs in. Called on the player's region thread. */
    public void deliverQueuedVotes(Player player) {
        FoliaScheduler.runAsync(plugin, () -> {
            List<Vote> queued = storage.popQueuedVotes(player.getName());
            if (queued.isEmpty()) return;
            long total  = storage.getVotes(player.getName());
            long streak = storage.getStreak(player.getName());
            for (Vote v : queued) {
                final long t = total;
                final long s = streak;
                FoliaScheduler.runForPlayer(plugin, player, () ->
                        deliverRewards(player, v.getServiceName(), t, s));
                total++;
            }
            player.sendMessage(color("&b&lNeonVoter &8» &7You had &b" + queued.size()
                    + " &7queued vote(s). Rewards delivered!"));
        });
    }

    /** Execute all matching rewards for a player (must be on main/region thread). */
    private void deliverRewards(Player player, String service, long totalVotes, long streak) {
        if (!playerMsg.isEmpty()) {
            player.sendMessage(color(playerMsg
                    .replace("{player}", player.getName())
                    .replace("{service}", service)
                    .replace("{total_votes}", String.valueOf(totalVotes))));
        }

        for (VoteReward reward : rewards) {
            if (!reward.matchesService(service)) continue;
            if (!checkConditions(reward, totalVotes, streak)) continue;
            executeActions(player, reward, service, totalVotes);
        }
    }

    /** Evaluate all conditions on a reward — all must pass. */
    private boolean checkConditions(VoteReward reward, long totalVotes, long streak) {
        for (String cond : reward.getConditions()) {
            if (cond.startsWith("every:")) {
                int n = Integer.parseInt(cond.substring(6).trim());
                if (n <= 0 || totalVotes % n != 0) return false;
            } else if (cond.startsWith("streak:")) {
                int n = Integer.parseInt(cond.substring(7).trim());
                if (streak != n) return false;
            }
        }
        return true;
    }

    /** Execute all actions in a reward. */
    private void executeActions(Player player, VoteReward reward, String service, long totalVotes) {
        for (String action : reward.getActions()) {
            String processed = action
                    .replace("{player}", player.getName())
                    .replace("{service}", service)
                    .replace("{total_votes}", String.valueOf(totalVotes));

            if (processed.startsWith("COMMAND:")) {
                String cmd = processed.substring(8).trim();
                FoliaScheduler.runGlobal(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));

            } else if (processed.startsWith("BROADCAST:")) {
                String msg = color(processed.substring(10).trim());
                Bukkit.broadcast(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(msg));

            } else if (processed.startsWith("MESSAGE:")) {
                player.sendMessage(color(processed.substring(8).trim()));

            } else if (processed.startsWith("SOUND:")) {
                try {
                    Sound sound = Sound.valueOf(processed.substring(6).trim().toUpperCase());
                    player.playSound(player.getLocation(), sound, 1f, 1f);
                } catch (IllegalArgumentException e) {
                    logger.warning("[NeonVoter] Unknown sound: " + processed.substring(6).trim());
                }

            } else if (processed.startsWith("TITLE:")) {
                String[] parts = processed.substring(6).split("\\|", 2);
                String title    = color(parts[0].trim());
                String subtitle = parts.length > 1 ? color(parts[1].trim()) : "";
                player.showTitle(net.kyori.adventure.title.Title.title(
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().deserialize(title),
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().deserialize(subtitle)));
            }
        }
    }

    private String color(String s) {
        return s == null ? "" : s.replace("&", "\u00A7");
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object obj) {
        if (obj instanceof List) {
            List<?> raw = (List<?>) obj;
            List<String> result = new ArrayList<>();
            for (Object o : raw) result.add(String.valueOf(o));
            return result;
        }
        return new ArrayList<>();
    }
}
