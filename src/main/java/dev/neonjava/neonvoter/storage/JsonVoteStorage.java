package dev.neonjava.neonvoter.storage;

import com.google.gson.*;
import dev.neonjava.neonvoter.network.Vote;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.logging.Logger;

/**
 * JSON-file based vote storage.
 *
 * Stores vote counts, streaks and queued offline votes in JSON files
 * inside the plugin data folder.
 *
 * Thread-safe: uses ReentrantReadWriteLock for vote records,
 * and ConcurrentHashMap for the queued vote cache.
 *
 * File format (votes.json v1):
 * {
 *   "version": 1,
 *   "records": {
 *     "PlayerName": {
 *       "votes": 42,
 *       "streak": 7,
 *       "lastVoted": 1720000000000
 *     }
 *   }
 * }
 *
 * Queued votes stored in queued_votes.json:
 * {
 *   "PlayerName": [
 *     {"service": "MinecraftMP", "address": "127.0.0.1", "timestamp": 1720000000000}
 *   ]
 * }
 */
public class JsonVoteStorage implements VoteStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int VERSION = 1;

    private final File votesFile;
    private final File queuedFile;
    private final Logger logger;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // In-memory vote records: playerName (lowercase) → record
    private final Map<String, VoteRecord> records = new HashMap<>();

    // Queued offline votes
    private final Map<String, List<Vote>> queued = new ConcurrentHashMap<>();

    public JsonVoteStorage(File dataFolder, Logger logger) {
        this.votesFile  = new File(dataFolder, "votes.json");
        this.queuedFile = new File(dataFolder, "queued_votes.json");
        this.logger     = logger;
        load();
        loadQueued();
    }

    // ── VoteStorage Implementation ────────────────────────────────────────────

    @Override
    public long incrementVotes(String playerName) {
        String key = playerName.toLowerCase();
        lock.writeLock().lock();
        try {
            VoteRecord r = records.computeIfAbsent(key, k -> new VoteRecord(playerName));
            r.votes++;
            r.displayName = playerName;
            long now = System.currentTimeMillis();
            // Streak logic: if last vote was < 2 days ago, increment streak, else reset
            long dayMs = 86_400_000L;
            if (r.lastVoted > 0 && (now - r.lastVoted) < dayMs * 2) {
                r.streak++;
            } else {
                r.streak = 1;
            }
            r.lastVoted = now;
            return r.votes;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public long getVotes(String playerName) {
        lock.readLock().lock();
        try {
            VoteRecord r = records.get(playerName.toLowerCase());
            return r != null ? r.votes : 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long getStreak(String playerName) {
        lock.readLock().lock();
        try {
            VoteRecord r = records.get(playerName.toLowerCase());
            return r != null ? r.streak : 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void queueVote(String playerName, Vote vote) {
        queued.computeIfAbsent(playerName.toLowerCase(), k -> new ArrayList<>()).add(vote);
        saveQueued();
    }

    @Override
    public List<Vote> popQueuedVotes(String playerName) {
        List<Vote> votes = queued.remove(playerName.toLowerCase());
        if (votes != null && !votes.isEmpty()) {
            saveQueued();
            return votes;
        }
        return Collections.emptyList();
    }

    @Override
    public List<String> getTopVoters(int count) {
        lock.readLock().lock();
        try {
            return records.values().stream()
                    .sorted((a, b) -> Long.compare(b.votes, a.votes))
                    .limit(count)
                    .map(r -> r.displayName + " - " + r.votes + " votes")
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void save() {
        lock.readLock().lock();
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", VERSION);
            JsonObject recs = new JsonObject();
            for (Map.Entry<String, VoteRecord> entry : records.entrySet()) {
                VoteRecord r = entry.getValue();
                JsonObject obj = new JsonObject();
                obj.addProperty("displayName", r.displayName);
                obj.addProperty("votes", r.votes);
                obj.addProperty("streak", r.streak);
                obj.addProperty("lastVoted", r.lastVoted);
                recs.add(entry.getKey(), obj);
            }
            root.add("records", recs);
            try {
                atomicWrite(votesFile, GSON.toJson(root));
            } catch (Exception e) {
                logger.warning("[NeonVoter] Failed to save votes.json: " + e.getMessage());
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void close() {
        save();
        saveQueued();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void load() {
        if (!votesFile.exists()) return;
        try {
            String json = new String(Files.readAllBytes(votesFile.toPath()), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject recs = root.has("records") ? root.getAsJsonObject("records") : root;
            for (Map.Entry<String, JsonElement> entry : recs.entrySet()) {
                JsonObject obj = entry.getValue().getAsJsonObject();
                VoteRecord r = new VoteRecord(entry.getKey());
                if (obj.has("displayName")) r.displayName = obj.get("displayName").getAsString();
                if (obj.has("votes"))       r.votes     = obj.get("votes").getAsLong();
                if (obj.has("streak"))      r.streak    = obj.get("streak").getAsLong();
                if (obj.has("lastVoted"))   r.lastVoted = obj.get("lastVoted").getAsLong();
                records.put(entry.getKey().toLowerCase(), r);
            }
            logger.info("[NeonVoter] Loaded " + records.size() + " vote record(s).");
        } catch (Exception e) {
            logger.warning("[NeonVoter] Failed to load votes.json: " + e.getMessage());
        }
    }

    private void loadQueued() {
        if (!queuedFile.exists()) return;
        try {
            String json = new String(Files.readAllBytes(queuedFile.toPath()), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                List<Vote> list = new ArrayList<>();
                for (JsonElement el : entry.getValue().getAsJsonArray()) {
                    JsonObject obj = el.getAsJsonObject();
                    list.add(new Vote(
                            obj.get("service").getAsString(),
                            entry.getKey(),
                            obj.get("address").getAsString(),
                            obj.get("timestamp").getAsLong()));
                }
                queued.put(entry.getKey().toLowerCase(), list);
            }
        } catch (Exception e) {
            logger.warning("[NeonVoter] Failed to load queued_votes.json: " + e.getMessage());
        }
    }

    private void saveQueued() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, List<Vote>> entry : queued.entrySet()) {
            JsonArray arr = new JsonArray();
            for (Vote v : entry.getValue()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("service",   v.getServiceName());
                obj.addProperty("address",   v.getAddress());
                obj.addProperty("timestamp", v.getTimestamp());
                arr.add(obj);
            }
            root.add(entry.getKey(), arr);
        }
        try { atomicWrite(queuedFile, GSON.toJson(root)); }
        catch (Exception e) { logger.warning("[NeonVoter] Failed to save queued_votes.json: " + e.getMessage()); }
    }

    private static void atomicWrite(File target, String content) throws Exception {
        File tmp = new File(target.getParent(), target.getName() + ".tmp");
        Files.write(tmp.toPath(), content.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ── Data Class ────────────────────────────────────────────────────────────

    private static class VoteRecord {
        String displayName;
        long votes   = 0;
        long streak  = 0;
        long lastVoted = 0;

        VoteRecord(String name) { this.displayName = name; }
    }
}
