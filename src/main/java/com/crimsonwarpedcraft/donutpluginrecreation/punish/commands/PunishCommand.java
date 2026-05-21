package com.crimsonwarpedcraft.donutpluginrecreation.punish.commands;

import com.crimsonwarpedcraft.donutpluginrecreation.DonutPluginRecreation;
import com.crimsonwarpedcraft.donutpluginrecreation.punish.store.PlayerDataStore;
import com.destroystokyo.paper.profile.PlayerProfile;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /offand <player> <reason>} (alias {@code /punish}) — applies a configurable ban
 * with an optional data wipe and persists the ban into {@code playerdata.db}.
 *
 * <p>Reasons live under {@code punishments:} in {@code config.yml}, e.g.
 * <pre>
 * punishments:
 *   Krypton:
 *     BanTime: lifetime
 *     ResetData: true
 * </pre>
 *
 * <p>Only operators may run this command.
 */
@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class PunishCommand implements CommandExecutor, TabCompleter {

  private final DonutPluginRecreation plugin;
  private final PlayerDataStore store;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Plugin instance and store are shared by Bukkit.")
  public PunishCommand(DonutPluginRecreation plugin, PlayerDataStore store) {
    this.plugin = plugin;
    this.store = store;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!sender.isOp()) {
      sender.sendMessage(plugin.message("messages.no-permission"));
      return true;
    }

    if (args.length < 2) {
      sender.sendMessage(plugin.color("&cUsage: /offand <player> <reason>"));
      sender.sendMessage(plugin.color("&7Reasons: &f" + String.join(", ", reasonKeys())));
      return true;
    }

    String targetName = args[0];
    String reasonInput = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
    String reasonKey = matchReasonKey(reasonInput);
    if (reasonKey == null) {
      sender.sendMessage(plugin.color("&cUnknown reason &f" + reasonInput
          + "&c. Valid: &f" + String.join(", ", reasonKeys())));
      return true;
    }

    ConfigurationSection section = plugin.getConfig()
        .getConfigurationSection("punishments." + reasonKey);
    if (section == null) {
      sender.sendMessage(plugin.color("&cReason &f" + reasonKey + " &cis not configured."));
      return true;
    }

    String banTime = section.getString("BanTime", "lifetime");
    boolean resetData = section.getBoolean("ResetData", false);
    Date expiry = parseExpiry(banTime);

    OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
    String banMessage = "Punished: " + reasonKey + " (" + banTime + ")";
    final String displayName = offline.getName() != null ? offline.getName() : targetName;

    Instant expiryInstant = expiry == null ? null : expiry.toInstant();
    PlayerProfile profile = Bukkit.createProfile(
        offline.getUniqueId(),
        offline.getName() != null ? offline.getName() : targetName);
    @SuppressWarnings("deprecation")
    BanList<PlayerProfile> banList = Bukkit.getBanList(BanList.Type.PROFILE);
    banList.addBan(profile, banMessage, expiryInstant, sender.getName());

    Player onlineTarget = offline.isOnline() ? offline.getPlayer() : null;
    String capturedIp = null;
    if (onlineTarget != null) {
      var address = onlineTarget.getAddress();
      if (address != null && address.getAddress() != null) {
        capturedIp = address.getAddress().getHostAddress();
      }
      if (resetData) {
        wipePlayerData(onlineTarget);
      }
      onlineTarget.kickPlayer(plugin.color("&c" + banMessage));
    } else if (resetData && offline.hasPlayedBefore()) {
      sender.sendMessage(plugin.color(
          "&7Player is offline; data wipe will only run if they rejoin briefly."));
    }
    if (capturedIp == null && store != null) {
      capturedIp = store.lastIpFor(offline.getUniqueId());
    }
    if (store != null) {
      long expiresAt = expiry == null ? -1L : expiry.getTime();
      store.recordBan(new PlayerDataStore.BanRecord(
          offline.getUniqueId(),
          displayName,
          capturedIp,
          reasonKey,
          banTime,
          expiresAt,
          false));
    }

    plugin.susFlagManager().clear(offline.getUniqueId());

    Bukkit.getOnlinePlayers().stream()
        .filter(CommandSender::isOp)
        .forEach(op -> op.sendMessage(plugin.color(
            "&c[&4PUNISH&c] &f" + sender.getName() + " &7punished &f" + displayName
                + " &7for &f" + reasonKey + " &7(&f" + banTime + "&7"
                + (resetData ? "&7, &cdata wiped" : "") + "&7)")));
    sender.sendMessage(plugin.color("&aPunished &f" + displayName + " &afor &f" + reasonKey));
    return true;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    if (!sender.isOp()) {
      return Collections.emptyList();
    }
    if (args.length == 1) {
      List<String> names = new ArrayList<>();
      for (Player p : Bukkit.getOnlinePlayers()) {
        if (p.getName().toLowerCase(Locale.ROOT)
            .startsWith(args[0].toLowerCase(Locale.ROOT))) {
          names.add(p.getName());
        }
      }
      return names;
    }
    if (args.length == 2) {
      List<String> matches = new ArrayList<>();
      String lower = args[1].toLowerCase(Locale.ROOT);
      for (String key : reasonKeys()) {
        if (key.toLowerCase(Locale.ROOT).startsWith(lower)) {
          matches.add(key);
        }
      }
      return matches;
    }
    return Collections.emptyList();
  }

  private List<String> reasonKeys() {
    ConfigurationSection root = plugin.getConfig().getConfigurationSection("punishments");
    if (root == null) {
      return Collections.emptyList();
    }
    return new ArrayList<>(root.getKeys(false));
  }

  private String matchReasonKey(String input) {
    String lower = input.toLowerCase(Locale.ROOT).trim();
    for (String key : reasonKeys()) {
      if (key.equalsIgnoreCase(lower)) {
        return key;
      }
    }
    String normalized = lower.replaceAll("[^a-z0-9]", "");
    for (String key : reasonKeys()) {
      if (key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").equals(normalized)) {
        return key;
      }
    }
    return null;
  }

  /**
   * Parses durations like {@code lifetime}, {@code 1h}, {@code 30m}, {@code 7d}, {@code 2w},
   * {@code 1mo}, {@code 1y}. Returns {@code null} for permanent bans.
   */
  private Date parseExpiry(String spec) {
    if (spec == null) {
      return null;
    }
    String text = spec.trim().toLowerCase(Locale.ROOT);
    if (text.isEmpty() || text.equals("lifetime") || text.equals("perm")
        || text.equals("permanent") || text.equals("forever")) {
      return null;
    }
    long now = System.currentTimeMillis();
    long[] unitMs = parseUnits(text);
    if (unitMs == null) {
      return null;
    }
    return new Date(now + unitMs[0]);
  }

  private long[] parseUnits(String text) {
    int i = 0;
    long total = 0;
    while (i < text.length()) {
      int start = i;
      while (i < text.length() && Character.isDigit(text.charAt(i))) {
        i++;
      }
      if (start == i) {
        return null;
      }
      long value = Long.parseLong(text.substring(start, i));
      int unitStart = i;
      while (i < text.length() && Character.isLetter(text.charAt(i))) {
        i++;
      }
      String unit = text.substring(unitStart, i);
      long ms = unitToMs(unit, value);
      if (ms < 0) {
        return null;
      }
      total += ms;
      while (i < text.length() && !Character.isDigit(text.charAt(i))) {
        i++;
      }
    }
    return total == 0 ? null : new long[]{total};
  }

  private long unitToMs(String unit, long value) {
    return switch (unit) {
      case "s", "sec", "secs", "second", "seconds" -> value * 1000L;
      case "m", "min", "mins", "minute", "minutes" -> value * 60_000L;
      case "h", "hr", "hrs", "hour", "hours" -> value * 3_600_000L;
      case "d", "day", "days" -> value * 86_400_000L;
      case "w", "wk", "wks", "week", "weeks" -> value * 7L * 86_400_000L;
      case "mo", "mon", "month", "months" -> value * 30L * 86_400_000L;
      case "y", "yr", "yrs", "year", "years" -> value * 365L * 86_400_000L;
      default -> -1L;
    };
  }

  private void wipePlayerData(Player target) {
    target.getInventory().clear();
    target.getInventory().setArmorContents(new ItemStack[4]);
    target.getEnderChest().clear();
    target.setExp(0f);
    target.setLevel(0);
    target.setTotalExperience(0);
    target.setHealth(target.getMaxHealth());
    target.setFoodLevel(20);
    target.setSaturation(5f);
    target.getActivePotionEffects().forEach(effect -> target.removePotionEffect(effect.getType()));
    for (Statistic stat : Statistic.values()) {
      try {
        if (stat.getType() == Statistic.Type.UNTYPED) {
          target.setStatistic(stat, 0);
        }
      } catch (Throwable ignored) {
      }
    }
  }
}
