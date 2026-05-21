package com.crimsonwarpedcraft.donutpluginrecreation.punish.listeners;

import com.crimsonwarpedcraft.donutpluginrecreation.DonutPluginRecreation;
import com.crimsonwarpedcraft.donutpluginrecreation.punish.store.PlayerDataStore;
import com.destroystokyo.paper.profile.PlayerProfile;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Detects ban-evasion attempts.
 *
 * <p>Policy is configurable via {@code alt-ban-policy} in {@code config.yml}:
 * <ul>
 *   <li>{@code strict} — any shared IP triggers an immediate 1-month ban.</li>
 *   <li>{@code new-accounts-only} — bans only if the alt has never joined this IP before
 *       OR the account is younger than {@code alt-ban-account-min-age-hours}. Otherwise
 *       the alt is sus-flagged and staff is notified. This is the default.</li>
 *   <li>{@code flag-only} — never auto-bans; always sus-flags + notifies staff.</li>
 * </ul>
 */
@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class AltBanListener implements Listener {

  private static final long ONE_MONTH_MS = 30L * 24L * 60L * 60L * 1000L;
  private static final long ONE_HOUR_MS = 60L * 60L * 1000L;
  private static final String EVADE_REASON = "Possible Ban Evading";

  private final DonutPluginRecreation plugin;
  private final PlayerDataStore store;

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
      justification = "Plugin and store are shared by design.")
  public AltBanListener(DonutPluginRecreation plugin, PlayerDataStore store) {
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

    PlayerDataStore.BanRecord ownBan = store.activeBanFor(event.getUniqueId());
    if (ownBan != null) {
      event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
          "Banned: " + ownBan.reason
              + (ownBan.expiresAt < 0 ? "" : " (expires "
                  + Instant.ofEpochMilli(ownBan.expiresAt) + ")"));
      return;
    }

    PlayerDataStore.BanRecord shared = store.activeBanSharingIp(ip, event.getUniqueId());
    if (shared == null) {
      return;
    }

    String policy = plugin.getConfig()
        .getString("alt-ban-policy", "new-accounts-only")
        .toLowerCase(Locale.ROOT)
        .trim();

    boolean shouldBan = switch (policy) {
      case "strict" -> true;
      case "flag-only" -> false;
      default -> isLikelyEvader(event, ip);
    };

    if (!shouldBan) {
      flagAndNotify(event.getUniqueId(), event.getName(), shared);
      return;
    }

    long expiresAt = System.currentTimeMillis() + ONE_MONTH_MS;
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

    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
        EVADE_REASON + "\nDuration: 1 month\nLinked to: "
            + (shared.name == null ? shared.uuid.toString() : shared.name));
  }

  private boolean isLikelyEvader(AsyncPlayerPreLoginEvent event, String ip) {
    PlayerDataStore.Profile profile = store.profileOf(event.getUniqueId());
    if (profile == null) {
      return true;
    }
    long minAgeHours = Math.max(0L,
        plugin.getConfig().getLong("alt-ban-account-min-age-hours", 168L));
    long ageMs = System.currentTimeMillis() - profile.firstSeenAt;
    if (ageMs < minAgeHours * ONE_HOUR_MS) {
      return true;
    }
    boolean knownAtThisIp;
    synchronized (profile.recentIps) {
      knownAtThisIp = profile.recentIps.contains(ip);
    }
    return !knownAtThisIp;
  }

  private void flagAndNotify(UUID uuid, String name, PlayerDataStore.BanRecord shared) {
    try {
      String linked = shared.name == null ? shared.uuid.toString() : shared.name;
      plugin.susFlagManager().flag(uuid, name,
          "Shares IP with banned " + linked + " (" + shared.reason + ")");
      Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().stream()
          .filter(CommandSender::isOp)
          .forEach(op -> op.sendMessage(plugin.color(
              "&e[&6ALT&e] &f" + name + " &7shares IP with banned &f" + linked
                  + " &7(&f" + shared.reason + "&7) — flagged, not auto-banned."))));
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
