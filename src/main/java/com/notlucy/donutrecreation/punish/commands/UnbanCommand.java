package com.notlucy.donutrecreation.punish.commands;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.notlucy.donutrecreation.DonutRecreation;
import com.notlucy.donutrecreation.punish.store.PlayerDataStore;
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
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class UnbanCommand implements CommandExecutor, TabCompleter {

  private final DonutRecreation plugin;
  private final PlayerDataStore store;

  public UnbanCommand(DonutRecreation plugin, PlayerDataStore store) {
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
      sender.sendMessage(plugin.color("&cUsage: /unban <player>"));
      return true;
    }
    String name = args[0];
    OfflinePlayer target = Bukkit.getOfflinePlayer(name);
    String display = target.getName() != null ? target.getName() : name;

    if (store != null) {
      store.removeBan(target.getUniqueId());
    }

    try {
      PlayerProfile profile = Bukkit.createProfile(target.getUniqueId(), display);
      @SuppressWarnings("deprecation")
      BanList<PlayerProfile> banList = Bukkit.getBanList(BanList.Type.PROFILE);
      banList.pardon(profile);
    } catch (Throwable ignored) {
    }

    sender.sendMessage(plugin.color("&aUnbanned &f" + display));
    return true;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    if (!sender.hasPermission("donutrecreation.*") || args.length != 1) {
      return Collections.emptyList();
    }
    List<String> names = new ArrayList<>();
    String lower = args[0].toLowerCase(Locale.ROOT);
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (player.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
        names.add(player.getName());
      }
    }
    return names;
  }
}