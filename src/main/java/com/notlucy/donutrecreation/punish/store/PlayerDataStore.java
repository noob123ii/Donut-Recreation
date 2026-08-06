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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
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
    public final String banId;
    public final UUID uuid;
    public final String name;
    public final String ip;
    public final String reason;
    public final String banTime;
    public final long bannedAt;
    public final long expiresAt;
    public final boolean evader;

    public BanRecord(String banId, UUID uuid, String name, String ip, String reason,
                     String banTime, long bannedAt, long expiresAt, boolean evader) {
      this.banId = banId;
      this.uuid = uuid;
      this.name = name;
      this.ip = ip;
      this.reason = reason;
      this.banTime = banTime;
      this.bannedAt = bannedAt;
      this.expiresAt = expiresAt;
      this.evader = evader;
    }

    public boolean isActive(long now) {
      return expiresAt < 0 || expiresAt > now;
    }

    public String timeRemaining() {
      if (expiresAt < 0) {
        return "Permanent";
      }
      long remaining = expiresAt - System.currentTimeMillis();
      if (remaining <= 0) {
        return "Expired";
      }
      long seconds = remaining / 1000L;
      long minutes = seconds / 60L;
      long hours = minutes / 60L;
      long days = hours / 24L;
      if (days > 0) {
        return days + "d " + (hours % 24) + "h " + (minutes % 60) + "m";
      }
      if (hours > 0) {
        return hours + "h " + (minutes % 60) + "m " + (seconds % 60) + "s";
      }
      if (minutes > 0) {
        return minutes + "m " + (seconds % 60) + "s";
      }
      return seconds + "s";
    }

    public String timeSince() {
      long elapsed = System.currentTimeMillis() - bannedAt;
      if (elapsed < 0) {
        return "just now";
      }
      long seconds = elapsed / 1000L;
      long minutes = seconds / 60L;
      long hours = minutes / 60L;
      long days = hours / 24L;
      if (days > 0) {
        return days + "d " + (hours % 24) + "h ago";
      }
      if (hours > 0) {
        return hours + "h " + (minutes % 60) + "m ago";
      }
      if (minutes > 0) {
        return minutes + "m " + (seconds % 60) + "s ago";
      }
      return seconds + "s ago";
    }
  }

  public static final class WipeSnapshot {
    public final String name;
    public final List<org.bukkit.inventory.ItemStack> inventory;
    public final List<org.bukkit.inventory.ItemStack> armor;
    public final List<org.bukkit.inventory.ItemStack> offhand;
    public final List<org.bukkit.inventory.ItemStack> enderChest;
    public final List<org.bukkit.inventory.ItemStack> variableEnderChest;
    public final int level;
    public final float exp;
    public final int totalExp;
    public final Map<String, Double> coins;
    public final boolean pendingRestore;

    public WipeSnapshot(String name,
        List<org.bukkit.inventory.ItemStack> inventory,
        List<org.bukkit.inventory.ItemStack> armor,
        List<org.bukkit.inventory.ItemStack> offhand,
        List<org.bukkit.inventory.ItemStack> enderChest,
        List<org.bukkit.inventory.ItemStack> variableEnderChest,
        int level, float exp, int totalExp,
        Map<String, Double> coins, boolean pendingRestore) {
      this.name = name;
      this.inventory = inventory;
      this.armor = armor;
      this.offhand = offhand;
      this.enderChest = enderChest;
      this.variableEnderChest = variableEnderChest;
      this.level = level;
      this.exp = exp;
      this.totalExp = totalExp;
      this.coins = coins;
      this.pendingRestore = pendingRestore;
    }

    public static WipeSnapshot capture(org.bukkit.entity.Player player) {
      return new WipeSnapshot(
          player.getName(),
          copyOf(player.getInventory().getStorageContents()),
          copyOf(player.getInventory().getArmorContents()),
          copyOf(player.getInventory().getExtraContents()),
          copyOf(player.getEnderChest().getContents()),
          com.notlucy.donutrecreation.punish.economy.VariableEnderChestsHook.contentsOf(player),
          player.getLevel(),
          player.getExp(),
          player.getTotalExperience(),
          com.notlucy.donutrecreation.punish.economy.CoinsEngineHook.snapshot(player),
          false);
    }

    private static List<org.bukkit.inventory.ItemStack> copyOf(org.bukkit.inventory.ItemStack[] items) {
      List<org.bukkit.inventory.ItemStack> out = new ArrayList<>(items.length);
      for (org.bukkit.inventory.ItemStack item : items) {
        out.add(item == null ? null : item.clone());
      }
      return out;
    }

    public void applyTo(org.bukkit.entity.Player player) {
      player.getInventory().setStorageContents(toArray(inventory, 36));
      player.getInventory().setArmorContents(toArray(armor, 4));
      org.bukkit.inventory.ItemStack[] extra = toArray(offhand, 1);
      if (extra.length > 0) {
        player.getInventory().setItemInOffHand(
            extra[0] == null ? null : extra[0]);
      }
      player.getEnderChest().setContents(toArray(enderChest, 27));
      com.notlucy.donutrecreation.punish.economy.VariableEnderChestsHook.restore(player, variableEnderChest);
      player.setLevel(level);
      player.setExp(exp);
      player.setTotalExperience(totalExp);
      com.notlucy.donutrecreation.punish.economy.CoinsEngineHook.restore(player, coins);
    }

    private static org.bukkit.inventory.ItemStack[] toArray(
        List<org.bukkit.inventory.ItemStack> list, int expected) {
      if (list == null) {
        return new org.bukkit.inventory.ItemStack[expected];
      }
      org.bukkit.inventory.ItemStack[] out = new org.bukkit.inventory.ItemStack[list.size()];
      for (int i = 0; i < list.size(); i++) {
        out[i] = list.get(i);
      }
      return out;
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
  private final ConcurrentMap<String, BanRecord> banIndex = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Deque<UUID>> ips = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Profile> profiles = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, WipeSnapshot> wipeSnapshots = new ConcurrentHashMap<>();
  private final Object lock = new Object();
  private volatile boolean dirty = false;
  private volatile int task = -1;
  private final AtomicLong banCounter = new AtomicLong(System.currentTimeMillis());

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
    wipeSnapshots.clear();
    load();
  }

  public void removeBan(UUID id) {
    if (id == null) {
      return;
    }
    BanRecord removed = bans.remove(id);
    if (removed != null && removed.banId != null) {
      banIndex.remove(removed.banId);
    }
    dirty = true;
  }

  public String generateBanId() {
    return "BAN-" + banCounter.incrementAndGet();
  }

  public BanRecord lookupBanById(String banId) {
    if (banId == null) {
      return null;
    }
    return banIndex.get(banId);
  }

  public BanRecord lastBanFor(UUID id) {
    return bans.get(id);
  }

  public void recordBan(BanRecord ban) {
    if (ban == null || ban.uuid == null) {
      return;
    }
    bans.put(ban.uuid, ban);
    if (ban.banId != null) {
      banIndex.put(ban.banId, ban);
    }
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

  public int countBannedSharingIp(String ip, UUID excluding) {
    if (ip == null || ip.isEmpty()) {
      return 0;
    }
    Deque<UUID> bucket = ips.get(ip);
    if (bucket == null) {
      return 0;
    }
    List<UUID> snapshot;
    synchronized (bucket) {
      snapshot = new ArrayList<>(bucket);
    }
    long now = System.currentTimeMillis();
    int count = 0;
    for (UUID id : snapshot) {
      if (id.equals(excluding)) {
        continue;
      }
      BanRecord ban = bans.get(id);
      if (ban != null && ban.isActive(now)) {
        count++;
      }
    }
    return count;
  }

  public Profile profileOf(UUID id) {
    return profiles.get(id);
  }

  public UUID findUuidByName(String name) {
    if (name == null) return null;
    String lower = name.toLowerCase(Locale.ROOT);
    for (var entry : profiles.entrySet()) {
      Profile p = entry.getValue();
      if (p.name != null && p.name.equalsIgnoreCase(lower)) {
        return entry.getKey();
      }
    }
    return null;
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

  public void saveWipeSnapshot(UUID id, WipeSnapshot snapshot) {
    if (id == null || snapshot == null) {
      return;
    }
    wipeSnapshots.put(id, snapshot);
    dirty = true;
  }

  public WipeSnapshot wipeSnapshotFor(UUID id) {
    return id == null ? null : wipeSnapshots.get(id);
  }

  public boolean hasWipeSnapshot(UUID id) {
    return id != null && wipeSnapshots.containsKey(id);
  }

  public void removeWipeSnapshot(UUID id) {
    if (id != null && wipeSnapshots.remove(id) != null) {
      dirty = true;
    }
  }

  public void markWipeSnapshotPendingRestore(UUID id) {
    if (id == null) {
      return;
    }
    WipeSnapshot snapshot = wipeSnapshots.get(id);
    if (snapshot == null) {
      return;
    }
    wipeSnapshots.put(id, new WipeSnapshot(
        snapshot.name, snapshot.inventory, snapshot.armor, snapshot.offhand,
        snapshot.enderChest, snapshot.variableEnderChest,
        snapshot.level, snapshot.exp, snapshot.totalExp,
        snapshot.coins, true));
    dirty = true;
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
          String banId = entry.getString("banId", null);
          if (banId == null) {
            banId = "BAN-" + banCounter.incrementAndGet();
          }
          long bannedAt = entry.getLong("bannedAt", System.currentTimeMillis());
          BanRecord ban = new BanRecord(
              banId,
              id,
              entry.getString("name"),
              entry.getString("ip"),
              entry.getString("reason", ""),
              entry.getString("bantime", ""),
              bannedAt,
              entry.getLong("expiresAt", -1L),
              entry.getBoolean("evader", false));
          bans.put(id, ban);
          banIndex.put(banId, ban);
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
    ConfigurationSection snapSection = yaml.getConfigurationSection("wipesnapshots");
    if (snapSection != null) {
      for (String key : snapSection.getKeys(false)) {
        ConfigurationSection entry = snapSection.getConfigurationSection(key);
        if (entry == null) {
          continue;
        }
        try {
          UUID id = UUID.fromString(key);
          wipeSnapshots.put(id, readSnapshot(entry));
        } catch (IllegalArgumentException ignored) {
        }
      }
    }
  }

  private WipeSnapshot readSnapshot(ConfigurationSection entry) {
    List<org.bukkit.inventory.ItemStack> inventory = readItems(entry, "inventory");
    List<org.bukkit.inventory.ItemStack> armor = readItems(entry, "armor");
    List<org.bukkit.inventory.ItemStack> offhand = readItems(entry, "offhand");
    List<org.bukkit.inventory.ItemStack> enderChest = readItems(entry, "enderchest");
    Map<String, Double> coins = new LinkedHashMap<>();
    ConfigurationSection coinSection = entry.getConfigurationSection("coins");
    if (coinSection != null) {
      for (String currency : coinSection.getKeys(false)) {
        coins.put(currency, coinSection.getDouble(currency));
      }
    }
    return new WipeSnapshot(
        entry.getString("name"),
        inventory,
        armor,
        offhand,
        enderChest,
        readItems(entry, "variableenderchest"),
        entry.getInt("level", 0),
        (float) entry.getDouble("exp", 0.0),
        entry.getInt("totalExp", 0),
        coins,
        entry.getBoolean("pendingRestore", false));
  }

  private List<org.bukkit.inventory.ItemStack> readItems(
      ConfigurationSection entry, String sectionName) {
    List<org.bukkit.inventory.ItemStack> out = new ArrayList<>();
    ConfigurationSection section = entry.getConfigurationSection(sectionName);
    if (section == null) {
      return out;
    }
    for (String slot : section.getKeys(false)) {
      org.bukkit.inventory.ItemStack item = section.getItemStack(slot);
      out.add(item);
    }
    return out;
  }

  private void writeItems(ConfigurationSection entry, String sectionName,
      List<org.bukkit.inventory.ItemStack> items) {
    ConfigurationSection section = entry.createSection(sectionName);
    int i = 0;
    for (org.bukkit.inventory.ItemStack item : items) {
      if (item != null && item.getType() != org.bukkit.Material.AIR) {
        section.set(String.valueOf(i), item);
      }
      i++;
    }
  }

  private void save() {
    synchronized (lock) {
      YamlConfiguration yaml = new YamlConfiguration();

      Map<String, Object> bansOut = new LinkedHashMap<>();
      for (BanRecord ban : bans.values()) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("banId", ban.banId);
        entry.put("name", ban.name);
        entry.put("ip", ban.ip);
        entry.put("reason", ban.reason);
        entry.put("bantime", ban.banTime);
        entry.put("bannedAt", ban.bannedAt);
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

      ConfigurationSection snapRoot = yaml.createSection("wipesnapshots");
      for (Map.Entry<UUID, WipeSnapshot> mapEntry : wipeSnapshots.entrySet()) {
        WipeSnapshot snapshot = mapEntry.getValue();
        ConfigurationSection entry = snapRoot.createSection(mapEntry.getKey().toString());
        entry.set("name", snapshot.name);
        entry.set("level", snapshot.level);
        entry.set("exp", snapshot.exp);
        entry.set("totalExp", snapshot.totalExp);
        entry.set("pendingRestore", snapshot.pendingRestore);
        writeItems(entry, "inventory", snapshot.inventory);
        writeItems(entry, "armor", snapshot.armor);
        writeItems(entry, "offhand", snapshot.offhand);
        writeItems(entry, "enderchest", snapshot.enderChest);
        writeItems(entry, "variableenderchest", snapshot.variableEnderChest);
        if (snapshot.coins != null && !snapshot.coins.isEmpty()) {
          ConfigurationSection coinSection = entry.createSection("coins");
          for (Map.Entry<String, Double> coin : snapshot.coins.entrySet()) {
            coinSection.set(coin.getKey(), coin.getValue());
          }
        }
      }

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