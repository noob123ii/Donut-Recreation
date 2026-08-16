package com.notlucy.donutrecreation.staffmode;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.notlucy.donutrecreation.spawn.manager.SkinStore;
import com.notlucy.donutrecreation.util.LogData;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class HideManager {

  /** Profile name used while a name is hidden. Matches no scoreboard team and renders no tag. */
  private static final String HIDDEN_NAME = "";

  public interface BotRespawner {
    void respawnFor(Player viewer, UUID botUuid);
  }

  private final Set<UUID> hideName = ConcurrentHashMap.newKeySet();
  private final Set<UUID> hideSkin = ConcurrentHashMap.newKeySet();
  private final Set<TestBot> extraTargets = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Map<UUID, Component>> displayNameCache = new ConcurrentHashMap<>();
  private BotRespawner botRespawner;

  public void setBotRespawner(BotRespawner botRespawner) {
    this.botRespawner = botRespawner;
  }

  public boolean toggleName(UUID id) {
    boolean newState = !hideName.contains(id);
    if (newState) {
      hideName.add(id);
    } else {
      hideName.remove(id);
    }
    return newState;
  }

  public void setHidingName(UUID id, boolean hidden) {
    if (hidden) {
      hideName.add(id);
    } else {
      hideName.remove(id);
    }
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

  public void setHidingSkin(UUID id, boolean hidden) {
    if (hidden) {
      hideSkin.add(id);
    } else {
      hideSkin.remove(id);
    }
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
    displayNameCache.remove(id);
  }

  public void registerExtraTarget(TestBot bot) {
    extraTargets.add(bot);
  }

  public void unregisterExtraTarget(TestBot bot) {
    extraTargets.remove(bot);
  }

  public void applyToViewer(Player viewer) {
    boolean n = hideName.contains(viewer.getUniqueId());
    boolean s = hideSkin.contains(viewer.getUniqueId());
    if (!n && !s) {
      return;
    }
    for (Player target : Bukkit.getOnlinePlayers()) {
      if (target.equals(viewer)) {
        continue;
      }
      applyIdentity(viewer, target, n, s);
    }
    for (TestBot bot : extraTargets) {
      applyBotIdentity(viewer, bot, true);
    }
  }

  public void applyToViewers(Player target) {
    for (Player viewer : Bukkit.getOnlinePlayers()) {
      if (viewer.equals(target)) {
        continue;
      }
      boolean n = hideName.contains(viewer.getUniqueId());
      boolean s = hideSkin.contains(viewer.getUniqueId());
      if (n || s) {
        applyIdentity(viewer, target, n, s);
      }
    }
    for (TestBot bot : extraTargets) {
      for (Player viewer : Bukkit.getOnlinePlayers()) {
        if (hideName.contains(viewer.getUniqueId())
            || hideSkin.contains(viewer.getUniqueId())) {
          applyBotIdentity(viewer, bot, true);
        }
      }
    }
  }

  public void restoreAllNames(Player viewer) {
    boolean s = hideSkin.contains(viewer.getUniqueId());
    for (Player target : Bukkit.getOnlinePlayers()) {
      if (target.equals(viewer)) {
        continue;
      }
      applyIdentity(viewer, target, false, s);
    }
    for (TestBot bot : extraTargets) {
      applyBotIdentity(viewer, bot, false);
    }
  }

  public void restoreAllSkins(Player viewer) {
    boolean n = hideName.contains(viewer.getUniqueId());
    for (Player target : Bukkit.getOnlinePlayers()) {
      if (target.equals(viewer)) {
        continue;
      }
      applyIdentity(viewer, target, n, false);
    }
    for (TestBot bot : extraTargets) {
      applyBotIdentity(viewer, bot, false);
    }
  }

  /**
   * Swaps the tab-list identity of {@code target} for {@code viewer} only:
   *  - name hidden: profile name becomes blank (no scoreboard team matches it, so the
   *    above-head tag, including any role prefix, disappears) while the cached full
   *    display name keeps the tab list looking normal for the viewer;
   *  - skin hidden: textures are dropped from the profile.
   * Restoring sends the original profile back, which brings the name, role and skin
   * back. Other players' clients never receive these packets.
   */
  private void applyIdentity(Player viewer, Player target,
      boolean nameHidden, boolean skinHidden) {
    if (nameHidden) {
      cacheName(viewer, target);
    } else {
      dropCachedName(viewer, target);
    }
    try {
      send(viewer, new WrapperPlayServerPlayerInfoRemove(List.of(target.getUniqueId())));
      UserProfile profile;
      if (nameHidden && !skinHidden) {
        profile = renamedWithTextures(target);
      } else if (nameHidden) {
        profile = new UserProfile(target.getUniqueId(), HIDDEN_NAME);
      } else {
        profile = targetProfile(target, !skinHidden);
      }
      EnumSet<Action> actions = EnumSet.of(
          Action.ADD_PLAYER, Action.UPDATE_LISTED, Action.UPDATE_DISPLAY_NAME);
      PlayerInfo info = new PlayerInfo(profile, true, 0, GameMode.SURVIVAL,
          nameHidden ? cachedName(viewer, target) : null, null);
      send(viewer, new WrapperPlayServerPlayerInfoUpdate(actions, List.of(info)));
      respawnPlayerEntity(viewer, target);
      LogData.get().fine("[hidename] " + (nameHidden ? "hidden" : "shown")
          + " name for " + target.getName() + " to " + viewer.getName());
    } catch (Throwable error) {
      LogData.get().warning("[hidename] identity swap failed for "
          + target.getName() + " to " + viewer.getName() + ": " + error);
    }
  }

  private void cacheName(Player viewer, Player target) {
    displayNameCache.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>())
        .putIfAbsent(target.getUniqueId(), fullDisplayName(target));
  }

  private void dropCachedName(Player viewer, Player target) {
    Map<UUID, Component> names = displayNameCache.get(viewer.getUniqueId());
    if (names != null) {
      names.remove(target.getUniqueId());
    }
  }

  private Component cachedName(Player viewer, Player target) {
    Map<UUID, Component> names = displayNameCache.get(viewer.getUniqueId());
    return names == null ? null : names.get(target.getUniqueId());
  }

  /** The full display name (with role prefix, as shown in the name tag) for a player. */
  private static Component fullDisplayName(Player target) {
    Team team = target.getScoreboard().getEntryTeam(target.getName());
    if (team != null) {
      return LegacyComponentSerializer.legacySection().deserialize(
          team.getPrefix() + team.getColor() + target.getName() + team.getSuffix());
    }
    Component listName = target.playerListName();
    if (listName != null) {
      return listName;
    }
    return Component.text(target.getName());
  }

  private void applyBotIdentity(Player viewer, TestBot bot, boolean hidden) {
    if (botRespawner == null) {
      return;
    }
    try {
      send(viewer, new WrapperPlayServerPlayerInfoRemove(List.of(bot.uuid())));
      botRespawner.respawnFor(viewer, bot.uuid());
      LogData.get().fine("[hidename] bot " + (hidden ? "hidden" : "shown")
          + " for " + viewer.getName());
    } catch (Throwable error) {
      LogData.get().warning("[hidename] bot identity swap failed for "
          + viewer.getName() + ": " + error);
    }
  }

  private static UserProfile renamedWithTextures(Player target) {
    SkinStore.SkinRecord live = SkinStore.liveOf(target);
    if (live != null && live.texture() != null && !live.texture().isEmpty()) {
      return new UserProfile(target.getUniqueId(), HIDDEN_NAME, List.of(
          new TextureProperty("textures", live.texture(), live.signature())));
    }
    return new UserProfile(target.getUniqueId(), HIDDEN_NAME);
  }

  private void respawnPlayerEntity(Player viewer, Player target) {
    try {
      send(viewer, new WrapperPlayServerDestroyEntities(target.getEntityId()));
      Location loc = target.getLocation();
      com.github.retrooper.packetevents.protocol.world.Location pl =
          new com.github.retrooper.packetevents.protocol.world.Location(
              loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
      send(viewer, new WrapperPlayServerSpawnEntity(
          target.getEntityId(), target.getUniqueId(), EntityTypes.PLAYER, pl,
          loc.getYaw(), 0, new Vector3d(0, 0, 0)));
      send(viewer, equipmentPacket(target));
    } catch (Throwable ignored) {
    }
  }

  /** Re-sends the target's held items and armor, which a respawn wipes on the client. */
  private static WrapperPlayServerEntityEquipment equipmentPacket(Player target) {
    org.bukkit.inventory.PlayerInventory inv = target.getInventory();
    List<Equipment> equipment = List.of(
        equipment(EquipmentSlot.MAIN_HAND, inv.getItemInMainHand()),
        equipment(EquipmentSlot.OFF_HAND, inv.getItemInOffHand()),
        equipment(EquipmentSlot.HELMET, inv.getHelmet()),
        equipment(EquipmentSlot.CHEST_PLATE, inv.getChestplate()),
        equipment(EquipmentSlot.LEGGINGS, inv.getLeggings()),
        equipment(EquipmentSlot.BOOTS, inv.getBoots()));
    return new WrapperPlayServerEntityEquipment(target.getEntityId(), equipment);
  }

  private static Equipment equipment(EquipmentSlot slot, org.bukkit.inventory.ItemStack item) {
    return new Equipment(slot, SpigotConversionUtil.fromBukkitItemStack(item));
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
