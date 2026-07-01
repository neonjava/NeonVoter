package dev.neonjava.neonvoter.network;

/**
 * Represents a parsed vote received from a voting website.
 */
public final class Vote {
    private final String serviceName;
    private final String username;
    private final String address;
    private final long timestamp;

    public Vote(String serviceName, String username, String address, long timestamp) {
        this.serviceName = serviceName;
        this.username = username;
        this.address = address;
        this.timestamp = timestamp;
    }

    public String getServiceName() { return serviceName; }
    public String getUsername()    { return username; }
    public String getAddress()     { return address; }
    public long   getTimestamp()   { return timestamp; }

    @Override
    public String toString() {
        return "Vote{service=" + serviceName + ", user=" + username + ", addr=" + address + "}";
    }
}
