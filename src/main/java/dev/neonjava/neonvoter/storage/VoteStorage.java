package dev.neonjava.neonvoter.storage;

import dev.neonjava.neonvoter.network.Vote;

import java.util.List;

/**
 * Interface for vote persistence.
 * Implementations: {@link JsonVoteStorage}, and future MySQLVoteStorage.
 */
public interface VoteStorage {

    /** Increment the vote count for this player, return new total. */
    long incrementVotes(String playerName);

    /** Get the current total vote count for this player. */
    long getVotes(String playerName);

    /** Get the current consecutive vote streak for this player. */
    long getStreak(String playerName);

    /** Queue a vote for an offline player. */
    void queueVote(String playerName, Vote vote);

    /** Remove and return all queued votes for a player (called on login). */
    List<Vote> popQueuedVotes(String playerName);

    /** Get the top N voters as a sorted list of [name, votes] strings. */
    List<String> getTopVoters(int count);

    /** Save current state to disk. */
    void save();

    /** Close any resources (DB connections, etc.) */
    void close();
}
