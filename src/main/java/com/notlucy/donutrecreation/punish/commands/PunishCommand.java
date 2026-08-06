package com.notlucy.donutrecreation.punish.commands;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.notlucy.donutrecreation.DonutRecreation;
import com.notlucy.donutrecreation.punish.store.PlayerDataStore;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class PunishCommand implements CommandExecutor, TabCompleter {

  private final DonutRecreation plugin;
  private final PlayerDataStore store;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Plugin instance and store are shared by Bukkit.")
  public PunishCommand(DonutRecreation plugin, PlayerDataStore store) {
    this.plugin = plugin;
    this.store = store;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!plugin.hasStaffAccess(sender)) {
      sender.sendMessage(plugin.message("messages.no-permission"));
      return true;
    }

    if (args.length < 2) {
      sender.sendMessage(plugin.color("&cUsage: /offend <player> <reason>"));
      sender.sendMessage(plugin.color("&7Reasons: &f" + String.join(", ", keys())));
      return true;
    }

    String targetName = args[0];
    String reasonInput = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
    String key = matchKey(reasonInput);
    if (key == null) {
      sender.sendMessage(plugin.color("&cUnknown reason &f" + reasonInput
          + "&c. Valid: &f" + String.join(", ", keys())));
      return true;
    }

    ConfigurationSection section = plugin.getConfig()
        .getConfigurationSection("punishments." + key);
    if (section == null) {
      sender.sendMessage(plugin.color("&cReason &f" + key + " &cis not configured."));
      return true;
    }

    String banTime = section.getString("BanTime", "lifetime");
    String muteTime = section.getString("MuteTime", null);
    boolean resetData = section.getBoolean("ResetData", false);
    Date expiry = parseTime(banTime);

    OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
    final String displayName = target.getName() != null ? target.getName() : targetName;

    UUID targetId = resolveUuid(targetName, target);
    if (targetId == null) {
      sender.sendMessage(plugin.color("&cCould not resolve &f" + targetName + "&c. Try again or use the full name."));
      return true;
    }

    PlayerProfile profile = Bukkit.createProfile(targetId, displayName);
    @SuppressWarnings("deprecation")
    BanList<PlayerProfile> banList = Bukkit.getBanList(BanList.Type.PROFILE);
    if (banList.isBanned(profile)) {
      banList.pardon(profile);
    }

    Player online = target.isOnline() ? target.getPlayer() : null;
    String ip = null;
    if (online != null) {
      var address = online.getAddress();
      if (address != null && address.getAddress() != null) {
        ip = address.getAddress().getHostAddress();
      }
    }
    if (ip == null && store != null) {
      ip = store.lastIpFor(target.getUniqueId());
    }

    long expiresAt = expiry == null ? -1L : expiry.getTime();
    String banId = store != null ? store.generateBanId() : "?";
    PlayerDataStore.BanRecord banRecord = new PlayerDataStore.BanRecord(
        banId,
        targetId,
        displayName,
        ip,
        key,
        banTime,
        System.currentTimeMillis(),
        expiresAt,
        false);
    if (store != null) {
      store.recordBan(banRecord);
    }

    if (online != null) {
      if (resetData) {
        wipe(online);
      }
      online.kickPlayer(plugin.banScreenMessage(banRecord));
    } else if (resetData && target.hasPlayedBefore()) {
      sender.sendMessage(plugin.color(
          "&7Player is offline; data wipe will only run if they rejoin briefly."));
    }

    plugin.susFlagManager().clear(target.getUniqueId());

    if (muteTime != null && !muteTime.isEmpty() && online != null) {
      Date muteExpiry = parseTime(muteTime);
      long muteMs = muteExpiry == null ? -1L : muteExpiry.getTime() - System.currentTimeMillis();
      if (muteMs > 0) {
        try {
          net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
          net.luckperms.api.model.user.User user = lp.getUserManager().getUser(targetId);
          if (user != null) {
            user.data().add(net.luckperms.api.node.types.PermissionNode.builder("luckperms.chat.mute").build());
            lp.getUserManager().saveUser(user);
            long muteMinutes = muteMs / 60_000L;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
              try {
                net.luckperms.api.model.user.User u = lp.getUserManager().getUser(targetId);
                if (u != null) {
                  u.data().remove(net.luckperms.api.node.types.PermissionNode.builder("luckperms.chat.mute").build());
                  lp.getUserManager().saveUser(u);
                }
              } catch (Throwable ignored) { }
            }, muteMs / 50L);
            plugin.getLogger().info("[offend] Muted " + displayName + " for " + muteMinutes + "m");
          }
        } catch (Throwable e) {
          plugin.getLogger().warning("[offend] Failed to mute " + displayName + ": " + e.getMessage());
        }
      }
    }

    String banIdStr = store != null && store.lastBanFor(targetId) != null
        ? store.lastBanFor(targetId).banId : "?";
    Bukkit.getOnlinePlayers().stream()
        .filter(p -> p.hasPermission("donutrecreation.*"))
        .forEach(op -> op.sendMessage(plugin.color(
            "&c[&4PUNISH&c] &f" + sender.getName() + " &7punished &f" + displayName
                + " &7for &f" + key + " &7(&f" + banTime + "&7"
                + (resetData ? "&7, &cdata wiped" : "")
                + (muteTime != null ? "&7, &amuted " + muteTime : "")
                + "&7) &8[" + banIdStr + "]")));
    sender.sendMessage(plugin.color("&aPunished &f" + displayName + " &afor &f" + key));
    return true;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    if (!plugin.hasStaffAccess(sender)) {
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
      for (String reason : keys()) {
        if (reason.toLowerCase(Locale.ROOT).startsWith(lower)) {
          matches.add(reason);
        }
      }
      return matches;
    }
    return Collections.emptyList();
  }

  private List<String> keys() {
    ConfigurationSection root = plugin.getConfig().getConfigurationSection("punishments");
    if (root == null) {
      return Collections.emptyList();
    }
    return new ArrayList<>(root.getKeys(false));
  }

  private UUID resolveUuid(String name, OfflinePlayer fallback) {
    Player online = Bukkit.getPlayerExact(name);
    if (online != null) {
      return online.getUniqueId();
    }
    if (store != null) {
      UUID fromStore = store.findUuidByName(name);
      if (fromStore != null) {
        return fromStore;
      }
    }
    return fallback.getUniqueId();
  }

  private String matchKey(String input) {
    String lower = input.toLowerCase(Locale.ROOT).trim();
    for (String reason : keys()) {
      if (reason.equalsIgnoreCase(lower)) {
        return reason;
      }
    }
    String normalized = lower.replaceAll("[^a-z0-9]", "");
    for (String reason : keys()) {
      if (reason.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "").equals(normalized)) {
        return reason;
      }
    }
    return null;
  }

  private Date parseTime(String spec) {
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
      long ms = unitMs(unit, value);
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

  private long unitMs(String unit, long value) {
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

  private void wipe(Player target) {
    if (store != null && !store.hasWipeSnapshot(target.getUniqueId())) {
      try {
        store.saveWipeSnapshot(target.getUniqueId(),
            PlayerDataStore.WipeSnapshot.capture(target));
        plugin.getLogger().info("[offend] Saved data snapshot for " + target.getName());
      } catch (Throwable e) {
        plugin.getLogger().warning("[offend] Failed to snapshot " + target.getName()
            + ": " + e.getMessage());
      }
    }
    Map<String, Double> newPlayerBalances = new LinkedHashMap<>();
    ConfigurationSection balances = plugin.getConfig()
        .getConfigurationSection("punishments.new-player-balances");
    if (balances != null) {
      for (String currency : balances.getKeys(false)) {
        newPlayerBalances.put(currency, balances.getDouble(currency, 0.0));
      }
    } else {
      newPlayerBalances.put("coins", 50.0);
      newPlayerBalances.put("money", 5000.0);
    }
    com.notlucy.donutrecreation.punish.economy.CoinsEngineHook.resetToNewPlayer(target, newPlayerBalances);
    com.notlucy.donutrecreation.punish.economy.VariableEnderChestsHook.clear(target);
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