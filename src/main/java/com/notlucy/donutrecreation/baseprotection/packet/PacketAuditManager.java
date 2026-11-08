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
package com.notlucy.donutrecreation.baseprotection.packet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;

@SuppressWarnings({"checkstyle:MissingJavadocType", "checkstyle:MissingJavadocMethod"})
public final class PacketAuditManager {

  public enum Risk {
    BLOCK_DATA,
    ENTITY_DATA,
    PLAYER_DATA,
    WORLD_EVENT,
    UI,
    OTHER
  }

  private static final Set<PacketTypeCommon> BLOCK_PACKETS = Set.of(
      PacketType.Play.Server.CHUNK_DATA,
      PacketType.Play.Server.UPDATE_LIGHT,
      PacketType.Play.Server.MULTI_BLOCK_CHANGE,
      PacketType.Play.Server.BLOCK_CHANGE,
      PacketType.Play.Server.BLOCK_ENTITY_DATA,
      PacketType.Play.Server.BLOCK_ACTION,
      PacketType.Play.Server.UPDATE_SIGN,
      PacketType.Play.Server.OPEN_SIGN_EDITOR,
      PacketType.Play.Server.ADD_TRANSIENT_BLOCK,
      PacketType.Play.Server.DEBUG_BLOCK_VALUE,
      PacketType.Play.Server.DEBUG_CHUNK_VALUE,
      PacketType.Play.Server.TEST_INSTANCE_BLOCK_STATUS,
      PacketType.Play.Server.GAME_TEST_HIGHLIGHT_POS
  );

  private static final Set<PacketTypeCommon> ENTITY_PACKETS = Set.of(
      PacketType.Play.Server.SPAWN_ENTITY,
      PacketType.Play.Server.SPAWN_LIVING_ENTITY,
      PacketType.Play.Server.SPAWN_PLAYER,
      PacketType.Play.Server.SPAWN_EXPERIENCE_ORB,
      PacketType.Play.Server.SPAWN_PAINTING,
      PacketType.Play.Server.SPAWN_WEATHER_ENTITY,
      PacketType.Play.Server.DESTROY_ENTITIES,
      PacketType.Play.Server.ENTITY_RELATIVE_MOVE,
      PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION,
      PacketType.Play.Server.ENTITY_ROTATION,
      PacketType.Play.Server.ENTITY_TELEPORT,
      PacketType.Play.Server.ENTITY_POSITION_SYNC,
      PacketType.Play.Server.ENTITY_MOVEMENT,
      PacketType.Play.Server.ENTITY_HEAD_LOOK,
      PacketType.Play.Server.ENTITY_METADATA,
      PacketType.Play.Server.ENTITY_EQUIPMENT,
      PacketType.Play.Server.ENTITY_ANIMATION,
      PacketType.Play.Server.ENTITY_STATUS,
      PacketType.Play.Server.ENTITY_VELOCITY,
      PacketType.Play.Server.ENTITY_EFFECT,
      PacketType.Play.Server.REMOVE_ENTITY_EFFECT,
      PacketType.Play.Server.HURT_ANIMATION,
      PacketType.Play.Server.MOVE_MINECART,
      PacketType.Play.Server.PROJECTILE_POWER,
      PacketType.Play.Server.DAMAGE_EVENT,
      PacketType.Play.Server.ATTACH_ENTITY,
      PacketType.Play.Server.SET_PASSENGERS,
      PacketType.Play.Server.COLLECT_ITEM,
      PacketType.Play.Server.UPDATE_ENTITY_NBT,
      PacketType.Play.Server.WAYPOINT,
      PacketType.Play.Server.SCULK_VIBRATION_SIGNAL,
      PacketType.Play.Server.DEBUG_ENTITY_VALUE,
      PacketType.Play.Server.DEBUG_EVENT
  );

  private static final Set<PacketTypeCommon> PLAYER_PACKETS = Set.of(
      PacketType.Play.Server.PLAYER_INFO,
      PacketType.Play.Server.PLAYER_INFO_UPDATE,
      PacketType.Play.Server.PLAYER_INFO_REMOVE,
      PacketType.Play.Server.PLAYER_CHAT_HEADER,
      PacketType.Play.Server.CHAT_MESSAGE,
      PacketType.Play.Server.DISGUISED_CHAT,
      PacketType.Play.Server.SYSTEM_CHAT_MESSAGE,
      PacketType.Play.Server.FACE_PLAYER,
      PacketType.Play.Server.CAMERA,
      PacketType.Play.Server.USE_BED
  );

  private static final Set<PacketTypeCommon> WORLD_PACKETS = Set.of(
      PacketType.Play.Server.EXPLOSION,
      PacketType.Play.Server.PARTICLE,
      PacketType.Play.Server.EFFECT,
      PacketType.Play.Server.SOUND_EFFECT,
      PacketType.Play.Server.ENTITY_SOUND_EFFECT,
      PacketType.Play.Server.BLOCK_BREAK_ANIMATION
  );

  private static final Set<PacketTypeCommon> UI_PACKETS = Set.of(
      PacketType.Play.Server.OPEN_WINDOW,
      PacketType.Play.Server.CLOSE_WINDOW,
      PacketType.Play.Server.WINDOW_ITEMS,
      PacketType.Play.Server.WINDOW_PROPERTY,
      PacketType.Play.Server.SET_SLOT,
      PacketType.Play.Server.SET_CURSOR_ITEM,
      PacketType.Play.Server.SET_PLAYER_INVENTORY,
      PacketType.Play.Server.MERCHANT_OFFERS
  );

