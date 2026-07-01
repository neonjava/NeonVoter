package dev.neonjava.neonvoter;

import dev.neonjava.neonvoter.command.NeonVoterCommand;
import dev.neonjava.neonvoter.crypto.KeyManager;
import dev.neonjava.neonvoter.listener.PlayerJoinListener;
import dev.neonjava.neonvoter.network.VoteServer;
import dev.neonjava.neonvoter.reward.RewardManager;
import dev.neonjava.neonvoter.storage.JsonVoteStorage;
import dev.neonjava.neonvoter.storage.VoteStorage;
import dev.neonjava.neonvoter.util.FoliaScheduler;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.security.KeyPair;
import java.util.Base64;
import java.util.UUID;

/**
 * NeonVoter — All-in-one Folia-compatible vote receiver and reward plugin.
 *
 * Combines the functionality of NuVotifier (vote server) and SuperbVote (rewards)
 * into a single, modern plugin. No separate Votifier plugin required.
 *
 * Author: neonjava
 * Folia-supported: true
 *
 * How it works:
 *  1. On enable, generates (or loads) an RSA key pair used for v1 protocol auth.
 *  2. Starts an embedded Netty vote server on the configured port.
 *  3. Voting websites connect, send an encrypted (v1) or signed (v2) vote packet.
 *  4. The vote is decrypted/verified and passed to RewardManager.
 *  5. RewardManager delivers configured rewards to online players,
 *     or queues them for offline players.
 */
public final class NeonVoter extends JavaPlugin {

    private static NeonVoter instance;

    private KeyPair rsaKeyPair;
    private VoteServer voteServer;
    private VoteStorage voteStorage;
    private RewardManager rewardManager;

    @Override
    public void onEnable() {
        instance = this;

        // ── Config ─────────────────────────────────────────────────────────────
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        // ── RSA Keys ───────────────────────────────────────────────────────────
        File rsaDir = new File(getDataFolder(), "rsa");
        File pubKey = new File(rsaDir, "public.key");
        File privKey = new File(rsaDir, "private.key");

        if (!pubKey.exists() || !privKey.exists()) {
            getLogger().info("[NeonVoter] Generating RSA key pair (2048-bit)... please wait.");
            try {
                rsaKeyPair = KeyManager.generate();
                KeyManager.save(rsaDir, rsaKeyPair);
                getLogger().info("[NeonVoter] Keys saved to: " + rsaDir.getAbsolutePath());
                getLogger().info("[NeonVoter] ╔══════════════════════════════════════════════╗");
                getLogger().info("[NeonVoter] ║  COPY public.key to your voting site(s)!    ║");
                getLogger().info("[NeonVoter] ║  Location: plugins/NeonVoter/rsa/public.key ║");
                getLogger().info("[NeonVoter] ╚══════════════════════════════════════════════╝");
            } catch (Exception e) {
                getLogger().severe("[NeonVoter] Failed to generate RSA keys: " + e.getMessage());
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        } else {
            try {
                rsaKeyPair = KeyManager.load(rsaDir);
                getLogger().info("[NeonVoter] RSA key pair loaded.");
            } catch (Exception e) {
                getLogger().severe("[NeonVoter] Failed to load RSA keys: " + e.getMessage());
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        // ── Token for v2 protocol ──────────────────────────────────────────────
        String token = getConfig().getString("vote-server.token", "");
        if (token == null || token.isEmpty()) {
            token = UUID.randomUUID().toString().replace("-", "");
            getConfig().set("vote-server.token", token);
            saveConfig();
            getLogger().info("[NeonVoter] Generated v2 token and saved to config.yml.");
        }

        // ── Storage ────────────────────────────────────────────────────────────
        voteStorage = new JsonVoteStorage(getDataFolder(), getLogger());

        // ── Reward Manager ─────────────────────────────────────────────────────
        rewardManager = new RewardManager(this, voteStorage);

        // ── Auto-save every 5 minutes ──────────────────────────────────────────
        FoliaScheduler.runAsyncTimer(this, () -> voteStorage.save(), 20 * 60 * 5L, 20 * 60 * 5L);

        // ── Vote Server ────────────────────────────────────────────────────────
        String host    = getConfig().getString("vote-server.host", "0.0.0.0");
        int    port    = getConfig().getInt("vote-server.port", 8192);
        boolean noV1   = getConfig().getBoolean("vote-server.disable-v1", false);
        final String finalToken = token;

        voteServer = new VoteServer(host, port, rsaKeyPair, finalToken, noV1,
                vote -> rewardManager.handleVote(vote), getLogger());
        voteServer.start(err -> {
            if (err != null) {
                getLogger().severe("[NeonVoter] Vote server FAILED to start: " + err.getMessage());
            }
        });

        // ── Commands ───────────────────────────────────────────────────────────
        NeonVoterCommand cmd = new NeonVoterCommand(this);
        getCommand("neonvoter").setExecutor(cmd);
        getCommand("neonvoter").setTabCompleter(cmd);
        getCommand("vote").setExecutor(cmd);

        // ── Event Listeners ────────────────────────────────────────────────────
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        // ── Startup Banner ─────────────────────────────────────────────────────
        getLogger().info("§b§l┌──────────────────────────────────────┐");
        getLogger().info("§b§l│         §fNeonVoter §bv" + getDescription().getVersion() + "              §b│");
        getLogger().info("§b§l│  §7By §fneonjava §7• Folia Ready ✔         §b│");
        getLogger().info("§b§l│  §7Vote server: §f" + host + ":" + port + "          §b│");
        getLogger().info("§b§l│  §7Transport: §f" + (FoliaScheduler.isFolia() ? "Folia" : "Paper/Spigot") + "                §b│");
        getLogger().info("§b§l└──────────────────────────────────────┘");
    }

    @Override
    public void onDisable() {
        if (voteServer != null) voteServer.shutdown();
        if (voteStorage != null) voteStorage.close();
        getLogger().info("[NeonVoter] Plugin disabled. Goodbye!");
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public static NeonVoter getInstance() {
        return instance;
    }

    public RewardManager getRewardManager() { return rewardManager; }
    public VoteStorage getVoteStorage()     { return voteStorage; }
    public KeyPair getRsaKeyPair()          { return rsaKeyPair; }

    public boolean isVoteServerRunning() {
        return voteServer != null && voteServer.isRunning();
    }
}
