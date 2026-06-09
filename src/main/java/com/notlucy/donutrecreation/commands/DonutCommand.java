package com.notlucy.donutrecreation.commands;

import com.notlucy.donutrecreation.DonutRecreation;
import com.notlucy.donutrecreation.punish.store.PlayerDataStore;
import com.notlucy.donutrecreation.util.LogData;
import java.util.Collections;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class DonutCommand implements CommandExecutor, TabCompleter {

  private final DonutRecreation plugin;
  private final PlayerDataStore store;

  public DonutCommand(DonutRecreation plugin, PlayerDataStore store) {
    this.plugin = plugin;
    this.store = store;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command,
                         String label, String[] args) {
    if (!sender.isOp()) {
      sender.sendMessage(plugin.message("messages.no-permission"));
      return true;
    }
    if (args.length == 0 || !"reload".equalsIgnoreCase(args[0])) {
      sender.sendMessage(plugin.color("&cUsage: /donut reload"));
      return true;
    }
    plugin.reloadConfig();
    if (store != null) {
      store.reload();
    }
    sender.sendMessage(plugin.color("&aConfig and player database reloaded."));
    plugin.getLogger().info(sender.getName() + " reloaded config and player database.");
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
    if (!sender.isOp() || args.length != 1) {
      return Collections.emptyList();
    }
    if ("reload".toLowerCase().startsWith(args[0].toLowerCase())) {
      return List.of("reload");
    }
    return Collections.emptyList();
  }
}
