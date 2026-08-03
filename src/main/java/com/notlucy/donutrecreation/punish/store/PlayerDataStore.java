package com.notlucy.donutrecreation.punish.store;

import com.notlucy.donutrecreation.util.LogData;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class PlayerDataStore {

  private static final int IP_HISTORY_PER_UUID = 5;
  private static final int UUID_HISTORY_PER_IP = 16;
  private static final long SAVE_PERIOD_TICKS = 200L;

  public static final class BanRecord {
    public final UUID uuid;
    public final String name;
    public final String ip;
    public final String reason;
    public final String banTime;
    public final long expiresAt;
    public final boolean evader;

    public BanRecord(UUID uuid, String name, String ip, String reason,
                     String banTime, long expiresAt, boolean evader) {
      this.uuid = uuid;
      this.name = name;
      this.ip = ip;
      this.reason = reason;
      this.banTime = banTime;
      this.expiresAt = expiresAt;
      this.evader = evader;
    }

    public boolean isActive(long now) {
      return expiresAt < 0 || expiresAt > now;
    }
  }

  public static final class Profile {
    public volatile String name;
    public volatile String fingerprint;
    public volatile long firstSeenAt;
    public volatile long lastSeenAt;
    public volatile int joinCount;
    public final Deque<String> recentIps = new ArrayDeque<>();

    public synchronized List<String> snapshotIps() {
      return new ArrayList<>(recentIps);
    }
  }

  private final File file;
  private final ConcurrentMap<UUID, BanRecord> bans = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Deque<UUID>> ips = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Profile> profiles = new ConcurrentHashMap<>();
  private final Object lock = new Object();
  private volatile boolean dirty = false;
  private volatile int task = -1;

  public PlayerDataStore(File dataFolder) {
    this.file = new File(dataFolder, "playerdata.db");
    load();
  }

  public void startAsyncSaver(Plugin plugin) {
    if (task != -1) {
      return;
    }
    task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
      if (dirty) {
        dirty = false;
        save();
      }
    }, SAVE_PERIOD_TICKS, SAVE_PERIOD_TICKS).getTaskId();
  }

  public void shutdown() {
    if (task != -1) {
      Bukkit.getScheduler().cancelTask(task);
      task = -1;
    }
    if (dirty) {
      dirty = false;
      save();
    }
  }

  public void recordJoin(UUID id, String name, String ip) {
    if (id == null) {
      return;
    }
    long now = System.currentTimeMillis();
    Profile profile = profiles.computeIfAbsent(id, k -> {
      Profile fresh = new Profile();
      fresh.firstSeenAt = now;
      return fresh;
    });
    if (name != null) {
      profile.name = name;
    }
    profile.lastSeenAt = now;
    profile.joinCount++;
    if (ip != null && !ip.isEmpty()) {
      synchronized (profile.recentIps) {
        profile.recentIps.remove(ip);
        profile.recentIps.addFirst(ip);
        while (profile.recentIps.size() > IP_HISTORY_PER_UUID) {
          profile.recentIps.pollLast();
        }
      }
      Deque<UUID> bucket = ips.computeIfAbsent(ip, k -> new ArrayDeque<>());
      synchronized (bucket) {
        bucket.remove(id);
        bucket.addFirst(id);
        while (bucket.size() > UUID_HISTORY_PER_IP) {
          bucket.pollLast();
        }
      }
    }
    dirty = true;
  }

  public void recordFingerprint(UUID id, String fingerprint) {
    if (id == null || fingerprint == null) {
      return;
    }
    Profile profile = profiles.computeIfAbsent(id, k -> {
      Profile fresh = new Profile();
      fresh.firstSeenAt = System.currentTimeMillis();
      return fresh;
    });
    profile.fingerprint = fingerprint;
    dirty = true;
  }

  public void reload() {
    bans.clear();
    ips.clear();
    profiles.clear();
    load();
  }

  public void removeBan(UUID id) {
    if (id == null) {
      return;
    }
    bans.remove(id);
    dirty = true;
  }

  public void recordBan(BanRecord ban) {
    if (ban == null || ban.uuid == null) {
      return;
    }
    bans.put(ban.uuid, ban);
    if (ban.ip != null && !ban.ip.isEmpty()) {
      Deque<UUID> bucket = ips.computeIfAbsent(ban.ip, k -> new ArrayDeque<>());
      synchronized (bucket) {
        bucket.remove(ban.uuid);
        bucket.addFirst(ban.uuid);
        while (bucket.size() > UUID_HISTORY_PER_IP) {
          bucket.pollLast();
        }
      }
      Profile profile = profiles.computeIfAbsent(ban.uuid, k -> {
        Profile fresh = new Profile();
        fresh.firstSeenAt = System.currentTimeMillis();
        return fresh;
      });
      synchronized (profile.recentIps) {
        profile.recentIps.remove(ban.ip);
        profile.recentIps.addFirst(ban.ip);
        while (profile.recentIps.size() > IP_HISTORY_PER_UUID) {
          profile.recentIps.pollLast();
        }
      }
      if (ban.name != null) {
        profile.name = ban.name;
      }
    }
    dirty = true;
  }

  public BanRecord activeBanFor(UUID id) {
    if (id == null) {
      return null;
    }
    BanRecord ban = bans.get(id);
    return (ban != null && ban.isActive(System.currentTimeMillis())) ? ban : null;
  }

  public BanRecord activeBanSharingIp(String ip, UUID excluding) {
    if (ip == null || ip.isEmpty()) {
      return null;
    }
    Deque<UUID> bucket = ips.get(ip);
    if (bucket == null) {
      return null;
    }
    List<UUID> snapshot;
    synchronized (bucket) {
      snapshot = new ArrayList<>(bucket);
    }
    long now = System.currentTimeMillis();
    for (UUID id : snapshot) {
      if (id.equals(excluding)) {
        continue;
      }
      BanRecord ban = bans.get(id);
      if (ban != null && ban.isActive(now)) {
        return ban;
      }
    }
    return null;
  }

  public Profile profileOf(UUID id) {
    return profiles.get(id);
  }

  public List<UUID> altsOf(UUID id) {
    Profile profile = profiles.get(id);
    if (profile == null) {
      return Collections.emptyList();
    }
    Set<UUID> linked = new HashSet<>();
    for (String ip : profile.snapshotIps()) {
      Deque<UUID> bucket = ips.get(ip);
      if (bucket == null) {
        continue;
      }
      synchronized (bucket) {
        linked.addAll(bucket);
      }
    }
    linked.remove(id);
    return new ArrayList<>(linked);
  }

  public String lastIpFor(UUID id) {
    Profile profile = profiles.get(id);
    if (profile == null) {
      return null;
    }
    synchronized (profile.recentIps) {
      return profile.recentIps.peekFirst();
    }
  }

  public String lastNameFor(UUID id) {
    Profile profile = profiles.get(id);
    return profile == null ? null : profile.name;
  }

  private void load() {
    if (!file.exists()) {
      return;
    }
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection banSection = yaml.getConfigurationSection("bans");
    if (banSection != null) {
      for (String key : banSection.getKeys(false)) {
        ConfigurationSection entry = banSection.getConfigurationSection(key);
        if (entry == null) {
          continue;
        }
        try {
          UUID id = UUID.fromString(key);
          BanRecord ban = new BanRecord(
              id,
              entry.getString("name"),
              entry.getString("ip"),
              entry.getString("reason", ""),
              entry.getString("bantime", ""),
              entry.getLong("expiresAt", -1L),
              entry.getBoolean("evader", false));
          bans.put(id, ban);
        } catch (IllegalArgumentException ignored) {
        }
      }
    }
    ConfigurationSection profileSection = yaml.getConfigurationSection("profiles");
    if (profileSection != null) {
      for (String key : profileSection.getKeys(false)) {
        ConfigurationSection entry = profileSection.getConfigurationSection(key);
        if (entry == null) {
          continue;
        }
        Profile profile = new Profile();
        profile.name = entry.getString("name");
        profile.fingerprint = entry.getString("fingerprint");
        profile.firstSeenAt = entry.getLong("firstSeenAt", System.currentTimeMillis());
        profile.lastSeenAt = entry.getLong("lastSeenAt", profile.firstSeenAt);
        profile.joinCount = entry.getInt("joinCount", 0);
        List<String> recentIps = entry.getStringList("recentIps");
        for (String ip : recentIps) {
          profile.recentIps.addLast(ip);
        }
        try {
          profiles.put(UUID.fromString(key), profile);
        } catch (IllegalArgumentException ignored) {
        }
      }
    }
    ConfigurationSection ipSection = yaml.getConfigurationSection("ips");
    if (ipSection != null) {
      for (String ip : ipSection.getKeys(false)) {
        List<String> ids = ipSection.getStringList(ip);
        Deque<UUID> bucket = ips.computeIfAbsent(ip, k -> new ArrayDeque<>());
        for (String id : ids) {
          try {
            bucket.addLast(UUID.fromString(id));
          } catch (IllegalArgumentException ignored) {
          }
        }
      }
    }
  }

  private void save() {
    synchronized (lock) {
      YamlConfiguration yaml = new YamlConfiguration();

      Map<String, Object> bansOut = new LinkedHashMap<>();
      for (BanRecord ban : bans.values()) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", ban.name);
        entry.put("ip", ban.ip);
        entry.put("reason", ban.reason);
        entry.put("bantime", ban.banTime);
        entry.put("expiresAt", ban.expiresAt);
        entry.put("evader", ban.evader);
        bansOut.put(ban.uuid.toString(), entry);
      }
      yaml.createSection("bans", bansOut);

      Map<String, Object> profilesOut = new LinkedHashMap<>();
      for (Map.Entry<UUID, Profile> mapEntry : profiles.entrySet()) {
        Profile profile = mapEntry.getValue();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", profile.name);
        entry.put("fingerprint", profile.fingerprint);
        entry.put("firstSeenAt", profile.firstSeenAt);
        entry.put("lastSeenAt", profile.lastSeenAt);
        entry.put("joinCount", profile.joinCount);
        entry.put("recentIps", profile.snapshotIps());
        profilesOut.put(mapEntry.getKey().toString(), entry);
      }
      yaml.createSection("profiles", profilesOut);

      Map<String, Object> ipsOut = new LinkedHashMap<>();
      for (Map.Entry<String, Deque<UUID>> mapEntry : ips.entrySet()) {
        List<String> list;
        synchronized (mapEntry.getValue()) {
          list = new ArrayList<>(mapEntry.getValue().size());
          for (UUID id : mapEntry.getValue()) {
            list.add(id.toString());
          }
        }
        ipsOut.put(mapEntry.getKey(), list);
      }
      yaml.createSection("ips", ipsOut);

      try {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()
            && !parent.mkdirs() && !parent.exists()) {
          LogData.get().warning("[offend] could not create dir " + parent);
          return;
        }
        yaml.save(file);
      } catch (IOException error) {
        LogData.get().warning("[offend] failed to write playerdata.db: " + error);
      }
    }
  }
}