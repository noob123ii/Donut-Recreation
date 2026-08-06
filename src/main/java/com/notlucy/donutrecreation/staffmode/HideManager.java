package com.notlucy.donutrecreation.staffmode;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.util.LegacyComponent;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.CollisionRule;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.NameTagVisibility;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.OptionData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.ScoreBoardTeamInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.TeamMode;
import com.notlucy.donutrecreation.spawn.manager.SkinStore;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class HideManager {

  private final Set<UUID> hideName = ConcurrentHashMap.newKeySet();
  private final Set<UUID> hideSkin = ConcurrentHashMap.newKeySet();

  public boolean toggleName(UUID id) {
    boolean newState = !hideName.contains(id);
    if (newState) {
      hideName.add(id);
    } else {
      hideName.remove(id);
    }
    return newState;
  }

  public boolean toggleSkin(UUID id) {
    boolean newState = !hideSkin.contains(id);
    if (newState) {
      hideSkin.add(id);
    } else {
      hideSkin.remove(id);
    }
    return newState;
  }

  public boolean isHidingName(UUID id) {
    return hideName.contains(id);
  }

  public boolean isHidingSkin(UUID id) {
    return hideSkin.contains(id);
  }

  public void clear(UUID id) {
    hideName.remove(id);
    hideSkin.remove(id);
  }

  public void applyToViewer(Player viewer) {
    for (Player target : Bukkit.getOnlinePlayers()) {
      if (target.equals(viewer)) {
        continue;
      }
      if (hideName.contains(viewer.getUniqueId())) {
        sendNameState(viewer, target, true);
      }
      if (hideSkin.contains(viewer.getUniqueId())) {
        sendSkinState(viewer, target, true);
      }
    }
  }

  public void applyToViewers(Player target) {
    for (Player viewer : Bukkit.getOnlinePlayers()) {
      if (viewer.equals(target)) {
        continue;
      }
      if (hideName.contains(viewer.getUniqueId())) {
        sendNameState(viewer, target, true);
      }
      if (hideSkin.contains(viewer.getUniqueId())) {
        sendSkinState(viewer, target, true);
      }
    }
  }

  public void restoreAll(Player viewer) {
    for (Player target : Bukkit.getOnlinePlayers()) {
      if (target.equals(viewer)) {
        continue;
      }
      sendNameState(viewer, target, false);
      sendSkinState(viewer, target, false);
    }
  }

  private void sendNameState(Player viewer, Player target, boolean hidden) {
    try {
      String teamName = "dn" + target.getUniqueId().toString()
          .replace("-", "").substring(0, 13);
      if (hidden) {
        ScoreBoardTeamInfo info = new ScoreBoardTeamInfo(
            LegacyComponent.empty(), new LegacyComponent("\u00a7k"), LegacyComponent.empty(),
            NameTagVisibility.ALWAYS, CollisionRule.ALWAYS, NamedTextColor.WHITE,
            OptionData.NONE);
        send(viewer, new WrapperPlayServerTeams(
            teamName, TeamMode.CREATE, info, List.of(target.getName())));
      } else {
        send(viewer, new WrapperPlayServerTeams(
            teamName, TeamMode.REMOVE, Optional.empty(), List.of()));
      }
      PlayerInfo display = new PlayerInfo(targetProfile(target, false), true, 0,
          GameMode.SURVIVAL, displayName(target, hidden), null);
      send(viewer, new WrapperPlayServerPlayerInfoUpdate(
          EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME),
          List.of(display)));
    } catch (Throwable ignored) {
    }
  }

  private void sendSkinState(Player viewer, Player target, boolean hidden) {
    try {
      UserProfile profile = hidden
          ? new UserProfile(target.getUniqueId(), target.getName(), List.of())
          : targetProfile(target, true);
      PlayerInfo info = new PlayerInfo(profile, true, 0, GameMode.SURVIVAL, null, null);
      send(viewer, new WrapperPlayServerPlayerInfoUpdate(
          EnumSet.of(
              WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
              WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
          List.of(info)));
    } catch (Throwable ignored) {
    }
  }

  private static Component displayName(Player target, boolean hidden) {
    return hidden
        ? Component.text(target.getName()).decorate(TextDecoration.OBFUSCATED)
        : Component.text(target.getName());
  }

  private static UserProfile targetProfile(Player target, boolean withTextures) {
    if (withTextures) {
      SkinStore.SkinRecord live = SkinStore.liveOf(target);
      if (live != null && live.texture() != null && !live.texture().isEmpty()) {
        return new UserProfile(live.uuid(), live.name(), List.of(
            new TextureProperty("textures", live.texture(), live.signature())));
      }
    }
    return new UserProfile(target.getUniqueId(), target.getName());
  }

  private static void send(Player viewer, PacketWrapper<?> packet) {
    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
  }
}
