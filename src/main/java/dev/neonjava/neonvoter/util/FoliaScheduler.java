package dev.neonjava.neonvoter.util;

import dev.neonjava.neonvoter.NeonVoter;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

/**
 * Folia-safe scheduler abstraction.
 *
 * On regular Paper/Spigot, falls back to Bukkit's scheduler.
 * On Folia, uses the new regionised schedulers.
 *
 * NeonVoter only needs:
 *  - runAsync()       — for storage I/O (vote count reads/writes)
 *  - runGlobal()      — for broadcasts (non-region-specific)
 *  - runForPlayer()   — for delivering rewards to a player on their region thread
 */
public final class FoliaScheduler {

    private static final boolean IS_FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        IS_FOLIA = folia;
    }

    private FoliaScheduler() {}

    public static boolean isFolia() { return IS_FOLIA; }

    /** Run a task asynchronously (for I/O, storage, etc.) */
    public static void runAsync(NeonVoter plugin, Runnable task) {
        if (IS_FOLIA) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, $ -> task.run());
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * Run a task on the global region thread (for broadcasts, non-region-specific Bukkit API).
     * On Folia, uses GlobalRegionScheduler.run().
     */
    public static void runGlobal(NeonVoter plugin, Runnable task) {
        if (IS_FOLIA) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, $ -> task.run());
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Run a task on the region owning the given player.
     * On Folia, uses entity scheduler (safest option).
     * On regular Paper, just schedules on the main thread.
     */
    public static void runForPlayer(NeonVoter plugin, Player player, Runnable task) {
        if (IS_FOLIA) {
            player.getScheduler().run(plugin, $ -> task.run(), null);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, task);
        }
    }

    /** Run a repeating async task. */
    public static void runAsyncTimer(NeonVoter plugin, Runnable task, long delayTicks, long periodTicks) {
        if (IS_FOLIA) {
            long delayMs  = delayTicks  * 50L;
            long periodMs = periodTicks * 50L;
            plugin.getServer().getAsyncScheduler().runAtFixedRate(
                    plugin, $ -> task.run(), delayMs, periodMs, TimeUnit.MILLISECONDS);
        } else {
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        }
    }
}
