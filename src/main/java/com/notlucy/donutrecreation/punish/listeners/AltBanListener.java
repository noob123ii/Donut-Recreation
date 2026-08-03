package com.notlucy.donutrecreation.punish.listeners;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.notlucy.donutrecreation.DonutRecreation;
import com.notlucy.donutrecreation.punish.store.PlayerDataStore;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class AltBanListener implements Listener {

  private static final long MONTH_MS = 30L * 24L * 60L * 60L * 1000L;
  private static final long HOUR_MS = 60L * 60L * 1000L;
  private static final String EVADE_REASON = "Possible Ban Evading";

  private final DonutRecreation plugin;
  private final PlayerDataStore store;

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
      justification = "Plugin and store are shared by design.")
  public AltBanListener(DonutRecreation plugin, PlayerDataStore store) {
    this.plugin = plugin;
    this.store = store;
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPreLogin(AsyncPlayerPreLoginEvent event) {
    if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
      return;
    }
    InetAddress address = event.getAddress();
    if (address == null) {
      return;
    }
    String ip = address.getHostAddress();
    if (ip == null || ip.isEmpty()) {
      return;
    }

    PlayerDataStore.BanRecord ban = store.activeBanFor(event.getUniqueId());
    if (ban != null) {
      String expires = ban.expiresAt < 0
          ? "&cNever"
          : "&f" + Instant.ofEpochMilli(ban.expiresAt);
      String msg = plugin.color(
          "&c&lYOU ARE BANNED\n\n"
              + "&7Reason: &f" + ban.reason + "\n"
              + "&7Duration: &f" + ban.banTime + "\n"
              + "&7Expires: " + expires + "\n\n"
              + "&7Appeal at &9discord.gg/example");
      event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, msg);
      return;
    }

    PlayerDataStore.BanRecord other = store.activeBanSharingIp(ip, event.getUniqueId());
    if (other == null) {
      return;
    }

    String policy = plugin.getConfig()
        .getString("alt-ban-policy", "new-accounts-only")
        .toLowerCase(Locale.ROOT)
        .trim();

    boolean shouldBan = switch (policy) {
      case "strict" -> true;
      case "flag-only" -> false;
      default -> isEvading(event, ip);
    };

    if (!shouldBan) {
      flag(event.getUniqueId(), event.getName(), other);
      return;
    }

    long expiresAt = System.currentTimeMillis() + MONTH_MS;
    PlayerDataStore.BanRecord evader = new PlayerDataStore.BanRecord(
        event.getUniqueId(),
        event.getName(),
        ip,
        EVADE_REASON,
        "1mo",
        expiresAt,
        true);
    store.recordBan(evader);

    try {
      PlayerProfile profile = Bukkit.createProfile(event.getUniqueId(), event.getName());
      @SuppressWarnings("deprecation")
      BanList<PlayerProfile> banList = Bukkit.getBanList(BanList.Type.PROFILE);
      banList.addBan(profile, EVADE_REASON, Instant.ofEpochMilli(expiresAt), "AltBanListener");
    } catch (Throwable ignored) {
    }

    String msg = plugin.color(
        "&c&lBAN EVASION DETECTED\n\n"
            + "&7Reason: &f" + EVADE_REASON + "\n"
            + "&7Duration: &f1 month\n"
            + "&7Linked to: &f" + (other.name == null ? other.uuid.toString() : other.name));
    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, msg);
  }

  private boolean isEvading(AsyncPlayerPreLoginEvent event, String ip) {
    PlayerDataStore.Profile profile = store.profileOf(event.getUniqueId());
    if (profile == null) {
      return false;
    }
    long minAge = Math.max(0L,
        plugin.getConfig().getLong("alt-ban-account-min-age-hours", 168L));
    long age = System.currentTimeMillis() - profile.firstSeenAt;
    if (age < minAge * HOUR_MS) {
      return true;
    }
    boolean known;
    synchronized (profile.recentIps) {
      known = profile.recentIps.contains(ip);
    }
    return !known;
  }

  private void flag(UUID uuid, String name, PlayerDataStore.BanRecord other) {
    try {
      String linked = other.name == null ? other.uuid.toString() : other.name;
      plugin.susFlagManager().flag(uuid, name,
          "Shares IP with banned " + linked + " (" + other.reason + ")");
      Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().stream()
          .filter(p -> p.hasPermission("donutrecreation.*"))
          .forEach(op -> op.sendMessage(plugin.color(
              "&e[&6ALT&e] &f" + name + " &7shares IP with banned &f" + linked
                  + " &7(&f" + other.reason + "&7) — flagged, not auto-banned."))));
    } catch (Throwable ignored) {
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    var address = event.getPlayer().getAddress();
    String ip = (address != null && address.getAddress() != null)
        ? address.getAddress().getHostAddress()
        : null;
    store.recordJoin(event.getPlayer().getUniqueId(), event.getPlayer().getName(), ip);
  }
}