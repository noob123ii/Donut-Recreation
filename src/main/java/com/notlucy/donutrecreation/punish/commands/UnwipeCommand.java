package com.notlucy.donutrecreation.punish.commands;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.notlucy.donutrecreation.DonutRecreation;
import com.notlucy.donutrecreation.punish.store.PlayerDataStore;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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
    if (!plugin.hasStaffAccess(sender)) {
      sender.sendMessage(plugin.message("messages.no-permission"));
      return true;
    }

    if (args.length < 1) {
      sender.sendMessage(plugin.color("&cUsage: /unwipe <player>"));
      return true;
    }

    String targetName = args[0];

    UUID targetId = null;
    Player onlinePlayer = Bukkit.getPlayerExact(targetName);
    if (onlinePlayer != null) {
      targetId = onlinePlayer.getUniqueId();
    }
    if (targetId == null && store != null) {
      targetId = store.findUuidByName(targetName);
    }
    if (targetId == null) {
      OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
      targetId = offline.getUniqueId();
    }

    OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
    if (!target.hasPlayedBefore()) {
      sender.sendMessage(plugin.color("&cPlayer " + targetName + " has never joined."));
      return true;
    }

    if (target.isOnline()) {
      Player online = target.getPlayer();
      if (online != null && store != null) {
        PlayerDataStore.WipeSnapshot snapshot = store.wipeSnapshotFor(targetId);
        if (snapshot != null) {
          try {
            snapshot.applyTo(online);
            plugin.getLogger().info("[unwipe] Restored data snapshot for " + online.getName());
          } catch (Throwable e) {
            plugin.getLogger().warning("[unwipe] Failed to restore snapshot for "
                + online.getName() + ": " + e.getMessage());
          }
        }
      }
    } else if (store != null) {
      store.markWipeSnapshotPendingRestore(targetId);
      plugin.getLogger().info("[unwipe] " + targetName + " is offline; "
          + "data will be restored on next join.");
    }

    if (store != null) {
      store.removeBan(targetId);
    }

    try {
      PlayerProfile profile = Bukkit.createProfile(
          targetId, target.getName() != null ? target.getName() : targetName);
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
                + " &7(ban removed, data restored)")));

    boolean restored = false;
    if (store != null && target.isOnline()) {
      PlayerDataStore.WipeSnapshot snapshot = store.wipeSnapshotFor(targetId);
      restored = snapshot != null;
      store.removeWipeSnapshot(targetId);
    } else if (store != null && store.wipeSnapshotFor(targetId) != null) {
      restored = true;
    }
    if (target.isOnline()) {
      Player online = target.getPlayer();
      if (online != null) {
        online.sendMessage(plugin.color("&aYou have been unwiped by "
            + sender.getName() + ". Your data was restored."));
      }
    }

    sender.sendMessage(plugin.color("&aUnwiped &f"
        + (target.getName() != null ? target.getName() : targetName)
        + " &a(ban removed, "
        + (restored ? "data restored" : "no stored snapshot to restore")
        + ")"));

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
    return Collections.emptyList();
  }
}
