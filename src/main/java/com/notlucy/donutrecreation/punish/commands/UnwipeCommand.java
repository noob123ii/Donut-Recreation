package com.notlucy.donutrecreation.punish.commands;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.notlucy.donutrecreation.DonutRecreation;
import com.notlucy.donutrecreation.punish.store.PlayerDataStore;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class UnwipeCommand implements CommandExecutor, TabCompleter {

  private final DonutRecreation plugin;
  private final PlayerDataStore store;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Plugin instance and store are shared by Bukkit.")
  public UnwipeCommand(DonutRecreation plugin, PlayerDataStore store) {
    this.plugin = plugin;
    this.store = store;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!sender.hasPermission("donutrecreation.*")) {
      sender.sendMessage(plugin.message("messages.no-permission"));
      return true;
    }

    if (args.length < 1) {
      sender.sendMessage(plugin.color("&cUsage: /unwipe <player>"));
      return true;
    }

    String targetName = args[0];
    OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
    if (!target.hasPlayedBefore()) {
      sender.sendMessage(plugin.color("&cPlayer " + targetName + " has never joined."));
      return true;
    }

    if (target.isOnline()) {
      Player online = target.getPlayer();
      if (online != null) {
        online.getInventory().clear();
        online.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
        online.getEnderChest().clear();
        online.setExp(0f);
        online.setLevel(0);
        online.setTotalExperience(0);
        online.setHealth(online.getMaxHealth());
        online.setFoodLevel(20);
        online.setSaturation(5f);
        online.getActivePotionEffects().forEach(e -> online.removePotionEffect(e.getType()));
        for (org.bukkit.Statistic stat : org.bukkit.Statistic.values()) {
          try {
            if (stat.getType() == org.bukkit.Statistic.Type.UNTYPED) {
              online.setStatistic(stat, 0);
            }
          } catch (Throwable ignored) {
          }
        }
      }
    }

    store.removeBan(target.getUniqueId());

    try {
      PlayerProfile profile = Bukkit.createProfile(
          target.getUniqueId(), target.getName() != null ? target.getName() : targetName);
      @SuppressWarnings("deprecation")
      BanList<PlayerProfile> banList = Bukkit.getBanList(BanList.Type.PROFILE);
      if (banList.isBanned(profile)) {
        banList.pardon(profile);
      }
    } catch (Throwable ignored) {
    }

    Bukkit.getOnlinePlayers().stream()
        .filter(p -> p.hasPermission("donutrecreation.*"))
        .forEach(op -> op.sendMessage(plugin.color(
            "&a[&2UNWIPE&a] &f" + sender.getName() + " &7unwiped &f"
                + (target.getName() != null ? target.getName() : targetName)
                + " &7(ban removed, data reset to default)")));

    sender.sendMessage(plugin.color("&aUnwiped &f"
        + (target.getName() != null ? target.getName() : targetName)
        + " &a(ban removed, inventory cleared)"));

    if (target.isOnline()) {
      Player online = target.getPlayer();
      if (online != null) {
        online.sendMessage(plugin.color("&aYou have been unwiped by " + sender.getName() + "."));
      }
    }

    return true;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    if (!sender.hasPermission("donutrecreation.*")) {
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
    return Collections.emptyList();
  }
}
