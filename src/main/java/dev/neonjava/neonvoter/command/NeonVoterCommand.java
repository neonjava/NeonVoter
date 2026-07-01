package dev.neonjava.neonvoter.command;

import dev.neonjava.neonvoter.NeonVoter;
import dev.neonjava.neonvoter.network.Vote;
import dev.neonjava.neonvoter.storage.VoteStorage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main /neonvoter (aliases: /nv, /voter) admin command.
 *
 * Sub-commands:
 *  /nv reload          – reloads config and rewards
 *  /nv info            – shows server IP, port, and public key location
 *  /nv status          – shows vote server status
 *  /nv votes [player]  – shows vote count for yourself or another player
 *  /nv top             – shows top 10 voters
 *  /nv fakevote <player> <service>  – fires a test vote (op only)
 */
public class NeonVoterCommand implements CommandExecutor, TabCompleter {

    private final NeonVoter plugin;

    public NeonVoterCommand(NeonVoter plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // /vote command (no args) — sends configured vote links
        if (command.getName().equalsIgnoreCase("vote")) {
            sendVoteLinks(sender);
            return true;
        }

        // /neonvoter sub-commands
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("neonvoter.admin")) { noPerms(sender); return true; }
                plugin.getConfig().options().copyDefaults(true);
                plugin.reloadConfig();
                plugin.getRewardManager().loadRewards();
                sender.sendMessage(c("&b&lNeonVoter &8» &aConfig reloaded successfully!"));
                return true;

            case "info":
                if (!sender.hasPermission("neonvoter.admin")) { noPerms(sender); return true; }
                sender.sendMessage(c("&b&lNeonVoter &8» &7Server Info:"));
                sender.sendMessage(c("  &7Port: &b" + plugin.getConfig().getInt("vote-server.port", 8192)));
                sender.sendMessage(c("  &7Host: &b" + plugin.getConfig().getString("vote-server.host", "0.0.0.0")));
                sender.sendMessage(c("  &7RSA Public Key: &b" + plugin.getDataFolder() + "/rsa/public.key"));
                sender.sendMessage(c("  &7Token (v2): &b" + plugin.getConfig().getString("vote-server.token", "(see config.yml)")));
                sender.sendMessage(c("  &7Vote Server: " + (plugin.isVoteServerRunning() ? "&aOnline" : "&cOffline")));
                return true;

            case "status":
                sender.sendMessage(c("&b&lNeonVoter &8» Vote server is "
                        + (plugin.isVoteServerRunning() ? "&aOnline" : "&cOffline")));
                return true;

            case "votes":
                String targetName = (args.length > 1) ? args[1] : (sender instanceof Player ? sender.getName() : null);
                if (targetName == null) { sender.sendMessage(c("&cUsage: /nv votes <player>")); return true; }
                VoteStorage storage = plugin.getVoteStorage();
                long votes = storage.getVotes(targetName);
                long streak = storage.getStreak(targetName);
                sender.sendMessage(c("&b&lNeonVoter &8» &7" + targetName + " has &b" + votes
                        + " &7total votes (streak: &b" + streak + "&7)."));
                return true;

            case "top":
                List<String> top = plugin.getVoteStorage().getTopVoters(10);
                sender.sendMessage(c("&b&lTop Voters &8(NeonVoter)"));
                sender.sendMessage(c("&7&m                                    "));
                for (int i = 0; i < top.size(); i++) {
                    String medal = i == 0 ? "&6#1" : i == 1 ? "&7#2" : i == 2 ? "&c#3" : "&f#" + (i + 1);
                    sender.sendMessage(c("  " + medal + " &7" + top.get(i)));
                }
                if (top.isEmpty()) sender.sendMessage(c("  &7No votes recorded yet."));
                return true;

            case "fakevote":
                if (!sender.hasPermission("neonvoter.fakevote")) { noPerms(sender); return true; }
                if (args.length < 3) { sender.sendMessage(c("&cUsage: /nv fakevote <player> <service>")); return true; }
                String fakePlayer  = args[1];
                String fakeService = args[2];
                Vote fakeVote = new Vote(fakeService, fakePlayer, "127.0.0.1", System.currentTimeMillis());
                plugin.getRewardManager().handleVote(fakeVote);
                sender.sendMessage(c("&b&lNeonVoter &8» &aFake vote fired for &b" + fakePlayer
                        + " &afrom &b" + fakeService + "&a."));
                return true;

            default:
                sendHelp(sender, label);
        }

        return true;
    }

    private void sendVoteLinks(CommandSender sender) {
        List<String> lines = plugin.getConfig().getStringList("vote-command.message");
        if (lines.isEmpty()) {
            sender.sendMessage(c("&b&lNeonVoter &8» &7No vote links configured yet."));
            return;
        }
        for (String line : lines) sender.sendMessage(c(line));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(c("&b&lNeonVoter &8» &7Commands:"));
        sender.sendMessage(c("  &b/" + label + " info &8- &7Show server info \u0026 public key location"));
        sender.sendMessage(c("  &b/" + label + " status &8- &7Vote server online/offline"));
        sender.sendMessage(c("  &b/" + label + " votes [player] &8- &7View vote count"));
        sender.sendMessage(c("  &b/" + label + " top &8- &7Top 10 voters"));
        sender.sendMessage(c("  &b/" + label + " reload &8- &7Reload config (admin)"));
        sender.sendMessage(c("  &b/" + label + " fakevote <player> <service> &8- &7Test vote (admin)"));
    }

    private void noPerms(CommandSender s) {
        s.sendMessage(c("&b&lNeonVoter &8» &cYou don't have permission to do that."));
    }

    private String c(String s) {
        return s.replace("&", "\u00A7");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("info", "status", "votes", "top", "reload", "fakevote")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("votes")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
