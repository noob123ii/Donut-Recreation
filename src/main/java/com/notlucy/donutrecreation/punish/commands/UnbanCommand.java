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
    if (!sender.isOp()) {
      sender.sendMessage(plugin.message("messages.no-permission"));
      return true;
    }
    if (args.length < 1) {
      sender.sendMessage(plugin.color("&cUsage: /unban <player>"));
      return true;
    }
    String targetName = args[0];
    OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
    String name = offline.getName() != null ? offline.getName() : targetName;

    if (store != null) {
      store.removeBan(offline.getUniqueId());
    }

    try {
      PlayerProfile profile = Bukkit.createProfile(offline.getUniqueId(), name);
      @SuppressWarnings("deprecation")
      BanList<PlayerProfile> banList = Bukkit.getBanList(BanList.Type.PROFILE);
      banList.pardon(profile);
    } catch (Throwable ignored) {
    }

    sender.sendMessage(plugin.color("&aUnbanned &f" + name));
    return true;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    if (!sender.isOp() || args.length != 1) {
      return Collections.emptyList();
    }
    List<String> names = new ArrayList<>();
    String lower = args[0].toLowerCase(Locale.ROOT);
    for (Player p : Bukkit.getOnlinePlayers()) {
      if (p.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
        names.add(p.getName());
      }
    }
    return names;
  }
}