  private static final class PacketStats {
    private final PacketTypeCommon type;
    private final Risk risk;
    private final boolean handled;
    private long count;
    private long blocked;

    PacketStats(PacketTypeCommon type, Risk risk, boolean handled) {
      this.type = type;
      this.risk = risk;
      this.handled = handled;
    }
  }

  private final Plugin plugin;
  private final Map<PacketTypeCommon, PacketStats> stats = new ConcurrentHashMap<>();
  private final AtomicLong totalPackets = new AtomicLong();
  private final Set<String> blockedNames = ConcurrentHashMap.newKeySet();
  private volatile boolean enabled;
  private volatile boolean logNewTypes;
  private volatile boolean blockUnhandledRisky;
  private BukkitTask reportTask;

  public PacketAuditManager(Plugin plugin) {
    this.plugin = plugin;
    reload();
  }

  public void reload() {
    enabled = plugin.getConfig().getBoolean("packet-audit.enabled", true);
    logNewTypes = plugin.getConfig().getBoolean("packet-audit.log-new-types", true);
    blockUnhandledRisky = plugin.getConfig()
        .getBoolean("packet-audit.block-unhandled-risky", true);
    blockedNames.clear();
    for (String name : plugin.getConfig().getStringList("packet-audit.blocked-packets")) {
      if (name != null && !name.isBlank()) {
        blockedNames.add(name.trim().toLowerCase(Locale.ROOT));
      }
    }
  }

  public void start() {
    stop();
    long seconds = Math.max(0L,
        plugin.getConfig().getLong("packet-audit.report-interval-seconds", 0L));
    if (seconds <= 0) {
      return;
    }
    long ticks = seconds * 20L;
    reportTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
        plugin, this::logReport, ticks, ticks);
  }

  public void stop() {
    if (reportTask != null) {
      reportTask.cancel();
      reportTask = null;
    }
  }

  public boolean shouldBlock(PacketTypeCommon type, boolean handled) {
    if (type == null) return false;
    String name = type.getName().toLowerCase(Locale.ROOT);
    if (blockedNames.contains(name)) {
      return true;
    }
    if (!blockUnhandledRisky || handled) {
      return false;
    }
    Risk risk = classify(type);
    return risk == Risk.BLOCK_DATA || risk == Risk.ENTITY_DATA;
  }

  public void record(PacketTypeCommon type, boolean handled, boolean blocked) {
    if (!enabled || type == null) {
      return;
    }
    totalPackets.incrementAndGet();
    PacketStats packetStats = stats.computeIfAbsent(type,
        key -> new PacketStats(key, classify(key), handled));
    packetStats.count++;
    if (blocked) {
      packetStats.blocked++;
    }
    if (packetStats.count == 1 && logNewTypes) {
      plugin.getLogger().info("[packet-audit] " + type.getName()
          + " risk=" + packetStats.risk
          + " handled=" + packetStats.handled
          + " blocked=" + blocked);
    }
  }

  public void clear() {
    stats.clear();
    totalPackets.set(0L);
  }

  public List<String> reportLines(int limit) {
    List<PacketStats> sorted = new ArrayList<>(stats.values());
    sorted.sort(Comparator.comparing((PacketStats entry) -> entry.risk)
        .thenComparing(entry -> entry.type.getName()));
    List<String> lines = new ArrayList<>(sorted.size() + 2);
    lines.add("Observed packet types: " + sorted.size()
        + " total packets: " + totalPackets.get());
    int written = 0;
    for (PacketStats entry : sorted) {
      if (written++ >= limit) {
        lines.add("... " + (sorted.size() - limit) + " more packet types");
        break;
      }
      lines.add(entry.type.getName()
          + " risk=" + entry.risk
          + " handled=" + entry.handled
          + " count=" + entry.count
          + " blocked=" + entry.blocked);
    }
    return lines;
  }

  public void logReport() {
    for (String line : reportLines(500)) {
      plugin.getLogger().info("[packet-audit] " + line);
    }
  }

  public static Risk classify(PacketTypeCommon type) {
    if (type == null) return Risk.OTHER;
    if (BLOCK_PACKETS.contains(type)) return Risk.BLOCK_DATA;
    if (ENTITY_PACKETS.contains(type)) return Risk.ENTITY_DATA;
    if (PLAYER_PACKETS.contains(type)) return Risk.PLAYER_DATA;
    if (WORLD_PACKETS.contains(type)) return Risk.WORLD_EVENT;
    if (UI_PACKETS.contains(type)) return Risk.UI;
    if (type == PacketType.Play.Server.SPAWN_POSITION) return Risk.OTHER;

    String name = type.getName().toLowerCase(Locale.ROOT);
    if (name.contains("chunk") || name.contains("block") || name.contains("sign")
        || name.contains("light")) {
      return Risk.BLOCK_DATA;
    }
    if (name.startsWith("spawn_") || name.startsWith("entity_")
        || name.contains("waypoint") || name.contains("vibration")) {
      return Risk.ENTITY_DATA;
    }
    if (name.startsWith("player_") || name.contains("chat")
        || name.contains("camera") || name.contains("bed")) {
      return Risk.PLAYER_DATA;
    }
    if (name.contains("sound") || name.contains("particle")
        || name.contains("explosion") || name.contains("effect")) {
      return Risk.WORLD_EVENT;
    }
    if (name.contains("window") || name.contains("slot")
        || name.contains("inventory") || name.contains("merchant")) {
      return Risk.UI;
    }
    return Risk.OTHER;
  }
}
