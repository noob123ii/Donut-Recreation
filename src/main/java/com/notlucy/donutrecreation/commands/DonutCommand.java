/*
 * Copyright (C) 2026 Notlucy and Donut Recreation contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.notlucy.donutrecreation.commands;

import java.util.Collections;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import com.notlucy.donutrecreation.DonutRecreation;
import com.notlucy.donutrecreation.punish.store.PlayerDataStore;
import com.notlucy.donutrecreation.spawn.manager.StashManager;
import org.bukkit.entity.Player;
import org.bukkit.World;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public class DonutCommand implements CommandExecutor, TabCompleter {

  private final DonutRecreation plugin;
  private final PlayerDataStore store;
  private StashManager stashManager;
  private ChunkGenerator chunkGenerator;
  private com.notlucy.donutrecreation.baseprotection.packet.PacketHider packetHider;
  private com.notlucy.donutrecreation.baseprotection.packet.PacketAuditManager packetAudit;

  public DonutCommand(DonutRecreation plugin, PlayerDataStore store) {
    this.plugin = plugin;
    this.store = store;
  }

  public void setStashManager(StashManager stashManager) {
    this.stashManager = stashManager;
  }

  public void setChunkGenerator(ChunkGenerator chunkGenerator) {
    this.chunkGenerator = chunkGenerator;
  }

  public void setPacketHider(com.notlucy.donutrecreation.baseprotection.packet.PacketHider packetHider) {
    this.packetHider = packetHider;
    this.packetAudit = packetHider == null ? null : packetHider.audit();
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command,
                         String label, String[] args) {
    if (!plugin.hasStaffAccess(sender)) {
      sender.sendMessage(plugin.message("messages.no-permission"));
      return true;
    }
    if (args.length == 0) {
      sender.sendMessage(plugin.color(
          "&cUsage: /donut reload | /donut chunk generate <border> | /donut xray [rescan]"));
      return true;
    }
    if ("reload".equalsIgnoreCase(args[0])) {
      try {
        plugin.reloadConfig();
        if (store != null) {
          store.reload();
        }
        if (stashManager != null) {
          stashManager.reload();
        }
        if (packetHider != null) {
          packetHider.reload();
        }
        sender.sendMessage(plugin.color("&aConfig, player database, stash templates, and block registry reloaded."));
        plugin.getLogger().info(sender.getName() + " reloaded config, player database, stash templates, and block registry.");
      } catch (Throwable e) {
        sender.sendMessage(plugin.color("&cReload failed: " + e.getMessage()));
        plugin.getLogger().warning("[donut] reload failed: " + e);
      }
      return true;
    }
    if ("chunk".equalsIgnoreCase(args[0])) {
      if (chunkGenerator == null) {
        sender.sendMessage(plugin.color("&cChunk generator not initialized."));
        return true;
      }
      if (args.length < 3 || !"generate".equalsIgnoreCase(args[1])) {
        sender.sendMessage(plugin.color("&cUsage: /donut chunk generate <border>"));
        return true;
      }
      if (!(sender instanceof Player)) {
        sender.sendMessage(plugin.color("&cThis command can only be run by a player."));
        return true;
      }
      Player player = (Player) sender;
      World world = player.getWorld();
      try {
        int border = Integer.parseInt(args[2]);
        chunkGenerator.generateChunks(world, border, player);
      } catch (NumberFormatException e) {
        sender.sendMessage(plugin.color("&cInvalid border size. Must be a number (e.g., 10000 for 10k)."));
      }
      return true;
    }
    if ("xray".equalsIgnoreCase(args[0])) {
      var hiderManager = packetHider == null ? null : packetHider.revealManager();
      if (hiderManager == null) {
        sender.sendMessage(plugin.color("&cBase hider is not initialized."));
        return true;
      }
      if (args.length > 1 && "rescan".equalsIgnoreCase(args[1])) {
        sender.sendMessage(plugin.color(
            "&eTerrain/surface hiding was removed (it caused lag). "
                + "&7Placed bases above y=60 and chests below y=0 are now hidden instantly via packets."));
        return true;
      }
      Player player = sender instanceof Player p ? p : null;
      for (String line : hiderManager.xrayStatus(player)) {
        sender.sendMessage(plugin.color("&7" + line));
      }
      return true;
    }
    if ("packets".equalsIgnoreCase(args[0])) {
      if (packetAudit == null) {
        sender.sendMessage(plugin.color("&cPacket audit manager is not initialized."));
        return true;
      }
      if (args.length > 1 && "clear".equalsIgnoreCase(args[1])) {
        packetAudit.clear();
        sender.sendMessage(plugin.color("&aPacket audit statistics cleared."));
        return true;
      }
      for (String line : packetAudit.reportLines(100)) {
        sender.sendMessage(plugin.color("&7" + line));
      }
      return true;
    }
    sender.sendMessage(plugin.color(
        "&cUsage: /donut reload | /donut chunk generate <border> | /donut xray [rescan]"));
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
    if (!plugin.hasStaffAccess(sender)) {
      return Collections.emptyList();
    }
    if (args.length == 1) {
      if ("reload".toLowerCase().startsWith(args[0].toLowerCase())) {
        return List.of("reload");
      }
      if ("chunk".toLowerCase().startsWith(args[0].toLowerCase())) {
        return List.of("chunk");
      }
      if ("packets".toLowerCase().startsWith(args[0].toLowerCase())) {
        return List.of("packets");
      }
      if ("xray".toLowerCase().startsWith(args[0].toLowerCase())) {
        return List.of("xray");
      }
      return Collections.emptyList();
    }
    if (args.length == 2 && "chunk".equalsIgnoreCase(args[0])) {
      if ("generate".toLowerCase().startsWith(args[1].toLowerCase())) {
        return List.of("generate");
      }
      return Collections.emptyList();
    }
    if (args.length == 2 && "packets".equalsIgnoreCase(args[0])) {
      if ("clear".toLowerCase().startsWith(args[1].toLowerCase())) {
        return List.of("clear");
      }
      return Collections.emptyList();
    }
    if (args.length == 2 && "xray".equalsIgnoreCase(args[0])) {
      if ("rescan".toLowerCase().startsWith(args[1].toLowerCase())) {
        return List.of("rescan");
      }
      return Collections.emptyList();
    }
    return Collections.emptyList();
  }
}
