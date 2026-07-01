package dev.neonjava.neonvoter.reward;

import java.util.List;

/**
 * Represents a single configurable reward block from config.yml.
 *
 * Each reward has optional service filters, optional conditions (every:N, streak:N),
 * and a list of actions to execute (COMMAND, MESSAGE, BROADCAST, SOUND, TITLE).
 */
public final class VoteReward {
    private final String name;
    private final List<String> services;   // empty = all services
    private final List<String> conditions; // e.g. "every:5", "streak:7"
    private final List<String> actions;    // e.g. "COMMAND:give {player} diamond 1"

    public VoteReward(String name, List<String> services, List<String> conditions, List<String> actions) {
        this.name = name;
        this.services = services;
        this.conditions = conditions;
        this.actions = actions;
    }

    public String getName()            { return name; }
    public List<String> getServices()  { return services; }
    public List<String> getConditions(){ return conditions; }
    public List<String> getActions()   { return actions; }

    /**
     * Returns true if this reward applies to the given voting service name.
     * If services list is empty, it applies to all services.
     */
    public boolean matchesService(String serviceName) {
        if (services == null || services.isEmpty()) return true;
        String lower = serviceName.toLowerCase();
        return services.stream().anyMatch(s -> s.toLowerCase().equals(lower));
    }
}
