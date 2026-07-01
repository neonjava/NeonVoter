# 🗳️ NeonVoter

**NeonVoter** is a modern, all-in-one **vote receiver and reward plugin** for Minecraft servers running **Paper, Folia, or any fork**. It merges the functionality of Votifier (vote server) and SuperbVote (reward system) into a single, lightweight plugin — no separate Votifier installation required.

> **Author:** [neonjava](https://github.com/neonjava)  
> **Folia Supported:** ✅ Yes  
> **Minecraft Version:** 1.21.1+  
> **Java Version:** 17+

---

## ✨ Features

- 📡 **Embedded Vote Server** — Built-in Netty vote server, no separate Votifier plugin needed
- 🔐 **RSA Key Pair** — Auto-generates 2048-bit RSA keys on first start; `public.key` is shared with voting sites
- 🔑 **v1 + v2 Protocol** — Supports both legacy Votifier v1 (RSA) and modern NuVotifier v2 (HMAC-SHA256 JSON)
- 🎁 **Configurable Rewards** — Per-vote commands, messages, sounds, titles, broadcasts
- 🌐 **Service-specific Rewards** — Different rewards per voting site (MinecraftMP, Planet Minecraft, etc.)
- 🔢 **Streak & Cumulative Rewards** — Bonus rewards every N votes or at vote streak milestones
- 💤 **Offline Vote Queuing** — Votes queued while player is offline, delivered on next login
- 📊 **Top Voters** — `/nv top` leaderboard with vote counts
- ⚡ **Folia-native** — Uses `EntityScheduler`, `GlobalRegionScheduler`, `AsyncScheduler` for full Folia compatibility
- 🗄️ **JSON Storage** — Flat-file storage with atomic writes, no database required

---

## 🚀 Quick Start

### 1. Install
Drop `NeonVoter-1.0.0.jar` into your `plugins/` folder and restart.

### 2. RSA Key
On first start, NeonVoter generates:
```
plugins/NeonVoter/rsa/public.key   ← Share this with voting sites
plugins/NeonVoter/rsa/private.key  ← Keep this PRIVATE, never share
```

### 3. Configure Your Voting Site
1. On your voting site (e.g., MinecraftMP), go to server settings
2. Set the **Votifier Host** to your server's IP
3. Set the **Votifier Port** to `8192` (or whatever you set in `config.yml`)
4. Paste the contents of `public.key` into the **Public Key** field
5. Save — votes will now be delivered to your server!

### 4. Open Port
Make sure port `8192` (TCP) is open in your firewall:
```bash
# UFW
ufw allow 8192/tcp

# iptables
iptables -A INPUT -p tcp --dport 8192 -j ACCEPT
```

---

## ⚙️ Configuration

The full `config.yml` is auto-generated on first start with all options documented:

```yaml
# Vote server settings
vote-server:
  host: "0.0.0.0"
  port: 8192
  disable-v1: false  # Set true to require v2 protocol only

# Reward actions: COMMAND, MESSAGE, BROADCAST, SOUND, TITLE
rewards:
  - name: "Default Vote Reward"
    services: []          # Empty = all services
    conditions: []        # empty = always
    actions:
      - "COMMAND:give {player} diamond 1"
      - "SOUND:ENTITY_PLAYER_LEVELUP"
      - "TITLE:&bThanks for voting!|&7Reward delivered!"

  - name: "7-Day Streak Bonus"
    conditions:
      - "streak:7"
    actions:
      - "COMMAND:give {player} emerald 5"
      - "BROADCAST:&6{player} has a 7-day voting streak!"

  - name: "Every 5th Vote"
    conditions:
      - "every:5"
    actions:
      - "COMMAND:give {player} experience_bottle 3"
```

### Available Placeholders
| Placeholder | Value |
|---|---|
| `{player}` | Player's in-game name |
| `{service}` | Voting site name (e.g. `MinecraftMP`) |
| `{total_votes}` | Player's total vote count |

### Reward Conditions
| Condition | Description |
|---|---|
| `every:N` | Trigger every N-th total vote (e.g. `every:10` = every 10th vote) |
| `streak:N` | Trigger when player's streak equals exactly N days |

### Action Types
| Prefix | Example | Description |
|---|---|---|
| `COMMAND:` | `COMMAND:give {player} diamond 1` | Runs console command |
| `MESSAGE:` | `MESSAGE:&aThanks for voting!` | Sends private message to player |
| `BROADCAST:` | `BROADCAST:&b{player} voted!` | Broadcasts to all players |
| `SOUND:` | `SOUND:ENTITY_PLAYER_LEVELUP` | Plays sound to voter |
| `TITLE:` | `TITLE:&bThanks!|&7Reward given!` | Shows title (`title\|subtitle`) |

---

## 📟 Commands

| Command | Permission | Description |
|---|---|---|
| `/neonvoter info` | `neonvoter.admin` | Show server IP, port, key location |
| `/neonvoter status` | `neonvoter.admin` | Vote server online/offline status |
| `/neonvoter votes [player]` | any | Show vote count |
| `/neonvoter top` | any | Top 10 voters leaderboard |
| `/neonvoter reload` | `neonvoter.admin` | Reload config and rewards |
| `/neonvoter fakevote <player> <service>` | `neonvoter.fakevote` | Simulate a test vote |
| `/vote` | `neonvoter.vote` | Show configured vote links |

**Aliases:** `/nv`, `/voter`

---

## 🔒 Permissions

| Permission | Default | Description |
|---|---|---|
| `neonvoter.admin` | op | Full admin access |
| `neonvoter.vote` | everyone | Use `/vote` command |
| `neonvoter.fakevote` | op | Trigger fake votes for testing |

---

## 🧠 How Vote Protocols Work

### v1 (Legacy RSA)
```
Voting Site  ──── TCP connect to port 8192 ────►  NeonVoter
             ◄─── "VOTIFIER 2 <pubKey>\n"  ───────
             ──── [256 bytes RSA encrypted block] ─►  decrypted with private key
```

### v2 (NuVotifier JSON + HMAC)
```
Voting Site  ──── TCP connect ────────────────►  NeonVoter
             ◄─── "VOTIFIER 2 <pubKey>\n" ────────
             ──── { JSON payload + HMAC-SHA256 } ─►  signature verified with token
```

---

## 🏗️ Building from Source

```bash
git clone https://github.com/neonjava/NeonVoter.git
cd NeonVoter
mvn clean package
# Output: target/NeonVoter-1.0.0.jar
```

**Requirements:** Java 17+, Maven 3.8+

---

## 📦 Dependencies (Shaded)
- [Netty](https://netty.io/) 4.1.x — embedded vote server I/O
- [Gson](https://github.com/google/gson) 2.10.1 — v2 protocol JSON + storage

---

## 📄 License

MIT License — see [LICENSE](LICENSE)

---

## 🙏 Credits

- Protocol design based on [NuVotifier](https://github.com/NuVotifier/NuVotifier) (GPL-3.0)
- Reward system inspired by [SuperbVote](https://github.com/astei/SuperbVote)
