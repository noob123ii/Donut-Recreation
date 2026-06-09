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

/**
 * Persistent store for ban records, multi-IP histories, fingerprints and join metadata.
 *
 * <p>Backed by a YAML file at {@code <dataFolder>/playerdata.db}. Writes are batched and
 * flushed asynchronously by an internal scheduler started via {@link #startAsyncSaver(Plugin)}.
 */
@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class PlayerDataStore {

  private static final int IP_HISTORY_PER_UUID = 5;
  private static final int UUID_HISTORY_PER_IP = 16;
  private static final long SAVE_PERIOD_TICKS = 200L;

  /**
   * Immutable record of a single ban — the on-disk and in-memory ban entry.
   */
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

  /**
   * Mutable per-player profile: rolling IP history, brand fingerprint, and join metadata.
   */
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
  private final ConcurrentMap<String, Deque<UUID>> ipToUuids = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Profile> profiles = new ConcurrentHashMap<>();
  private final Object ioLock = new Object();
  private volatile boolean dirty = false;
  private volatile int saverTaskId = -1;

  public PlayerDataStore(File dataFolder) {
    this.file = new File(dataFolder, "playerdata.db");
    load();
  }

  public void startAsyncSaver(Plugin plugin) {
    if (saverTaskId != -1) {
      return;
    }
    saverTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
      if (dirty) {
        dirty = false;
        save();
      }
    }, SAVE_PERIOD_TICKS, SAVE_PERIOD_TICKS).getTaskId();
  }

  public void shutdown() {
    if (saverTaskId != -1) {
      Bukkit.getScheduler().cancelTask(saverTaskId);
      saverTaskId = -1;
    }
    if (dirty) {
      dirty = false;
      save();
    }
  }

  public void recordJoin(UUID uuid, String name, String ip) {
    if (uuid == null) {
      return;
    }
    long now = System.currentTimeMillis();
    Profile profile = profiles.computeIfAbsent(uuid, k -> {
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
      Deque<UUID> bucket = ipToUuids.computeIfAbsent(ip, k -> new ArrayDeque<>());
      synchronized (bucket) {
        bucket.remove(uuid);
        bucket.addFirst(uuid);
        while (bucket.size() > UUID_HISTORY_PER_IP) {
          bucket.pollLast();
        }
      }
    }
    dirty = true;
  }

  public void recordFingerprint(UUID uuid, String fingerprint) {
    if (uuid == null || fingerprint == null) {
      return;
    }
    Profile profile = profiles.computeIfAbsent(uuid, k -> {
      Profile fresh = new Profile();
      fresh.firstSeenAt = System.currentTimeMillis();
      return fresh;
    });
    profile.fingerprint = fingerprint;
    dirty = true;
  }

  public void reload() {
    bans.clear();
    ipToUuids.clear();
    profiles.clear();
    load();
  }

  public void removeBan(UUID uuid) {
    if (uuid == null) {
      return;
    }
    bans.remove(uuid);
    dirty = true;
  }

  public void recordBan(BanRecord record) {
    if (record == null || record.uuid == null) {
      return;
    }
    bans.put(record.uuid, record);
    if (record.ip != null && !record.ip.isEmpty()) {
      Deque<UUID> bucket = ipToUuids.computeIfAbsent(record.ip, k -> new ArrayDeque<>());
      synchronized (bucket) {
        bucket.remove(record.uuid);
        bucket.addFirst(record.uuid);
        while (bucket.size() > UUID_HISTORY_PER_IP) {
          bucket.pollLast();
        }
      }
      Profile profile = profiles.computeIfAbsent(record.uuid, k -> {
        Profile fresh = new Profile();
        fresh.firstSeenAt = System.currentTimeMillis();
        return fresh;
      });
      synchronized (profile.recentIps) {
        profile.recentIps.remove(record.ip);
        profile.recentIps.addFirst(record.ip);
        while (profile.recentIps.size() > IP_HISTORY_PER_UUID) {
          profile.recentIps.pollLast();
        }
      }
      if (record.name != null) {
        profile.name = record.name;
      }
    }
    dirty = true;
  }

  public BanRecord activeBanFor(UUID uuid) {
    if (uuid == null) {
      return null;
    }
    BanRecord record = bans.get(uuid);
    return (record != null && record.isActive(System.currentTimeMillis())) ? record : null;
  }

  /**
   * Returns the first active ban whose IP-history overlaps {@code ip} and is not the
   * caller's own UUID. Snapshot-based to avoid mid-iteration mutation issues.
   */
  public BanRecord activeBanSharingIp(String ip, UUID excluding) {
    if (ip == null || ip.isEmpty()) {
      return null;
    }
    Deque<UUID> bucket = ipToUuids.get(ip);
    if (bucket == null) {
      return null;
    }
    List<UUID> snapshot;
    synchronized (bucket) {
      snapshot = new ArrayList<>(bucket);
    }
    long now = System.currentTimeMillis();
    for (UUID candidate : snapshot) {
      if (candidate.equals(excluding)) {
        continue;
      }
      BanRecord record = bans.get(candidate);
      if (record != null && record.isActive(now)) {
        return record;
      }
    }
    return null;
  }

  public Profile profileOf(UUID uuid) {
    return profiles.get(uuid);
  }

  public List<UUID> altsOf(UUID uuid) {
    Profile profile = profiles.get(uuid);
    if (profile == null) {
      return Collections.emptyList();
    }
    Set<UUID> linked = new HashSet<>();
    for (String ip : profile.snapshotIps()) {
      Deque<UUID> bucket = ipToUuids.get(ip);
      if (bucket == null) {
        continue;
      }
      synchronized (bucket) {
        linked.addAll(bucket);
      }
    }
    linked.remove(uuid);
    return new ArrayList<>(linked);
  }

  public String lastIpFor(UUID uuid) {
    Profile profile = profiles.get(uuid);
    if (profile == null) {
      return null;
    }
    synchronized (profile.recentIps) {
      return profile.recentIps.peekFirst();
    }
  }

  public String lastNameFor(UUID uuid) {
    Profile profile = profiles.get(uuid);
    return profile == null ? null : profile.name;
  }

  private void load() {
    if (!file.exists()) {
      return;
    }
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    ConfigurationSection bansSection = yaml.getConfigurationSection("bans");
    if (bansSection != null) {
      for (String key : bansSection.getKeys(false)) {
        ConfigurationSection entry = bansSection.getConfigurationSection(key);
        if (entry == null) {
          continue;
        }
        try {
          UUID id = UUID.fromString(key);
          BanRecord record = new BanRecord(
              id,
              entry.getString("name"),
              entry.getString("ip"),
              entry.getString("reason", ""),
              entry.getString("bantime", ""),
              entry.getLong("expiresAt", -1L),
              entry.getBoolean("evader", false));
          bans.put(id, record);
        } catch (IllegalArgumentException ignored) {
        }
      }
    }
    ConfigurationSection profilesSection = yaml.getConfigurationSection("profiles");
    if (profilesSection != null) {
      for (String key : profilesSection.getKeys(false)) {
        ConfigurationSection entry = profilesSection.getConfigurationSection(key);
        if (entry == null) {
          continue;
        }
        Profile profile = new Profile();
        profile.name = entry.getString("name");
        profile.fingerprint = entry.getString("fingerprint");
        profile.firstSeenAt = entry.getLong("firstSeenAt", System.currentTimeMillis());
        profile.lastSeenAt = entry.getLong("lastSeenAt", profile.firstSeenAt);
        profile.joinCount = entry.getInt("joinCount", 0);
        List<String> ips = entry.getStringList("recentIps");
        for (String ip : ips) {
          profile.recentIps.addLast(ip);
        }
        try {
          profiles.put(UUID.fromString(key), profile);
        } catch (IllegalArgumentException ignored) {
        }
      }
    }
    ConfigurationSection ipsSection = yaml.getConfigurationSection("ips");
    if (ipsSection != null) {
      for (String ip : ipsSection.getKeys(false)) {
        List<String> uuidStrings = ipsSection.getStringList(ip);
        Deque<UUID> bucket = ipToUuids.computeIfAbsent(ip, k -> new ArrayDeque<>());
        for (String uuidString : uuidStrings) {
          try {
            bucket.addLast(UUID.fromString(uuidString));
          } catch (IllegalArgumentException ignored) {
          }
        }
      }
    }
  }

  private void save() {
    synchronized (ioLock) {
      YamlConfiguration yaml = new YamlConfiguration();

      Map<String, Object> bansOut = new LinkedHashMap<>();
      for (BanRecord record : bans.values()) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", record.name);
        entry.put("ip", record.ip);
        entry.put("reason", record.reason);
        entry.put("bantime", record.banTime);
        entry.put("expiresAt", record.expiresAt);
        entry.put("evader", record.evader);
        bansOut.put(record.uuid.toString(), entry);
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
      for (Map.Entry<String, Deque<UUID>> mapEntry : ipToUuids.entrySet()) {
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
