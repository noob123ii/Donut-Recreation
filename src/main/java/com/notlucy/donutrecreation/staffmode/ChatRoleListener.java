package com.notlucy.donutrecreation.staffmode;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scoreboard.Team;

/** Formats chat names with the role prefix/colour shown in the tab list and name tags. */
public final class ChatRoleListener implements Listener {

  @EventHandler(priority = EventPriority.LOWEST)
  public void onChat(AsyncPlayerChatEvent event) {
    if (event.isCancelled()) {
      return;
    }
    event.setFormat(chatName(event.getPlayer()) + ": %2$s");
  }

  private static String chatName(Player sender) {
    Team team = sender.getScoreboard().getEntryTeam(sender.getName());
    if (team != null) {
      return team.getPrefix() + team.getColor() + sender.getName() + team.getSuffix();
    }
    Component listName = sender.playerListName();
    if (listName != null) {
      return LegacyComponentSerializer.legacySection().serialize(listName);
    }
    return sender.getName();
  }
}